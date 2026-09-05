/**
 * Inventory.js — Çanta ve ekipman modeli.
 *
 * Metin2'nin düzeni: sabit sayıda çanta gözü ve slot başına tek ekipman.
 * Model saf veri; görsel taraf (karakterdeki zırh parçaları, arayüz)
 * olaylara abone olarak kendini günceller.
 */
import { SLOTS, SLOT_ORDER } from '../data/items.js';

export class Inventory {
  /**
   * @param {object} o { gozSayisi }
   */
  constructor(o = {}) {
    this.gozSayisi = o.gozSayisi ?? 30;
    this.gozler = new Array(this.gozSayisi).fill(null);
    this.ekipman = {};
    for (const s of SLOT_ORDER) this.ekipman[s] = null;
    this.listeners = new Set();
  }

  /** Değişiklikleri dinle. @returns {function} aboneliği bırakan işlev */
  subscribe(fn) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  _notify(olay, veri) {
    for (const fn of this.listeners) fn(olay, veri);
  }

  get doluGoz() { return this.gozler.filter(Boolean).length; }
  get bosMu() { return this.doluGoz === 0; }
  get doluMu() { return this.doluGoz >= this.gozSayisi; }

  /**
   * Çantaya ekler.
   * @returns {number} yerleştirilen gözün indeksi, yer yoksa -1
   */
  ekle(item) {
    const i = this.gozler.indexOf(null);
    if (i < 0) return -1;
    this.gozler[i] = item;
    this._notify('ekle', { item, index: i });
    return i;
  }

  /** Gözden çıkarır ve eşyayı döndürür. */
  cikar(index) {
    const item = this.gozler[index];
    if (!item) return null;
    this.gozler[index] = null;
    this._notify('cikar', { item, index });
    return item;
  }

  /**
   * Çantadaki eşyayı kuşanır. Slot doluysa eski eşya çantaya döner.
   * @returns {boolean}
   */
  kusan(index) {
    const item = this.gozler[index];
    if (!item) return false;

    // Yüzükler boş olan yüzük slotuna gitsin
    let slot = item.slot;
    if (item.yuzuk) slot = this.ekipman.yuzuk1 ? 'yuzuk2' : 'yuzuk1';
    if (!(slot in this.ekipman)) return false;

    const eski = this.ekipman[slot];
    this.gozler[index] = eski;           // takas: eski eşya aynı göze düşer
    this.ekipman[slot] = item;
    this._notify('kusan', { item, slot, eski });
    return true;
  }

  /**
   * Kuşanılmış eşyayı çıkarır; çantada yer yoksa çıkarmaz.
   * @returns {boolean}
   */
  cikart(slot) {
    const item = this.ekipman[slot];
    if (!item) return false;
    if (this.doluMu) return false;
    this.ekipman[slot] = null;
    this.ekle(item);
    this._notify('cikart', { item, slot });
    return true;
  }

  /**
   * Kuşanılmış eşyayı çantaya bakmadan çıkarır ve döndürür.
   *
   * Çağıran, dönen eşyayı bir yere koymakla yükümlü (oyunda yere düşüyor).
   * Çanta dolu diye çıkarmayı reddetmek, oyuncunun ekipmanı üstünden
   * alamamasına yol açıyordu.
   *
   * @returns {?object} çıkarılan eşya
   */
  cikartZorla(slot) {
    const item = this.ekipman[slot];
    if (!item) return null;
    this.ekipman[slot] = null;
    this._notify('cikart', { item, slot, yereAtildi: true });
    return item;
  }

  /** Kuşanılmış eşyaların toplam katkısı. */
  toplam() {
    let saldiri = 0, savunma = 0;
    for (const s of SLOT_ORDER) {
      const it = this.ekipman[s];
      if (!it) continue;
      saldiri += it.saldiri;
      savunma += it.savunma;
    }
    return { saldiri, savunma };
  }

  /** Görsel karşılığı olan slotların eşyaları. */
  gorselEkipman() {
    const out = {};
    for (const s of SLOT_ORDER) {
      const g = SLOTS[s]?.gorsel;
      if (g) out[g] = this.ekipman[s];
    }
    return out;
  }

  /** Kaydetme/yükleme için sade veri. */
  disaAktar() {
    return { gozler: this.gozler, ekipman: this.ekipman };
  }
}
