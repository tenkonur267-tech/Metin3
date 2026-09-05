/**
 * Architecture.js — Doğu Asya mimarisi için modüler yapı parçaları.
 *
 * Metin2'nin görsel imzası kıvrık saçaklı kiremit çatılar, kırmızı lake
 * sütunlar ve taş temellerdir. Buradaki üreteçler bu parçaları parametrik
 * olarak kurar; köyler bunları birleştirerek oluşturulur.
 */
import * as THREE from 'three';
import { getTexture } from '../core/Textures.js';
import { applyPlanarUV, scaleCylinderUV } from '../core/GeoUtils.js';

/* ------------------------------------------------------------------ */
/* Kıvrık saçaklı çatı                                                  */
/* ------------------------------------------------------------------ */
/**
 * Klasik pagoda çatısı. Mahyadan saçağa doğru dikleşen değil, tam tersine
 * yumuşayan bir eğri kullanılır (üstte dik, altta yayvan) ve köşeler
 * yukarı kıvrılır — silueti "Asya çatısı" yapan şey budur.
 *
 * @param {number} w     taban genişliği (X)
 * @param {number} d     taban derinliği (Z)
 * @param {number} h     çatı yüksekliği
 * @param {object} o     seçenekler
 */
export function makeRoof(w, d, h, o = {}) {
  const {
    overhang = 0.55,     // saçağın duvardan taşma miktarı
    ridgeW = 0.12,       // mahya genişliğinin tabana oranı (0 = piramit)
    ridgeD = 0.12,
    curl = 0.42,         // köşe kıvrılma yüksekliği
    sag = 1.75,          // eğri üssü: >1 => üstte dik, altta yayvan
    rings = 10,          // dikey bölüntü
    perSide = 6,         // kenar başına yatay bölüntü
  } = o;

  const topX = (w * 0.5) * ridgeW;
  const topZ = (d * 0.5) * ridgeD;
  const botX = w * 0.5 + overhang;
  const botZ = d * 0.5 + overhang;

  // Bir halkanın çevresini dolaşan noktalar (dikdörtgen kontur)
  // u ∈ [0,1) çevre parametresi -> (nx, nz) birim kare üzerinde
  const perim = [];
  const sides = [
    [-1, -1, 1, -1], // arka
    [1, -1, 1, 1],   // sağ
    [1, 1, -1, 1],   // ön
    [-1, 1, -1, -1], // sol
  ];
  for (const [ax, az, bx, bz] of sides) {
    for (let i = 0; i < perSide; i++) {
      const t = i / perSide;
      perim.push([ax + (bx - ax) * t, az + (bz - az) * t]);
    }
  }
  perim.push(perim[0].slice()); // dikişi kapat (UV sürekliliği için ayrı nokta)
  const P = perim.length;

  const pos = [];
  const uvs = [];
  const idx = [];

  // Kiremit sıraları her çatıda aynı fiziksel boyutta görünsün diye UV'ler
  // dünya birimiyle ölçekleniyor (bir doku tekrarı = TILE birim).
  const TILE = 2.4;
  const perimLen = 4 * (botX + botZ);
  const slopeLen = Math.hypot(h, ((botX - topX) + (botZ - topZ)) * 0.5);

  for (let r = 0; r <= rings; r++) {
    const t = r / rings;                     // 0 = mahya, 1 = saçak
    const y = h * Math.pow(1 - t, sag);
    const ex = topX + (botX - topX) * t;
    const ez = topZ + (botZ - topZ) * t;
    for (let p = 0; p < P; p++) {
      const [nx, nz] = perim[p];
      // Köşeye yakınlık: kenar ortasında 0, köşede 1
      const corner = Math.min(Math.abs(nx), Math.abs(nz));
      const lift = curl * h * Math.pow(corner, 2.0) * Math.pow(t, 2.6);
      pos.push(nx * ex, y + lift, nz * ez);
      uvs.push((p / (P - 1)) * perimLen / TILE, t * slopeLen / TILE);
    }
  }

  for (let r = 0; r < rings; r++) {
    for (let p = 0; p < P - 1; p++) {
      const a = r * P + p;
      const b = a + 1;
      const c = (r + 1) * P + p;
      const dd = c + 1;
      idx.push(a, c, b, b, c, dd);
    }
  }

  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  geo.setIndex(idx);
  geo.computeVertexNormals();
  return geo;
}

