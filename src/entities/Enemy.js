/**
 * Enemy.js — Canavarlar.
 *
 * İki gövde planı var: dört ayaklı (kurt, yaban domuzu) ve iki ayaklı
 * (haydut). İkisi de aynı iskelet mantığıyla kuruluyor ve aynı basit yapay
 * zekâyı paylaşıyor: dolaş → kovala → saldır → öl.
 *
 * Animasyonlar, savaşçıdaki gibi kat edilen yola bağlı bir faz üzerinden
 * sürülüyor; böylece hız değiştiğinde ayaklar kaymıyor.
 */
import * as THREE from 'three';

const TAU = Math.PI * 2;
const _v = new THREE.Vector3();

/** Canavar türleri. */
export const ENEMY_TYPES = {
  kurt: {
    name: 'Kurt', plan: 'quad', hp: 90, damage: 14, xp: 60,
    speed: 5.4, aggro: 22, attackRange: 2.0, attackTime: 0.85, damageAt: [0.32, 0.52],
    scale: 0.9, radius: 0.55, cycleDistance: 2.0,
    colors: { body: 0x5b5148, belly: 0x8a7d6e, dark: 0x342e28, eye: 0xffcc44 },
  },
  domuz: {
    name: 'Yaban Domuzu', plan: 'quad', hp: 170, damage: 22, xp: 95,
    speed: 4.2, aggro: 18, attackRange: 2.1, attackTime: 1.0, damageAt: [0.34, 0.56],
    scale: 1.05, radius: 0.7, cycleDistance: 1.9,
    colors: { body: 0x4a3b2f, belly: 0x6b5645, dark: 0x241c15, eye: 0xd44a2a },
  },
  haydut: {
    name: 'Haydut', plan: 'biped', hp: 140, damage: 19, xp: 110,
    speed: 4.6, aggro: 20, attackRange: 2.3, attackTime: 0.95, damageAt: [0.34, 0.56],
    scale: 1.0, radius: 0.5, cycleDistance: 2.1,
    colors: { body: 0x6b4a34, belly: 0x8f7355, dark: 0x2e2116, eye: 0xe8d59a },
  },
};

function part(geo, mat, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(geo, mat);
  m.position.set(x, y, z);
  m.castShadow = true;
  return m;
}

/** Kutu köşelerini kırarak organik bir hacim verir. */
function blob(w, h, d, bevel = 0.22) {
  const g = new THREE.BoxGeometry(w, h, d, 1, 1, 1);
  const p = g.attributes.position;
  const v = new THREE.Vector3();
  for (let i = 0; i < p.count; i++) {
    v.fromBufferAttribute(p, i);
    const ez = Math.abs(v.z) / (d / 2);
    if (ez > 0.9) { v.x *= 1 - bevel; v.y *= 1 - bevel; }
    p.setXYZ(i, v.x, v.y, v.z);
  }
  g.computeVertexNormals();
  return g;
}

export class Enemy {
  /**
   * @param {string} typeId  ENEMY_TYPES anahtarı
   * @param {THREE.Vector3} position
   * @param {Terrain} terrain
   */
  constructor(typeId, position, terrain) {
    const t = ENEMY_TYPES[typeId];
    this.type = t;
    this.typeId = typeId;
    this.terrain = terrain;
    this.name = t.name;
    this.hpMax = t.hp;
    this.hp = t.hp;
    this.damage = t.damage;
    this.xpReward = t.xp;
    this.radius = t.radius * t.scale;
    this.attackRange = t.attackRange;

    this.pos = position.clone();
    this.pos.y = terrain.heightAt(position.x, position.z);
    this.home = this.pos.clone();
    this.yaw = Math.random() * TAU;
    this.speed = 0;
    this.phase = Math.random() * TAU;

    this.state = 'idle';
    this.stateTime = Math.random() * 3;
    this.alive = true;
    this.dead = false;
    this.hitFlash = 0;
    this.attackDidHit = false;
    this.wanderTarget = null;

    this.root = new THREE.Group();
    this.root.name = 'enemy-' + typeId;
    this.j = {};
    this._build();
    this.root.position.copy(this.pos);
    this.root.rotation.y = this.yaw;
  }

