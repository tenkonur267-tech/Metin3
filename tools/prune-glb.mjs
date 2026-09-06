/**
 * prune-glb.mjs — GLB'den kullanılmayan animasyonları ve mesh'leri atar.
 *
 * KayKit karakter paketleri 73 animasyon taşıyor; JSON bölümü ve tampon
 * verisinin dörtte üçünden fazlası buradan geliyor (3.6 MB dosyanın ~3.2 MB'ı).
 * Oyunda bir avuç klip kullanılıyor, gerisi tek dosyalık pakete boşuna
 * gömülüyordu.
 *
 * Kullanım:
 *   node tools/prune-glb.mjs girdi.glb çıktı.glb --klip=Idle,Walking_A,...
 *                                                [--mesh-cikar=Mug,Badge_Shield]
 */
import fs from 'node:fs';

const [girdi, cikti, ...bayraklar] = process.argv.slice(2);
if (!girdi || !cikti) {
  console.error('kullanım: prune-glb.mjs girdi.glb çıktı.glb --klip=A,B,C');
  process.exit(1);
}
const bayrak = (ad) => {
  const b = bayraklar.find((x) => x.startsWith(`--${ad}=`));
  return b ? b.slice(ad.length + 3).split(',').filter(Boolean) : null;
};
const tutulacakKlipler = bayrak('klip');
const atilacakMeshler = new Set(bayrak('mesh-cikar') || []);

/* ---- Oku ---- */
const ham = fs.readFileSync(girdi);
if (ham.readUInt32LE(0) !== 0x46546c67) throw new Error('GLB değil');
const jsonUz = ham.readUInt32LE(12);
const j = JSON.parse(ham.slice(20, 20 + jsonUz).toString('utf8'));
const binBas = 20 + jsonUz + 8;
const binUz = ham.readUInt32LE(20 + jsonUz);
const bin = ham.slice(binBas, binBas + binUz);

/* ---- Animasyonları süz ---- */
if (tutulacakKlipler) {
  const tut = new Set(tutulacakKlipler);
  const eksik = tutulacakKlipler.filter((k) => !(j.animations || []).some((a) => a.name === k));
  if (eksik.length) console.warn('  uyarı: bulunamayan klip:', eksik.join(', '));
  j.animations = (j.animations || []).filter((a) => tut.has(a.name));
}

/* ---- Mesh düğümlerini süz (düğüm silinmiyor, mesh bağı kaldırılıyor) ---- */
if (atilacakMeshler.size) {
  for (const n of j.nodes) {
    if (n.mesh !== undefined && atilacakMeshler.has(n.name)) delete n.mesh;
  }
}

/* ---- Kullanılan accessor'ları topla ---- */
const accKullanim = new Set();
const meshKullanim = new Set();
for (const n of j.nodes) if (n.mesh !== undefined) meshKullanim.add(n.mesh);
j.meshes = (j.meshes || []).map((m, i) => (meshKullanim.has(i) ? m : null));

for (const [i, m] of (j.meshes || []).entries()) {
  if (!m) continue;
  for (const p of m.primitives) {
    for (const a of Object.values(p.attributes)) accKullanim.add(a);
    if (p.indices !== undefined) accKullanim.add(p.indices);
    for (const t of p.targets || []) for (const a of Object.values(t)) accKullanim.add(a);
  }
}
for (const s of j.skins || []) {
  if (s.inverseBindMatrices !== undefined) accKullanim.add(s.inverseBindMatrices);
}
for (const a of j.animations || []) {
  for (const s of a.samplers) { accKullanim.add(s.input); accKullanim.add(s.output); }
}

/* ---- Kullanılan bufferView'lar ---- */
const bvKullanim = new Set();
for (const i of accKullanim) {
  const acc = j.accessors[i];
  if (acc && acc.bufferView !== undefined) bvKullanim.add(acc.bufferView);
}
for (const img of j.images || []) if (img.bufferView !== undefined) bvKullanim.add(img.bufferView);

