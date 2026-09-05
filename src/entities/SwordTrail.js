/**
 * SwordTrail.js — Kılıç izi.
 *
 * Namlunun dip ve uç noktaları her karede kaydediliyor; bu ikililer bir şerit
 * oluşturuyor. Şerit sona doğru saydamlaşıyor, böylece savurma hareketinin
 * yönü ve hızı gözle okunabiliyor — düşük poligonlu bir karakterde vuruşu
 * "hissettiren" en ucuz efekt bu.
 */
import * as THREE from 'three';

export class SwordTrail {
  /**
   * @param {number} segments  şeritte tutulan kare sayısı
   * @param {number} color     iz rengi
   */
  constructor(segments = 14, color = 0xbfe4ff) {
    this.segments = segments;
    this.head = 0;
    this.filled = 0;

    // Her segment iki köşe (dip + uç)
    const count = segments * 2;
    this.positions = new Float32Array(count * 3);
    this.alphas = new Float32Array(count);

    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(this.positions, 3));
    geo.setAttribute('aAlpha', new THREE.BufferAttribute(this.alphas, 1));

    // Şerit indeksleri: ardışık segmentler arasında iki üçgen
    const idx = [];
    for (let i = 0; i < segments - 1; i++) {
      const a = i * 2, b = a + 1, c = a + 2, d = a + 3;
      idx.push(a, c, b, b, c, d);
    }
    geo.setIndex(idx);
    geo.setDrawRange(0, 0);

    const mat = new THREE.ShaderMaterial({
      uniforms: { uColor: { value: new THREE.Color(color) } },
      vertexShader: `
        attribute float aAlpha;
        varying float vAlpha;
        void main() {
          vAlpha = aAlpha;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }`,
      fragmentShader: `
        uniform vec3 uColor;
        varying float vAlpha;
        void main() {
          if (vAlpha <= 0.001) discard;
          gl_FragColor = vec4(uColor, vAlpha);
        }`,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
      side: THREE.DoubleSide,
    });

    this.mesh = new THREE.Mesh(geo, mat);
    this.mesh.frustumCulled = false;
    this.mesh.renderOrder = 5;
    this.mesh.visible = false;
    this.geo = geo;
    this.setColor(color);
  }

  /**
   * İz rengini ayarlar. Ham krallık rengi ekranda sönük kalıyor; çeliğin
   * parlamasını taklit etmek için beyaza doğru çekiliyor.
   */
  setColor(color) {
    const c = new THREE.Color(color).lerp(new THREE.Color(0xffffff), 0.55);
    this.mesh.material.uniforms.uColor.value.copy(c);
  }

  /** Yeni bir dip/uç ikilisi ekler. */
  push(base, tip) {
    // Kayan pencere: en eskiyi at, sona ekle
    if (this.filled >= this.segments) {
      this.positions.copyWithin(0, 6);
      this.filled = this.segments - 1;
    }
    const o = this.filled * 6;
    this.positions[o] = base.x;
    this.positions[o + 1] = base.y;
    this.positions[o + 2] = base.z;
    this.positions[o + 3] = tip.x;
    this.positions[o + 4] = tip.y;
    this.positions[o + 5] = tip.z;
    this.filled++;

    // En eski uç saydam, en yeni uç parlak
    for (let i = 0; i < this.filled; i++) {
      const t = i / Math.max(1, this.filled - 1);
      const a = Math.pow(t, 1.6);
      this.alphas[i * 2] = a * 0.35;      // dip daha sönük
      this.alphas[i * 2 + 1] = a * 0.85;  // uç daha parlak
    }

    this.geo.attributes.position.needsUpdate = true;
    this.geo.attributes.aAlpha.needsUpdate = true;
    this.geo.setDrawRange(0, Math.max(0, (this.filled - 1) * 6));
    this.mesh.visible = this.filled > 1;
  }

  /** İzi söndürür (savurma bitince). */
  fade() {
    if (this.filled <= 0) return;
    this.filled = Math.max(0, this.filled - 2);
    this.geo.setDrawRange(0, Math.max(0, (this.filled - 1) * 6));
    if (this.filled <= 1) this.mesh.visible = false;
  }

  clear() {
    this.filled = 0;
    this.geo.setDrawRange(0, 0);
    this.mesh.visible = false;
  }

  addTo(scene) { scene.add(this.mesh); return this; }
}
