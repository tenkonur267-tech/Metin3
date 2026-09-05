/**
 * MetinStone.js — Metin taşı.
 *
 * Oyunun adını veren nesne: zemine saplanmış, üzerinde ışıyan damarlar olan
 * koyu bir kaya. Vuruldukça damarları parlar ve sarsılır, canı bittiğinde
 * parçalanıp etrafına canavar salar.
 *
 * Tamamen prosedürel: gövde bozulmuş bir çokyüzlüden, damarlar emissive
 * dokudan, hale de dönen bir düzlemden geliyor.
 */
import * as THREE from 'three';
import { getTexture } from '../core/Textures.js';
import { HealthBar3D } from '../ui/HealthBar3D.js';

/** Taş kademeleri: seviye arttıkça can, boyut ve renk değişir. */
export const METIN_TIERS = [
  { level: 1, name: 'Metin Taşı', hp: 400, scale: 1.0, color: 0xff3a2a, xp: 260, spawn: 2 },
  { level: 2, name: 'Kızıl Metin Taşı', hp: 750, scale: 1.15, color: 0xff7a1e, xp: 480, spawn: 3 },
  { level: 3, name: 'Karanlık Metin Taşı', hp: 1300, scale: 1.32, color: 0xa855f7, xp: 900, spawn: 4 },
  { level: 4, name: 'Lanetli Metin Taşı', hp: 2200, scale: 1.5, color: 0x22d3ee, xp: 1600, spawn: 5 },
];

function rand(seed) {
  let s = seed >>> 0;
  return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
}

/**
 * Sivri, fasetli kristal gövde.
 * Silindirin köşeleri gürültüyle bozulup üstü daraltılıyor: düzgün bir
 * geometrik cisim yerine kırılmış kaya silueti çıkıyor.
 */
function crystalGeometry(seed = 5, height = 4.2, radius = 1.25) {
  const g = new THREE.CylinderGeometry(radius * 0.14, radius, height, 7, 5);
  const pos = g.attributes.position;
  const r = rand(seed);
  const v = new THREE.Vector3();
  // Aynı halkadaki köşeler birlikte bozulsun diye yükseklik başına sapma
  const ringNoise = [];
  for (let i = 0; i < 40; i++) ringNoise.push((r() - 0.5) * 0.34);

  for (let i = 0; i < pos.count; i++) {
    v.fromBufferAttribute(pos, i);
    const t = (v.y + height / 2) / height;           // 0 taban, 1 tepe
    const ring = Math.round(t * 5);
    const k = 1 + ringNoise[ring % ringNoise.length] * (0.35 + t * 0.9);
    // Yukarı doğru inceltip hafifçe yana eğ
    v.x *= k;
    v.z *= k * (1 + ringNoise[(ring + 3) % ringNoise.length] * 0.5);
    v.x += Math.sin(t * 2.1) * radius * 0.16;
    v.z += Math.cos(t * 1.7) * radius * 0.12;
    pos.setXYZ(i, v.x, v.y, v.z);
  }
  g.computeVertexNormals();
  g.translate(0, height / 2 - height * 0.10, 0);      // tabanı zemine gömülü dursun
  return g;
}

export class MetinStone {
  /**
   * @param {object} tier   METIN_TIERS girdisi
   * @param {THREE.Vector3} position
   * @param {object} opts { seed }
   */
  constructor(tier, position, opts = {}) {
    this.tier = tier;
    this.name = tier.name;
    this.level = tier.level;
    this.hpMax = tier.hp;
    this.hp = tier.hp;
    this.xpReward = tier.xp;
    this.spawnCount = tier.spawn;
    this.alive = true;
    this.dead = false;
    this.radius = 1.9 * tier.scale;      // çarpışma ve vuruş yarıçapı
    this.seed = opts.seed ?? 5;

    this.group = new THREE.Group();
    this.group.position.copy(position);
    this.group.name = 'metin-' + tier.level;

    this._build();

    this.bar = new HealthBar3D({
      width: 1.8, height: 0.18, color: tier.color,
      yOffset: 5.2 * tier.scale, hideWhenFull: true,
    });
    this.bar.hideWhenFull = false;       // Metin taşının çubuğu hep görünür
    this.group.add(this.bar.group);
    this.bar.group.position.set(0, 5.2 * tier.scale, 0);

    this.time = 0;
    this.shake = 0;
    this.flash = 0;
  }