/** Mahya sırtı — çatının tepesindeki kalın kiremit bant. */
export function makeRidgeCap(w, d, h, o = {}) {
  const { ridgeW = 0.12, ridgeD = 0.12, thick = 0.16 } = o;
  const rw = Math.max(w * ridgeW * 2, 0.3) + thick;
  const rd = Math.max(d * ridgeD * 2, 0.3) + thick;
  const g = new THREE.BoxGeometry(rw, thick * 1.6, rd);
  applyPlanarUV(g, 1.2);
  g.translate(0, h + thick * 0.5, 0);
  return g;
}

/* ------------------------------------------------------------------ */
/* Materyal seti                                                        */
/* ------------------------------------------------------------------ */
export function makeMaterials(theme) {
  const M = (map, extra = {}) =>
    new THREE.MeshLambertMaterial({ map, ...extra });

  return {
    // UV'ler artık dünya ölçeğinde olduğu için doku tekrarı 1'de kalıyor;
    // ince ayar repeat ile değil, buradaki çarpanlarla yapılıyor.
    roof: M(getTexture('roof', { a: theme.roofA, b: theme.roofB, repeat: 1 })),
    wood: M(getTexture('wood', { a: theme.woodA, b: theme.woodB, repeat: 1 })),
    pillar: new THREE.MeshLambertMaterial({
      color: theme.pillar,
      map: getTexture('wood', { a: theme.woodA, b: theme.woodB, repeat: 1 }),
    }),
    stone: M(getTexture('stone', { a: theme.stoneA, b: theme.stoneB, repeat: 1 })),
    plaster: M(getTexture('plaster', { a: theme.plaster, repeat: 0.7 })),
    paper: new THREE.MeshLambertMaterial({
      map: getTexture('paper', { a: theme.paper, b: theme.woodB, repeat: 1 }),
      emissive: new THREE.Color(theme.accent).multiplyScalar(0.10),
    }),
    accent: new THREE.MeshLambertMaterial({ color: theme.accent }),
    trim: new THREE.MeshLambertMaterial({ color: theme.trim }),
    banner: new THREE.MeshLambertMaterial({
      map: getTexture('banner', { a: theme.accent, b: theme.trim }),
      side: THREE.DoubleSide,
    }),
    // Fenerler kendi ışığını yayıyormuş gibi görünsün ama yine de sahnenin
    // ışığından etkilensin diye emissive'li Lambert kullanılıyor.
    lantern: new THREE.MeshLambertMaterial({
      color: new THREE.Color(theme.lantern).multiplyScalar(0.55),
      emissive: new THREE.Color(theme.lantern),
      emissiveIntensity: 0.85,
    }),
  };
}

/* ------------------------------------------------------------------ */
/* Yapı parçaları — hepsi { geo, mat } listesi döndürür ve                */
/* köy kurucusu bunları tek bir merged mesh'e toplar.                   */
/* ------------------------------------------------------------------ */

/** Yatay eksende döndürülmüş kutu yardımcı. */
const UV_SCALE = 2.2;   // bir doku tekrarının kapladığı dünya birimi

function box(w, h, d, x, y, z, ry = 0) {
  const g = new THREE.BoxGeometry(w, h, d);
  applyPlanarUV(g, UV_SCALE);
  if (ry) g.rotateY(ry);
  g.translate(x, y, z);
  return g;
}

