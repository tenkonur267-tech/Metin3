/**
 * InventoryUI.js — Çanta ve ekipman ekranı.
 *
 * Metin2'nin düzenini mobile uyarlıyor: solda ekipman slotları, altta
 * ızgara çanta. Sürükle-bırak yerine dokunmayla seçim ve buton kullanılıyor;
 * küçük ekranda sürükleme hedefi ıskalamak çok kolay.
 */
import { SLOTS, SLOT_ORDER, TIERS } from '../data/items.js';

export class InventoryUI {
  /**
   * @param {HTMLElement} root
   * @param {Inventory} inv
   */
  constructor(root, inv) {
    this.inv = inv;
    this.acik = false;
    this.secili = null;      // { tur: 'canta'|'ekipman', index|slot }

    this.el = document.createElement('div');
    this.el.id = 'inventory';
    this.el.hidden = true;
    root.appendChild(this.el);
    this._build();

    this._unsub = inv.subscribe(() => this.yenile());
  }

  _build() {
    this.el.innerHTML = `
      <div class="inv-panel">
        <header>
          <h2>Envanter</h2>
          <button class="inv-close" type="button" aria-label="Kapat">✕</button>
        </header>

        <section class="inv-equip">
          <div class="inv-slots"></div>
          <div class="inv-totals">
            <span class="t-atk">Saldırı <b>0</b></span>
            <span class="t-def">Savunma <b>0</b></span>
          </div>
        </section>

        <section class="inv-bagwrap">
          <div class="inv-bag"></div>
        </section>

        <section class="inv-detail" hidden>
          <div class="d-name"></div>
          <div class="d-stats"></div>
          <div class="d-actions"></div>
        </section>
      </div>
    `;
    this.panel = this.el.querySelector('.inv-panel');
    this.slotsEl = this.el.querySelector('.inv-slots');
    this.bagEl = this.el.querySelector('.inv-bag');
    this.detailEl = this.el.querySelector('.inv-detail');
    this.atkEl = this.el.querySelector('.t-atk b');
    this.defEl = this.el.querySelector('.t-def b');

    this.el.querySelector('.inv-close').addEventListener('click', () => this.kapat());
    // Panelin dışına dokununca kapansın
    this.el.addEventListener('click', (e) => { if (e.target === this.el) this.kapat(); });

    // Ekipman slotları
    for (const slot of SLOT_ORDER) {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'inv-slot';
      b.dataset.slot = slot;
      b.innerHTML = `<span class="s-icon">${SLOTS[slot].simge}</span>
                     <span class="s-label">${SLOTS[slot].ad}</span>`;
      b.addEventListener('click', () => this.sec('ekipman', slot));
      this.slotsEl.appendChild(b);
    }

    // Çanta gözleri
    for (let i = 0; i < this.inv.gozSayisi; i++) {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'inv-cell';
      b.dataset.index = String(i);
      b.addEventListener('click', () => this.sec('canta', i));
      this.bagEl.appendChild(b);
    }
    this.yenile();
  }

  /* ---------------- Durum ---------------- */

  ac() { this.acik = true; this.el.hidden = false; this.yenile(); }
  kapat() { this.acik = false; this.el.hidden = true; this.secili = null; }
  degistir() { this.acik ? this.kapat() : this.ac(); }

  sec(tur, key) {
    const ayni = this.secili && this.secili.tur === tur
      && (this.secili.index === key || this.secili.slot === key);
    this.secili = ayni ? null
      : (tur === 'canta' ? { tur, index: key } : { tur, slot: key });
    this.yenile();
  }

  _seciliItem() {
    if (!this.secili) return null;
    return this.secili.tur === 'canta'
      ? this.inv.gozler[this.secili.index]
      : this.inv.ekipman[this.secili.slot];
  }

  /* ---------------- Çizim ---------------- */

