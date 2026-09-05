/**
 * RiggedCharacter.js — Dışarıdan yüklenen çıplak taban gövdeyi kendi
 * animasyonlarımızla süren karakter.
 *
 * Amaç, üstüne zırh giydirilebilen modüler bir karakter: model yalnızca
 * deri ve iskelet getiriyor, hareket ve ekipman koddan geliyor.
 *
 * Aktarım (retargeting)
 * --------------------
 * İki iskeletin dinlenme pozları farklı: prosedürel savaşçının kolları
 * aşağıda, dışarıdan gelen rig ise T-pozunda. Ayrıca kemik eksen düzenleri
 * de aynı değil. Bu yüzden pozları doğrudan açı kopyalayarak aktarmak
 * karakteri katlıyor.
 *
 * Bunun yerine dünya uzayında delta aktarımı yapılıyor: prosedürel savaşçı
 * görünmez bir "sürücü" iskelet olarak çalıştırılıyor, her kemik için iki
 * iskeletin dinlenme yönelimleri arasındaki fark bir kez ölçülüyor
 *
 *     offset = sürücüDinlenme⁻¹ · hedefDinlenme
 *
 * ve her karede sürücünün ulaştığı yönelim bu farkla hedefe taşınıyor.
 * Yaklaşım iskelet adlandırmasından ve eksen düzeninden bağımsız çalışır.
 */
import * as THREE from 'three';
import { GLTFLoader } from '../../vendor/three/jsm/loaders/GLTFLoader.js';
import { Warrior, JOINTS } from './Warrior.js';

/** Poz eklemi -> kemik adı. Ad temizliği (nokta vb.) sonradan uygulanır. */
const DEFAULT_BONE_MAP = {
  hips: 'spine',
  spine: 'spine001',
  chest: 'spine002',
  neck: 'spine004',
  head: 'spine005',
  shoulderL: 'shoulderL', armL: 'upper_armL', foreArmL: 'forearmL', handL: 'handL',
  shoulderR: 'shoulderR', armR: 'upper_armR', foreArmR: 'forearmR', handR: 'handR',
  thighL: 'thighL', shinL: 'shinL', footL: 'footL',
  thighR: 'thighR', shinR: 'shinR', footR: 'footR',
};

/** Kemik adlarını karşılaştırmaya uygun hale getirir. */
function norm(n) {
  return String(n).toLowerCase().replace(/[^a-z0-9]/g, '');
}

const _q = new THREE.Quaternion();
const _e = new THREE.Euler();
const _v = new THREE.Vector3();
const _qa = new THREE.Quaternion();
const _qb = new THREE.Quaternion();
const _qc = new THREE.Quaternion();

/** Prosedürel savaşçının dinlenme kalça yüksekliği. */
const LEG_REST_HIP_Y = 0.98;

export class RiggedCharacter {
  constructor(scene, cfg, gltf) {
    this.cfg = cfg;
    this.root = new THREE.Group();
    this.root.name = 'rigged-character';
    this.root.add(scene);
    this.model = scene;
    this.gltf = gltf;

    this._collectBones();
    this._mapJoints();
    this._applySkin();
    this._fitScale();
    this._bindDriver();

    /* -- Durum: sürücüden yansıtılır -- */
    this.state = this.driver.state;
    this.stateTime = 0;
    this.durations = this.driver.durations;

    this.equipment = new Map();
    this.height = cfg.height ?? 1.95;
  }

  /* ---------------- Kurulum ---------------- */

  _collectBones() {
    this.bones = new Map();
    this.model.traverse((o) => {
      if (o.isBone) this.bones.set(norm(o.name), o);
    });
    if (!this.bones.size) throw new Error('Model iskelet içermiyor');
  }