function cyl(rt, rb, h, seg, x, y, z) {
  const g = new THREE.CylinderGeometry(rt, rb, h, seg);
  scaleCylinderUV(g, (rt + rb) * 0.5, h, UV_SCALE);
  g.translate(x, y, z);
  return g;
}

/** Bir parça listesini yerinde döndürüp öteler. */
function transformParts(parts, x, y, z, ry = 0) {
  const m = new THREE.Matrix4().compose(
    new THREE.Vector3(x, y, z),
    new THREE.Quaternion().setFromEuler(new THREE.Euler(0, ry, 0)),
    new THREE.Vector3(1, 1, 1));
  for (const p of parts) p.geo.applyMatrix4(m);
  return parts;
}

/**
 * Ortasında kağıt kapı/pencere açıklığı olan çerçeveli duvar paneli.
 * X ekseni boyunca uzanır, +Z yönüne bakar; tabanı y=0'dadır.
 *
 * Düz bir sıva yüzeyi büyük ölçekte boş bir pano gibi görünüyor; ahşap
 * dikmeler ve yatay kuşaklar hem ölçeği okunur kılıyor hem de dönem
 * mimarisinin karkas dokusunu veriyor.
 */
function panelWall(mats, len, h, t, openW, openH, o = {}) {
  const { paper = true, sill = 0 } = o;
  const out = [];
  const a = (g, m) => out.push({ geo: g, mat: m });

  const ow = Math.min(openW, len - 0.6);
  const sideW = (len - ow) / 2;
  if (sideW > 0.05) {
    for (const s of [-1, 1]) {
      a(box(sideW, h, t, s * (ow / 2 + sideW / 2), h / 2, 0), mats.plaster);
    }
  }
  // Açıklık: eşik altı dolgu, kağıt panel, üstünde alınlık
  if (sill > 0.02) a(box(ow, sill, t, 0, sill / 2, 0), mats.plaster);
  const oh = Math.min(openH, h - sill - 0.15);
  a(box(ow, oh, t * 0.55, 0, sill + oh / 2, 0), paper ? mats.paper : mats.plaster);
  const top = h - sill - oh;
  if (top > 0.05) a(box(ow, top, t, 0, sill + oh + top / 2, 0), mats.plaster);

  // Ahşap karkas
  const ft = t * 1.35;
  a(box(0.13, h, ft, -ow / 2 - 0.065, h / 2, 0), mats.wood);
  a(box(0.13, h, ft, ow / 2 + 0.065, h / 2, 0), mats.wood);
  a(box(len, 0.15, ft, 0, sill + oh + 0.075, 0), mats.wood);
  a(box(len, 0.14, ft, 0, h - 0.07, 0), mats.wood);
  a(box(len, 0.13, ft, 0, 0.065, 0), mats.wood);
  if (sill > 0.02) a(box(len, 0.12, ft, 0, sill, 0), mats.wood);
  return out;
}

/** Kat çevresini saran alçak korkuluk. */
function railing(mats, w, d, h = 0.72) {
  const out = [];
  const a = (g, m) => out.push({ geo: g, mat: m });
  const hw = w / 2, hd = d / 2;
  for (const [len, x, z, ry] of [
    [w, 0, hd, 0], [w, 0, -hd, 0], [d, hw, 0, Math.PI / 2], [d, -hw, 0, Math.PI / 2],
  ]) {
    const n = Math.max(3, Math.round(len / 0.7));
    for (let i = 0; i <= n; i++) {
      const o = -len / 2 + (i / n) * len;
      const px = x + Math.cos(ry) * o;
      const pz = z - Math.sin(ry) * o;
      a(box(0.075, h, 0.075, px, h / 2, pz), mats.trim);
    }
    a(box(len, 0.09, 0.12, x, h, z, ry), mats.wood);
    a(box(len, 0.07, 0.10, x, h * 0.55, z, ry), mats.wood);
  }
  return out;
}

