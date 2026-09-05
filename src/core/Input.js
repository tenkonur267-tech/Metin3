/**
 * Input.js — Mobil öncelikli girdi.
 *
 * Sol yarı: dokunulan yerde beliren sanal joystick (hareket).
 * Sağ yarı: parmak sürükleyerek kamera döndürme, çift dokunuşla yakınlaştırma.
 * Ayrıca iki parmakla makas hareketi kamerayı yakınlaştırır/uzaklaştırır.
 * Masaüstünde WASD + fare ile aynı davranış.
 */
import * as THREE from 'three';

export class Input {
  /**
   * @param {HTMLElement} el  girdi yakalanacak katman (canvas üstündeki div)
   */
  constructor(el) {
    this.el = el;
    this.move = new THREE.Vector2();   // -1..1, y ileri
    this.moveMag = 0;                  // 0..1
    this.look = new THREE.Vector2();   // bu karedeki kamera delta'sı (px)
    this.zoomDelta = 0;
    this.running = false;              // joystick sonuna kadar itildiyse koşu
    this.buttons = { attack: false, jump: false, skill1: false, skill2: false };
    this.pressed = new Set();          // bu karede yeni basılanlar

    this._stick = { id: null, ox: 0, oy: 0, x: 0, y: 0, radius: 62 };
    this._look = { id: null, x: 0, y: 0 };
    this._pinch = { a: null, b: null, dist: 0 };
    this._keys = new Set();
    this._pointers = new Map();

    this._buildStickUI();
    this._bind();
  }

  /* ---------------- Sanal joystick görseli ---------------- */
  _buildStickUI() {
    const base = document.createElement('div');
    base.className = 'stick-base';
    const knob = document.createElement('div');
    knob.className = 'stick-knob';
    base.appendChild(knob);
    base.style.display = 'none';
    this.el.appendChild(base);
    this._stickEl = base;
    this._knobEl = knob;
  }

  _showStick(x, y) {
    const r = this.el.getBoundingClientRect();
    this._stickEl.style.left = (x - r.left) + 'px';
    this._stickEl.style.top = (y - r.top) + 'px';
    this._stickEl.style.display = 'block';
    this._knobEl.style.transform = 'translate(-50%, -50%)';
  }

  _moveKnob(dx, dy) {
    this._knobEl.style.transform = `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px))`;
  }

  _hideStick() { this._stickEl.style.display = 'none'; }

