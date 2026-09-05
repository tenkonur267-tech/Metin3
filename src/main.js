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
import { PlayerController } from './entities/PlayerController.js';
import { SwordTrail } from './entities/SwordTrail.js';
import { HUD } from './ui/HUD.js';
import { KingdomSelect } from './ui/KingdomSelect.js';
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
    };

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

    p(0.95, 'Son rötuşlar…');
    await nextFrame();
    // Oyuncu karakteri (krallık seçilince zırhı yeniden kurulacak)
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

    this.player = new Warrior(kingdom.armor, { scale: 1, weapon: 'twohand' });
    this.player.addTo(this.engine.scene);

    this.trail = new SwordTrail(16, kingdom.color).addTo(this.engine.scene);

    this.controller = new PlayerController(
      this.player, this.terrain, this.engine.camera,
      { world: this.collision, stats: this.stats });
    this.controller.onSkillFail = (sk) => this.hud.toast(`${sk.name}: yeterli mana yok`);
    this.controller.teleport(village.spawn.x, village.spawn.z, village.spawnFacing);

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

    // Mana yavaşça dolsun, saldırı mana yaksın
    this.stats.mp = Math.min(this.stats.mpMax, this.stats.mp + dt * 4.5);

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
    this.hud.tickFps(dt);

    this.input.endFrame();
  };

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