/**
 * Tek katlı köy evi / dükkân.
 * Kırmızı lake sütunlar, sıvalı dolgu duvarlar, kağıt kapı ve kıvrık çatı.
 */
export function buildHouse(mats, opts = {}) {
  const {
    w = 6, d = 5, wallH = 3.0,
    roofH = 2.4, plinth = 0.45,
    porch = true,
  } = opts;

  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });

  // Taş temel
  add(box(w + 0.9, plinth, d + 0.9, 0, plinth / 2, 0), mats.stone);
  // Ahşap döşeme
  add(box(w + 0.4, 0.14, d + 0.4, 0, plinth + 0.07, 0), mats.wood);

  const y0 = plinth + 0.14;
  const hy = y0 + wallH / 2;

  // Köşe sütunları
  const pr = 0.19;
  const px = w / 2 - pr, pz = d / 2 - pr;
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    add(cyl(pr, pr * 1.12, wallH, 8, sx * px, hy, sz * pz), mats.pillar);
  }

  // Duvarlar: ön yüzde kapı, yanlarda kafesli pencere, arkada sağır panel
  const t = 0.16;
  const doorW = Math.min(2.2, w * 0.42);
  const winW = Math.min(2.0, d * 0.5);
  const iw = w - pr * 2;
  const idp = d - pr * 2;

  // Ön (+Z): kapı
  parts.push(...transformParts(
    panelWall(mats, iw, wallH, t, doorW, wallH * 0.78),
    0, y0, d / 2 - t / 2, 0));
  // Arka (-Z): dar sağır pencere
  parts.push(...transformParts(
    panelWall(mats, iw, wallH, t, iw * 0.3, wallH * 0.34, { paper: false, sill: wallH * 0.45 }),
    0, y0, -d / 2 + t / 2, Math.PI));
  // Yanlar: kağıt pencere
  for (const s of [-1, 1]) {
    parts.push(...transformParts(
      panelWall(mats, idp, wallH, t, winW, wallH * 0.4, { sill: wallH * 0.4 }),
      s * (w / 2 - t / 2), y0, 0, s * Math.PI / 2));
  }

  // Üst kuşak kirişi (çatıyı taşıyan bant)
  const beamY = y0 + wallH + 0.13;
  add(box(w + 0.5, 0.26, d + 0.5, 0, beamY, 0), mats.wood);
  // Dişli konsollar (dougong benzeri)
  const nb = Math.max(3, Math.round(w / 1.2));
  for (let i = 0; i < nb; i++) {
    const x = -w / 2 + (i + 0.5) * (w / nb);
    for (const sz of [-1, 1]) {
      add(box(0.22, 0.34, 0.62, x, beamY + 0.28, sz * (d / 2 + 0.1)), mats.trim);
    }
  }

  // Çatı
  const roofY = beamY + 0.26;
  const rg = makeRoof(w + 0.3, d + 0.3, roofH, { overhang: 0.8, curl: 0.46 });
  rg.translate(0, roofY, 0);
  add(rg, mats.roof);
  const cap = makeRidgeCap(w + 0.3, d + 0.3, roofH);
  cap.translate(0, roofY, 0);
  add(cap, mats.trim);

  // Sundurma (giriş saçağı)
  if (porch) {
    const pw = doorW + 1.4, pd = 1.5;
    const pzc = d / 2 + pd / 2;
    const ph = wallH * 0.72;
    for (const s of [-1, 1]) {
      add(cyl(0.13, 0.15, ph, 8, s * (pw / 2 - 0.15), y0 + ph / 2, pzc + pd / 2 - 0.2), mats.pillar);
    }
    const prg = makeRoof(pw, pd + 0.6, 0.85, { overhang: 0.45, curl: 0.5, rings: 7 });
    prg.translate(0, y0 + ph, pzc);
    add(prg, mats.roof);
    // Basamak
    add(box(doorW + 0.6, 0.16, 0.7, 0, 0.08, d / 2 + 0.35), mats.stone);
  }

  return parts;
}

