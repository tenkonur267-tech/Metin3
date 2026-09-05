/**
 * HealthBar3D.js — Hedefin üstünde duran can çubuğu.
 *
 * Sprite kullanıldığı için her zaman kameraya dönük kalır. Doluluk oranı
 * canvas'ı yeniden çizerek değil, dolgu sprite'ının ölçeğini değiştirerek
 * güncelleniyor — vuruş başına doku yüklemesi olmuyor.
 */
import * as THREE from 'three';

function flatSprite(color, opacity = 1) {
  const mat = new THREE.SpriteMaterial({
    color,
    transparent: true,
    opacity,
    depthWrite: false,
    depthTest: false,
  });
  return new THREE.Sprite(mat);
}

export class HealthBar3D {
  /**
   * @param {object} o { width, height, color, yOffset }
   */
  constructor(o = {}) {
    this.width = o.width ?? 1.4;
    this.height = o.height ?? 0.16;
    this.yOffset = o.yOffset ?? 2.4;
    this.hideWhenFull = o.hideWhenFull ?? true;

    this.group = new THREE.Group();
    this.group.renderOrder = 18;

    // Çerçeve, zemin, dolgu
    this.frame = flatSprite(0x000000, 0.72);
    this.frame.scale.set(this.width + 0.06, this.height + 0.06, 1);
    this.bg = flatSprite(0x2a1512, 0.9);
    this.bg.scale.set(this.width, this.height, 1);
    this.fill = flatSprite(o.color ?? 0xd0342c, 1);
    this.fill.scale.set(this.width, this.height, 1);

    this.group.add(this.frame, this.bg, this.fill);
    this.frame.position.z = -0.002;
    this.fill.position.z = 0.002;

    this.ratio = 1;
    this.visibleTimer = 0;
    this.setRatio(1);
  }

  setColor(hex) { this.fill.material.color.setHex(hex); }

  setRatio(r) {
    this.ratio = THREE.MathUtils.clamp(r, 0, 1);
    const w = this.width * this.ratio;
    this.fill.scale.x = w;
    // Sprite merkezden ölçeklendiği için sola yaslamak üzere kaydır
    this.fill.position.x = -(this.width - w) / 2;
    if (this.hideWhenFull) this.visibleTimer = 4;
  }

  /** @param {THREE.Vector3} pos hedefin dünya konumu */
  update(dt, pos, cameraDistance = 10) {
    this.group.position.set(pos.x, pos.y + this.yOffset, pos.z);
    if (this.hideWhenFull) {
      if (this.visibleTimer > 0) this.visibleTimer -= dt;
      this.group.visible = this.ratio < 1 && (this.visibleTimer > 0 || this.ratio < 1);
    }
    // Uzakta okunaklı kalsın diye hafifçe büyüt
    const s = THREE.MathUtils.clamp(cameraDistance / 12, 0.85, 2.2);
    this.group.scale.setScalar(s);
  }

  addTo(parent) { parent.add(this.group); return this; }
  dispose(parent) {
    parent.remove(this.group);
    for (const s of [this.frame, this.bg, this.fill]) s.material.dispose();
  }
}
