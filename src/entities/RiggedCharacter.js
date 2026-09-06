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
import { loadGltf } from './loadGltf.js';
import { Warrior, JOINTS } from './Warrior.js';
import { buildArmorSet, buildVisual, makeContext, buildFace, ARMOR_SLOTS, GRUP_SLOTLARI }
  from './ArmorSet.js';

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

    // Ekipman haritası kurulum adımlarından önce hazır olmalı: yüz de
    // equip() üzerinden takılıyor.
    this.equipment = new Map();

    this._collectBones();
    this._mapJoints();
    this._applySkin();
    this._fitScale();
    this._measureFrame();
    this._bindDriver();
    this._measure();
    this._addFace();

    /* -- Durum: sürücüden yansıtılır -- */
    this.state = this.driver.state;
    this.stateTime = 0;
    this.durations = this.driver.durations;
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
   * Modelin kendi eksen tabanını ölçer ve modeli motorun düzenine çevirir.
   *
   * İki ayrı iş var ve ikisi de gerekli:
   *
   * 1. **Taban.** Ekipman karakter uzayında yazılıyor (X sağ, Y yukarı,
   *    Z ileri) ama riglerin dinlenme ekseni dışa aktarıma göre değişiyor;
   *    bu taban gövdede modelin önü +X, sağı -Z çıkıyor. Ölçülen taban,
   *    ekipman takılırken karakter uzayından model uzayına çeviriyor.
   *
   * 2. **Model dönüşü.** Motorun düzeninde bakış yönü `(sin yaw, cos yaw)`,
   *    yani kök dönüşü modelin +Z'sini öne getirir. Önü +X olan bir model
   *    bu düzende yan yürüyor: yüzü yürüdüğü yöne 90° dik duruyor.
   *    Ölçülen ileri yön +Z'ye getirilerek düzeltiliyor.
   *
   * Kemikler modele göre yönlendirildiği için (update() istenen yönelimi
   * model uzayında kuruyor) modeli döndürmek gövdeyi, zırhı ve yüzü
   * birlikte çeviriyor; aralarındaki hizayı bozmuyor.
   */
  _measureFrame() {
    this.model.updateMatrixWorld(true);
    const mq = this.model.getWorldQuaternion(new THREE.Quaternion()).invert();
    const mp = this.model.getWorldPosition(new THREE.Vector3());
    const local = (o) => o.getWorldPosition(new THREE.Vector3())
      .sub(mp).applyQuaternion(mq);

    const up = new THREE.Vector3(0, 1, 0);
    let fwd = null;

    // İleri yön: ayak parmağı bileğin önündedir — en güvenilir gösterge
    const toe = this.bones.get('toel') || this.bones.get('toe_l');
    if (toe && this.j.footL) {
      const v = local(toe).sub(local(this.j.footL));
      v.y = 0;
      if (v.lengthSq() > 1e-8) fwd = v.normalize();
    }
    // Yedek: omuz ekseninden dik yön
    if (!fwd && this.j.armL && this.j.armR) {
      const r = local(this.j.armR).sub(local(this.j.armL));
      r.y = 0;
      if (r.lengthSq() > 1e-8) fwd = new THREE.Vector3().crossVectors(r.normalize(), up).normalize();
    }
    if (!fwd) fwd = new THREE.Vector3(0, 0, 1);

    /*
     * Sağ ekseni omuzlardan ölçmeyi denemiştim, ama bu rigde omuz kemiği
     * adları geometriye göre aynalı: ölçülen taban sol el düzeninde çıkıyor
     * (determinant -1) ve böyle bir matristen quaternion çıkarmak bozuk
     * dönüş üretiyor. Sağ ekseni ileri ve yukarıdan türetmek tabanın daima
     * sağ el düzeninde olmasını garantiliyor.
     */
    const right = new THREE.Vector3().crossVectors(up, fwd).normalize();

    const m = new THREE.Matrix4().makeBasis(right, up, fwd);
    this.frameQ = new THREE.Quaternion().setFromRotationMatrix(m);
    this.frameAxes = { right, up: up.clone(), fwd };

    // Ölçülen ileri yönü motorun ileri yönüne (+Z) çevir
    this.yawFix = -Math.atan2(fwd.x, fwd.z);
    this.rotationY = (this.cfg.rotationY || 0) + this.yawFix;
    this.model.rotation.y = this.rotationY;
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
    const modelPos = this.model.getWorldPosition(new THREE.Vector3());
    this.offset = {};
    this.restQ = {};    // kemiğin dinlenme yönelimi (model köküne göre)
    this.restP = {};    // kemiğin dinlenme konumu (model köküne göre)
    this.restPC = {};   // aynı konum, karakter uzayında (X sağ, Y yukarı, Z ileri)
    const frameInv = (this.frameQ || new THREE.Quaternion()).clone().invert();
    for (const joint of this.mappedJoints) {
      const dq = this.driver.j[joint].getWorldQuaternion(new THREE.Quaternion());
      const tq = this.j[joint].getWorldQuaternion(new THREE.Quaternion());
      tq.premultiply(modelInv);                       // modelin kökine göre
      this.restQ[joint] = tq.clone();
      this.restP[joint] = this.j[joint].getWorldPosition(new THREE.Vector3())
        .sub(modelPos).applyQuaternion(modelInv)
        .divideScalar(this.modelScale || 1);
      /*
       * Aynı konum karakter uzayında da tutuluyor: zırh parçaları orada
       * yazıldığı için "kemik gövde ekseninden ne kadar önde" gibi soruları
       * ancak bu uzayda doğru yanıtlanıyor. Model uzayında ileri ekseni +X
       * olabiliyor ve z'ye bakmak yanlış yöne kaydırıyor.
       */
      this.restPC[joint] = this.restP[joint].clone()
        .applyQuaternion(frameInv);
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

  /**
   * Zırh parçalarının ölçüleceği kemik boyutları.
   *
   * Zırh dünya metresiyle değil kemik uzunluklarıyla ölçekleniyor; böylece
   * farklı boy ve orandaki riglere aynı set oturuyor.
   */
  _measure() {
    const len = (joint) => {
      const b = this.j[joint];
      const kid = b && b.children.find((c) => c.isBone);
      return kid ? kid.position.length() : 0;
    };
    const gap = (a, bJoint) => {
      const ba = this.j[a], bb = this.j[bJoint];
      if (!ba || !bb) return 0;
      return ba.getWorldPosition(_v).distanceTo(bb.getWorldPosition(new THREE.Vector3()))
        / (this.modelScale || 1);
    };
    this.model.updateMatrixWorld(true);
    const inv = 1 / (this.modelScale || 1);

    // Gövde boyu: tek bir omur değil, kalçadan boyuna kadar olan mesafe.
    // Göğüslüğü tek omurun boyuna göre ölçeklemek onu gövdenin ortasında
    // küçük bir bant halinde bırakıyordu.
    const torso = (this.j.hips && this.j.neck)
      ? this.j.hips.getWorldPosition(_v)
        .distanceTo(this.j.neck.getWorldPosition(new THREE.Vector3())) * inv
      : 0.70;

    // Kafa yarıçapı: kafa kemiği ile mesh'in tepesi arasındaki mesafe.
    const bbox = new THREE.Box3().setFromObject(this.model);
    const headBoneY = this.j.head
      ? this.j.head.getWorldPosition(_v).y
      : bbox.max.y - 0.2;
    // Kafa kemiği boynun tepesinde duruyor; kafa hacmi onun üstünde kalıyor
    const headHeight = Math.max((bbox.max.y - headBoneY) * inv, 0.16);
    const head = headHeight * 0.46;

    /*
     * Ayak ölçüleri çizme için: bilek yüksekliği ve bilekten parmağa mesafe.
     * Bunları tahmin etmek çizmeyi ayağın üstünde havada bırakıyordu.
     */
    const footBone = this.j.footL;
    const toeBone = this.bones.get('toel') || this.bones.get('toe_l');
    let footAnkle = 0.09, footFwd = 0.13, forward = 1;
    if (footBone) {
      // Bilek yüksekliği modelin tabanına göre; restP modelin orijinine göre
      // olduğu için doğrudan kullanmak 2 cm gibi anlamsız bir değer veriyordu.
      /*
       * bbox dünya uzayında; restP ise modelin kökine göre ve ölçekten
       * arındırılmış. İkisini doğrudan çıkarmak (eski kod) modelin dünya
       * yüksekliğini de işin içine katıyor ve alt sınıra, 3 cm'e yapışıyordu:
       * çizme ayağı sarmak yerine üstünde ince bir tepsi gibi duruyordu.
       */
      const modelY = this.model.getWorldPosition(new THREE.Vector3()).y;
      const tabanLocal = (bbox.min.y - modelY) * inv;
      const fp = this.restP.footL;
      if (fp) footAnkle = Math.max(fp.y - tabanLocal, 0.03);
      if (toeBone) {
        const modelInv2 = this.model.getWorldQuaternion(new THREE.Quaternion()).invert();
        const modelPos2 = this.model.getWorldPosition(new THREE.Vector3());
        const toLocal = (o) => o.getWorldPosition(new THREE.Vector3())
          .sub(modelPos2).applyQuaternion(modelInv2).divideScalar(this.modelScale || 1);
        const tp = toLocal(toeBone);
        const fpL = toLocal(footBone);
        footFwd = Math.max(tp.distanceTo(fpL), 0.05);
        void tp; void fpL;
        /*
         * İleri yön: _alignRestFrame sağ ekseni +X'e getirdiği ve yukarı
         * +Y olduğu için sağ el kuralıyla ileri +Z olur. Ayak parmağından
         * çıkarmayı denemiştim ama bu rigde parmak kemiği bileğin neredeyse
         * tam altında duruyor ve işaret güvenilir çıkmıyor.
         */
        forward = 1;
      }
    }

    this.dim = {
      torso,
      footAnkle,
      footFwd,
      forward,
      chest: len('chest') || torso * 0.25,
      upperArm: len('armL') || 0.33,
      foreArm: len('foreArmL') || 0.24,
      hand: len('handL') || 0.12,
      thigh: len('thighL') || 0.40,
      shin: len('shinL') || 0.50,
      foot: len('footL') || 0.13,
      head,
      headHeight,
      shoulderWidth: gap('armL', 'armR') || 0.36,
      hipWidth: gap('thighL', 'thighR') || 0.20,
    };
  }

  /** Yüz ve saç: zırhtan bağımsız, çıkarılmıyor. */
  _addFace() {
    const face = buildFace(this.dim, this.cfg.face || {});
    this.equip('face', face.piece, face.joint, face.offset);
  }

  /* ---------------- Ekipman ---------------- */

  /**
   * Krallık paletine göre bütün zırh setini takar.
   * @param {object} theme kingdom.armor
   */
  equipArmor(theme) {
    this.unequipArmor();
    const pieces = buildArmorSet(theme, this.dim, this.restPC);
    for (const [slot, def] of Object.entries(pieces)) {
      const joint = def.joint || ARMOR_SLOTS[slot];
      if (!joint || !this.j[joint]) continue;
      this.equip(slot, def.piece, joint, def.offset);
    }
    // Kılıç izi için namlu uçları
    const sword = pieces.sword;
    if (sword) {
      this.weaponBase = sword.piece.userData.weaponBase;
      this.weaponTip = sword.piece.userData.weaponTip;
    }
    this.armorTheme = theme;
    return pieces;
  }

  unequipArmor() {
    for (const slot of Object.keys(ARMOR_SLOTS)) this.unequip(slot);
    this.weaponBase = this.weaponTip = null;
  }

  /**
   * Krallık paletini kaydeder ama parça takmaz.
   *
   * Oyuncunun görünümü envanterden geliyor: hangi eşya kuşanılıysa o eşyanın
   * kendi modeli üretiliyor. Bu yüzden oyuncuda tam set takmak yerine yalnızca
   * tema saklanıyor; parçaları applyEquipmentVisuals üretiyor.
   */
  setArmorTheme(theme) {
    this.armorTheme = theme;
    return this;
  }

  /**
   * Kuşanılan eşyalara göre karakterin parçalarını yeniden üretir.
   *
   * Metin2'de her eşyanın kendi modeli vardır: kılıç yerine balta kuşanınca
   * elde balta görünür, lamel zırh yerine pul göğüslük kuşanınca gövde
   * değişir, zırh çıkarılınca karakter çıplak kalır. Burada da öyle: bir
   * grubun eşyası değiştiğinde eski parçalar sökülüp yenisi eşyanın
   * `gorunum`una göre üretiliyor.
   *
   * Üretim yalnızca gerçekten değişen grup için yapılıyor (anahtar
   * karşılaştırması), böylece envanterin her dokunuşunda bütün zırh yeniden
   * kurulmuyor.
   *
   * @param {Object<string, ?object>} gorsel  görünüm grubu -> kuşanılan eşya
   */
  applyEquipmentVisuals(gorsel) {
    this._gorunumAnahtar ||= {};
    this._gorunumSlot ||= {};
    const theme = this.armorTheme || this.cfg.armor;
    if (!theme) return;

    for (const grup of Object.keys(GRUP_SLOTLARI)) {
      const item = gorsel[grup] || null;
      // Aynı model + aynı kademe ise dokunma
      const anahtar = item ? `${item.gorunum || item.tplId}:${item.kademe}` : '';
      if (this._gorunumAnahtar[grup] === anahtar) continue;
      this._gorunumAnahtar[grup] = anahtar;

      for (const slot of this._gorunumSlot[grup] || GRUP_SLOTLARI[grup]) this.unequip(slot);
      this._gorunumSlot[grup] = [];
      if (grup === 'sword') this.weaponBase = this.weaponTip = null;
      if (!item) continue;

      const ctx = makeContext(theme, this.dim, this.restPC, item.kademe);
      const parcalar = buildVisual(grup, item.gorunum, ctx);
      for (const [slot, def] of Object.entries(parcalar)) {
        const joint = def.joint || ARMOR_SLOTS[slot];
        if (!joint || !this.j[joint]) continue;
        this.equip(slot, def.piece, joint, def.offset);
        this._gorunumSlot[grup].push(slot);
      }
      // Kılıç izi namlunun uçlarını izliyor
      const silah = parcalar.sword;
      if (silah) {
        this.weaponBase = silah.piece.userData.weaponBase;
        this.weaponTip = silah.piece.userData.weaponTip;
      }
    }
    this.silahVar = !!gorsel.sword;
  }

  /** Zırhsız (çıplak) görünüm — ekipman sistemi için. */
  get isBare() { return this.equipment.size <= 1; }


  /**
   * Bir zırh parçasını kemiğe takar.
   *
   * Parçalar karakter uzayında tasarlanıyor (Y yukarı, Z ileri, X sağ).
   * Kemiklerin yerel eksenleri rige göre değiştiği için parça, kemiğin
   * dinlenme yönelimiyle ters döndürülerek takılıyor: böylece tasarım
   * sırasında kemik eksen düzenini bilmek gerekmiyor, parça yine de
   * animasyonda kemiği takip ediyor.
   *
   * @param {string} slot
   * @param {THREE.Object3D} piece
   * @param {string} joint  eklem adı (varsayılan: slot ile aynı)
   * @param {THREE.Vector3|number[]} [offset]  karakter uzayında kaydırma
   */
  equip(slot, piece, joint = slot, offset = null) {
    this.unequip(slot);
    const bone = this.j[joint];
    if (!bone) { console.warn('[rig] ekipman için kemik yok:', joint); return null; }

    const inv = (this.restQ[joint] || new THREE.Quaternion()).clone().invert();
    const frame = this.frameQ || new THREE.Quaternion();
    // Parça karakter uzayında yazıldı: önce modelin eksen tabanına, sonra
    // kemiğin yerel uzayına çevir.
    piece.quaternion.copy(inv).multiply(frame);
    if (offset) {
      const v = Array.isArray(offset) ? new THREE.Vector3(...offset) : offset.clone();
      piece.position.copy(v.applyQuaternion(frame).applyQuaternion(inv));
    }
    bone.add(piece);
    this.equipment.set(slot, { piece, bone });
    return piece;
  }

  unequip(slot) {
    const cur = this.equipment.get(slot);
    if (!cur) return;
    cur.bone.remove(cur.piece);
    /*
     * Ekipman değiştikçe parçalar yeniden üretiliyor; sökülen parçanın
     * geometrisi bırakılmazsa her kuşanmada GPU'da birikirdi. Dokular
     * paylaşılan önbellekten geldiği için materyal.dispose() onlara
     * dokunmuyor.
     */
    cur.piece.traverse((o) => {
      o.geometry?.dispose();
      if (Array.isArray(o.material)) o.material.forEach((m) => m.dispose());
      else o.material?.dispose();
    });
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
    this.model.rotation.y = (this.rotationY || 0) + (this.driver._curRootYaw || 0);

    return ev;
  }

  /* ---------------- Savaş arayüzü ---------------- */

  get isSwinging() {
    if (!this.weaponTip || this.silahVar === false) return false;
    return this.state === 'attack' || this.state === 'spin' || this.state === 'tripleCut';
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
    const gltf = await loadGltf(cfg.file);
    return new RiggedCharacter(gltf.scene, cfg, gltf);
  }
}
