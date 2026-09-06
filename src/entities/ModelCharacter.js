/**
 * ModelCharacter.js — Dışarıdan yüklenen riglenmiş karakter.
 *
 * Prosedürel `Warrior` ile aynı arayüzü sunar (setState / update / root /
 * isSwinging / inDamageWindow ...), böylece oyunun geri kalanı hangi
 * karakterin kullanıldığını bilmek zorunda kalmaz: `PlayerController` ve
 * `main.js` iki durumda da aynı kodu çalıştırır.
 *
 * Desteklenen biçimler: .glb / .gltf (GLTFLoader) ve .fbx (FBXLoader).
 *
 * Animasyon klipleri isimleriyle eşleştirilir. Farklı kaynaklar farklı
 * adlandırma kullandığı için (Mixamo "mixamo.com", Quaternius "Idle",
 * kimi paketler "Armature|Walk") eşleştirme eş anlamlı listeleri üzerinden
 * yapılır; yapılandırma dosyasıyla elle de belirtilebilir.
 */
import * as THREE from 'three';
import { GLTFLoader } from '../../vendor/three/jsm/loaders/GLTFLoader.js';
import { loadGltf } from './loadGltf.js';
import { FBXLoader } from '../../vendor/three/jsm/loaders/FBXLoader.js';

/** Oyun durumları ve klip adlarında aranacak eş anlamlılar (öncelik sırasıyla). */
const CLIP_SYNONYMS = {
  idle: ['idle', 'stand', 'breathing', 'rest', 'wait'],
  combatIdle: ['combatidle', 'battleidle', 'fightidle', 'guard', 'combat', 'battle', 'ready'],
  walk: ['walk', 'walking'],
  run: ['run', 'running', 'sprint', 'jog'],
  attack: ['attack', 'slash', 'swing', 'melee', 'atk', 'strike', 'chop', 'cut'],
  spin: ['spin', 'whirl', 'twirl', 'cyclone', 'spinattack', 'sweep'],
  tripleCut: ['combo', 'triple', 'flurry', 'special'],
  jump: ['jump', 'air', 'falling', 'leap'],
  hit: ['hit', 'hurt', 'damage', 'impact', 'flinch', 'recoil', 'takedamage', 'gethit'],
  die: ['die', 'death', 'dead', 'ko', 'defeat'],
};

/** Bir kez oynanıp biten durumlar. */
const ONE_SHOT = new Set(['attack', 'spin', 'tripleCut', 'dash', 'hit', 'die']);

/** Klip adını karşılaştırmaya uygun hale getirir. */
function normalize(name) {
  return String(name)
    .toLowerCase()
    .replace(/mixamo\.?com/g, '')
    .replace(/^.*[|:]/, '')        // "Armature|Walk" -> "Walk"
    .replace(/[^a-z0-9]/g, '');
}

/**
 * Klipleri oyun durumlarına eşler.
 * @returns {{map: Object, attacks: THREE.AnimationClip[], report: string[]}}
 */
