/**
 * FloatingText.js — Havada süzülen hasar/kazanç rakamları.
 *
 * Vuruşun isabet ettiğini anlatan en doğrudan geri bildirim bu. Yazılar
 * kamera'ya dönük sprite'lar olarak çiziliyor; her vuruşta yeni doku
 * üretmek pahalı olacağı için rakamlar önceden hazırlanmış bir atlastan
 * okunuyor ve sprite'lar havuzdan geri dönüşümle veriliyor.
 */
import * as THREE from 'three';

const GLYPHS = '0123456789+-!KRİTİK ';   // atlasa girecek karakterler
const CELL = 64;                          // atlastaki hücre boyu (piksel)

/** Rakam atlası: her karakter tek bir hücrede. */
function buildAtlas() {
  const cols = GLYPHS.length;
  const c = document.createElement('canvas');
  c.width = cols * CELL;
  c.height = CELL;
  const ctx = c.getContext('2d');
  ctx.font = `bold ${CELL * 0.72}px "Trebuchet MS", system-ui, sans-serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  for (let i = 0; i < cols; i++) {
    const x = i * CELL + CELL / 2;
    ctx.lineWidth = CELL * 0.14;
    ctx.strokeStyle = 'rgba(0,0,0,0.92)';
    ctx.strokeText(GLYPHS[i], x, CELL / 2);
    ctx.fillStyle = '#ffffff';
    ctx.fillText(GLYPHS[i], x, CELL / 2);
  }
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

/** Tek bir yazıyı oluşturan karakter sprite'ları. */
class Line {
  constructor(atlas, maxChars = 8) {
    this.group = new THREE.Group();
    this.group.visible = false;
    this.chars = [];
    for (let i = 0; i < maxChars; i++) {
      const mat = new THREE.SpriteMaterial({
        map: atlas.clone(),
        transparent: true,
        depthWrite: false,
        depthTest: false,
      });
      mat.map.repeat.set(1 / GLYPHS.length, 1);
      const s = new THREE.Sprite(mat);
      s.visible = false;
      this.group.add(s);
      this.chars.push(s);
    }
    this.group.renderOrder = 20;
    this.life = 0;
    this.duration = 1.1;
  }

  /** @param {string} text @param {THREE.Vector3} pos @param {object} style */
  show(text, pos, style) {
    const { color = 0xffffff, scale = 0.5, rise = 1.6, duration = 1.1 } = style;
    this.duration = duration;
    this.rise = rise;
    this.life = 0;
    this.baseScale = scale;
    this.group.position.copy(pos);
    this.group.visible = true;

    const n = Math.min(text.length, this.chars.length);
    const w = scale * 0.62;
    for (let i = 0; i < this.chars.length; i++) {
      const s = this.chars[i];
      if (i >= n) { s.visible = false; continue; }
      const idx = GLYPHS.indexOf(text[i]);
      if (idx < 0) { s.visible = false; continue; }
      s.visible = true;
      s.material.map.offset.x = idx / GLYPHS.length;
      s.material.color.setHex(color);
      s.material.opacity = 1;
      s.position.set((i - (n - 1) / 2) * w, 0, 0);
      s.scale.setScalar(scale);
    }
    // Hafif yatay savrulma: üst üste binen vuruşlar ayrışsın
    this.drift = (Math.random() - 0.5) * 0.7;
  }

  update(dt) {
    if (!this.group.visible) return false;
    this.life += dt;
    const u = this.life / this.duration;
    if (u >= 1) { this.group.visible = false; return false; }

    // Yükselirken yavaşla, sonda sön
    const ease = 1 - Math.pow(1 - u, 2.2);
    this.group.position.y += this.rise * dt * (1 - ease * 0.75);
    this.group.position.x += this.drift * dt;
    const pop = u < 0.14 ? 0.6 + (u / 0.14) * 0.55 : 1.15 - Math.min(1, (u - 0.14) / 0.86) * 0.15;
    const fade = u < 0.65 ? 1 : 1 - (u - 0.65) / 0.35;
    for (const s of this.chars) {
      if (!s.visible) continue;
      s.material.opacity = fade;
      s.scale.setScalar(this.baseScale * pop);
    }
    return true;
  }
}

export class FloatingText {
  constructor(scene, poolSize = 24) {
    this.atlas = buildAtlas();
    this.pool = [];
    this.active = [];
    for (let i = 0; i < poolSize; i++) {
      const l = new Line(this.atlas);
      scene.add(l.group);
      this.pool.push(l);
    }
  }

  /**
   * @param {string} text
   * @param {THREE.Vector3} pos
   * @param {object} style { color, scale, rise, duration }
   */
  spawn(text, pos, style = {}) {
    const line = this.pool.pop() || this.active.shift();
    if (!line) return;
    line.show(String(text), pos, style);
    this.active.push(line);
  }

  /** Hasar rakamı — kritikler daha büyük ve altın renkli. */
  damage(amount, pos, { crit = false, toPlayer = false } = {}) {
    if (toPlayer) {
      this.spawn('-' + Math.round(amount), pos, { color: 0xff5a4a, scale: 0.42, rise: 1.2 });
    } else if (crit) {
      this.spawn(Math.round(amount) + '!', pos, { color: 0xffd257, scale: 0.72, rise: 2.0, duration: 1.35 });
    } else {
      this.spawn(String(Math.round(amount)), pos, { color: 0xfff0d0, scale: 0.48, rise: 1.6 });
    }
  }

  update(dt) {
    for (let i = this.active.length - 1; i >= 0; i--) {
      if (!this.active[i].update(dt)) {
        this.pool.push(this.active.splice(i, 1)[0]);
      }
    }
  }
}
