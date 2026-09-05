/**
 * Combat.js — Savaş döngüsü.
 *
 * Metin taşlarını ve canavarları dünyaya yerleştirir, vuruşları çözer,
 * hasar/tecrübe/seviye işler.
 *
 * Performans için canavarlar tembel yaratılıyor: her taşın çevresinde bir
 * "kamp" tanımı duruyor, oyuncu yaklaşınca canavarlar gerçekten oluşuyor,
 * uzaklaşınca sahneden kaldırılıyor. Böylece mobilde aynı anda ekranda
 * onlarca değil, bir avuç düşman oluyor.
 */
import * as THREE from 'three';
import { MetinStone, METIN_TIERS } from '../entities/MetinStone.js';
import { Enemy, ENEMY_TYPES } from '../entities/Enemy.js';
import { HealthBar3D } from '../ui/HealthBar3D.js';
import { LootDrop } from '../entities/LootDrop.js';
import { rollDrop } from '../data/items.js';
import { KINGDOMS } from '../data/kingdoms.js';

const TAU = Math.PI * 2;
const _center = new THREE.Vector3();   // hedefin vuruş merkezi
const _textPos = new THREE.Vector3();  // hasar yazısının konumu

/** Oyuncunun hareketine göre vuruş menzili ve açısı. */
const SWING = {
  attack: { range: 2.9, arc: Math.PI * 0.62, mult: 1.0 },
  tripleCut: { range: 3.1, arc: Math.PI * 0.70, mult: 0.72 },
  spin: { range: 3.4, arc: Math.PI * 2, mult: 0.55 },
};

/** Canavarların yaklaşınca oluşturulacağı mesafe. */
const SPAWN_IN = 62;
const SPAWN_OUT = 95;

function rng(seed) {
  let s = seed >>> 0;
  return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
}

export class Combat {
  /**
   * @param {object} deps { scene, terrain, collision, floatingText, hud, stats }
   */
  constructor(deps) {
    this.scene = deps.scene;
    this.terrain = deps.terrain;
    this.collision = deps.collision;
    this.text = deps.floatingText;
    this.hud = deps.hud;
    this.stats = deps.stats;

    this.stones = [];
    this.camps = [];        // { stone, pos, defs, enemies, active }
    this.enemies = [];      // sahnede canlı olanlar
    this.bars = new Map();  // enemy -> HealthBar3D

    this.hitCooldown = new Map();   // hedef -> kalan süre
    this.drops = [];                // yerde duran eşyalar
    this.onLoot = null;             // toplanan eşya geri çağrısı
    this.onPlayerDamaged = null;
    this.onLevelUp = null;
    this.lastTarget = null;
    this.lastTargetTime = 0;
  }

  /* ---------------- Dünyaya yerleştirme ---------------- */

  /**
   * Metin taşlarını köylerin dışına, yürünebilir zemine dağıtır.
   * @param {number} count
   */
  populate(count = 12) {
    const r = rng(90210);
    const half = this.terrain.half - 120;
    let placed = 0, tries = 0;

    while (placed < count && tries < count * 60) {
      tries++;
      const x = (r() * 2 - 1) * half;
      const z = (r() * 2 - 1) * half;

      // Köylerin ve yolların yakınına konmasın
      let tooClose = false;
      for (const k of KINGDOMS) {
        if (Math.hypot(x - k.center.x, z - k.center.z) < 170) { tooClose = true; break; }
      }
      if (tooClose) continue;
      // Diğer taşlardan uzak dursun
      for (const s of this.stones) {
        if (Math.hypot(x - s.group.position.x, z - s.group.position.z) < 95) { tooClose = true; break; }
      }
      if (tooClose) continue;

      const y = this.terrain.heightAt(x, z);
      if (y < this.terrain.waterLevel + 2) continue;
      if (this.terrain.slopeAt(x, z) > 0.22) continue;
      if (y > 80) continue;

      // Merkeze uzaklık arttıkça daha güçlü taşlar
      const distFromCenter = Math.hypot(x, z) / half;
      const tierIdx = Math.min(METIN_TIERS.length - 1,
        Math.floor(distFromCenter * METIN_TIERS.length * 0.9 + r() * 0.8));
      const tier = METIN_TIERS[tierIdx];

      const stone = new MetinStone(tier, new THREE.Vector3(x, y, z), { seed: 5 + placed * 13 });
      stone.addTo(this.scene);
      this.stones.push(stone);

      // Taş katı bir engel
      const col = { kind: 'circle', x, z, r: stone.radius };
      stone.collider = col;
      this.collision.add(col);

      // Çevresindeki kamp: taşın seviyesine göre canavar karışımı
      this.camps.push(this._makeCamp(stone, tierIdx, r));
      placed++;
    }
    return placed;
  }

