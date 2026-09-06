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
import { GLTFLoader } from '../../vendor/three/jsm/loaders/GLTFLoader.js';

/** base64 gövdeyi ArrayBuffer'a çevirir. */
function base64ToBuffer(b64) {
  const bin = atob(b64);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf.buffer;
}

/**
 * @param {string} url  normal bir yol ya da data: URI
 * @returns {Promise<object>} gltf
 */
export function loadGltf(url) {
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
  return new Promise((ok, hata) => loader.parse(buf, '', ok, hata));
}
