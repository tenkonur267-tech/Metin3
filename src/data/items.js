/**
 * items.js — Eşya tanımları.
 *
 * Metin2'nin düzenini izliyor: her eşyanın bir ekipman slotu, bir seviyesi
 * ve bir kademesi var. Kademe hem istatistikleri hem de rengi belirliyor,
 * böylece yerde duran bir eşyanın ne kadar iyi olduğu bakışta anlaşılıyor.
 */

/**
 * Ekipman slotları.
 *
 * `gorsel` bir ArmorSet görünüm grubunu adlandırıyor. Bir slota eşya
 * kuşanıldığında o gruptaki parçalar eşyanın kendi `gorunum` biçimiyle
 * yeniden üretilip kemiklere takılıyor; çıkarıldığında sökülüyor. Yani
 * envanterdeki her eşyanın oyunda kendi görüntüsü var.
 *
 * Takı slotlarının görseli yok: Metin2'de de kolye, küpe ve yüzük
 * karakterde görünmez, yalnızca istatistik verir.
 */
export const SLOTS = {
  silah:    { ad: 'Silah',     gorsel: 'sword',  simge: '⚔' },
  zirh:     { ad: 'Zırh',      gorsel: 'chest',  simge: '🥋' },
  migfer:   { ad: 'Miğfer',    gorsel: 'helmet', simge: '⛑' },
  kalkan:   { ad: 'Kalkan',    gorsel: 'shield', simge: '🛡' },
  kolluk:   { ad: 'Kolluk',    gorsel: 'bracer', simge: '🧤' },
  ayakkabi: { ad: 'Ayakkabı',  gorsel: 'boot',   simge: '🥾' },
  pelerin:  { ad: 'Pelerin',   gorsel: 'cape',   simge: '🧣' },
  kolye:    { ad: 'Kolye',     gorsel: null,     simge: '📿' },
  kupe:     { ad: 'Küpe',      gorsel: null,     simge: '💠' },
  yuzuk1:   { ad: 'Yüzük',     gorsel: null,     simge: '💍' },
  yuzuk2:   { ad: 'Yüzük',     gorsel: null,     simge: '💍' },
};

export const SLOT_ORDER = ['silah', 'zirh', 'migfer', 'kalkan', 'kolluk', 'ayakkabi',
  'pelerin', 'kolye', 'kupe', 'yuzuk1', 'yuzuk2'];

/** Karakterde parça değiştiren gruplar (ArmorSet görünüm tabloları). */
export const GORSEL_GRUPLAR = ['sword', 'chest', 'helmet', 'shield', 'bracer', 'boot', 'cape'];

/**
 * Kademeler: ad öneki, renk ve istatistik çarpanı.
 * Metin2'deki "+0..+9" yerine kademe adı kullanılıyor; daha okunur.
 */
export const TIERS = [
  { ad: 'Yıpranmış', renk: 0x9a9a94, hex: '#9a9a94', carpan: 0.75, agirlik: 40 },
  { ad: '',          renk: 0xd8d3c4, hex: '#d8d3c4', carpan: 1.00, agirlik: 34 },
  { ad: 'Sağlam',    renk: 0x6ec177, hex: '#6ec177', carpan: 1.35, agirlik: 16 },
  { ad: 'Usta İşi',  renk: 0x5aa9e6, hex: '#5aa9e6', carpan: 1.80, agirlik: 7 },
  { ad: 'Efsanevi',  renk: 0xc77dff, hex: '#c77dff', carpan: 2.50, agirlik: 3 },
];

/**
 * Eşya şablonları. `taban` değerleri seviye 1 içindir; seviye ve kademe
 * ile ölçeklenir.
 */
