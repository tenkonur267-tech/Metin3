/**
 * ClipDriver.js — Hazır animasyon kliplerini çalıştıran görünmez sürücü.
 *
 * Karakter modeli (çıplak insan taban gövde) kendi animasyonunu taşımıyor.
 * Hareket şimdiye kadar prosedürel savaşçıdan aktarılıyordu; sonuç yürüyüşte
 * yeterince iyi değildi. Burada aynı aktarım düzeni korunuyor ama sürücü
 * olarak gerçek hareket yakalama klipleri taşıyan bir iskelet kullanılıyor:
 * kliplerin ait olduğu rig (Mixamo) ile hedef rig farklı olsa bile, aktarım
 * her iki iskeletin **kendi dinlenme duruşuna göre farkı** üzerinden yapıldığı
 * için isim ve eksen düzeni uyuşmak zorunda değil.
 *
 * Sürücü sahneye eklenmiyor; yalnızca kemik hiyerarşisi ve mikser için var.
 */
import * as THREE from 'three';
import { JOINTS } from './Warrior.js';

const _q = new THREE.Quaternion();

/** Mixamo rig adları -> oyun eklemleri. */
export const MIXAMO_MAP = {
  hips: 'mixamorig:Hips',
  spine: 'mixamorig:Spine',
  chest: 'mixamorig:Spine2',
  neck: 'mixamorig:Neck',
  head: 'mixamorig:Head',
  shoulderL: 'mixamorig:LeftShoulder',
  armL: 'mixamorig:LeftArm',
  foreArmL: 'mixamorig:LeftForeArm',
  handL: 'mixamorig:LeftHand',
  shoulderR: 'mixamorig:RightShoulder',
  armR: 'mixamorig:RightArm',
  foreArmR: 'mixamorig:RightForeArm',
  handR: 'mixamorig:RightHand',
  thighL: 'mixamorig:LeftUpLeg',
  shinL: 'mixamorig:LeftLeg',
  footL: 'mixamorig:LeftFoot',
  thighR: 'mixamorig:RightUpLeg',
  shinR: 'mixamorig:RightLeg',
  footR: 'mixamorig:RightFoot',
};

const norm = (s) => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');

/** Durum -> klip adında aranacak parçalar. */
const KLIP_ADLARI = {
  idle: ['idle', 'stand'],
  walk: ['walk'],
  run: ['run', 'sprint', 'jog'],
};

export class ClipDriver {
  /**
   * @param {object} gltf  animasyon kaynağı (mesh gerekmez, iskelet yeter)
   * @param {object} [cfg] { boneMap, walkSpeed, runSpeed }
   */
  constructor(gltf, cfg = {}) {
    this.root = gltf.scene;
    this.root.updateMatrixWorld(true);

    const map = { ...MIXAMO_MAP, ...(cfg.boneMap || {}) };
    const kemikler = new Map();
    this.root.traverse((o) => { if (o.name) kemikler.set(norm(o.name), o); });
    this.j = {};
    const eksik = [];
    for (const jn of JOINTS) {
      const b = kemikler.get(norm(map[jn] || ''));
      if (b) this.j[jn] = b; else eksik.push(jn);
    }
    if (eksik.length) console.warn('[mocap] eşleşmeyen eklem:', eksik.join(', '));

    /*
     * Dinlenme duruşu her şeyden önce ölçülüyor: klip çalmaya başladıktan
     * sonra ölçülürse fark hesabı kayar ve karakter sürekli eğik durur.
     */
    this._restW = {};
    for (const jn of JOINTS) {
      if (this.j[jn]) this._restW[jn] = this.j[jn].getWorldQuaternion(new THREE.Quaternion());
    }
    this._restHipsY = this.j.hips ? this.j.hips.position.y : 1;
    this._restHipsZ = this.j.hips ? this.j.hips.position.z : 0;
    // Kalça yüksekliği rigden rige değişiyor; sıçrama miktarı oranla taşınıyor
    this._hipOlcek = this._restHipsY > 1e-4 ? 0.98 / this._restHipsY : 1;

    this.mixer = new THREE.AnimationMixer(this.root);
    this.actions = {};
    for (const [durum, parcalar] of Object.entries(KLIP_ADLARI)) {
      const c = gltf.animations.find((a) => parcalar.some((p) => norm(a.name).includes(p)));
      if (c) this.actions[durum] = this.mixer.clipAction(c);
    }
    /*
     * Klibin yer hızı ölçülüyor, tahmin edilmiyor.
     *
     * Hazır yürüyüş klipleri yerinde sayar (kök ötelemesi yok): karakter
     * ilerlemez, ayaklar gövdeye göre geri süpürür. Basılı ayağın gövdeye
     * göre geri gittiği mesafe, o çevrimde kat edilmesi gereken yoldur.
     * Oynatma hızı buna göre ayarlanmazsa ayaklar zeminde kayıyor — ölçüm
     * yerine sabit bir değer kullanmak yürüyüşü bozan şeydi.
     */
    this.klipHizi = {};
    for (const [durum, a] of Object.entries(this.actions)) {
      if (durum === 'idle') continue;
      const olculen = this._olcKlipHizi(a);
      this.klipHizi[durum] = olculen
        || (durum === 'run' ? (cfg.runSpeed ?? 3.9) : (cfg.walkSpeed ?? 1.45));
    }
    if (cfg.walkSpeed) this.klipHizi.walk = cfg.walkSpeed;
    if (cfg.runSpeed) this.klipHizi.run = cfg.runSpeed;
    console.info('[mocap] ölçülen klip yer hızı:',
      Object.entries(this.klipHizi).map(([k, v]) => `${k}=${v.toFixed(2)} m/s`).join(' '));

    this.state = 'idle';
    this.stateTime = 0;
    this.animTime = 0;
    this.durations = {};
    this._curRootYaw = 0;
    this.mevcut = null;
    this.setState('idle', { force: true });
  }