  yenile() {
    // Ekipman
    for (const b of this.slotsEl.children) {
      const item = this.inv.ekipman[b.dataset.slot];
      b.classList.toggle('dolu', !!item);
      b.classList.toggle('secili',
        this.secili?.tur === 'ekipman' && this.secili.slot === b.dataset.slot);
      const icon = b.querySelector('.s-icon');
      icon.textContent = item ? item.simge : SLOTS[b.dataset.slot].simge;
      icon.style.color = item ? item.hex : '';
      b.querySelector('.s-label').textContent = item ? item.ad : SLOTS[b.dataset.slot].ad;
      b.style.setProperty('--tier', item ? item.hex : 'transparent');
    }

    // Çanta
    for (const b of this.bagEl.children) {
      const i = Number(b.dataset.index);
      const item = this.inv.gozler[i];
      b.classList.toggle('dolu', !!item);
      b.classList.toggle('secili',
        this.secili?.tur === 'canta' && this.secili.index === i);
      b.textContent = item ? item.simge : '';
      b.style.setProperty('--tier', item ? item.hex : 'transparent');
      b.title = item ? item.ad : '';
    }

    // Toplamlar
    const t = this.inv.toplam();
    this.atkEl.textContent = t.saldiri;
    this.defEl.textContent = t.savunma;

    this._detayCiz();
  }

  _detayCiz() {
    const item = this._seciliItem();
    if (!item) { this.detailEl.hidden = true; return; }
    this.detailEl.hidden = false;

    const tier = TIERS[item.kademe];
    this.detailEl.querySelector('.d-name').innerHTML =
      `<b style="color:${item.hex}">${item.ad}</b>
       <small>Sv ${item.seviye}${tier.ad ? ' · ' + tier.ad : ''}</small>`;

    const bits = [];
    if (item.saldiri) bits.push(`Saldırı +${item.saldiri}`);
    if (item.savunma) bits.push(`Savunma +${item.savunma}`);
    this.detailEl.querySelector('.d-stats').textContent = bits.join('   ');

    const acts = this.detailEl.querySelector('.d-actions');
    acts.innerHTML = '';
    if (this.secili.tur === 'canta') {
      // Kuşanınca hangi slottakiyle karşılaştırıldığını göster
      const slot = item.yuzuk ? (this.inv.ekipman.yuzuk1 ? 'yuzuk2' : 'yuzuk1') : item.slot;
      const mevcut = this.inv.ekipman[slot];
      if (mevcut) {
        const dAtk = item.saldiri - mevcut.saldiri;
        const dDef = item.savunma - mevcut.savunma;
        const fmt = (v) => (v > 0 ? `+${v}` : String(v));
        const cls = (v) => (v > 0 ? 'iyi' : v < 0 ? 'kotu' : '');
        acts.insertAdjacentHTML('beforeend',
          `<span class="d-compare">Takılıya göre
             <b class="${cls(dAtk)}">${fmt(dAtk)}</b> sal.
             <b class="${cls(dDef)}">${fmt(dDef)}</b> sav.</span>`);
      }
      /*
       * Hedef seçim anında yakalanıyor. Butonun içinden `this.secili`
       * okumak, envanter değişince arayüz yeniden çizildiği için tıklama
       * sırası kaydığında null'a düşüyordu.
       */
      const index = this.secili.index;
      this._buton(acts, 'Kuşan', () => {
        this.inv.kusan(index);
        this.secili = null;
      });
    } else {
      /*
       * Çanta doluyken çıkarma reddediliyor. Eskiden buton normal görünüp
       * hiçbir şey yapmıyordu; oyuncu eşyayı çıkardığını sanıp karakterde
       * durmaya devam ettiğini görüyordu. Artık butonun kendisi durumu
       * söylüyor.
       */
      const slot = this.secili.slot;
      if (this.inv.doluMu) {
        const b = this._buton(acts, 'Çanta dolu', () => {
          this.onMesaj?.('Çanta dolu — çıkarmak için önce yer aç');
        });
        b.classList.add('pasif');
      } else {
        this._buton(acts, 'Çıkar', () => {
          if (!this.inv.cikart(slot)) {
            this.onMesaj?.('Çanta dolu — çıkarmak için önce yer aç');
            return;
          }
          this.secili = null;
        });
      }
    }
  }

  _buton(parent, ad, fn) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'inv-action';
    b.textContent = ad;
    b.addEventListener('click', () => { fn(); this.yenile(); });
    parent.appendChild(b);
    return b;
  }

  dispose() { this._unsub?.(); this.el.remove(); }
}
