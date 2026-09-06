/**
 * main.js — Oyunun giriş noktası.
 *
 * Akış: krallık seçim ekranı açılırken dünya arka planda parça parça
 * kuruluyor (her adım arasında tarayıcıya nefes aldırılıyor ki yükleme
 * çubuğu akıcı kalsın), sonra seçilen krallığın köyünde oyun başlıyor.
 */
import * as THREE from 'three';
import { Engine, detectQuality } from './core/Engine.js';
import { Input } from './core/Input.js';
import { Terrain } from './world/Terrain.js';
import { Village } from './world/Village.js';
import { Nature } from './world/Nature.js';
import { CollisionWorld } from './world/Collision.js';
import { Warrior } from './entities/Warrior.js';
import { tryLoadCharacterModel } from './entities/CharacterFactory.js';
import { PlayerController } from './entities/PlayerController.js';
import { SwordTrail } from './entities/SwordTrail.js';
import { HUD } from './ui/HUD.js';
import { KingdomSelect } from './ui/KingdomSelect.js';
import { Combat } from './systems/Combat.js';
import { Inventory } from './systems/Inventory.js';
import { InventoryUI } from './ui/InventoryUI.js';
import { starterItems } from './data/items.js';
import { SURUM } from './data/version.js';
import { FloatingText } from './ui/FloatingText.js';
import { KINGDOMS } from './data/kingdoms.js';

const nextFrame = () => new Promise((r) => requestAnimationFrame(() => r()));

class Game {
  constructor() {
    this.quality = detectQuality();
    this.engine = new Engine(document.getElementById('scene'), this.quality);
    this.input = new Input(document.getElementById('input-layer'));
    this.hud = new HUD(document.getElementById('hud'), this.input);
    this.select = new KingdomSelect(document.getElementById('overlay'));

    this.villages = new Map();
    this.npcs = [];
    this.running = false;

    // Karakter durumu
    this.stats = {
      level: 1, hp: 320, hpMax: 320, mp: 120, mpMax: 120, xp: 0, xpMax: 1000,
      // Saldırı = seviyeden gelen taban + kuşanılan ekipman
      tabanSaldiri: 34, attack: 34, defense: 0,
      critChance: 0.15, critMult: 1.8,
    };

    // Çanta ve ekipman
    /*
     * Metin2'de bir çanta sayfası 5x9. Otuz göz, taş kırma hızına göre çok
     * çabuk doluyordu ve dolu çantada ekipman çıkarılamadığı için "çıkardım
     * ama üstümde duruyor" gibi görünüyordu.
     */
    this.inventory = new Inventory({ gozSayisi: 45 });
    this.inventoryUI = new InventoryUI(document.getElementById('hud'), this.inventory);
    this.inventoryUI.onMesaj = (m) => this.hud.toast(m);
    // Çanta doluyken çıkarılan eşya yere düşsün: çıkarma hiçbir zaman
    // engellenmesin.
    this.inventoryUI.onYereAt = (item) => this._esyaYereAt(item);
    this.inventory.subscribe(() => this._ekipmanUygula());

    this.select.onStart((k) => this.start(k));
    document.getElementById('hud').style.display = 'none';
    document.getElementById('input-layer').style.display = 'none';

    this.buildWorld();
  }

