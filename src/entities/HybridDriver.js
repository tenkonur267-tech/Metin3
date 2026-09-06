/**
 * HybridDriver.js — Yürüyüş mocap'ten, savaş prosedürelden.
 *
 * İndirilebilen hazır klip setlerinde yürüme/koşma/bekleme var ama saldırı,
 * darbe, ölüm gibi durumlar yok. Bu sürücü ikisini birleştiriyor: durum
 * hangisinde varsa oradan besleniyor. Aktarım her iki kaynakta da "kendi
 * dinlenme duruşuna göre fark" üzerinden yapıldığı için ikisi aynı hedefe
 * sorunsuz bağlanıyor ve kaynak değişiminde iki fark arasında kısa bir
 * geçiş yapılabiliyor — yoksa saldırıya geçerken karakter zıplıyor.
 */
import * as THREE from 'three';

const _a = new THREE.Quaternion();
const _b = new THREE.Quaternion();
const _v = new THREE.Vector3();

export class HybridDriver {
  /**
   * @param {ClipDriver} klip   yürüyüş kaynağı
   * @param {Warrior} proc      prosedürel savaşçı (savaş hareketleri)
   */
  constructor(klip, proc) {
    this.klip = klip;
    this.proc = proc;
    this.root = proc.root;              // kök ölçek/konum prosedürelden
    this.j = proc.j;                    // eklem adları aynı; yalnızca varlık kontrolü için
    this.durations = proc.durations;
    this.state = 'idle';
    this.stateTime = 0;
    this.aktif = klip.destekler('idle') ? klip : proc;
    this.onceki = null;
    this.gecis = 1;                     // 0..1, 1 = geçiş bitti
    this.gecisSure = 0.16;

    /*
     * Üst/alt gövde ayrımı.
     *
     * Hazır yürüyüş klipleri kolları serbest sallandırıyor; oyuncunun elinde
     * kılıç var ve o duruşu prosedürel poz kütüphanesi taşıyor. Klip bütün
     * gövdeye uygulandığında karakter yürürken kılıcı bırakmış gibi
     * duruyordu. Bu yüzden yalnızca alt gövde ve omurga klipten, kollar ve
     * baş prosedürelden besleniyor.
     */
    this.altGovde = new Set(['hips', 'spine',
      'thighL', 'shinL', 'footL', 'thighR', 'shinR', 'footR']);
  }

  _kaynak(name) { return this.klip.destekler(name) ? this.klip : this.proc; }

  setState(name, opts = {}) {
    const yeni = this._kaynak(name);
    if (yeni !== this.aktif) {
      this.onceki = this.aktif;
      this.gecis = 0;
    }
    this.aktif = yeni;
    this.state = name;
    this.stateTime = 0;
    // İki kaynak da aynı durumda kalsın: geçiş anında ikisi de hazır olsun
    this.klip.setState(name, opts);
    this.proc.setState(name, opts);
  }

  update(dt, speed = 0) {
    this.stateTime += dt;
    if (this.gecis < 1) this.gecis = Math.min(1, this.gecis + dt / this.gecisSure);
    this.klip.update(dt, speed);
    const ev = this.proc.update(dt, speed);
    this.state = this.proc.state;
    // Olayları (saldırı bitti vb.) yalnızca prosedürel taraf üretiyor
    return this.aktif === this.proc ? ev : null;
  }

  /** Bu eklemi hangi kaynak sürüyor? */
  _eklemKaynagi(joint) {
    if (this.aktif !== this.klip) return this.proc;
    return this.altGovde.has(joint) ? this.klip : this.proc;
  }

  jointDelta(joint, out = new THREE.Quaternion()) {
    this._eklemKaynagi(joint).jointDelta(joint, _b);
    if (this.gecis < 1 && this.onceki) {
      const eskiKaynak = this.onceki === this.klip && !this.altGovde.has(joint)
        ? this.proc : this.onceki;
      eskiKaynak.jointDelta(joint, _a);
      return out.copy(_a).slerp(_b, this.gecis);
    }
    return out.copy(_b);
  }

  hipsOffset(out = new THREE.Vector3()) {
    this.aktif.hipsOffset(out);
    if (this.gecis < 1 && this.onceki) {
      this.onceki.hipsOffset(_v);
      out.lerpVectors(_v, out, this.gecis);
    }
    return out;
  }

  get _curRootYaw() { return this.proc._curRootYaw || 0; }
  get animTime() { return this.proc.animTime; }
  set animTime(v) { this.proc.animTime = v; }
  get locoPhase() { return this.proc.locoPhase; }
  set locoPhase(v) { this.proc.locoPhase = v; }
  get vy() { return this.proc.vy; }
  set vy(v) { this.proc.vy = v; }
}