  _makeCamp(stone, tierIdx, r) {
    const pool = tierIdx === 0 ? ['kurt', 'kurt', 'domuz']
      : tierIdx === 1 ? ['kurt', 'domuz', 'haydut']
        : ['domuz', 'haydut', 'haydut'];
    const n = 3 + Math.floor(r() * 3);
    const defs = [];
    for (let i = 0; i < n; i++) {
      const a = r() * TAU;
      const d = 7 + r() * 12;
      defs.push({
        type: pool[Math.floor(r() * pool.length)],
        x: stone.group.position.x + Math.sin(a) * d,
        z: stone.group.position.z + Math.cos(a) * d,
      });
    }
    return { stone, defs, enemies: [], active: false };
  }

  /* ---------------- Canavar akışı ---------------- */

  _updateCamps(playerPos) {
    for (const camp of this.camps) {
      const d = Math.hypot(
        playerPos.x - camp.stone.group.position.x,
        playerPos.z - camp.stone.group.position.z);

      if (!camp.active && d < SPAWN_IN) {
        for (const def of camp.defs) {
          const e = this._spawnEnemy(def.type, def.x, def.z);
          camp.enemies.push(e);
        }
        camp.active = true;
      } else if (camp.active && d > SPAWN_OUT) {
        for (const e of camp.enemies) this._removeEnemy(e);
        camp.enemies.length = 0;
        camp.active = false;
      }
    }
  }

  _spawnEnemy(typeId, x, z) {
    const e = new Enemy(typeId, new THREE.Vector3(x, 0, z), this.terrain);
    e.addTo(this.scene);
    this.enemies.push(e);
    const bar = new HealthBar3D({
      width: 1.0, height: 0.12,
      color: 0xc0392b,
      yOffset: (e.hitHeight + 0.7) * e.type.scale,
    });
    bar.addTo(this.scene);
    this.bars.set(e, bar);
    return e;
  }

  _removeEnemy(e) {
    const i = this.enemies.indexOf(e);
    if (i >= 0) this.enemies.splice(i, 1);
    const bar = this.bars.get(e);
    if (bar) { bar.dispose(this.scene); this.bars.delete(e); }
    e.dispose(this.scene);
  }

  /* ---------------- Vuruş çözümü ---------------- */

  /**
   * Oyuncunun savurması hasar penceresindeyse menzildeki hedeflere vurur.
   * @param {object} player  Warrior/ModelCharacter
   * @param {THREE.Vector3} pos oyuncunun konumu
   * @param {number} yaw
   */
  _resolvePlayerHits(player, pos, yaw, dt) {
    for (const [k, v] of this.hitCooldown) {
      const nv = v - dt;
      if (nv <= 0) this.hitCooldown.delete(k); else this.hitCooldown.set(k, nv);
    }
    if (!player.inDamageWindow) return;

    const cfg = SWING[player.state] || SWING.attack;
    const fx = Math.sin(yaw), fz = Math.cos(yaw);

    const tryHit = (target, center, radius) => {
      if (this.hitCooldown.has(target)) return;
      const dx = center.x - pos.x, dz = center.z - pos.z;
      const dist = Math.hypot(dx, dz) - radius;
      if (dist > cfg.range) return;
      if (cfg.arc < TAU) {
        const dot = (dx * fx + dz * fz) / Math.max(1e-4, Math.hypot(dx, dz));
        if (Math.acos(THREE.MathUtils.clamp(dot, -1, 1)) > cfg.arc / 2) return;
      }
      const { amount, crit } = this._rollDamage(cfg.mult);
      this.hitCooldown.set(target, 0.38);
      this._applyDamage(target, center, amount, crit);
    };

    for (const s of this.stones) {
      if (!s.alive) continue;
      tryHit(s, s.getHitCenter(_center), s.radius);
    }
    for (const e of this.enemies) {
      if (!e.alive) continue;
      tryHit(e, e.getHitCenter(_center), e.radius);
    }
  }