  _joint(name, parent, x, y, z) {
    const g = new THREE.Group();
    g.position.set(x, y, z);
    parent.add(g);
    this.j[name] = g;
    return g;
  }

  _build() {
    const t = this.type;
    const c = t.colors;
    const L = (col) => new THREE.MeshLambertMaterial({ color: col });
    this.mats = {
      body: L(c.body), belly: L(c.belly), dark: L(c.dark),
      eye: new THREE.MeshBasicMaterial({ color: c.eye }),
    };
    // Hasar yanıp sönmesi için gövde materyalinin emissive'i kullanılıyor
    this.mats.body.emissive = new THREE.Color(0xff2200);
    this.mats.body.emissiveIntensity = 0;
    this.mats.belly.emissive = new THREE.Color(0xff2200);
    this.mats.belly.emissiveIntensity = 0;

    if (t.plan === 'quad') this._buildQuadruped();
    else this._buildBiped();

    this.root.scale.setScalar(t.scale);
  }

  /* -------- Dört ayaklı: kurt / domuz -------- */
  _buildQuadruped() {
    const M = this.mats;
    const isBoar = this.typeId === 'domuz';
    const bodyLen = isBoar ? 1.5 : 1.4;
    const bodyH = isBoar ? 0.72 : 0.6;
    const hipY = isBoar ? 0.78 : 0.82;

    const core = this._joint('core', this.root, 0, hipY, 0);
    core.add(part(blob(isBoar ? 0.78 : 0.62, bodyH, bodyLen), M.body, 0, 0, 0));
    core.add(part(blob(isBoar ? 0.60 : 0.46, bodyH * 0.5, bodyLen * 0.86), M.belly, 0, -bodyH * 0.3, 0));
    if (isBoar) {
      // Domuzun sırt kamburu ve kılları
      core.add(part(blob(0.5, 0.3, 0.7, 0.35), M.dark, 0, bodyH * 0.45, -0.1));
      for (let i = 0; i < 5; i++) {
        core.add(part(new THREE.ConeGeometry(0.05, 0.22, 4), M.dark,
          0, bodyH * 0.6, -0.35 + i * 0.18));
      }
    }

    // Boyun ve kafa
    const neck = this._joint('neck', core, 0, bodyH * 0.20, bodyLen * 0.44);
    // Boynu gövdeye bağlayan konik geçiş: kafa havada durmasın
    neck.add(part(new THREE.CylinderGeometry(0.20, 0.30, 0.34, 8).rotateX(Math.PI / 2),
      M.body, 0, 0.02, 0.10));
    neck.add(part(new THREE.SphereGeometry(0.21, 10, 8), M.body, 0, 0.02, 0.20));
    const head = this._joint('head', neck, 0, 0.06, 0.26);
    head.add(part(blob(0.32, 0.30, 0.38), M.body, 0, 0, 0.12));
    // Uzun burun/suratlık
    head.add(part(blob(isBoar ? 0.27 : 0.19, isBoar ? 0.23 : 0.17, isBoar ? 0.30 : 0.36),
      M.body, 0, -0.05, isBoar ? 0.38 : 0.42));
    // Burun ucu: küçük, yoksa gözleri kapatan siyah bir top gibi duruyor
    head.add(part(new THREE.SphereGeometry(0.040, 8, 6), M.dark, 0, -0.04,
      isBoar ? 0.54 : 0.61));
    if (isBoar) {
      // Dişler
      // Dişler: burnun iki yanından yukarı kıvrılan küçük fildişleri.
      // Öncekiler kocaman turuncu koni gibi duruyordu.
      for (const s of [-1, 1]) {
        const tusk = new THREE.ConeGeometry(0.028, 0.17, 6);
        tusk.rotateZ(s * 0.30);
        tusk.rotateX(-0.45);
        head.add(part(tusk, new THREE.MeshLambertMaterial({ color: 0xe8e0cc }),
          s * 0.13, -0.05, 0.44));
      }
    } else {
      // Kulaklar
      for (const s of [-1, 1]) {
        const ear = new THREE.ConeGeometry(0.09, 0.22, 4);
        head.add(part(ear, M.dark, s * 0.12, 0.20, 0.02));
      }
    }
    // Gözler: küçük, göz akı + bebek
    for (const s of [-1, 1]) {
      head.add(part(new THREE.SphereGeometry(0.038, 8, 6), M.eye, s * 0.118, 0.09, 0.24));
      head.add(part(new THREE.SphereGeometry(0.018, 6, 5), M.dark, s * 0.126, 0.09, 0.27));
    }

    /*
     * Bacaklar: ön/arka × sol/sağ. Önceki sürümde ince silindirlerdi ve
     * gövdeye değmedikleri için hayvan "masa" gibi görünüyordu; artık
     * kalçadan bileğe incelen kalın koniler ve omuz/kalça yuvarlaması var.
     */
    const legs = [
      ['FL', -1, bodyLen * 0.32], ['FR', 1, bodyLen * 0.32],
      ['BL', -1, -bodyLen * 0.32], ['BR', 1, -bodyLen * 0.32],
    ];
    const upperLen = hipY * 0.50, lowerLen = hipY * 0.42;
    const legX = isBoar ? 0.28 : 0.23;
    for (const [id, side, z] of legs) {
      // Gövdeye kaynak omuz/kalça yuvarlaması
      core.add(part(new THREE.SphereGeometry(0.155, 8, 6), M.body,
        side * legX, -bodyH * 0.30, z));
      const hip = this._joint('leg' + id, core, side * legX, -bodyH * 0.32, z);
      hip.add(part(new THREE.CylinderGeometry(0.145, 0.105, upperLen, 8), M.body, 0, -upperLen / 2, 0));
      const knee = this._joint('knee' + id, hip, 0, -upperLen, 0);
      knee.add(part(new THREE.SphereGeometry(0.10, 8, 6), M.body, 0, 0, 0));
      knee.add(part(new THREE.CylinderGeometry(0.098, 0.072, lowerLen, 8), M.body, 0, -lowerLen / 2, 0));
      const foot = this._joint('foot' + id, knee, 0, -lowerLen, 0);
      foot.add(part(blob(0.17, 0.11, 0.24, 0.3), M.dark, 0, -0.04, 0.04));
    }
    this.legIds = ['FL', 'FR', 'BL', 'BR'];
    this.legLengths = { upper: upperLen, lower: lowerLen };

    // Kuyruk
    const tail = this._joint('tail', core, 0, bodyH * 0.2, -bodyLen * 0.5);
    tail.add(part(new THREE.CylinderGeometry(0.05, 0.02, isBoar ? 0.28 : 0.5, 5)
      .rotateX(Math.PI / 2), M.dark, 0, 0, isBoar ? -0.14 : -0.25));
    this.hitHeight = hipY + 0.3;
  }