  /* ---------------- Olay bağlama ---------------- */
  _bind() {
    const el = this.el;
    el.style.touchAction = 'none';

    el.addEventListener('pointerdown', (e) => {
      el.setPointerCapture?.(e.pointerId);
      this._pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      const r = el.getBoundingClientRect();
      const leftHalf = (e.clientX - r.left) < r.width * 0.5;

      if (leftHalf && this._stick.id === null) {
        this._stick.id = e.pointerId;
        this._stick.ox = e.clientX;
        this._stick.oy = e.clientY;
        this._showStick(e.clientX, e.clientY);
      } else if (this._look.id === null) {
        this._look.id = e.pointerId;
        this._look.x = e.clientX;
        this._look.y = e.clientY;
      }
      this._updatePinch();
    });

    el.addEventListener('pointermove', (e) => {
      if (!this._pointers.has(e.pointerId)) return;
      this._pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });

      if (e.pointerId === this._stick.id) {
        let dx = e.clientX - this._stick.ox;
        let dy = e.clientY - this._stick.oy;
        const len = Math.hypot(dx, dy);
        const R = this._stick.radius;
        if (len > R) { dx = dx / len * R; dy = dy / len * R; }
        this._moveKnob(dx, dy);
        this.move.set(dx / R, -dy / R);
        this.moveMag = Math.min(1, len / R);
        this.running = this.moveMag > 0.82;
      } else if (e.pointerId === this._look.id) {
        this.look.x += e.clientX - this._look.x;
        this.look.y += e.clientY - this._look.y;
        this._look.x = e.clientX;
        this._look.y = e.clientY;
      }
      this._updatePinch();
    });

    const end = (e) => {
      this._pointers.delete(e.pointerId);
      if (e.pointerId === this._stick.id) {
        this._stick.id = null;
        this.move.set(0, 0);
        this.moveMag = 0;
        this.running = false;
        this._hideStick();
      }
      if (e.pointerId === this._look.id) this._look.id = null;
      this._pinch.a = this._pinch.b = null;
    };
    el.addEventListener('pointerup', end);
    el.addEventListener('pointercancel', end);
    el.addEventListener('lostpointercapture', end);

    el.addEventListener('wheel', (e) => {
      this.zoomDelta += e.deltaY * 0.01;
      e.preventDefault();
    }, { passive: false });

    el.addEventListener('contextmenu', (e) => e.preventDefault());

    /* ---------------- Klavye ---------------- */
    window.addEventListener('keydown', (e) => {
      if (e.repeat) return;
      this._keys.add(e.code);
      if (e.code === 'Space') { this.buttons.jump = true; this.pressed.add('jump'); e.preventDefault(); }
      if (e.code === 'KeyJ' || e.code === 'KeyE') { this.buttons.attack = true; this.pressed.add('attack'); }
      if (e.code === 'Digit1') this.pressed.add('skill1');
      if (e.code === 'Digit2') this.pressed.add('skill2');
    });
    window.addEventListener('keyup', (e) => {
      this._keys.delete(e.code);
      if (e.code === 'Space') this.buttons.jump = false;
      if (e.code === 'KeyJ' || e.code === 'KeyE') this.buttons.attack = false;
    });
  }

  _updatePinch() {
    const ids = [...this._pointers.keys()];
    if (ids.length >= 2) {
      const a = this._pointers.get(ids[0]);
      const b = this._pointers.get(ids[1]);
      const d = Math.hypot(a.x - b.x, a.y - b.y);
      if (this._pinch.dist) this.zoomDelta += (this._pinch.dist - d) * 0.03;
      this._pinch.dist = d;
    } else {
      this._pinch.dist = 0;
    }
  }

  /** HUD düğmelerini girdiye bağlar. */
  bindButton(el, name, { repeat = false } = {}) {
    const down = (e) => {
      e.preventDefault();
      e.stopPropagation();
      this.buttons[name] = true;
      this.pressed.add(name);
      el.classList.add('held');
      if (repeat) {
        clearInterval(el._rep);
        el._rep = setInterval(() => this.pressed.add(name), 220);
      }
    };
    const up = (e) => {
      e.preventDefault();
      e.stopPropagation();
      this.buttons[name] = false;
      el.classList.remove('held');
      clearInterval(el._rep);
    };
    el.addEventListener('pointerdown', down);
    el.addEventListener('pointerup', up);
    el.addEventListener('pointercancel', up);
    el.addEventListener('pointerleave', up);
  }

  /** Klavye yönünü joystick vektörüyle birleştirir. */
  _keyboardMove() {
    let x = 0, y = 0;
    if (this._keys.has('KeyW') || this._keys.has('ArrowUp')) y += 1;
    if (this._keys.has('KeyS') || this._keys.has('ArrowDown')) y -= 1;
    if (this._keys.has('KeyA') || this._keys.has('ArrowLeft')) x -= 1;
    if (this._keys.has('KeyD') || this._keys.has('ArrowRight')) x += 1;
    return { x, y, run: this._keys.has('ShiftLeft') || this._keys.has('ShiftRight') };
  }

  /** Her karenin başında çağrılır; birleşik hareket vektörünü döndürür. */
  sample() {
    const k = this._keyboardMove();
    let mx = this.move.x, my = this.move.y, mag = this.moveMag, run = this.running;
    if (k.x || k.y) {
      const len = Math.hypot(k.x, k.y);
      mx = k.x / len; my = k.y / len;
      mag = 1;
      run = k.run;
    }
    return { x: mx, y: my, mag, run };
  }

  /** Kare sonunda tüketilen deltaları sıfırlar. */
  endFrame() {
    this.look.set(0, 0);
    this.zoomDelta = 0;
    this.pressed.clear();
  }

  consumePressed(name) {
    if (this.pressed.has(name)) { this.pressed.delete(name); return true; }
    return false;
  }
}