/* ---- Yeniden numaralandır ve tamponu yeniden kur ---- */
const bvYeni = new Map();
const parcalar = [];
let uzunluk = 0;
for (const [i, bv] of j.bufferViews.entries()) {
  if (!bvKullanim.has(i)) continue;
  const hiza = (4 - (uzunluk % 4)) % 4;
  if (hiza) { parcalar.push(Buffer.alloc(hiza)); uzunluk += hiza; }
  const veri = bin.slice(bv.byteOffset || 0, (bv.byteOffset || 0) + bv.byteLength);
  bvYeni.set(i, { indeks: bvYeni.size, byteOffset: uzunluk, byteLength: bv.byteLength,
    byteStride: bv.byteStride, target: bv.target });
  parcalar.push(veri);
  uzunluk += bv.byteLength;
}
const yeniBin = Buffer.concat(parcalar);
j.bufferViews = [...bvYeni.values()].map((v) => {
  const o = { buffer: 0, byteOffset: v.byteOffset, byteLength: v.byteLength };
  if (v.byteStride !== undefined) o.byteStride = v.byteStride;
  if (v.target !== undefined) o.target = v.target;
  return o;
});

const accYeni = new Map();
const yeniAcc = [];
for (const [i, acc] of j.accessors.entries()) {
  if (!accKullanim.has(i)) continue;
  const kopya = { ...acc };
  if (acc.bufferView !== undefined) kopya.bufferView = bvYeni.get(acc.bufferView).indeks;
  accYeni.set(i, yeniAcc.length);
  yeniAcc.push(kopya);
}
j.accessors = yeniAcc;

const meshYeni = new Map();
const yeniMesh = [];
for (const [i, m] of j.meshes.entries()) {
  if (!m) continue;
  for (const p of m.primitives) {
    for (const k of Object.keys(p.attributes)) p.attributes[k] = accYeni.get(p.attributes[k]);
    if (p.indices !== undefined) p.indices = accYeni.get(p.indices);
    for (const t of p.targets || []) for (const k of Object.keys(t)) t[k] = accYeni.get(t[k]);
  }
  meshYeni.set(i, yeniMesh.length);
  yeniMesh.push(m);
}
j.meshes = yeniMesh;
for (const n of j.nodes) if (n.mesh !== undefined) n.mesh = meshYeni.get(n.mesh);
for (const s of j.skins || []) {
  if (s.inverseBindMatrices !== undefined) s.inverseBindMatrices = accYeni.get(s.inverseBindMatrices);
}
for (const a of j.animations || []) {
  for (const s of a.samplers) { s.input = accYeni.get(s.input); s.output = accYeni.get(s.output); }
}
for (const img of j.images || []) {
  if (img.bufferView !== undefined) img.bufferView = bvYeni.get(img.bufferView).indeks;
}
j.buffers = [{ byteLength: yeniBin.length }];

/* ---- Yaz ---- */
let jsonMetin = JSON.stringify(j);
while (jsonMetin.length % 4) jsonMetin += ' ';
const jsonBuf = Buffer.from(jsonMetin, 'utf8');
const binPad = (4 - (yeniBin.length % 4)) % 4;
const binBuf = Buffer.concat([yeniBin, Buffer.alloc(binPad)]);
const bas = Buffer.alloc(12);
bas.writeUInt32LE(0x46546c67, 0); bas.writeUInt32LE(2, 4);
bas.writeUInt32LE(12 + 8 + jsonBuf.length + 8 + binBuf.length, 8);
const jbas = Buffer.alloc(8);
jbas.writeUInt32LE(jsonBuf.length, 0); jbas.writeUInt32LE(0x4e4f534a, 4);
const bbas = Buffer.alloc(8);
bbas.writeUInt32LE(binBuf.length, 0); bbas.writeUInt32LE(0x004e4942, 4);
fs.writeFileSync(cikti, Buffer.concat([bas, jbas, jsonBuf, bbas, binBuf]));

const eski = fs.statSync(girdi).size, yeni = fs.statSync(cikti).size;
console.log(`${girdi.split('/').pop()} -> ${cikti.split('/').pop()}: `
  + `${(eski / 1e6).toFixed(2)}MB -> ${(yeni / 1e6).toFixed(2)}MB `
  + `(%${Math.round(100 - 100 * yeni / eski)} küçüldü), `
  + `${(j.animations || []).length} klip, ${j.meshes.length} mesh`);