  /**
   * Klibin yer hızını ölçer (m/s).
   *
   * Klip boyunca örnekleniyor; her karede yere en yakın ayak "basılı" kabul
   * edilip gövdeye göre geriye gidişi toplanıyor. Toplam, bir çevrimde kat
   * edilen yol; süreye bölününce klibin tasarlandığı hız çıkıyor.
   *
   * @param {THREE.AnimationAction} action
   * @returns {number} 0 ölçülemediyse
   */
  _olcKlipHizi(action) {
    const clip = action.getClip();
    const N = 48;
    if (!this.j.footL || !this.j.footR || !this.j.hips || clip.duration <= 0) return 0;

    const eskiAgirlik = {};
    for (const [k, a] of Object.entries(this.actions)) {
      eskiAgirlik[k] = a.getEffectiveWeight();
      a.stop();
    }
    action.reset().play();
    action.paused = true;

    const orn = [];
    const kalca = new THREE.Vector3();
    const p = new THREE.Vector3();
    for (let i = 0; i <= N; i++) {
      action.time = (i / N) * clip.duration;
      this.mixer.update(0);
      this.root.updateMatrixWorld(true);
      this.j.hips.getWorldPosition(kalca);
      const kayit = { t: action.time };
      for (const s of ['L', 'R']) {
        this.j['foot' + s].getWorldPosition(p);
        kayit[s] = { x: p.x - kalca.x, y: p.y, z: p.z - kalca.z };
      }
      orn.push(kayit);
    }

    action.paused = false;
    action.stop();
    for (const [k, a] of Object.entries(this.actions)) {
      if (eskiAgirlik[k] > 0) { a.play(); a.setEffectiveWeight(eskiAgirlik[k]); }
    }

    // İlerleme ekseni: ayakların gövdeye göre en çok gezindiği yatay eksen
    const yay = (eks) => {
      let mn = Infinity, mx = -Infinity;
      for (const o of orn) for (const s of ['L', 'R']) {
        mn = Math.min(mn, o[s][eks]); mx = Math.max(mx, o[s][eks]);
      }
      return mx - mn;
    };
    const eksen = yay('z') >= yay('x') ? 'z' : 'x';

    // Basılı eşiği: örneklerdeki en alçak ayak + 3 cm
    let taban = Infinity;
    for (const o of orn) taban = Math.min(taban, o.L.y, o.R.y);
    const esik = taban + 0.03;

    let yol = 0;
    for (let i = 1; i < orn.length; i++) {
      for (const s of ['L', 'R']) {
        const a = orn[i - 1][s], b = orn[i][s];
        if (a.y > esik || b.y > esik) continue;
        yol += Math.abs(b[eksen] - a[eksen]);
      }
    }
    return yol > 1e-3 ? yol / clip.duration : 0;
  }

  /** Bu sürücünün oynatabildiği durumlar. */
  destekler(name) { return !!this.actions[name]; }

  setState(name, opts = {}) {
    const a = this.actions[name];
    if (!a) return;
    if (this.mevcut === a && !opts.force) { this.state = name; return; }
    a.reset().setEffectiveWeight(1).play();
    if (this.mevcut && this.mevcut !== a) this.mevcut.crossFadeTo(a, opts.fade ?? 0.18, false);
    this.mevcut = a;
    this.state = name;
    this.stateTime = 0;
  }

  /**
   * @param {number} dt
   * @param {number} speed  oyuncunun yatay hızı (klip hızı buna uydurulur)
   */
  update(dt, speed = 0) {
    this.stateTime += dt;
    this.animTime += dt;
    /*
     * Klip belirli bir hız için üretilmiş; oyunun hızı farklı olduğunda ayak
     * zeminde kayar. Oynatma hızı gerçek hıza oranlanıyor.
     */
    const tasarim = this.klipHizi[this.state];
    if (this.mevcut) {
      this.mevcut.timeScale = tasarim
        ? THREE.MathUtils.clamp(speed / tasarim, 0.25, 3.2)
        : 1;
    }
    this.mixer.update(dt);
    this.root.updateMatrixWorld(true);
    return null;
  }

  /** Kemiğin dinlenme duruşuna göre dünya farkı. */
  jointDelta(joint, out = new THREE.Quaternion()) {
    const b = this.j[joint];
    if (!b) return out.identity();
    b.getWorldQuaternion(out);
    const r = this._restW[joint];
    if (r) out.multiply(_q.copy(r).invert());
    return out;
  }

  /** Kalçanın dinlenme konumuna göre kayması (oyun birimine ölçekli). */
  hipsOffset(out = new THREE.Vector3()) {
    const h = this.j.hips;
    if (!h) return out.set(0, 0, 0);
    return out.set(0, (h.position.y - this._restHipsY) * this._hipOlcek,
      (h.position.z - this._restHipsZ) * this._hipOlcek);
  }
}