  /* ---------------- Dünya kurulumu ---------------- */
  async buildWorld() {
    const p = (t, label) => this.select.setProgress(t, label);

    p(0.05, 'Arazi şekillendiriliyor…');
    await nextFrame();
    this.terrain = new Terrain({
      size: 1400,
      segments: this.quality.terrainSegments,
    });
    this.terrain.addTo(this.engine.scene);

    this.collision = new CollisionWorld(16);

    let i = 0;
    for (const k of KINGDOMS) {
      i++;
      p(0.15 + i * 0.2, `${k.village} kuruluyor…`);
      await nextFrame();
      const v = new Village(k, this.terrain);
      v.addTo(this.engine.scene);
      this.collision.addAll(v.colliders);
      this.villages.set(k.id, v);
      this._spawnGuards(v);
    }

    p(0.82, 'Ormanlar ekiliyor…');
    await nextFrame();
    this.nature = new Nature(this.terrain, { count: this.quality.treeCount });
    this.nature.addTo(this.engine.scene);
    this.collision.addAll(this.nature.colliders);

    p(0.90, 'Metin taşları dikiliyor…');
    await nextFrame();
    this.floatingText = new FloatingText(this.engine.scene, 24);
    this.combat = new Combat({
      scene: this.engine.scene,
      terrain: this.terrain,
      collision: this.collision,
      floatingText: this.floatingText,
      hud: this.hud,
      stats: this.stats,
    });
    const stoneCount = this.combat.populate(this.quality.tier === 'low' ? 9 : 13);
    console.info(`[savaş] ${stoneCount} metin taşı yerleştirildi`);

    p(0.95, 'Son rötuşlar…');
    await nextFrame();
    // Dışarıdan model varsa şimdi yükle: krallık seçilirken beklesin,
    // başlat düğmesine basıldığında oyun anında açılsın.
    this.loadedModel = await tryLoadCharacterModel((msg) => p(0.96, msg));
    this.player = null;
    this.controller = null;

    p(1, 'Hazır');
    this.select.setReady();

    // Ekran arka planında yavaşça dönen bir tanıtım kamerası
    this._introAngle = 0;
    this._startPreviewLoop();
  }

  /** Köy kapısında nöbet tutan muhafızlar. */
  _spawnGuards(village) {
    for (const post of village.guardPosts) {
      // Muhafızlar kalkanlı: oyuncunun çift el kılıcından ayrışsınlar
      const w = new Warrior(village.kingdom.armor, { scale: 1, weapon: 'sword', shield: true });
      w.root.position.set(post.x, this.terrain.heightAt(post.x, post.z), post.z);
      w.root.rotation.y = post.ry;
      w.setState('combatIdle');
      w.animTime = Math.random() * 10;   // hepsi aynı anda nefes almasın
      this.engine.scene.add(w.root);
      this.npcs.push(w);
      this.collision.add({ kind: 'circle', x: post.x, z: post.z, r: 0.5 });
    }
  }

  /* ---------------- Tanıtım kamerası ---------------- */
  _startPreviewLoop() {
    const shinsoo = this.villages.get('shinsoo');
    const c = shinsoo.center;
    const cam = this.engine.camera;
    const loop = () => {
      if (this.running) return;
      this._previewRaf = requestAnimationFrame(loop);
      const dt = Math.min(0.05, this.engine.clock.getDelta());
      this._introAngle += dt * 0.055;
      const r = 170;
      cam.position.set(
        c.x + Math.sin(this._introAngle) * r,
        c.y + 62,
        c.z + Math.cos(this._introAngle) * r,
      );
      cam.lookAt(c.x, c.y + 14, c.z);
      this.engine.follow(new THREE.Vector3(c.x, c.y, c.z));
      for (const n of this.npcs) n.update(dt, 0);
      this.engine.render();
    };
    loop();
  }