function matchClips(clips, overrides = {}, attackList = null) {
  const byNorm = clips.map((c) => ({ clip: c, n: normalize(c.name) }));
  const used = new Set();
  const map = {};
  const report = [];

  // Önce yapılandırmadaki elle eşlemeler
  for (const [state, wanted] of Object.entries(overrides)) {
    const hit = clips.find((c) => c.name === wanted)
      || byNorm.find((b) => b.n === normalize(wanted))?.clip;
    if (hit) { map[state] = hit; used.add(hit); report.push(`${state} <- "${hit.name}" (elle)`); }
    else report.push(`${state} <- "${wanted}" BULUNAMADI`);
  }

  /*
   * Saldırı zinciri. Paketler çoğu zaman tek elli, çift elli, silahsız ve
   * menzilli saldırıları bir arada taşıdığı için ada bakarak toplamak yanlış
   * karışımlar üretiyor; bu yüzden yapılandırmada liste verilebiliyor ve
   * verildiğinde otomatik toplama devre dışı kalıyor.
   */
  const attacks = [];
  if (attackList && attackList.length) {
    for (const wanted of attackList) {
      const hit = clips.find((c) => c.name === wanted)
        || byNorm.find((b) => b.n === normalize(wanted))?.clip;
      if (hit) { attacks.push(hit); used.add(hit); }
      else report.push(`saldırı "${wanted}" BULUNAMADI`);
    }
  } else {
    for (const b of byNorm) {
      if (used.has(b.clip)) continue;
      if (CLIP_SYNONYMS.attack.some((s) => b.n.includes(s))) attacks.push(b.clip);
    }
    attacks.sort((a, c) => a.name.localeCompare(c.name, undefined, { numeric: true }));
  }

  for (const [state, syns] of Object.entries(CLIP_SYNONYMS)) {
    if (map[state]) continue;
    // Zincir açıkça verildiyse 'attack' için ada bakarak tahmin yürütme
    if (state === 'attack' && attacks.length) continue;
    let best = null, bestScore = -1;
    for (const b of byNorm) {
      if (used.has(b.clip)) continue;
      for (let i = 0; i < syns.length; i++) {
        if (!b.n.includes(syns[i])) continue;
        // Erken eş anlamlı + kısa isim = daha güvenli eşleşme
        const score = (syns.length - i) * 10 - b.n.length * 0.1;
        if (score > bestScore) { bestScore = score; best = b.clip; }
      }
    }
    if (best) {
      map[state] = best;
      used.add(best);
      report.push(`${state} <- "${best.name}"`);
    }
  }

  if (attacks.length) {
    map.attack = map.attack || attacks[0];
    report.push(`saldırı zinciri: ${attacks.map((a) => a.name).join(', ')}`);
  }
  const unused = byNorm.filter((b) => !used.has(b.clip) && !attacks.includes(b.clip));
  if (unused.length) report.push(`kullanılmayan: ${unused.map((u) => u.clip.name).join(', ')}`);
  return { map, attacks, report };
}

export class ModelCharacter {
  /**
   * `ModelCharacter.load()` ile oluşturulur; kurucu doğrudan çağrılmaz.
   */
  constructor(scene, clips, cfg) {
    this.cfg = cfg;
    this.root = new THREE.Group();
    this.root.name = 'model-character';
    this.root.add(scene);
    this.model = scene;

    /* -- Ölçek ve zemin hizası: model ne boyda gelirse gelsin -- */
    const box = new THREE.Box3().setFromObject(scene);
    const size = new THREE.Vector3();
    box.getSize(size);
    const targetH = cfg.height ?? 1.95;
    const s = (cfg.scale ?? (size.y > 0.001 ? targetH / size.y : 1));
    scene.scale.setScalar(s);
    // Ayakları y=0'a indir
    const box2 = new THREE.Box3().setFromObject(scene);
    scene.position.y -= box2.min.y;
    if (cfg.yOffset) scene.position.y += cfg.yOffset;
    if (cfg.rotationY) scene.rotation.y = cfg.rotationY;
    this.height = targetH;

    scene.traverse((o) => {
      if (o.isMesh || o.isSkinnedMesh) { o.castShadow = true; o.receiveShadow = true; }
    });

    /* -- Animasyon -- */
    this.mixer = new THREE.AnimationMixer(scene);
    const { map, attacks, report } = matchClips(clips, cfg.clips || {}, cfg.attackClips);
    this.clipMap = map;
    this.attackClips = attacks;
    this.clipReport = report;

    this.actions = {};
    for (const [state, clip] of Object.entries(map)) {
      this.actions[state] = this._makeAction(clip, state);
    }
    this.attackActions = attacks.map((c) => this._makeAction(c, 'attack'));

    /* -- Durum -- */
    this.state = 'idle';
    this.stateTime = 0;
    this.animTime = 0;
    this.attackVariant = 0;
    this.vy = 0;
    this.current = null;
    this.fade = cfg.fade ?? 0.15;

    /*
     * Oynatma hızı. Hazır klipler oyunun temposuna göre yavaş kalabiliyor;
     * `speed` ile hızlandırıldığında durum süresinin de kısalması gerekiyor,
     * yoksa hareket biter ama oyuncu kilitli kalır.
     */
    this.speedScale = cfg.speed || {};
    this.durations = {};
    for (const st of ONE_SHOT) {
      const a = this._actionFor(st, 0);
      if (!a) continue;
      this.durations[st] = a.getClip().duration / (this.speedScale[st] || 1);
    }

    /*
     * Yürüyüş klipleri belirli bir hız için üretilmiştir; oyunun hızı farklı
     * olduğunda ayak kayar. Klibin "tasarlandığı hız" yapılandırmadan gelir
     * ve oynatma hızı buna göre ölçeklenir.
     */
    this.clipSpeed = {
      walk: cfg.clipWalkSpeed ?? 1.75,
      run: cfg.clipRunSpeed ?? 7.0,
    };

    this._applyNodeVisibility();
    this._setupWeapon();
    this.setState('idle', { force: true });
  }