  _rollDamage(mult) {
    const base = this.stats.attack ?? 30;
    const variance = 0.85 + Math.random() * 0.3;
    const crit = Math.random() < (this.stats.critChance ?? 0.15);
    let amount = base * variance * mult;
    if (crit) amount *= this.stats.critMult ?? 1.8;
    return { amount, crit };
  }

  _applyDamage(target, center, amount, crit) {
    const died = target.takeDamage(amount);
    _textPos.set(center.x, center.y + 0.6, center.z);
    this.text.damage(amount, _textPos, { crit });

    this.lastTarget = target;
    this.lastTargetTime = 0;

    if (!died) return;
    if (target instanceof MetinStone) {
      this._onStoneDestroyed(target);
    } else {
      this._gainXp(target.xpReward);
      this.hud.toast(`${target.name} yenildi  +${target.xpReward} TP`);
      this._dropLoot(center, Math.max(1, this.stats.level), 0.35);
    }
  }

  _onStoneDestroyed(stone) {
    this._gainXp(stone.xpReward);
    this.hud.toast(`${stone.name} kırıldı  +${stone.xpReward} TP`);
    if (stone.collider) stone.collider.disabled = true;

    // Taş kırılınca içinden canavar çıkar
    const p = stone.group.position;
    for (let i = 0; i < stone.spawnCount; i++) {
      const a = (i / stone.spawnCount) * TAU + Math.random() * 0.5;
      const d = 3 + Math.random() * 3;
      const type = stone.level <= 1 ? 'kurt' : stone.level === 2 ? 'domuz' : 'haydut';
      const e = this._spawnEnemy(type, p.x + Math.sin(a) * d, p.z + Math.cos(a) * d);
      e.setState('chase');
    }
    // Metin taşı daha cömert: birden çok eşya düşürebilir
    const p2 = stone.group.position;
    const adet = 1 + Math.floor(Math.random() * stone.level);
    for (let i = 0; i < adet; i++) {
      this._dropLoot(_center.set(p2.x, p2.y + 1.0, p2.z),
        Math.max(1, this.stats.level + stone.level - 1), 0.9);
    }

    // Taş bir süre sonra geri gelsin
    stone.respawnIn = 45;
  }

  /**
   * Hedefin bulunduğu yere eşya düşürür.
   * @param {THREE.Vector3} pos
   * @param {number} level
   * @param {number} sans
   */
  /**
   * Belirli bir eşyayı yere bırakır (çantası dolu oyuncunun çıkardığı gibi).
   * @param {object} item
   * @param {THREE.Vector3} pos
   */
  dropItem(item, pos) {
    if (!item) return null;
    const drop = new LootDrop(item, new THREE.Vector3(pos.x, pos.y, pos.z), this.terrain);
    drop.addTo(this.scene);
    this.drops.push(drop);
    return drop;
  }

  _dropLoot(pos, level, sans) {
    const item = rollDrop(level, sans);
    if (!item) return;
    const a = Math.random() * TAU;
    const d = 0.4 + Math.random() * 0.9;
    const p = new THREE.Vector3(pos.x + Math.sin(a) * d, pos.y, pos.z + Math.cos(a) * d);
    const drop = new LootDrop(item, p, this.terrain);
    drop.addTo(this.scene);
    this.drops.push(drop);
  }

  _gainXp(amount) {
    const s = this.stats;
    s.xp += amount;
    while (s.xp >= s.xpMax) {
      s.xp -= s.xpMax;
      s.level++;
      s.xpMax = Math.round(s.xpMax * 1.45);
      s.hpMax = Math.round(s.hpMax * 1.16);
      s.mpMax = Math.round(s.mpMax * 1.12);
      s.tabanSaldiri = Math.round((s.tabanSaldiri ?? 30) * 1.14);
      s.attack = s.tabanSaldiri;
      s.hp = s.hpMax;
      s.mp = s.mpMax;
      this.hud.toast(`Seviye ${s.level}!`);
      this.onLevelUp?.(s.level);
    }
  }

  /* ---------------- Canavarların oyuncuya vuruşu ---------------- */

