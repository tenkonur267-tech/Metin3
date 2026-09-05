/**
 * PlayerController.js — Oyuncu hareketi, çarpışma ve kamera.
 *
 * Hareket kameraya göre: joystick ileri = kameranın baktığı yön. Bu, mobil
 * üçüncü şahıs oyunlarda beklenen davranış. Çarpışma çözümü basit ama
 * kararlı: her cisim için itme vektörü hesaplanıp konum düzeltiliyor.
 */
import * as THREE from 'three';

const UP = new THREE.Vector3(0, 1, 0);
const _v = new THREE.Vector3();
const _v2 = new THREE.Vector3();

export class PlayerController {
  constructor(warrior, terrain, camera, opts = {}) {
    this.warrior = warrior;
    this.terrain = terrain;
    this.camera = camera;

    this.pos = new THREE.Vector3(0, 0, 0);
    this.vel = new THREE.Vector3();
    this.yaw = 0;                 // karakterin baktığı yön
    this.targetYaw = 0;
    this.radius = 0.42;
    this.walkSpeed = 1.75;   // gerçek bir yürüyüş temposu
    this.runSpeed = 7.0;
    this.gravity = -22;
    this.jumpSpeed = 7.4;
    this.grounded = true;
    this.speed = 0;

    // Kamera yörüngesi
    this.camYaw = 0;
    this.camPitch = 0.32;
    this.camDist = 7.2;
    this.camDistTarget = 7.2;
    this.camMinDist = 2.6;
    this.camMaxDist = 16;
    this._camPos = new THREE.Vector3();
    this._camLook = new THREE.Vector3();
    this._camInit = false;

    this.world = opts.world || null;   // CollisionWorld (uzamsal ızgara)
    this.colliders = opts.colliders || [];
    this._near = [];
    // Hareketi kilitleyen, bir kez oynanıp biten eylemler
    this.ACTIONS = new Set(['attack', 'spin', 'tripleCut', 'dash']);
    this.attacking = false;
    this.comboStep = 0;
    this.comboTimer = 0;
    this.alive = true;

    // Beceriler: mana bedeli ve karakter durumu
    this.skills = {
      skill1: { state: 'tripleCut', mp: 25, name: 'Üç Yönlü Kesiş' },
      skill2: { state: 'spin', mp: 35, name: 'Kılıç Fırtınası' },
    };
    this.stats = opts.stats || null;   // { mp, mpMax } — beceriler bunu harcar
    this.onSkillFail = null;

    // Girdi tamponu: bir eylem oynarken basılan tuş kaybolmasın, eylem
    // biter bitmez oynasın. Aksiyon oyunlarında zincir kurmayı bu sağlar.
    this.buffer = null;
    this.bufferTime = 0;
    this.bufferWindow = 0.35;
  }

  setColliders(list) { this.colliders = list; }
  setWorld(world) { this.world = world; }

  /** Belirli konuma yerleştir ve kamerayı arkasına al. */
  teleport(x, z, facing = 0) {
    this.pos.set(x, this.terrain.heightAt(x, z), z);
    this.yaw = this.targetYaw = facing;
    this.camYaw = facing;
    this.vel.set(0, 0, 0);
    this._camInit = false;
  }

