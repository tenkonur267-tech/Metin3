/**
 * loadGltf.js — GLB/GLTF yükleme (data: URI'ler için ağ katmanı kullanmadan).
 *
 * Tek dosyalık paket sürümünde model, `data:model/gltf-binary;base64,...`
 * biçiminde sayfaya gömülü geliyor. GLTFLoader.loadAsync bunu almak için
 * fetch kullanıyor; yayınlanan sayfa ise sıkı bir içerik güvenlik politikası
 * (CSP) altında çalışıyor ve oradaki `connect-src` data: adreslerine giden
 * fetch'i engelleyebiliyor. Engellendiğinde model yüklenemiyor ve oyun sessizce
 * prosedürel yedek savaşçıya düşüyordu.
 *
 * Bu yüzden data: URI'ler burada JavaScript içinde çözülüp GLTFLoader.parse'a
 * doğrudan veriliyor: hiçbir ağ isteği yapılmıyor, CSP devreye girmiyor.
 */
import * as THREE from 'three';
import { GLTFLoader } from '../../vendor/three/jsm/loaders/GLTFLoader.js';

/** base64 gövdeyi ArrayBuffer'a çevirir. */
function base64ToBuffer(b64) {
  const bin = atob(b64);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf.buffer;
}

/** GLB'nin JSON bölümünü ve ikili bölümünün konumunu döndürür. */
function glbCoz(buf) {
  const dv = new DataView(buf);
  if (dv.getUint32(0, true) !== 0x46546c67) return null;
  const jsonUz = dv.getUint32(12, true);
  const json = JSON.parse(new TextDecoder().decode(new Uint8Array(buf, 20, jsonUz)));
  return { json, binBas: 20 + jsonUz + 8 };
}

/** Bayt dizisini data: URI'ye çevirir (fetch kullanmadan görüntü yüklemek için). */
function baytlarDataUri(bytes, mime) {
  let s = '';
  for (let i = 0; i < bytes.length; i += 0x8000) {
    s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  }
  return `data:${mime};base64,${btoa(s)}`;
}

/**
 * GLB içine gömülü dokuları elle bağlar.
 *
 * GLTFLoader gömülü görüntüyü blob adresine çevirip fetch ile alıyor; sayfanın
 * CSP'si `connect-src` ile bunu engelleyince doku sessizce eksik kalıyor ve
 * model bembeyaz görünüyordu. Burada görüntü baytları doğrudan buffer'dan
 * okunup `<img>` üzerinden (fetch değil) data: URI ile yükleniyor ve adına
 * göre materyale takılıyor.
 *
 * @param {object} gltf   ayrıştırılmış sonuç
 * @param {ArrayBuffer} buf  ham GLB
 */
async function dokulariBagla(gltf, buf) {
  const eksik = [];
  gltf.scene.traverse((o) => {
    const mats = Array.isArray(o.material) ? o.material : (o.material ? [o.material] : []);
    for (const m of mats) if (m && !m.map && m.name) eksik.push(m);
  });
  if (!eksik.length) return;

  const c = glbCoz(buf);
  if (!c) return;
  const { json, binBas } = c;

  const yukle = (uri) => new Promise((ok) => {
    const img = new Image();
    img.onload = () => ok(img);
    img.onerror = () => ok(null);
    img.src = uri;
  });

  const adaGore = new Map();
  for (const mat of json.materials || []) {
    const ti = mat.pbrMetallicRoughness?.baseColorTexture?.index;
    if (ti === undefined || !mat.name || adaGore.has(mat.name)) continue;
    const kaynak = json.textures?.[ti]?.source;
    const img = json.images?.[kaynak];
    if (!img || img.bufferView === undefined) continue;
    const bv = json.bufferViews[img.bufferView];
    const bytes = new Uint8Array(buf, binBas + (bv.byteOffset || 0), bv.byteLength);
    const el = await yukle(baytlarDataUri(bytes, img.mimeType || 'image/png'));
    if (!el) continue;
    const tex = new THREE.Texture(el);
    tex.flipY = false;                       // glTF dokuları ters çevrilmez
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.needsUpdate = true;
    adaGore.set(mat.name, tex);
  }
  for (const m of eksik) {
    const tex = adaGore.get(m.name);
    if (tex) { m.map = tex; m.needsUpdate = true; }
  }
}

/**
 * @param {string} url  normal bir yol ya da data: URI
 * @returns {Promise<object>} gltf
 */
export async function loadGltf(url) {
  const loader = new GLTFLoader();
  if (!url.startsWith('data:')) return loader.loadAsync(url);

  const virgul = url.indexOf(',');
  const bas = url.slice(0, virgul);
  const govde = url.slice(virgul + 1);
  if (!bas.includes(';base64')) {
    // base64 değilse metin gltf: doğrudan ayrıştırılabilir
    return new Promise((ok, hata) => loader.parse(decodeURIComponent(govde), '', ok, hata));
  }
  const buf = base64ToBuffer(govde);
  const gltf = await new Promise((ok, hata) => loader.parse(buf, '', ok, hata));
  await dokulariBagla(gltf, buf);
  return gltf;
}
