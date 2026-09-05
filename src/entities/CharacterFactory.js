/**
 * CharacterFactory.js — Hangi karakterin kullanılacağına karar verir.
 *
 * `assets/characters/warrior.json` varsa oradaki model yüklenir; dosya yoksa
 * ya da yükleme başarısız olursa oyun prosedürel `Warrior` ile çalışmaya
 * devam eder. Böylece depoya model eklemek isteğe bağlı kalır ve eksik ya
 * da bozuk bir dosya oyunu açılmaz hale getirmez.
 */
import { Warrior } from './Warrior.js';
import { ModelCharacter } from './ModelCharacter.js';

const CONFIG_URL = 'assets/characters/warrior.json';

/**
 * Karakter modelini yüklemeyi dener.
 * @returns {Promise<{character: ModelCharacter, cfg: object}|null>}
 */
export async function tryLoadCharacterModel(onStatus = () => {}) {
  // Tek dosyalık paket sürümünde yanına konacak bir assets klasörü yok;
  // olmayan dosyayı istemek konsola gereksiz 404 düşürür.
  if (globalThis.__METIN3_SINGLE_FILE__) return null;

  let cfg;
  try {
    const res = await fetch(CONFIG_URL, { cache: 'no-cache' });
    if (!res.ok) return null;          // yapılandırma yok: prosedürel karakter
    cfg = await res.json();
  } catch {
    return null;
  }

  if (!cfg || !cfg.file || cfg.enabled === false) return null;

  // Yollar yapılandırma dosyasına göre çözülür
  const base = CONFIG_URL.replace(/[^/]+$/, '');
  const resolve = (p) => (/^(https?:)?\/\//.test(p) || p.startsWith('/') ? p : base + p);
  cfg = {
    ...cfg,
    file: resolve(cfg.file),
    animationFiles: (cfg.animationFiles || []).map(resolve),
  };

  try {
    onStatus(`Karakter modeli yükleniyor: ${cfg.file.split('/').pop()}`);
    const character = await ModelCharacter.load(cfg);
    console.info('[karakter] model yüklendi:', cfg.file);
    console.info('[karakter] animasyon eşleşmesi:\n  ' + character.clipReport.join('\n  '));
    return { character, cfg };
  } catch (err) {
    // Model bozuksa oyunu düşürmek yerine prosedürel karaktere dön
    console.warn('[karakter] model yüklenemedi, prosedürel karaktere dönülüyor:', err.message);
    return null;
  }
}

/** Prosedürel savaşçı. */
export function createProceduralWarrior(kingdom, opts = {}) {
  return new Warrior(kingdom.armor, { weapon: 'twohand', ...opts });
}