export const TEMPLATES = [
  // Silahlar — her biri kendi modeliyle elde görünür
  { id: 'kilic',    ad: 'Çelik Kılıç',      slot: 'silah',    gorunum: 'kilic',   tabanSaldiri: 12, tabanSavunma: 0 },
  { id: 'buyukkil', ad: 'Çift El Kılıcı',   slot: 'silah',    gorunum: 'buyuk',   tabanSaldiri: 18, tabanSavunma: 0 },
  { id: 'balta',    ad: 'Savaş Baltası',    slot: 'silah',    gorunum: 'balta',   tabanSaldiri: 15, tabanSavunma: 0 },
  { id: 'mizrak',   ad: 'Ejder Mızrağı',    slot: 'silah',    gorunum: 'mizrak',  tabanSaldiri: 16, tabanSavunma: 1 },
  // Zırhlar
  { id: 'zirh',     ad: 'Lamel Zırh',       slot: 'zirh',     gorunum: 'lamel',   tabanSaldiri: 0,  tabanSavunma: 10 },
  { id: 'gogusluk', ad: 'Pul Göğüslük',     slot: 'zirh',     gorunum: 'pul',     tabanSaldiri: 0,  tabanSavunma: 13 },
  { id: 'derizirh', ad: 'Deri Zırh',        slot: 'zirh',     gorunum: 'deri',    tabanSaldiri: 2,  tabanSavunma: 7 },
  // Miğferler
  { id: 'migfer',   ad: 'Demir Miğfer',     slot: 'migfer',   gorunum: 'demir',   tabanSaldiri: 0,  tabanSavunma: 5 },
  { id: 'tuylu',    ad: 'Tüylü Başlık',     slot: 'migfer',   gorunum: 'tuylu',   tabanSaldiri: 1,  tabanSavunma: 4 },
  { id: 'bandana',  ad: 'Savaş Bandanası',  slot: 'migfer',   gorunum: 'bandana', tabanSaldiri: 2,  tabanSavunma: 2 },
  // Kalkanlar
  { id: 'kalkan',   ad: 'Yuvarlak Kalkan',  slot: 'kalkan',   gorunum: 'yuvarlak', tabanSaldiri: 0, tabanSavunma: 8 },
  { id: 'kulekal',  ad: 'Kule Kalkanı',     slot: 'kalkan',   gorunum: 'kule',    tabanSaldiri: 0,  tabanSavunma: 11 },
  // Kolluk / ayakkabı / pelerin
  { id: 'kolluk',   ad: 'Deri Kolluk',      slot: 'kolluk',   gorunum: 'deri',    tabanSaldiri: 1,  tabanSavunma: 3 },
  { id: 'plakakol', ad: 'Plaka Kolluk',     slot: 'kolluk',   gorunum: 'plaka',   tabanSaldiri: 0,  tabanSavunma: 5 },
  { id: 'cizme',    ad: 'Savaş Çizmesi',    slot: 'ayakkabi', gorunum: 'cizme',   tabanSaldiri: 0,  tabanSavunma: 4 },
  { id: 'hafifayk', ad: 'Hafif Ayakkabı',   slot: 'ayakkabi', gorunum: 'hafif',   tabanSaldiri: 1,  tabanSavunma: 2 },
  { id: 'pelerin',  ad: 'Sancak Pelerini',  slot: 'pelerin',  gorunum: 'sancak',  tabanSaldiri: 0,  tabanSavunma: 3 },
  { id: 'kurkpel',  ad: 'Kürk Pelerin',     slot: 'pelerin',  gorunum: 'kurk',    tabanSaldiri: 0,  tabanSavunma: 5 },
  // Takılar — görseli yok, yalnızca istatistik
  { id: 'kolye',    ad: 'Kaplan Dişi',      slot: 'kolye',    tabanSaldiri: 3,  tabanSavunma: 2 },
  { id: 'kupe',     ad: 'Yeşim Küpe',       slot: 'kupe',     tabanSaldiri: 2,  tabanSavunma: 1 },
  { id: 'yuzuk',    ad: 'Ejderha Yüzüğü',   slot: 'yuzuk1',   tabanSaldiri: 4,  tabanSavunma: 1 },
];

let nextId = 1;

/** Ağırlıklı kademe seçimi; seviye arttıkça iyi kademe şansı artar. */
function rollTier(level) {
  const bonus = Math.min(2.5, level * 0.10);
  const agirliklar = TIERS.map((t, i) => t.agirlik * (i >= 2 ? 1 + bonus : 1));
  const toplam = agirliklar.reduce((a, b) => a + b, 0);
  let r = Math.random() * toplam;
  for (let i = 0; i < TIERS.length; i++) {
    r -= agirliklar[i];
    if (r <= 0) return i;
  }
  return 0;
}

/**
 * Bir eşya örneği üretir.
 * @param {object} tpl TEMPLATES girdisi
 * @param {number} level eşya seviyesi
 * @param {number} [tierIdx]
 */
export function makeItem(tpl, level = 1, tierIdx = null) {
  const ti = tierIdx ?? rollTier(level);
  const tier = TIERS[ti];
  const olcek = (1 + (level - 1) * 0.35) * tier.carpan;
  // Yüzükler iki slottan birine girebilsin
  const slot = tpl.slot === 'yuzuk1' ? 'yuzuk1' : tpl.slot;
  return {
    uid: nextId++,
    tplId: tpl.id,
    gorunum: tpl.gorunum ?? null,
    grup: SLOTS[tpl.slot === 'yuzuk1' ? 'yuzuk1' : tpl.slot]?.gorsel ?? null,
    ad: (tier.ad ? tier.ad + ' ' : '') + tpl.ad,
    slot,
    yuzuk: tpl.slot === 'yuzuk1',
    seviye: level,
    kademe: ti,
    renk: tier.renk,
    hex: tier.hex,
    saldiri: Math.round(tpl.tabanSaldiri * olcek),
    savunma: Math.round(tpl.tabanSavunma * olcek),
    simge: SLOTS[slot]?.simge ?? '◈',
  };
}

/**
 * Öldürülen hedeften eşya düşürür.
 * @param {number} level  hedefin seviyesi
 * @param {number} sans   düşme olasılığı (0..1)
 * @returns {?object}
 */
export function rollDrop(level, sans = 0.5) {
  if (Math.random() > sans) return null;
  const tpl = TEMPLATES[Math.floor(Math.random() * TEMPLATES.length)];
  return makeItem(tpl, Math.max(1, level));
}

/** Başlangıç donanımı: oyuncu çıplak başlamasın. */
export function starterItems() {
  const pick = (id) => TEMPLATES.find((t) => t.id === id);
  return [
    makeItem(pick('kilic'), 1, 1),
    makeItem(pick('zirh'), 1, 1),
    makeItem(pick('cizme'), 1, 0),
  ];
}