/**
 * Çok katlı pagoda / tapınak kulesi — köy meydanının simgesi.
 */
export function buildPagoda(mats, opts = {}) {
  const { levels = 4, baseW = 9, baseH = 3.2, shrink = 0.78 } = opts;
  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });

  // Geniş taş kaide
  add(box(baseW + 3.2, 0.7, baseW + 3.2, 0, 0.35, 0), mats.stone);
  add(box(baseW + 2.2, 0.35, baseW + 2.2, 0, 0.85, 0), mats.stone);

  let y = 1.02;
  let w = baseW;
  for (let l = 0; l < levels; l++) {
    const h = baseH * Math.pow(0.88, l);
    const pr = 0.2 * Math.pow(shrink, l * 0.5);

    // Kat sütunları (3x3 ızgara kenarları)
    const cols = 3;
    for (let i = 0; i < cols; i++) for (let j = 0; j < cols; j++) {
      if (i !== 0 && i !== cols - 1 && j !== 0 && j !== cols - 1) continue;
      const x = (-w / 2 + 0.35) + (i / (cols - 1)) * (w - 0.7);
      const z = (-w / 2 + 0.35) + (j / (cols - 1)) * (w - 0.7);
      add(cyl(pr, pr * 1.1, h, 8, x, y + h / 2, z), mats.pillar);
    }
    // Kat cepheleri: dört yönde de ahşap karkaslı, ortası kağıt kapılı panel.
    // Zemin katta açıklık gerçek bir giriş, üst katlarda pencere yüksekliğinde.
    const t = 0.15;
    const iw = w - 0.7;
    const openW = Math.min(iw * 0.44, 3.0);
    const wh = h * 0.92;
    const isGround = l === 0;
    for (let side = 0; side < 4; side++) {
      const ry = side * Math.PI / 2;
      const off = w / 2 - 0.35;
      parts.push(...transformParts(
        panelWall(mats, iw, wh, t, openW,
          isGround ? wh * 0.74 : wh * 0.46,
          { sill: isGround ? 0 : wh * 0.30 }),
        Math.sin(ry) * off, y, Math.cos(ry) * off, ry));
    }
    // Kat çevresi korkuluğu (üst katlarda gezinti balkonu izlenimi)
    if (!isGround) {
      parts.push(...transformParts(
        railing(mats, w + 1.5, w + 1.5, 0.62), 0, y - 0.1, 0, 0));
    }

    // Kuşak + konsollar
    const by = y + h;
    add(box(w + 0.4, 0.24, w + 0.4, 0, by + 0.12, 0), mats.wood);
    const nb = Math.max(3, Math.round(w / 1.5));
    for (let i = 0; i < nb; i++) {
      const p = -w / 2 + (i + 0.5) * (w / nb);
      add(box(0.2, 0.3, 0.55, p, by + 0.34, w / 2 + 0.16), mats.trim);
      add(box(0.2, 0.3, 0.55, p, by + 0.34, -w / 2 - 0.16), mats.trim);
      add(box(0.55, 0.3, 0.2, w / 2 + 0.16, by + 0.34, p), mats.trim);
      add(box(0.55, 0.3, 0.2, -w / 2 - 0.16, by + 0.34, p), mats.trim);
    }

    // Kat çatısı
    const rh = 1.5 * Math.pow(0.93, l);
    const isTop = l === levels - 1;
    const rg = makeRoof(w + 0.5, w + 0.5, isTop ? rh * 1.6 : rh, {
      overhang: 1.1, curl: 0.55, ridgeW: isTop ? 0.04 : 0.16, ridgeD: isTop ? 0.04 : 0.16,
    });
    rg.translate(0, by + 0.24, 0);
    add(rg, mats.roof);

    y = by + 0.24 + (isTop ? 0 : rh * 0.36);
    w *= shrink;
  }

  // Tepe direği (sorin)
  const topY = y + 1.9;
  add(cyl(0.06, 0.1, 2.4, 6, 0, topY, 0), mats.trim);
  for (let i = 0; i < 4; i++) {
    add(new THREE.TorusGeometry(0.3 - i * 0.05, 0.05, 5, 12)
      .rotateX(Math.PI / 2)
      .translate(0, topY + 0.25 + i * 0.35, 0), mats.trim);
  }
  add(new THREE.SphereGeometry(0.22, 10, 8).translate(0, topY + 1.35, 0), mats.accent);

  return parts;
}