  /**
   * Karakter paketleri genelde bütün silah ve kalkan çeşitlerini modelin
   * içinde taşır ve hepsi birden görünür gelir. `show` listesindekiler açık,
   * geri kalan eşleşenler kapalı hale getiriliyor.
   */
  _applyNodeVisibility() {
    const { showNodes, hideNodes } = this.cfg;
    if (!showNodes && !hideNodes) return;
    const show = new Set((showNodes || []).map(normalize));
    const hide = new Set((hideNodes || []).map(normalize));
    const found = [];
    this.model.traverse((o) => {
      if (!o.name) return;
      const n = normalize(o.name);
      if (show.has(n)) { o.visible = true; found.push('+' + o.name); }
      else if (hide.has(n)) { o.visible = false; found.push('-' + o.name); }
    });
    if (found.length) console.info('[karakter] görünürlük:', found.join(' '));
  }

  _makeAction(clip, state) {
    const a = this.mixer.clipAction(clip);
    if (ONE_SHOT.has(state)) {
      a.setLoop(THREE.LoopOnce, 1);
      a.clampWhenFinished = true;
    }
    return a;
  }

  _actionFor(state, variant = 0) {
    if (state === 'attack' && this.attackActions.length) {
      return this.attackActions[variant % this.attackActions.length];
    }
    return this.actions[state] || null;
  }

  /**
   * Kılıç izi için namlu uçlarını bulur. Model bir silah taşımıyorsa iz
   * kapatılır — uydurma bir konumdan şerit çıkarmak yanlış görünür.
   */
  _setupWeapon() {
    const cfg = this.cfg;
    this.weaponBase = null;
    this.weaponTip = null;
    if (!cfg.weaponBone) return;

    let bone = null;
    const want = normalize(cfg.weaponBone);
    this.model.traverse((o) => {
      if (!bone && (o.isBone || o.isObject3D) && normalize(o.name) === want) bone = o;
    });
    if (!bone) {
      console.warn(`[ModelCharacter] silah kemiği bulunamadı: ${cfg.weaponBone}`);
      return;
    }
    const [bx, by, bz] = cfg.weaponBaseOffset ?? [0, 0.1, 0];
    const [tx, ty, tz] = cfg.weaponTipOffset ?? [0, 1.2, 0];
    this.weaponBase = new THREE.Object3D();
    this.weaponBase.position.set(bx, by, bz);
    this.weaponTip = new THREE.Object3D();
    this.weaponTip.position.set(tx, ty, tz);
    bone.add(this.weaponBase, this.weaponTip);
  }

  /* ---------------- Warrior ile aynı arayüz ---------------- */

  setState(name, opts = {}) {
    if (this.state === name && !opts.force) return;
    if (name === 'attack') this.attackVariant = opts.variant ?? 0;

    let next = this._actionFor(name, this.attackVariant);
    // Zincirdeki her savurma farklı uzunlukta olabilir
    if (name === 'attack' && next) {
      this.durations.attack = next.getClip().duration / (this.speedScale.attack || 1);
    }
    // Klip yoksa en yakın makul karşılığa düş
    if (!next) {
      const fallback = { combatIdle: 'idle', run: 'walk', walk: 'idle', jump: 'idle',
        spin: 'attack', tripleCut: 'attack', dash: 'run', hit: 'idle', die: 'idle' }[name];
      next = fallback ? this._actionFor(fallback, 0) : null;
    }
    this.state = name;
    this.stateTime = 0;
    if (!next) return;

    next.reset();
    next.setEffectiveWeight(1);
    next.enabled = true;
    if (this.current && this.current !== next) {
      next.crossFadeFrom(this.current, this.fade, false);
    }
    next.play();
    this.current = next;
  }