  /* -------- İki ayaklı: haydut -------- */
  _buildBiped() {
    const M = this.mats;
    const hipY = 0.92;
    const hips = this._joint('core', this.root, 0, hipY, 0);
    hips.add(part(blob(0.40, 0.26, 0.26), M.body, 0, 0, 0));
    const chest = this._joint('chest', hips, 0, 0.28, 0);
    chest.add(part(blob(0.48, 0.44, 0.30), M.body, 0, 0, 0));
    // Deri yelek ve kemer
    chest.add(part(blob(0.50, 0.30, 0.32, 0.28), M.dark, 0, 0.04, 0));
    chest.add(part(blob(0.36, 0.22, 0.32, 0.3), M.belly, 0, -0.10, 0.03));
    hips.add(part(blob(0.44, 0.09, 0.30, 0.2), M.dark, 0, 0.10, 0));
    // Boyun: kafa gövdeden kopuk durmasın
    chest.add(part(new THREE.CylinderGeometry(0.085, 0.11, 0.14, 8), M.belly, 0, 0.26, 0));
    const head = this._joint('head', chest, 0, 0.36, 0);
    const skull = new THREE.SphereGeometry(0.155, 12, 10);
    skull.scale(1, 1.08, 1.02);
    head.add(part(skull, M.belly, 0, 0.02, 0));
    head.add(part(blob(0.20, 0.11, 0.16, 0.3), M.belly, 0, -0.06, 0.10));  // çene
    // Bandana ve saç
    head.add(part(new THREE.CylinderGeometry(0.163, 0.163, 0.075, 12), M.dark, 0, 0.09, 0));
    head.add(part(new THREE.SphereGeometry(0.158, 12, 8, 0, Math.PI * 2, 0, Math.PI * 0.45),
      new THREE.MeshLambertMaterial({ color: 0x2a1f16 }), 0, 0.04, 0));
    for (const s of [-1, 1]) {
      head.add(part(new THREE.SphereGeometry(0.026, 7, 6),
        new THREE.MeshLambertMaterial({ color: 0xe8e2d6 }), s * 0.058, 0.015, 0.135));
      head.add(part(new THREE.SphereGeometry(0.013, 6, 5), M.dark, s * 0.062, 0.015, 0.150));
    }

    for (const side of [-1, 1]) {
      const S = side < 0 ? 'L' : 'R';
      // Omuz yuvarlaması: kol gövdeden kopuk başlamasın
      chest.add(part(new THREE.SphereGeometry(0.105, 8, 6), M.body, side * 0.25, 0.13, 0));
      const sh = this._joint('arm' + S, chest, side * 0.25, 0.12, 0);
      sh.add(part(new THREE.CylinderGeometry(0.088, 0.070, 0.30, 8), M.body, 0, -0.15, 0));
      const fore = this._joint('fore' + S, sh, 0, -0.30, 0);
      fore.add(part(new THREE.SphereGeometry(0.072, 8, 6), M.belly, 0, 0, 0));
      fore.add(part(new THREE.CylinderGeometry(0.070, 0.055, 0.28, 8), M.belly, 0, -0.14, 0));
      const hand = this._joint('hand' + S, fore, 0, -0.28, 0);
      hand.add(part(blob(0.095, 0.10, 0.085, 0.3), M.dark, 0, -0.05, 0));
      if (side > 0) {
        // Sağ elde sopa
        const club = new THREE.Group();
        club.add(part(new THREE.CylinderGeometry(0.035, 0.045, 0.7, 6), M.dark, 0, -0.3, 0));
        club.add(part(blob(0.16, 0.24, 0.16, 0.28), M.body, 0, -0.72, 0));
        club.rotation.x = -0.3;
        hand.add(club);
      }
    }

    const upperLen = 0.44, lowerLen = 0.40;
    for (const side of [-1, 1]) {
      const S = side < 0 ? 'L' : 'R';
      hips.add(part(new THREE.SphereGeometry(0.115, 8, 6), M.body, side * 0.13, -0.07, 0));
      const hip = this._joint('leg' + S, hips, side * 0.13, -0.08, 0);
      hip.add(part(new THREE.CylinderGeometry(0.115, 0.088, upperLen, 8), M.body, 0, -upperLen / 2, 0));
      const knee = this._joint('knee' + S, hip, 0, -upperLen, 0);
      knee.add(part(new THREE.SphereGeometry(0.085, 8, 6), M.body, 0, 0, 0));
      knee.add(part(new THREE.CylinderGeometry(0.082, 0.062, lowerLen, 8), M.body, 0, -lowerLen / 2, 0));
      const foot = this._joint('foot' + S, knee, 0, -lowerLen, 0);
      foot.add(part(blob(0.125, 0.095, 0.24, 0.3), M.dark, 0, -0.04, 0.05));
    }
    this.legIds = ['L', 'R'];
    this.legLengths = { upper: upperLen, lower: lowerLen };
    this.hitHeight = 1.15;
  }

