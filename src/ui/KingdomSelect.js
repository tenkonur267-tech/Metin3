/**
 * KingdomSelect.js — Açılış ekranı ve krallık seçimi.
 *
 * Oyuncu üç imparatorluktan birini seçiyor; seçim hem doğduğu köyü hem de
 * karakterinin zırh rengini ve dünyanın atmosferini belirliyor.
 */
import { KINGDOMS } from '../data/kingdoms.js';

const CRESTS = { shinsoo: '🐉', chunjo: '🐅', jinno: '🐢' };

export class KingdomSelect {
  constructor(overlayEl) {
    this.el = overlayEl;
    this.selected = null;
    this._onStart = null;
    this._build();
  }

  _build() {
    this.el.innerHTML = `
      <div id="title">
        <h1>METIN3</h1>
        <p>Üç Krallığın Gölgesi</p>
      </div>

      <div id="kingdoms"></div>

      <button id="start" disabled>KRALLIK SEÇ</button>

      <div id="loading">Dünya hazırlanıyor…<div id="loadbar"><i></i></div></div>

      <div id="legal">
        Kişisel kullanım için yapılmış, Metin2'den esinlenen bağımsız bir
        çalışma. Tüm modeller, dokular ve animasyonlar oyunun içinde
        prosedürel olarak üretilir; hiçbir ticari varlık kullanılmaz.
      </div>
    `;

    const list = this.el.querySelector('#kingdoms');
    for (const k of KINGDOMS) {
      const card = document.createElement('div');
      card.className = 'kingdom';
      card.style.setProperty('--kc', k.colorHex);
      card.dataset.id = k.id;
      const biomeName = { grass: 'Yeşil ovalar', sand: 'Kurak bozkır', snow: 'Karlı dağ eteği' }[k.biome];
      card.innerHTML = `
        <div class="crest">${CRESTS[k.id] || '◈'}</div>
        <h3>${k.name}</h3>
        <div class="sub">${k.title}</div>
        <div class="desc">${k.motto}</div>
        <div class="meta"><span>${k.village}</span><span>${biomeName}</span></div>
      `;
      card.addEventListener('click', () => this._select(k, card));
      list.appendChild(card);
    }

    this.startBtn = this.el.querySelector('#start');
    this.loadEl = this.el.querySelector('#loading');
    this.loadBar = this.el.querySelector('#loadbar > i');
    this.startBtn.addEventListener('click', () => {
      if (!this.selected || this.startBtn.disabled) return;
      this._onStart?.(this.selected);
    });
  }

  _select(kingdom, card) {
    this.selected = kingdom;
    for (const c of this.el.querySelectorAll('.kingdom')) c.classList.remove('sel');
    card.classList.add('sel');
    this.startBtn.textContent = `${kingdom.name.toUpperCase()} OLARAK BAŞLA`;
    this.startBtn.style.background =
      `linear-gradient(180deg, ${kingdom.colorHex}, color-mix(in srgb, ${kingdom.colorHex} 60%, #000))`;
    this.startBtn.style.color = '#fff';
    if (this._ready) this.startBtn.disabled = false;
  }

  /** Dünya yüklenirken ilerleme çubuğunu besler. */
  setProgress(t, label) {
    this.loadBar.style.width = Math.round(t * 100) + '%';
    if (label) this.loadEl.firstChild.textContent = label;
  }

  /** Dünya hazır: başlat düğmesi açılabilir. */
  setReady() {
    this._ready = true;
    this.loadEl.style.display = 'none';
    if (this.selected) this.startBtn.disabled = false;
    else this.startBtn.textContent = 'KRALLIK SEÇ';
  }

  onStart(fn) { this._onStart = fn; }

  hide() {
    this.el.classList.add('hidden');
    setTimeout(() => { this.el.style.display = 'none'; }, 520);
  }
}