  /* ---------------- Çarpışma ---------------- */
  _resolveCollisions() {
    const r = this.radius;
    const list = this.world
      ? this.world.query(this.pos.x, this.pos.z, this._near)
      : this.colliders;
    for (const c of list) {
      if (c.kind === 'circle') {
        const dx = this.pos.x - c.x, dz = this.pos.z - c.z;
        const d = Math.hypot(dx, dz);
        const min = c.r + r;
        if (d < min && d > 1e-5) {
          const push = (min - d) / d;
          this.pos.x += dx * push;
          this.pos.z += dz * push;
        } else if (d <= 1e-5) {
          this.pos.x += min;
        }
      } else if (c.kind === 'box') {
        // Kutu yerel uzayına taşı
        const cos = Math.cos(-c.ry), sin = Math.sin(-c.ry);
        const dx = this.pos.x - c.x, dz = this.pos.z - c.z;
        const lx = dx * cos - dz * sin;
        const lz = dx * sin + dz * cos;
        const ex = c.hw + r, ez = c.hd + r;
        if (Math.abs(lx) < ex && Math.abs(lz) < ez) {
          const px = ex - Math.abs(lx);
          const pz = ez - Math.abs(lz);
          let nx = 0, nz = 0;
          if (px < pz) nx = Math.sign(lx || 1) * px;
          else nz = Math.sign(lz || 1) * pz;
          // Dünya uzayına geri döndür
          const c2 = Math.cos(c.ry), s2 = Math.sin(c.ry);
          this.pos.x += nx * c2 - nz * s2;
          this.pos.z += nx * s2 + nz * c2;
        }
      }
    }
    // Harita sınırı
    const lim = this.terrain.half - 6;
    this.pos.x = THREE.MathUtils.clamp(this.pos.x, -lim, lim);
    this.pos.z = THREE.MathUtils.clamp(this.pos.z, -lim, lim);
  }

  /* ---------------- Güncelleme ---------------- */
  update(dt, input) {
    const w = this.warrior;

    /* -- Kamera girdisi -- */
    this.camYaw -= input.look.x * 0.005;
    this.camPitch = THREE.MathUtils.clamp(
      this.camPitch + input.look.y * 0.004, -0.35, 1.15);
    this.camDistTarget = THREE.MathUtils.clamp(
      this.camDistTarget + input.zoomDelta * 0.6, this.camMinDist, this.camMaxDist);
    this.camDist += (this.camDistTarget - this.camDist) * Math.min(1, dt * 10);

    /* -- Saldırı ve beceriler -- */
    if (this.comboTimer > 0) this.comboTimer -= dt;

    // Basılan tuşu tampona al (eylem sürüyorsa bile)
    for (const key of ['attack', 'skill1', 'skill2']) {
      if (input.consumePressed(key)) {
        this.buffer = key;
        this.bufferTime = this.bufferWindow;
      }
    }
    if (this.bufferTime > 0) {
      this.bufferTime -= dt;
      if (this.bufferTime <= 0) this.buffer = null;
    }

    if (this.alive && !this.attacking && this.buffer) {
      const key = this.buffer;
      this.buffer = null;
      this.bufferTime = 0;
      if (key === 'attack') {
        this.attacking = true;
        // Zincir: art arda basınca üç farklı savurma sırayla oynar
        const variant = this.comboTimer > 0 ? (this.comboStep + 1) % 3 : 0;
        this.comboStep = variant;
        w.setState('attack', { variant });
      } else {
        const sk = this.skills[key];
        if (this.stats && this.stats.mp < sk.mp) {
          this.onSkillFail?.(sk);
        } else {
          if (this.stats) this.stats.mp -= sk.mp;
          this.attacking = true;
          this.comboTimer = 0;
          w.setState(sk.state);
        }
      }
    }

    /* -- Hareket girdisi -- */
    const m = input.sample();
    let moveX = 0, moveZ = 0;
    const canMove = this.alive && !this.attacking;
    if (canMove && m.mag > 0.08) {
      // Kameraya göre yön
      const f = _v.set(Math.sin(this.camYaw), 0, Math.cos(this.camYaw));
      const rgt = _v2.set(Math.cos(this.camYaw), 0, -Math.sin(this.camYaw));
      moveX = f.x * m.y + rgt.x * m.x;
      moveZ = f.z * m.y + rgt.z * m.x;
      const len = Math.hypot(moveX, moveZ);
      if (len > 1e-5) { moveX /= len; moveZ /= len; }
      this.targetYaw = Math.atan2(moveX, moveZ);
    }

    const wantRun = m.run || m.mag > 0.82;
    const targetSpeed = (canMove && m.mag > 0.08)
      ? (wantRun ? this.runSpeed : this.walkSpeed) * Math.min(1, m.mag / 0.82)
      : 0;

    // Yumuşak hızlanma
    this.speed += (targetSpeed - this.speed) * Math.min(1, dt * (targetSpeed > this.speed ? 9 : 14));
    if (this.speed < 0.05) this.speed = 0;

    // Dik yamaçları tırmanma
    if (this.speed > 0) {
      const nx = this.pos.x + moveX * this.speed * dt;
      const nz = this.pos.z + moveZ * this.speed * dt;
      const dh = this.terrain.heightAt(nx, nz) - this.terrain.heightAt(this.pos.x, this.pos.z);
      const step = Math.hypot(nx - this.pos.x, nz - this.pos.z);
      const slope = step > 1e-5 ? dh / step : 0;
      if (slope < 1.25) {           // ~51° üstü tırmanılamaz
        this.pos.x = nx;
        this.pos.z = nz;
      } else {
        this.speed *= 0.4;
      }
    }

    /* -- Zıplama ve yerçekimi -- */
    if (this.alive && this.grounded && input.consumePressed('jump') && !this.attacking) {
      this.vel.y = this.jumpSpeed;
      this.grounded = false;
      w.setState('jump');
    }
    this.vel.y += this.gravity * dt;
    this.pos.y += this.vel.y * dt;

    this._resolveCollisions();

    const groundY = this.terrain.heightAt(this.pos.x, this.pos.z);
    if (this.pos.y <= groundY) {
      this.pos.y = groundY;
      this.vel.y = 0;
      if (!this.grounded) {
        this.grounded = true;
        if (this.alive && !this.attacking) w.setState(this._locomotionState());
      }
    } else {
      this.grounded = false;
    }

    /* -- Animasyon durumu -- */
    if (this.alive && !this.attacking && this.grounded && w.state !== 'hit') {
      const want = this._locomotionState();
      if (w.state !== want) w.setState(want);
    }
    if (!this.grounded && w.state !== 'attack' && this.alive) {
      w.vy = this.vel.y;
      if (w.state !== 'jump') w.setState('jump');
    }

    /* -- Dönüş -- */
    let d = this.targetYaw - this.yaw;
    while (d > Math.PI) d -= Math.PI * 2;
    while (d < -Math.PI) d += Math.PI * 2;
    this.yaw += d * Math.min(1, dt * 12);

    /* -- Karakteri yerleştir -- */
    w.root.position.copy(this.pos);
    w.root.rotation.y = this.yaw;

    const ev = w.update(dt, this.speed);
    if (ev) {
      const ended = ev.slice(0, -4);          // 'attack-end' -> 'attack'
      if (this.ACTIONS.has(ended)) {
        this.attacking = false;
        // Zincir penceresi yalnızca normal savurmadan sonra açılır
        this.comboTimer = ended === 'attack' ? 0.55 : 0;
        w.setState(this._locomotionState());
      } else if (ended === 'hit' && this.alive) {
        w.setState(this._locomotionState());
      }
    }

    this._updateCamera(dt);
    return ev;
  }