  /* ---------------- Animasyon ---------------- */

  _animate(dt) {
    const t = this.type;
    const quad = t.plan === 'quad';
    const j = this.j;

    // Yürüyüş fazı kat edilen yola bağlı
    if (this.speed > 0.05) {
      this.phase += dt * (this.speed / t.cycleDistance) * TAU;
      if (this.phase > TAU) this.phase -= TAU;
    }
    const ph = this.phase;
    const moving = this.speed > 0.05;
    const amp = moving ? Math.min(1, this.speed / t.speed) : 0;

    // Saldırı hamlesi
    let lunge = 0;
    if (this.state === 'attack') {
      const u = this.stateTime / t.attackTime;
      lunge = Math.sin(THREE.MathUtils.clamp(u, 0, 1) * Math.PI) * 1;
    }

    if (quad) {
      const core = j.core;
      core.position.y = (this.baseHipY ?? core.position.y);
      this.baseHipY ??= core.position.y;
      core.position.y = this.baseHipY + Math.abs(Math.sin(ph * 2)) * 0.05 * amp;
      core.rotation.x = -0.12 * lunge + Math.sin(ph * 2) * 0.03 * amp;

      // Çapraz ayak çiftleri: FL+BR, FR+BL
      const offs = { FL: 0, BR: 0, FR: Math.PI, BL: Math.PI };
      for (const id of this.legIds) {
        const p = ph + offs[id];
        const sw = Math.sin(p);
        j['leg' + id].rotation.x = sw * 0.75 * amp - 0.05 - lunge * 0.25;
        j['knee' + id].rotation.x = -Math.max(0, -sw) * 0.85 * amp - 0.12;
        j['foot' + id].rotation.x = Math.max(0, sw) * 0.3 * amp;
      }
      // Kafa: saldırıda öne atılır
      j.neck.rotation.x = 0.1 - lunge * 0.5 + Math.sin(ph) * 0.05 * amp;
      j.head.rotation.x = -0.05 + lunge * 0.35;
      if (j.tail) {
        j.tail.rotation.y = Math.sin(ph * 1.5) * 0.35 * (0.3 + amp);
        j.tail.rotation.x = -0.3 - amp * 0.3;
      }
    } else {
      const sw = Math.sin(ph);
      j.core.rotation.y = sw * 0.12 * amp;
      j.chest.rotation.y = -sw * 0.16 * amp;
      j.chest.rotation.x = 0.06 + lunge * 0.18;
      j.head.rotation.x = -0.04 - lunge * 0.12;

      j.legL.rotation.x = sw * 0.65 * amp - 0.04;
      j.legR.rotation.x = -sw * 0.65 * amp - 0.04;
      j.kneeL.rotation.x = -Math.max(0, -sw) * 0.9 * amp - 0.08;
      j.kneeR.rotation.x = -Math.max(0, sw) * 0.9 * amp - 0.08;
      j.footL.rotation.x = Math.max(0, sw) * 0.3 * amp;
      j.footR.rotation.x = Math.max(0, -sw) * 0.3 * amp;

      // Sol kol yürüyüşle sallanır, sağ kol sopayı savurur
      j.armL.rotation.x = -sw * 0.5 * amp;
      j.foreL.rotation.x = -0.4;
      j.armR.rotation.x = -0.3 + sw * 0.3 * amp - lunge * 2.4;
      j.armR.rotation.z = -0.2;
      j.foreR.rotation.x = -0.7 + lunge * 0.5;
    }
  }