/**
 * Krallık kapısı (paifang) — köyün ana girişi, en görkemli yapı.
 */
export function buildGate(mats, opts = {}) {
  const { width = 14, height = 7.5, depth = 3 } = opts;
  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });

  const px = width / 2 - 1.0;
  // Taş kaideler + kalın sütunlar
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    const x = sx * px, z = sz * (depth / 2 - 0.5);
    add(box(1.7, 0.9, 1.7, x, 0.45, z), mats.stone);
    add(cyl(0.42, 0.5, height, 10, x, 0.9 + height / 2, z), mats.pillar);
    // Sütun bileziği
    add(cyl(0.5, 0.5, 0.28, 10, x, 0.9 + height * 0.62, z), mats.trim);
  }

  // Üst kirişler
  const by = 0.9 + height;
  add(box(width + 1.6, 0.5, depth + 1.0, 0, by + 0.25, 0), mats.wood);
  add(box(width + 0.6, 0.34, depth + 0.4, 0, by - 0.4, 0), mats.wood);
  // Levha (isim tabelası)
  add(box(width * 0.34, 1.15, 0.22, 0, by - 1.35, depth / 2 + 0.3), mats.accent);
  add(box(width * 0.34 + 0.22, 1.35, 0.12, 0, by - 1.35, depth / 2 + 0.22), mats.trim);

  // Ana çatı
  const rg = makeRoof(width + 2.2, depth + 1.8, 2.6, { overhang: 1.3, curl: 0.6 });
  rg.translate(0, by + 0.5, 0);
  add(rg, mats.roof);
  const cap = makeRidgeCap(width + 2.2, depth + 1.8, 2.6);
  cap.translate(0, by + 0.5, 0);
  add(cap, mats.trim);

  // Yan küçük çatılar (üç açıklıklı paifang görünümü)
  for (const s of [-1, 1]) {
    const srg = makeRoof(4.6, depth + 1.2, 1.5, { overhang: 0.9, curl: 0.6, rings: 8 });
    srg.translate(s * (width / 2 + 1.4), by - 2.2, 0);
    add(srg, mats.roof);
    add(box(0.34, 3.0, 0.34, s * (width / 2 + 1.4), by - 3.7, 0), mats.pillar);
  }

  return parts;
}

/**
 * Sur duvarı parçası — kule ve mazgallı siper.
 */
export function buildWallSegment(mats, len, h = 4.2) {
  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });
  add(box(len, h, 1.6, 0, h / 2, 0), mats.stone);
  add(box(len, 0.3, 2.1, 0, h + 0.15, 0), mats.stone);
  // Mazgallar
  const n = Math.max(2, Math.floor(len / 1.6));
  for (let i = 0; i < n; i++) {
    const x = -len / 2 + (i + 0.5) * (len / n);
    add(box(len / n * 0.55, 0.7, 0.5, x, h + 0.65, -0.72), mats.stone);
    add(box(len / n * 0.55, 0.7, 0.5, x, h + 0.65, 0.72), mats.stone);
  }
  return parts;
}