  _resolveEnemyHits(playerPos) {
    for (const e of this.enemies) {
      if (!e.alive || !e.canLandHit) continue;
      const d = Math.hypot(playerPos.x - e.pos.x, playerPos.z - e.pos.z);
      if (d > e.attackRange * 1.25) continue;
      e.attackDidHit = true;
      let dmg = e.damage * (0.85 + Math.random() * 0.3);
      // Savunma hasarı azaltır ama sıfırlamaz
      const def = this.stats.defense || 0;
      dmg *= 100 / (100 + def * 2.2);
      this.onPlayerDamaged?.(Math.max(1, dmg), e);
    }
  }

  /* ---------------- Ana güncelleme ---------------- */

  /**
   * @param {number} dt
   * @param {object} player
   * @param {THREE.Vector3} pos
   * @param {number} yaw
   * @param {number} camDist
   */
  update(dt, player, pos, yaw, camDist) {
    this._updateCamps(pos);
    this._resolvePlayerHits(player, pos, yaw, dt);
    this._resolveEnemyHits(pos);

    /* -- Taşlar -- */
    for (let i = this.stones.length - 1; i >= 0; i--) {
      const s = this.stones[i];
      const d = Math.hypot(pos.x - s.group.position.x, pos.z - s.group.position.z);

      if (s.respawnIn !== undefined && s.dead) {
        s.respawnIn -= dt;
        if (s.respawnIn <= 0 && d > 40) {
          // Oyuncu uzaktayken sessizce geri gelsin
          s.hp = s.hpMax;
          s.alive = true;
          s.dead = false;
          s.deathTime = 0;
          delete s.respawnIn;
          s.bar.setRatio(1);
          s.bar.group.visible = true;
          s.body.scale.setScalar(1);
          s.body.position.y = 0;
          for (const sh of s.shards) sh.scale.setScalar(1);
          for (const m of s.motes) m.scale.setScalar(1);
          s.aura.scale.setScalar(1);
          s.group.visible = true;
          if (s.collider) s.collider.disabled = false;
        }
        continue;
      }
      if (d > 190) { s.group.visible = false; continue; }
      s.group.visible = true;
      if (s.update(dt, camDist) && s.dead) {
        s.group.visible = false;    // yıkıldı; yeniden doğana kadar gizli
      }
    }

    /* -- Canavarlar -- */
    for (let i = this.enemies.length - 1; i >= 0; i--) {
      const e = this.enemies[i];
      const gone = e.update(dt, pos);
      const bar = this.bars.get(e);
      if (bar) {
        bar.setRatio(e.hp / e.hpMax);
        bar.group.visible = e.alive && e.hp < e.hpMax;
        if (bar.group.visible) bar.update(dt, e.pos, camDist);
      }
      if (gone) {
        for (const camp of this.camps) {
          const j = camp.enemies.indexOf(e);
          if (j >= 0) camp.enemies.splice(j, 1);
        }
        this._removeEnemy(e);
      }
    }

    /* -- Yerdeki eşyalar -- */
    for (let i = this.drops.length - 1; i >= 0; i--) {
      const d = this.drops[i];
      if (!d.update(dt, pos)) continue;
      if (d.alindi) {
        /*
         * Çanta doluysa eşya alınamıyor; o zaman yerde kalmalı. Eskiden
         * koşulsuz yok ediliyordu ve dolu çantayla üstünden geçmek eşyayı
         * siliyordu. onLoot false döndüğünde toplama iptal ediliyor.
         */
        if (this.onLoot?.(d.item) === false) {
          d.alindi = false;
          d.bekleme = 2.5;           // hemen yeniden denemesin
          continue;
        }
      }
      d.dispose(this.scene);
      this.drops.splice(i, 1);
    }

    /* -- Hedef bilgisi arayüze -- */
    this.lastTargetTime += dt;
    if (this.lastTarget && this.lastTargetTime < 5) {
      const t = this.lastTarget;
      const alive = t.alive && !t.dead;
      this.hud.setTarget(alive ? {
        name: t.name,
        level: t.level,
        ratio: t.hp / t.hpMax,
      } : null);
      if (!alive) this.lastTarget = null;
    } else {
      this.hud.setTarget(null);
      this.lastTarget = null;
    }
  }

  /** Yakındaki canlı canavar sayısı — hata ayıklama ve arayüz için. */
  get activeEnemyCount() { return this.enemies.filter((e) => e.alive).length; }
}

export { METIN_TIERS, ENEMY_TYPES };