  /* ---------------- Yapay zekâ ---------------- */

  setState(s) {
    if (this.state === s) return;
    this.state = s;
    this.stateTime = 0;
    if (s === 'attack') this.attackDidHit = false;
  }

  takeDamage(amount) {
    if (!this.alive) return false;
    this.hp = Math.max(0, this.hp - amount);
    this.hitFlash = 0.16;
    if (this.hp <= 0) {
      this.alive = false;
      this.setState('die');
      return true;
    }
    // Vurulunca oyuncuyu hedefe alır
    if (this.state !== 'attack') this.setState('chase');
    return false;
  }

  /** Saldırının hasar penceresinde mi? (her saldırıda bir kez) */
  get canLandHit() {
    if (this.state !== 'attack' || this.attackDidHit) return false;
    const u = this.stateTime / this.type.attackTime;
    const [a, b] = this.type.damageAt;
    return u > a && u < b;
  }

  /**
   * @param {number} dt
   * @param {THREE.Vector3} playerPos
   * @returns {boolean} tamamen yok olduysa true
   */
  update(dt, playerPos) {
    this.stateTime += dt;
    const t = this.type;

    if (this.hitFlash > 0) {
      this.hitFlash = Math.max(0, this.hitFlash - dt);
      const k = this.hitFlash / 0.16;
      this.mats.body.emissiveIntensity = k * 0.9;
      this.mats.belly.emissiveIntensity = k * 0.9;
    }

    /* -- Ölüm -- */
    if (!this.alive) {
      const u = Math.min(1, this.stateTime / 1.5);
      this.root.rotation.z = -u * 1.5;
      this.root.position.y = this.pos.y - u * 0.25;
      this.speed = 0;
      if (u >= 1) {
        // Yere yattıktan sonra sönerek kaybolur
        const fade = Math.min(1, (this.stateTime - 1.5) / 1.2);
        for (const m of Object.values(this.mats)) {
          m.transparent = true;
          m.opacity = 1 - fade;
        }
        if (fade >= 1) { this.dead = true; return true; }
      }
      this._animate(dt);
      return false;
    }

    const toPlayer = _v.subVectors(playerPos, this.pos);
    toPlayer.y = 0;
    const dist = toPlayer.length();

    /* -- Durum geçişleri -- */
    switch (this.state) {
      case 'idle':
        this.speed *= 1 - Math.min(1, dt * 4);
        // Ara sıra yuvasının çevresinde dolaş
        if (this.stateTime > 3 + Math.random() * 3) {
          const a = Math.random() * TAU;
          const r = 3 + Math.random() * 7;
          this.wanderTarget = new THREE.Vector3(
            this.home.x + Math.sin(a) * r, 0, this.home.z + Math.cos(a) * r);
          this.setState('wander');
        }
        if (dist < t.aggro) this.setState('chase');
        break;

      case 'wander': {
        if (dist < t.aggro) { this.setState('chase'); break; }
        const d = _v.subVectors(this.wanderTarget, this.pos);
        d.y = 0;
        if (d.length() < 1 || this.stateTime > 6) { this.setState('idle'); break; }
        this._moveToward(d, t.speed * 0.32, dt);
        break;
      }

      case 'chase': {
        // Oyuncu uzaklaşırsa vazgeçip yuvasına dön
        if (dist > t.aggro * 1.8) {
          this.wanderTarget = this.home.clone();
          this.setState('wander');
          break;
        }
        if (dist <= t.attackRange) { this.setState('attack'); break; }
        this._moveToward(toPlayer, t.speed, dt);
        break;
      }

      case 'attack':
        this.speed *= 1 - Math.min(1, dt * 8);
        this._faceToward(toPlayer, dt * 6);
        if (this.stateTime >= t.attackTime) {
          this.setState(dist <= t.attackRange * 1.2 ? 'attack' : 'chase');
        }
        break;
    }

    // Zemine otur
    this.pos.y = this.terrain.heightAt(this.pos.x, this.pos.z);
    this.root.position.copy(this.pos);
    this.root.rotation.y = this.yaw;
    this._animate(dt);
    return false;
  }

  _faceToward(dir, k) {
    const target = Math.atan2(dir.x, dir.z);
    let d = target - this.yaw;
    while (d > Math.PI) d -= TAU;
    while (d < -Math.PI) d += TAU;
    this.yaw += d * Math.min(1, k);
  }

  _moveToward(dir, maxSpeed, dt) {
    const len = Math.hypot(dir.x, dir.z);
    if (len < 1e-4) return;
    this._faceToward(dir, dt * 5);
    this.speed += (maxSpeed - this.speed) * Math.min(1, dt * 4);
    // Baktığı yöne ilerler: dönüş tamamlanmadan yana kaymaz
    const fx = Math.sin(this.yaw), fz = Math.cos(this.yaw);
    this.pos.x += fx * this.speed * dt;
    this.pos.z += fz * this.speed * dt;
  }

  getHitCenter(out = new THREE.Vector3()) {
    return out.set(this.pos.x, this.pos.y + this.hitHeight * this.type.scale, this.pos.z);
  }

  addTo(scene) { scene.add(this.root); return this; }

  dispose(scene) {
    scene.remove(this.root);
    this.root.traverse((o) => { if (o.isMesh) o.geometry.dispose(); });
    for (const m of Object.values(this.mats)) m.dispose();
  }
}