export function buildWatchtower(mats, h = 7) {
  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });
  add(box(3.6, 0.6, 3.6, 0, 0.3, 0), mats.stone);
  add(box(3.0, h, 3.0, 0, 0.6 + h / 2, 0), mats.stone);
  add(box(3.8, 0.34, 3.8, 0, 0.6 + h + 0.17, 0), mats.wood);
  for (const sx of [-1, 1]) for (const sz of [-1, 1]) {
    add(cyl(0.15, 0.16, 2.2, 8, sx * 1.55, 0.6 + h + 1.4, sz * 1.55), mats.pillar);
  }
  const rg = makeRoof(4.4, 4.4, 1.7, { overhang: 0.9, curl: 0.55 });
  rg.translate(0, 0.6 + h + 2.5, 0);
  add(rg, mats.roof);
  return parts;
}

/** Taş fener (toro) — yollara ışık ve süs. */
export function buildStoneLantern(mats) {
  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });
  add(cyl(0.34, 0.42, 0.28, 8, 0, 0.14, 0), mats.stone);
  add(cyl(0.14, 0.16, 0.85, 8, 0, 0.7, 0), mats.stone);
  add(cyl(0.36, 0.28, 0.18, 8, 0, 1.22, 0), mats.stone);
  add(cyl(0.3, 0.3, 0.46, 6, 0, 1.54, 0), mats.lantern);
  add(cyl(0.05, 0.5, 0.42, 6, 0, 1.98, 0), mats.stone);
  add(new THREE.SphereGeometry(0.1, 8, 6).translate(0, 2.24, 0), mats.stone);
  return parts;
}

/** Asılı kırmızı fener direği. */
export function buildLanternPost(mats) {
  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });
  add(cyl(0.09, 0.12, 3.2, 8, 0, 1.6, 0), mats.wood);
  add(box(1.1, 0.1, 0.1, 0.45, 3.15, 0), mats.wood);
  const l = new THREE.SphereGeometry(0.3, 10, 8);
  l.scale(1, 1.25, 1);
  l.translate(0.9, 2.75, 0);
  add(l, mats.lantern);
  return parts;
}

/** Bayrak direği ve sancak. */
export function buildBanner(mats, h = 6) {
  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });
  add(cyl(0.1, 0.14, h, 8, 0, h / 2, 0), mats.wood);
  add(box(0.08, 2.6, 1.5, 0.12, h - 1.6, 0), mats.banner);
  add(new THREE.SphereGeometry(0.16, 8, 6).translate(0, h + 0.1, 0), mats.trim);
  return parts;
}

/** Basit tahta çit parçası. */
export function buildFence(mats, len = 4) {
  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });
  const n = Math.max(2, Math.round(len / 1.3));
  for (let i = 0; i <= n; i++) {
    const x = -len / 2 + (i / n) * len;
    add(box(0.14, 1.3, 0.14, x, 0.65, 0), mats.wood);
  }
  add(box(len, 0.1, 0.08, 0, 1.1, 0), mats.wood);
  add(box(len, 0.1, 0.08, 0, 0.6, 0), mats.wood);
  return parts;
}

/** Kuyu — köy meydanı detayı. */
export function buildWell(mats) {
  const parts = [];
  const add = (geo, mat) => parts.push({ geo, mat });
  add(new THREE.CylinderGeometry(1.1, 1.2, 1.0, 12, 1, true).translate(0, 0.5, 0), mats.stone);
  add(cyl(1.2, 1.2, 0.16, 12, 0, 1.02, 0), mats.stone);
  for (const s of [-1, 1]) add(box(0.16, 2.0, 0.16, s * 0.95, 2.0, 0), mats.wood);
  add(box(0.12, 0.12, 2.0, 0, 3.0, 0).rotateY(Math.PI / 2), mats.wood);
  const rg = makeRoof(2.8, 2.4, 0.8, { overhang: 0.4, curl: 0.5, rings: 7 });
  rg.translate(0, 3.0, 0);
  add(rg, mats.roof);
  return parts;
}