  /* ---------------- Oyunu başlat ---------------- */
  start(kingdom) {
    cancelAnimationFrame(this._previewRaf);
    this.kingdom = kingdom;
    this.engine.setAtmosphere(kingdom.biome);

    const village = this.villages.get(kingdom.id);

    // Dışarıdan yüklenmiş model varsa onu kullan, yoksa prosedürel savaşçı.
    // İki sınıf da aynı arayüzü sunduğu için aşağıdaki kod ikisinde de aynı.
    this.player = this.loadedModel
      ? this.loadedModel.character
      : new Warrior(kingdom.armor, { scale: 1, weapon: 'twohand' });
    /*
     * Model yüklenemezse prosedürel savaşçıya düşülüyor. O savaşçının zırhı
     * gövdesine gömülü: kuşanılan eşyalar silah, kalkan ve pelerini
     * değiştirebiliyor ama zırhı çıkaramıyor. Eskiden bu düşüş sessizdi ve
     * "eşyayı çıkardım, üstümde duruyor" gibi görünüyordu. Artık hem ekranda
     * söyleniyor hem de sol alttaki damgada kip yazıyor.
     */
    this.karakterKipi = this.loadedModel ? 'rig' : 'yedek';
    this.hud.damga = SURUM + ' · ' + this.karakterKipi;
    if (!this.loadedModel) {
      const neden = globalThis.__METIN3_MODEL_HATASI__;
      setTimeout(() => this.hud.toast(
        'Model yüklenemedi — yedek görünüm' + (neden ? ': ' + neden.slice(0, 60) : '')), 900);
    }
    /*
     * Oyuncunun görünümü envanterden geliyor: kuşanılan her eşya kendi
     * modelini üretiyor. Bu yüzden burada tam zırh seti takılmıyor, yalnızca
     * krallık paleti veriliyor; parçaları _ekipmanUygula kuruyor.
     */
    if (this.player.setArmorTheme) this.player.setArmorTheme(kingdom.armor);
    else if (this.player.equipArmor) this.player.equipArmor(kingdom.armor);
    this.player.addTo(this.engine.scene);

    this.trail = new SwordTrail(16, kingdom.color).addTo(this.engine.scene);

    this.controller = new PlayerController(
      this.player, this.terrain, this.engine.camera,
      { world: this.collision, stats: this.stats });
    this.controller.onSkillFail = (sk) => this.hud.toast(`${sk.name}: yeterli mana yok`);
    this.controller.teleport(village.spawn.x, village.spawn.z, village.spawnFacing);

    // Başlangıç donanımı: oyuncu çıplak başlamasın
    for (const it of starterItems()) {
      const i = this.inventory.ekle(it);
      if (i >= 0) this.inventory.kusan(i);
    }
    this.hud.el.bag.addEventListener('click', () => this.inventoryUI.degistir());
    this.combat.onLoot = (item) => this._esyaTopla(item);
    this.combat.onPlayerDamaged = (dmg, from) => this._takeDamage(dmg, from);
    this.combat.onLevelUp = () => {
      this.hud.setCharacter({ name: 'Savaşçı', level: this.stats.level, kingdom });
    };
    this.deathTimer = 0;

    this.hud.setCharacter({ name: 'Savaşçı', level: this.stats.level, kingdom });
    this.hud.setStats(this.stats);
    this.hud.showZone(village.kingdom.village, village.kingdom.title);

    document.getElementById('hud').style.display = '';
    document.getElementById('input-layer').style.display = '';
    this.select.hide();

    this.running = true;
    this.engine.clock.getDelta();     // birikmiş süreyi at
    this._loop();

    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) this.engine.clock.getDelta();
    });
  }

  /* ---------------- Oyun döngüsü ---------------- */
  _loop = () => {
    if (!this.running) return;
    requestAnimationFrame(this._loop);
    const dt = Math.min(0.05, this.engine.clock.getDelta());

    this.controller.update(dt, this.input);
    const pos = this.controller.pos;

    // Kılıç izi: savururken namlunun dip/uç noktalarını biriktir
    if (this.player.isSwinging) {
      this.trail.push(
        this.player.getWeaponBaseWorld(this._tv0 ||= new THREE.Vector3()),
        this.player.getWeaponTipWorld(this._tv1 ||= new THREE.Vector3()));
    } else {
      this.trail.fade();
    }

    // Mana ve can yavaşça dolsun
    this.stats.mp = Math.min(this.stats.mpMax, this.stats.mp + dt * 4.5);
    if (this.controller.alive) {
      this.stats.hp = Math.min(this.stats.hpMax, this.stats.hp + dt * 2.2);
    }

    // Savaş: metin taşları, canavarlar, vuruşlar
    this.combat.update(dt, this.player, pos, this.controller.yaw, this.controller.camDist);
    this.floatingText.update(dt);
    this._updateDeath(dt);

    // Yakındaki NPC'leri canlandır (uzaktakiler için işlem harcama)
    for (const n of this.npcs) {
      const d = n.root.position.distanceToSquared(pos);
      if (d < 90 * 90) n.update(dt, 0);
    }

    this.engine.follow(pos);
    this.engine.render();

    // Arayüz
    this.hud.setStats(this.stats);
    this.hud.drawMinimap(pos, this.controller.yaw, this.terrain);
    this.hud.setCoords(pos.x, pos.z, this._zoneLabel(pos));
    this.hud.tickFps();

    this.input.endFrame();
  };

  /**
   * Kuşanılan ekipmanı karaktere ve istatistiklere yansıtır.
   * Envanter her değiştiğinde çağrılır.
   */
  _ekipmanUygula() {
    if (!this.player) return;
    const t = this.inventory.toplam();
    // Taban değerler seviyeden gelir; ekipman üstüne eklenir
    this.stats.attack = this.stats.tabanSaldiri + t.saldiri;
    this.stats.defense = t.savunma;
    this.player.applyEquipmentVisuals?.(this.inventory.gorselEkipman());
  }

  /**
   * Kuşanılmış eşya çantaya sığmadığı için yere bırakıldı.
   * @param {object} item
   */
  _esyaYereAt(item) {
    if (!this.player || !this.combat) return;
    this.combat.dropItem?.(item, this.controller.pos);
    this.hud.toast(`${item.ad} yere bırakıldı`);
  }

  /** Yerden eşya toplandı. */
  _esyaTopla(item) {
    const i = this.inventory.ekle(item);
    // false: eşya alınamadı, yerde kalsın
    if (i < 0) { this.hud.toast('Çanta dolu — eşya alınamadı'); return false; }
    this.hud.toast(`${item.ad} alındı`);
    return true;
  }

  /** Canavar vuruşu oyuncuya isabet etti. */
  _takeDamage(amount, from) {
    if (!this.controller.alive) return;
    this.stats.hp = Math.max(0, this.stats.hp - amount);
    // Yazı oyuncunun başının üstünde belirsin
    this._dmgPos ||= new THREE.Vector3();
    this._dmgPos.set(
      this.controller.pos.x + (Math.random() - 0.5) * 0.5,
      this.controller.pos.y + 2.0,
      this.controller.pos.z);
    this.floatingText.damage(amount, this._dmgPos, { toPlayer: true });

    if (this.stats.hp <= 0) {
      this.controller.alive = false;
      this.controller.attacking = false;
      this.player.setState('die');
      this.hud.toast('Yenildin — köyde uyanıyorsun');
      this.deathTimer = 3.2;
    } else if (!this.controller.attacking && this.player.state !== 'hit') {
      this.player.setState('hit');
    }
    void from;
  }

  /** Ölümden sonra köyde yeniden doğuş. */
  _updateDeath(dt) {
    if (this.controller.alive || this.deathTimer <= 0) return;
    this.deathTimer -= dt;
    if (this.deathTimer > 0) return;

    const village = this.villages.get(this.kingdom.id);
    this.stats.hp = this.stats.hpMax;
    this.stats.mp = this.stats.mpMax;
    this.controller.alive = true;
    this.controller.teleport(village.spawn.x, village.spawn.z, village.spawnFacing);
    this.player.setState('idle', { force: true });
    this.hud.showZone(village.kingdom.village, 'Yeniden doğdun');
  }

  /** Oyuncunun bulunduğu bölgeyi belirler ve gerekirse bildirir. */
  _zoneLabel(pos) {
    for (const k of KINGDOMS) {
      const d = Math.hypot(pos.x - k.center.x, pos.z - k.center.z);
      if (d < 118) {
        this.hud.showZone(k.village, k.title);
        return k.village;
      }
    }
    this.hud._lastZone = null;
    return 'Yaban topraklar';
  }
}

/* Başlat */
window.addEventListener('error', (e) => {
  const el = document.getElementById('loading');
  if (el) el.innerHTML = `<span style="color:#e08a7a">Hata: ${e.message}</span>`;
});

// Hata ayıklama ve otomatik testler için oyun örneğini dışarı aç
window.game = new Game();
