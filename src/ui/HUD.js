/**
 * HUD.js — Oyun içi arayüz.
 *
 * Can/mana/tecrübe çubukları, mini harita, aksiyon düğmeleri ve bölge
 * bildirimi. DOM üzerinden çalışıyor: WebGL karesini yormuyor ve mobil
 * dokunma hedefleri tarayıcının kendi isabet testini kullanıyor.
 */
import { KINGDOMS } from '../data/kingdoms.js';

export class HUD {
  constructor(root, input) {
    this.root = root;
    this.input = input;
    this.el = {};
    this._build();
    this._bindButtons();
    this._lastZone = null;
    this._fpsFrames = 0;
  }

  _build() {
    this.root.innerHTML = `
      <div id="char-panel">
        <div id="portrait">⚔<span class="lvl">1</span></div>
        <div id="char-info">
          <div id="char-name"><span class="kd"></span><span class="nm">Savaşçı</span></div>
          <div class="bar hp"><i></i><span></span></div>
          <div class="bar mp"><i></i><span></span></div>
          <div class="bar xp"><i></i></div>
        </div>
      </div>

      <div id="target-panel">
        <div id="target-name"></div>
        <div class="bar target"><i></i></div>
      </div>

      <div id="minimap-wrap"><canvas id="minimap" width="118" height="118"></canvas></div>
      <div id="coords"></div>

      <div id="zone-banner"><div class="z1"></div><div class="z2"></div></div>

      <div id="actions">
        <div class="btn btn-md" id="btn-skill2">☯<small>35 MP</small></div>
        <div class="btn btn-md" id="btn-skill1">✦<small>25 MP</small></div>
        <div class="btn btn-md" id="btn-jump">⤒<small>ZIPLA</small></div>
        <div class="btn btn-lg" id="btn-attack">⚔</div>
      </div>

      <div id="toast"></div>
      <div id="hint">Sol yarı: hareket · Sağ yarı: kamera · ⚔ saldırı · ✦ ☯ beceri</div>
      <div id="fps"></div>
    `;
    const q = (s) => this.root.querySelector(s);
    this.el = {
      lvl: q('#portrait .lvl'),
      name: q('#char-name .nm'),
      kd: q('#char-name .kd'),
      hp: q('.bar.hp > i'), hpTxt: q('.bar.hp > span'),
      mp: q('.bar.mp > i'), mpTxt: q('.bar.mp > span'),
      xp: q('.bar.xp > i'),
      minimap: q('#minimap'),
      coords: q('#coords'),
      zone: q('#zone-banner'),
      zone1: q('#zone-banner .z1'),
      zone2: q('#zone-banner .z2'),
      hint: q('#hint'),
      fps: q('#fps'),
      toast: q('#toast'),
      targetPanel: q('#target-panel'),
      targetName: q('#target-name'),
      targetBar: q('.bar.target > i'),
      attack: q('#btn-attack'),
      jump: q('#btn-jump'),
      skill1: q('#btn-skill1'),
      skill2: q('#btn-skill2'),
    };
    this.ctx = this.el.minimap.getContext('2d');

    // İpucu bir süre sonra sönsün
    setTimeout(() => { this.el.hint.style.opacity = '0'; }, 9000);
  }

  _bindButtons() {
    this.input.bindButton(this.el.attack, 'attack', { repeat: true });
    this.input.bindButton(this.el.jump, 'jump');
    this.input.bindButton(this.el.skill1, 'skill1');
    this.input.bindButton(this.el.skill2, 'skill2');
  }

  /**
   * Vurulan hedefin adını ve canını üstte gösterir.
   * @param {?object} t { name, level, ratio } — null ise panel gizlenir
   */
  setTarget(t) {
    if (!t) { this.el.targetPanel.classList.remove('show'); return; }
    const label = t.level ? `${t.name}  ·  Sv ${t.level}` : t.name;
    if (this.el.targetName.textContent !== label) this.el.targetName.textContent = label;
    this.el.targetBar.style.transform = `scaleX(${Math.max(0, t.ratio)})`;
    this.el.targetPanel.classList.add('show');
  }

  /** Kısa süreli uyarı yazısı (mana yetmedi, bölge kilitli vb.). */
  toast(text) {
    this.el.toast.textContent = text;
    this.el.toast.classList.add('show');
    clearTimeout(this._toastT);
    this._toastT = setTimeout(() => this.el.toast.classList.remove('show'), 1800);
  }