  _mapJoints() {
    const map = { ...DEFAULT_BONE_MAP, ...(this.cfg.boneMap || {}) };
    this.j = {};
    this.rest = {};
    const missing = [];
    for (const joint of JOINTS) {
      const wanted = map[joint];
      const bone = wanted ? this.bones.get(norm(wanted)) : null;
      if (!bone) { missing.push(joint); continue; }
      this.j[joint] = bone;
      this.rest[joint] = bone.quaternion.clone();
    }
    if (missing.length) {
      console.warn('[rig] eşleşmeyen eklemler:', missing.join(', '));
    }
    this.mappedJoints = JOINTS.filter((j) => this.j[j]);
  }

  /**
   * Görünmez sürücü iskeleti kurar ve dinlenme farklarını ölçer.
   *
   * Sürücü sahneye eklenmiyor; yalnızca eklem hiyerarşisi için var. Poz
   * kütüphanesi ve yürüyüş IK'sı üzerinde olduğu gibi çalışıyor, biz de
   * ulaştığı yönelimleri hedef kemiklere taşıyoruz.
   */
  _bindDriver() {
    this.driver = new Warrior(
      this.cfg.armor || { base: '#7a2230', trim: '#d9b45a', cloth: 0x8e1f2a },
      { weapon: this.cfg.twoHanded === false ? 'sword' : 'twohand' });
    this.driver.root.updateMatrixWorld(true);
    this.model.updateMatrixWorld(true);

    const modelInv = this.model.getWorldQuaternion(new THREE.Quaternion()).invert();
    this.offset = {};
    for (const joint of this.mappedJoints) {
      const dq = this.driver.j[joint].getWorldQuaternion(new THREE.Quaternion());
      const tq = this.j[joint].getWorldQuaternion(new THREE.Quaternion());
      tq.premultiply(modelInv);                       // modelin kökine göre
      this.offset[joint] = dq.invert().multiply(tq);  // sürücü⁻¹ · hedef
    }
    this.restHipY = LEG_REST_HIP_Y;
  }

  /** Taban gövde dokusuz gelir; ten materyali burada verilir. */
  _applySkin() {
    const skin = this.cfg.skin || {};
    const mat = new THREE.MeshLambertMaterial({
      color: skin.color ?? 0xd8a97e,
    });
    this.skinMaterial = mat;
    this.model.traverse((o) => {
      if (o.isSkinnedMesh || o.isMesh) {
        if (!o.material || !o.material.map) o.material = mat;
        o.castShadow = true;
        o.receiveShadow = true;
        o.frustumCulled = false;   // iskeletle deforme olurken kutusu şaşabiliyor
      }
    });
  }

  /** Modeli hedef boya ölçekler ve ayaklarını y=0'a hizalar. */
  _fitScale() {
    const box = new THREE.Box3().setFromObject(this.model);
    const h = box.max.y - box.min.y;
    const target = this.cfg.height ?? 1.95;
    const s = this.cfg.scale ?? (h > 1e-4 ? target / h : 1);
    this.model.scale.setScalar(s);
    const box2 = new THREE.Box3().setFromObject(this.model);
    this.model.position.y -= box2.min.y;
    if (this.cfg.yOffset) this.model.position.y += this.cfg.yOffset;
    if (this.cfg.rotationY) this.model.rotation.y = this.cfg.rotationY;
    this.modelScale = s;
    this.restHipY *= s;
  }

  /* ---------------- Ekipman ---------------- */

  /**
   * Bir zırh parçasını kemiğe takar.
   * @param {string} slot  'chest' | 'head' | 'shoulderL' ...
   * @param {THREE.Object3D} piece
   * @param {string} joint eklem adı (varsayılan: slot ile aynı)
   */
  equip(slot, piece, joint = slot) {
    this.unequip(slot);
    const bone = this.j[joint];
    if (!bone) { console.warn('[rig] ekipman için kemik yok:', joint); return null; }
    bone.add(piece);
    this.equipment.set(slot, { piece, bone });
    return piece;
  }

