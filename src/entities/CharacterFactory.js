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
import { RiggedCharacter } from './RiggedCharacter.js';

/**
 * İki tür dış model desteği var:
 *
 *   'animated'  Model kendi animasyon kliplerini getirir (KayKit gibi hazır
 *               karakterler). ModelCharacter kullanılır.
 *   'rigged'    Model yalnızca deri ve iskelet getirir; hareket bizim poz
 *               kütüphanemizden gelir ve üstüne zırh takılabilir.
 *               RiggedCharacter kullanılır.
 */
const KINDS = { animated: ModelCharacter, rigged: RiggedCharacter };

const CONFIG_URL = 'assets/characters/warrior.json';

/**
 * Karakter modelini yüklemeyi dener.
 * @returns {Promise<{character: ModelCharacter, cfg: object}|null>}
 */
export async function tryLoadCharacterModel(onStatus = () => {}) {
  // Tek dosyalık paket sürümünde model, yapılandırmasıyla birlikte pakete
  // gömülür (bkz. tools/build-single.mjs); ağdan istenecek bir şey yoktur.
  const embedded = globalThis.__METIN3_CHARACTER__;
  if (embedded) {
    try {
      onStatus('Karakter modeli hazırlanıyor…');
      const Kind = KINDS[embedded.kind || 'animated'] || ModelCharacter;
      const character = await Kind.load(embedded);
      console.info('[karakter] gömülü model yüklendi:', embedded.kind || 'animated');
      if (character.clipReport) {
        console.info('[karakter] animasyon eşleşmesi:\n  ' + character.clipReport.join('\n  '));
      }
      return { character, cfg: embedded };
    } catch (err) {
      /*
       * Hata metnini de dışarı taşı: bu düşüş sessiz kaldığında oyuncu
       * yedek görünümde oynadığını bilmiyordu ve nedenini kimse göremiyordu.
       */
      console.warn('[karakter] gömülü model yüklenemedi:', err.message);
      globalThis.__METIN3_MODEL_HATASI__ = String(err && err.message || err);
      return null;
    }
  }
  // Yanına konacak bir assets klasörü yoksa olmayan dosyayı isteme
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
    const Kind = KINDS[cfg.kind || 'animated'] || ModelCharacter;
    const character = await Kind.load(cfg);
    console.info('[karakter] model yüklendi:', cfg.file, `(${cfg.kind || 'animated'})`);
    if (character.clipReport) {
      console.info('[karakter] animasyon eşleşmesi:\n  ' + character.clipReport.join('\n  '));
    }
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