  /** Hıza göre uygun hareket animasyonu. */
  _locomotionState() {
    return this.speed > 3.0 ? 'run' : this.speed > 0.2 ? 'walk' : 'idle';
  }

  _updateCamera(dt) {
    const headY = this.pos.y + 1.45;
    const lookX = this.pos.x, lookZ = this.pos.z;

    // İstenen kamera konumu
    const cp = Math.cos(this.camPitch), sp = Math.sin(this.camPitch);
    let cx = lookX - Math.sin(this.camYaw) * this.camDist * cp;
    let cz = lookZ - Math.cos(this.camYaw) * this.camDist * cp;
    let cy = headY + this.camDist * sp;

    // Kamera zemine gömülmesin
    const gy = this.terrain.heightAt(cx, cz);
    if (cy < gy + 1.2) cy = gy + 1.2;

    if (!this._camInit) {
      this._camPos.set(cx, cy, cz);
      this._camLook.set(lookX, headY, lookZ);
      this._camInit = true;
    } else {
      const k = Math.min(1, dt * 9);
      this._camPos.lerp(_v.set(cx, cy, cz), k);
      this._camLook.lerp(_v.set(lookX, headY, lookZ), Math.min(1, dt * 14));
    }
    this.camera.position.copy(this._camPos);
    this.camera.up.copy(UP);
    this.camera.lookAt(this._camLook);
  }
}