  unequip(slot) {
    const cur = this.equipment.get(slot);
    if (!cur) return;
    cur.bone.remove(cur.piece);
    this.equipment.delete(slot);
  }

  /* ---------------- Durum makinesi ---------------- */
  /* Bütün animasyon mantığı sürücüde; burası yalnızca yönlendirir. */

  setState(name, opts = {}) {
    this.driver.setState(name, opts);
    this.state = this.driver.state;
    this.stateTime = 0;
  }

  get vy() { return this.driver.vy; }
  set vy(v) { this.driver.vy = v; }

  get animTime() { return this.driver.animTime; }
  set animTime(v) { this.driver.animTime = v; }

  get locoPhase() { return this.driver.locoPhase; }
  set locoPhase(v) { this.driver.locoPhase = v; }

  update(dt, speed = 0) {
    const ev = this.driver.update(dt, speed);
    this.state = this.driver.state;
    this.stateTime = this.driver.stateTime;

    this.driver.root.updateMatrixWorld(true);
    this.model.updateMatrixWorld(true);
    const modelInv = this.model.getWorldQuaternion(_qa).invert();

    /*
     * Sürücünün ulaştığı yönelimi hedef kemiğe taşı.
     *
     * Eklemler ebeveynden çocuğa doğru işleniyor ve her kemiğin ebeveyninin
     * dünya matrisi okunmadan hemen önce tazeleniyor: aksi halde ebeveynin
     * bu karede yeni atanan dönüşü henüz matrise yansımadığı için çocuklar
     * bir kare eski yönelime göre hesaplanıyor ve zincir boyunca sapıyor.
     */
    for (const joint of this.mappedJoints) {
      const bone = this.j[joint];
      const want = this.driver.j[joint].getWorldQuaternion(_qb).multiply(this.offset[joint]);
      bone.parent.updateWorldMatrix(true, false);
      const parentWorld = bone.parent.getWorldQuaternion(_qc);
      parentWorld.premultiply(modelInv).invert();
      bone.quaternion.copy(parentWorld.multiply(want));
    }

    // Kök ötelemesi: sürücünün kalça yüksekliğini modele ölçekleyerek aktar
    const s = this.modelScale;
    const hipDelta = (this.driver.j.hips.position.y - LEG_REST_HIP_Y) * s;
    this.model.position.y = (this._baseY ??= this.model.position.y) + hipDelta;
    this.model.position.z = this.driver.j.hips.position.z * s;
    this.model.rotation.y = (this.cfg.rotationY || 0) + (this.driver._curRootYaw || 0);

    return ev;
  }

  /* ---------------- Savaş arayüzü ---------------- */

  get isSwinging() {
    return !!this.weaponTip &&
      (this.state === 'attack' || this.state === 'spin' || this.state === 'tripleCut');
  }

  get inDamageWindow() {
    const s = this.state;
    if (s === 'attack') {
      const u = this.stateTime / this.durations.attack;
      return u > 0.28 && u < 0.54;
    }
    if (s === 'spin') {
      const u = this.stateTime / this.durations.spin;
      return u > 0.12 && u < 0.86;
    }
    if (s === 'tripleCut') {
      const u = (this.stateTime / this.durations.tripleCut * 3) % 1;
      return u > 0.30 && u < 0.72;
    }
    return false;
  }

  getWeaponTipWorld(out = new THREE.Vector3()) {
    return this.weaponTip ? this.weaponTip.getWorldPosition(out) : out.copy(this.root.position);
  }

  getWeaponBaseWorld(out = new THREE.Vector3()) {
    return this.weaponBase ? this.weaponBase.getWorldPosition(out) : out.copy(this.root.position);
  }

  addTo(parent) { parent.add(this.root); return this; }

  /* ---------------- Yükleme ---------------- */

  static async load(cfg) {
    const gltf = await new GLTFLoader().loadAsync(cfg.file);
    return new RiggedCharacter(gltf.scene, cfg, gltf);
  }
}