  update(dt, speed = 0) {
    this.stateTime += dt;
    this.animTime += dt;

    // Yürüyüş/koşu kliplerini gerçek hıza göre oynat: ayak kaymasın.
    // Diğer hareketlerde yapılandırmadan gelen sabit hız çarpanı geçerli.
    if (this.current) {
      const ref = this.clipSpeed[this.state];
      this.current.timeScale = ref
        ? THREE.MathUtils.clamp(speed / ref, 0.25, 2.5)
        : (this.speedScale[this.state] || 1);
    }
    this.mixer.update(dt);

    const d = this.durations[this.state];
    if (d && this.stateTime >= d) return this.state + '-end';
    return null;
  }

  get isSwinging() {
    if (!this.weaponTip) return false;
    return this.state === 'attack' || this.state === 'spin' || this.state === 'tripleCut';
  }

  get inDamageWindow() {
    const d = this.durations[this.state];
    if (!d) return false;
    const u = this.stateTime / d;
    const [a, b] = this.cfg.damageWindow ?? [0.3, 0.55];
    return u > a && u < b;
  }

  getWeaponTipWorld(out = new THREE.Vector3()) {
    return this.weaponTip ? this.weaponTip.getWorldPosition(out) : out.copy(this.root.position);
  }

  getWeaponBaseWorld(out = new THREE.Vector3()) {
    return this.weaponBase ? this.weaponBase.getWorldPosition(out) : out.copy(this.root.position);
  }

  addTo(parent) { parent.add(this.root); return this; }

  /* ---------------- Yükleme ---------------- */

  /**
   * Model biçimini belirler.
   *
   * Uzantıya bakmak yetmiyor: tek dosyalık pakette model bir `data:` URI'si
   * olarak gömülü geliyor ve orada nokta karakteri base64 verisinin içinde
   * geçiyor. Bu yüzden data URI'lerinde MIME türüne bakılıyor.
   *
   * @param {string} url
   * @returns {string} 'glb' | 'gltf' | 'fbx' | tanınmayan girdinin kendisi
   */
  static formatOf(url) {
    if (url.startsWith('data:')) {
      // MIME, ilk ';' veya ',' karakterine kadar sürer
      const semi = url.indexOf(';');
      const comma = url.indexOf(',');
      const ends = [semi, comma].filter((i) => i > 0);
      const end = ends.length ? Math.min(...ends) : url.length;
      const mime = url.slice(5, end).toLowerCase();
      if (mime.includes('gltf-binary')) return 'glb';
      if (mime.includes('gltf')) return 'gltf';
      if (mime.includes('fbx')) return 'fbx';
      return mime || 'data';
    }
    return url.split('?')[0].split('.').pop().toLowerCase();
  }

  /**
   * @param {object} cfg  { file, scale, height, yOffset, rotationY, clips,
   *                        weaponBone, clipWalkSpeed, clipRunSpeed }
   * @returns {Promise<ModelCharacter>}
   */
  static async load(cfg) {
    const url = cfg.file;
    const ext = ModelCharacter.formatOf(url);
    let scene, clips;

    if (ext === 'glb' || ext === 'gltf') {
      const gltf = await loadGltf(url);
      scene = gltf.scene;
      clips = gltf.animations || [];
    } else if (ext === 'fbx') {
      const fbx = await new FBXLoader().loadAsync(url);
      scene = fbx;
      clips = fbx.animations || [];
    } else {
      throw new Error(`Desteklenmeyen model biçimi: ${ext} (glb, gltf veya fbx bekleniyor)`);
    }

    // Ek animasyon dosyaları (ayrı indirilen saldırı/ölüm klipleri gibi)
    for (const extra of cfg.animationFiles || []) {
      const e = ModelCharacter.formatOf(extra);
      const loaded = e === 'fbx'
        ? await new FBXLoader().loadAsync(extra)
        : await new GLTFLoader().loadAsync(extra);
      const got = loaded.animations || [];
      // Dosya adı klip adından daha bilgilendirici olabilir
      const base = extra.split('/').pop().replace(/\.[^.]+$/, '');
      for (const c of got) {
        if (/^(mixamo\.com|armature|take ?001|default)$/i.test(c.name)) c.name = base;
        clips.push(c);
      }
    }

    if (!clips.length) {
      throw new Error('Model animasyon içermiyor');
    }
    return new ModelCharacter(scene, clips, cfg);
  }
}