  setCharacter({ name, level, kingdom }) {
    this.el.name.textContent = name;
    this.el.lvl.textContent = level;
    this.el.kd.style.background = kingdom.colorHex;
    this.el.kd.style.color = kingdom.colorHex;
  }

  setStats({ hp, hpMax, mp, mpMax, xp, xpMax, level }) {
    this.el.hp.style.transform = `scaleX(${Math.max(0, hp / hpMax)})`;
    this.el.mp.style.transform = `scaleX(${Math.max(0, mp / mpMax)})`;
    this.el.xp.style.transform = `scaleX(${Math.max(0, xp / xpMax)})`;
    this.el.hpTxt.textContent = `${Math.ceil(hp)} / ${hpMax}`;
    this.el.mpTxt.textContent = `${Math.ceil(mp)} / ${mpMax}`;
    if (level !== undefined) this.el.lvl.textContent = level;
  }

  /** Bölge adını büyük harflerle bir süre gösterir. */
  showZone(title, subtitle) {
    if (this._lastZone === title) return;
    this._lastZone = title;
    this.el.zone1.textContent = title;
    this.el.zone2.textContent = subtitle || '';
    this.el.zone.classList.add('show');
    clearTimeout(this._zoneT);
    this._zoneT = setTimeout(() => this.el.zone.classList.remove('show'), 3200);
  }

  /* ---------------- Mini harita ---------------- */
  drawMinimap(player, yaw, terrain) {
    const ctx = this.ctx;
    const S = this.el.minimap.width;
    const R = S / 2;
    const range = 260;                 // haritada gösterilen dünya yarıçapı

    ctx.clearRect(0, 0, S, S);
    ctx.save();
    ctx.beginPath();
    ctx.arc(R, R, R - 1, 0, Math.PI * 2);
    ctx.clip();

    ctx.fillStyle = '#1b2416';
    ctx.fillRect(0, 0, S, S);

    const toMap = (wx, wz) => [
      R + ((wx - player.x) / range) * R,
      R + ((wz - player.z) / range) * R,
    ];

    // Yollar
    ctx.strokeStyle = 'rgba(180,150,100,.55)';
    ctx.lineWidth = 3;
    for (const seg of terrain.roads) {
      const [a, b] = seg;
      const [ax, az] = toMap(a.x, a.z);
      const [bx, bz] = toMap(b.x, b.z);
      ctx.beginPath();
      ctx.moveTo(ax, az);
      ctx.lineTo(bx, bz);
      ctx.stroke();
    }

    // Köyler
    for (const k of KINGDOMS) {
      const [x, z] = toMap(k.center.x, k.center.z);
      const r = (105 / range) * R;
      ctx.fillStyle = k.colorHex + '55';
      ctx.strokeStyle = k.colorHex;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(x, z, r, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      // Köy simgesi
      ctx.fillStyle = k.colorHex;
      ctx.beginPath();
      ctx.arc(x, z, 3.2, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.restore();

    // Oyuncu oku (harita kuzeye sabit, ok yönü döner)
    ctx.save();
    ctx.translate(R, R);
    ctx.rotate(-yaw + Math.PI);
    ctx.fillStyle = '#ffe9a8';
    ctx.strokeStyle = '#3a2c0c';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, -6.5);
    ctx.lineTo(4.5, 5);
    ctx.lineTo(0, 2.6);
    ctx.lineTo(-4.5, 5);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    ctx.restore();

    // Kuzey işareti
    ctx.fillStyle = 'rgba(217,180,90,.8)';
    ctx.font = 'bold 9px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('K', R, 11);
  }

  setCoords(x, z, extra = '') {
    this.el.coords.innerHTML =
      `${Math.round(x)}, ${Math.round(z)}${extra ? '<br>' + extra : ''}`;
  }

  /**
   * Kare hızı.
   *
   * Oyun döngüsünün `dt`si üst sınıra kırpıldığı için (bkz. main.js) onu
   * toplamak gerçek hızı değil, simülasyon hızını ölçer: cihaz 3 FPS'te
   * koşarken bile sayaç 20 gösterir. Bu yüzden gerçek saat kullanılıyor.
   */
  tickFps() {
    const now = performance.now();
    this._fpsLast ??= now;
    this._fpsFrames++;
    const elapsed = (now - this._fpsLast) / 1000;
    if (elapsed >= 0.5) {
      this.el.fps.textContent = Math.round(this._fpsFrames / elapsed) + ' FPS';
      this._fpsLast = now;
      this._fpsFrames = 0;
    }
  }
}