  _build() {
    const t = this.tier;
    const s = t.scale;
    const color = new THREE.Color(t.color);

    /* -- Gövde -- */
    const rockMap = getTexture('metinRock', { repeat: 2 });
    const veinMap = getTexture('metinVeins', { repeat: 2 });
    this.bodyMat = new THREE.MeshLambertMaterial({
      map: rockMap,
      emissive: color.clone(),
      emissiveMap: veinMap,
      emissiveIntensity: 0.55,
    });
    const body = new THREE.Mesh(crystalGeometry(this.seed, 4.2 * s, 1.25 * s), this.bodyMat);
    body.castShadow = true;
    body.receiveShadow = true;
    this.group.add(body);
    this.body = body;

    /* -- Tabandaki kırık parçalar -- */
    const r = rand(this.seed * 31);
    this.shards = [];
    for (let i = 0; i < 6; i++) {
      const a = (i / 6) * Math.PI * 2 + r() * 0.6;
      const d = (1.1 + r() * 0.8) * s;
      const h = (0.7 + r() * 1.0) * s;
      const shard = new THREE.Mesh(
        crystalGeometry(this.seed + i * 7, h, (0.22 + r() * 0.18) * s),
        this.bodyMat);
      shard.position.set(Math.sin(a) * d, 0, Math.cos(a) * d);
      shard.rotation.set((r() - 0.5) * 0.5, r() * 6.28, (r() - 0.5) * 0.5);
      shard.castShadow = true;
      this.group.add(shard);
      this.shards.push(shard);
    }

    /* -- Zemindeki dönen hale -- */
    const auraGeo = new THREE.PlaneGeometry(6.2 * s, 6.2 * s);
    auraGeo.rotateX(-Math.PI / 2);
    this.auraMat = new THREE.MeshBasicMaterial({
      map: getTexture('auraRing'),
      color: color.clone(),
      transparent: true,
      opacity: 0.55,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });
    this.aura = new THREE.Mesh(auraGeo, this.auraMat);
    this.aura.position.y = 0.06;
    this.aura.renderOrder = 2;
    this.group.add(this.aura);

    /* -- Havada süzülen kırıntılar -- */
    this.motes = [];
    for (let i = 0; i < 7; i++) {
      const m = new THREE.Mesh(
        new THREE.TetrahedronGeometry((0.10 + r() * 0.12) * s),
        new THREE.MeshBasicMaterial({ color: color.clone().lerp(new THREE.Color(0xffffff), 0.35) }));
      m.userData = {
        a: r() * Math.PI * 2,
        d: (1.5 + r() * 1.3) * s,
        y: (1.0 + r() * 2.6) * s,
        sp: 0.25 + r() * 0.5,
        bob: r() * Math.PI * 2,
      };
      this.group.add(m);
      this.motes.push(m);
    }
  }

  /** Hasar uygular. @returns {boolean} taş bu vuruşla yıkıldıysa true */
  takeDamage(amount) {
    if (!this.alive) return false;
    this.hp = Math.max(0, this.hp - amount);
    this.bar.setRatio(this.hp / this.hpMax);
    this.shake = 0.28;
    this.flash = 0.18;
    if (this.hp <= 0) {
      this.alive = false;
      this.deathTime = 0;
      return true;
    }
    return false;
  }

  /** Yıkım animasyonu bitince true döner; sahneden alınabilir. */
  update(dt, camDist = 12) {
    this.time += dt;
    const t = this.tier;

    if (this.alive) {
      // Damarlar nefes alır gibi parlasın, vuruşta bir anlığına patlasın
      const pulse = 0.5 + Math.sin(this.time * 2.2) * 0.18;
      const hurt = 1 - this.hp / this.hpMax;
      this.bodyMat.emissiveIntensity = pulse + hurt * 0.8 + this.flash * 6;
      if (this.flash > 0) this.flash = Math.max(0, this.flash - dt);

      // Vuruş sarsıntısı
      if (this.shake > 0) {
        this.shake = Math.max(0, this.shake - dt * 1.6);
        const k = this.shake * this.shake * 2.2;
        this.body.position.x = (Math.random() - 0.5) * k;
        this.body.position.z = (Math.random() - 0.5) * k;
        this.body.rotation.z = (Math.random() - 0.5) * k * 0.12;
      } else {
        this.body.position.x = this.body.position.z = 0;
        this.body.rotation.z = 0;
      }

      // Hale döner, canı azaldıkça hızlanır
      this.aura.rotation.y += dt * (0.45 + hurt * 1.6);
      this.auraMat.opacity = 0.42 + Math.sin(this.time * 3.1) * 0.10 + hurt * 0.25;

      // Kırıntılar taşın çevresinde süzülür
      for (const m of this.motes) {
        const u = m.userData;
        u.a += dt * u.sp;
        m.position.set(
          Math.sin(u.a) * u.d,
          u.y + Math.sin(this.time * 1.4 + u.bob) * 0.22,
          Math.cos(u.a) * u.d);
        m.rotation.x += dt * 1.1;
        m.rotation.y += dt * 0.8;
      }

      this.bar.update(dt, this.group.position, camDist);
      this.bar.group.position.set(0, 5.2 * t.scale, 0);
      return false;
    }

    /* -- Yıkım -- */
    this.deathTime += dt;
    const u = Math.min(1, this.deathTime / 1.1);
    this.bar.group.visible = false;
    // Parçalar dışa savrulup düşer
    this.body.scale.setScalar(1 - u * 0.85);
    this.body.rotation.y += dt * 3.2;
    this.body.position.y = -u * 1.4;
    this.bodyMat.emissiveIntensity = 3.5 * (1 - u);
    for (let i = 0; i < this.shards.length; i++) {
      const sh = this.shards[i];
      const a = (i / this.shards.length) * Math.PI * 2;
      sh.position.x += Math.sin(a) * dt * 5.5 * (1 - u);
      sh.position.z += Math.cos(a) * dt * 5.5 * (1 - u);
      sh.position.y += (2.6 * (1 - u * 2)) * dt;
      sh.rotation.x += dt * 4;
      sh.scale.setScalar(Math.max(0.001, 1 - u));
    }
    for (const m of this.motes) {
      m.position.y += dt * 3.5;
      m.scale.setScalar(Math.max(0.001, 1 - u));
    }
    this.auraMat.opacity = 0.7 * (1 - u);
    this.aura.scale.setScalar(1 + u * 1.8);

    if (u >= 1) { this.dead = true; return true; }
    return false;
  }

  /** Vuruş testi için merkez (taşın gövde ortası). */
  getHitCenter(out = new THREE.Vector3()) {
    return out.set(this.group.position.x, this.group.position.y + 1.8 * this.tier.scale, this.group.position.z);
  }

  addTo(scene) { scene.add(this.group); return this; }

  dispose(scene) {
    scene.remove(this.group);
    this.body.geometry.dispose();
    for (const s of this.shards) s.geometry.dispose();
    for (const m of this.motes) { m.geometry.dispose(); m.material.dispose(); }
    this.aura.geometry.dispose();
    this.auraMat.dispose();
    this.bodyMat.dispose();
  }
}
