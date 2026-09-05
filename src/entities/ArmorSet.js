/**
 * ArmorSet.js — Kemiklere takılan ekipman parçaları.
 *
 * Metin2'de kuşanılan her eşyanın karakterde kendi görüntüsü vardır: kılıç
 * ile balta elde farklı durur, lamel zırh ile pul göğüslük farklı bir siluet
 * verir. Burada da öyle: her eşya şablonunun bir `gorunum` adı var ve o ad
 * aşağıdaki tablodan bir üreteci seçiyor. Kuşanınca parçalar üretilip
 * kemiklere takılıyor, çıkarınca sökülüyor.
 *
 * Tasarım uzayı
 * -------------
 * Parçalar **karakter uzayında** yazılıyor: Y yukarı, Z ileri, X sağ.
 * Kemiklerin yerel eksen düzeni rigden rige değiştiği için parçayı kemiğin
 * yerel eksenlerine göre yazmak taşınabilir değil. RiggedCharacter.equip
 * parçayı kemiğin dinlenme yönelimiyle ters döndürerek takıyor; burada
 * yalnızca "yukarı", "ileri" ve "sağ" düşünmek yeterli.
 *
 * Konumlar kemiğin dinlenme noktasına göre, karakter uzayında veriliyor.
 */
import * as THREE from 'three';
import { getTexture } from '../core/Textures.js';
import { TIERS } from '../data/items.js';

/** Köşeleri kırılmış kutu — düz kutudan daha dövülmüş bir plaka verir. */
function plate(w, h, d, bevel = 0.18) {
  const g = new THREE.BoxGeometry(w, h, d);
  const p = g.attributes.position;
  const v = new THREE.Vector3();
  for (let i = 0; i < p.count; i++) {
    v.fromBufferAttribute(p, i);
    if (Math.abs(v.y) / (h / 2) > 0.9) { v.x *= 1 - bevel; v.z *= 1 - bevel; }
    p.setXYZ(i, v.x, v.y, v.z);
  }
  g.computeVertexNormals();
  return g;
}

/** Yukarı bakan kubbe kabuğu (kapalı tarafı +Y). */
function dome(r, cut = 0.6, seg = 12) {
  return new THREE.SphereGeometry(r, seg, Math.max(6, seg - 4), 0, Math.PI * 2, 0, Math.PI * cut);
}

function mesh(geo, mat, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(geo, mat);
  m.position.set(x, y, z);
  m.castShadow = true;
  return m;
}

/**
 * Krallık paletinden materyal seti.
 *
 * Kademe (eşya kalitesi) rengi kenarlık materyaline karışıyor: yıpranmış bir
 * parçanın kenarlığı sönük demir, efsanevi bir parçanınki mor ve hafif
 * ışıldıyor. Böylece kuşanılan eşyanın kalitesi envantere bakmadan,
 * karakterin kendisinden okunuyor — Metin2'deki "+" parıltısının karşılığı.
 *
 * @param {object} theme kingdom.armor
 * @param {?number} kademe TIERS dizini (null: krallık rengi, kademesiz)
 */
export function makeArmorMaterials(theme, kademe = null) {
  const L = (o) => new THREE.MeshLambertMaterial(o);
  const tier = kademe == null ? null : TIERS[kademe];
  let base = new THREE.Color(theme.base);
  let trim = new THREE.Color(theme.trim);
  let parlak = 0;
  if (tier) {
    const tc = new THREE.Color(tier.renk);
    if (kademe === 0) {
      // Yıpranmış: soluk ve kirli
      base.lerp(new THREE.Color(0x6b6760), 0.42);
      trim.lerp(new THREE.Color(0x7a736a), 0.60);
    } else if (kademe >= 2) {
      trim.lerp(tc, 0.55);
      parlak = (kademe - 1) * 0.10;          // Sağlam 0.10 … Efsanevi 0.30
      base.lerp(tc, (kademe - 1) * 0.06);
    }
  }
  const goldOpt = { color: trim };
  if (parlak > 0) { goldOpt.emissive = trim.clone().multiplyScalar(parlak); }
  return {
    /*
     * Lamel dokusu güçlü yatay çizgiler taşıyor. Her parçaya uygulandığında
     * karakter "çizgili silindirler yığını" gibi okunuyordu; bu yüzden doku
     * yalnızca geniş gövde yüzeylerinde kullanılıyor, geri kalan parçalar
     * düz renk + kenarlıkla çözülüyor.
     */
    lamellar: L({ map: getTexture('armor', { a: '#' + base.getHexString(), b: '#' + trim.getHexString() }) }),
    plate: L({ color: base.clone().multiplyScalar(1.05) }),
    plain: L({ color: base.clone().multiplyScalar(1.22) }),
    dark: L({ color: base.clone().multiplyScalar(0.62) }),
    gold: L(goldOpt),
    cloth: L({ color: theme.cloth }),
    leather: L({ color: 0x4a3222 }),
    leatherLight: L({ color: 0x6b4c2e }),
    fur: L({ color: 0x8d7a5f }),
    steel: L({ color: 0xc9d0d8 }),
    metal: L({ color: 0x8f98a3 }),
    wood: L({ color: 0x5b4126 }),
  };
}

/**
 * Görünüm üreteçlerinin ortak bağlamı.
 * @param {object} theme kingdom.armor
 * @param {object} dim   kemiklerden ölçülen boyutlar
 * @param {object} pos   eklemlerin dinlenme konumları (karakter uzayı)
 * @param {?number} kademe eşya kalitesi
 */
export function makeContext(theme, dim, pos, kademe = null) {
  const yOf = (j) => (pos && pos[j] ? pos[j].y : 0);
  /*
   * Gövde ekseni düzeltmesi: kemik gövdenin tam ortasında durmuyor (bu
   * rigde göğüs kemiği kalçadan birkaç santim önde). Parça kemiğe göre
   * ortalanırsa zırh öne kayıyor ve sırt açıkta kalıyor.
   */
  const eksen = (j) => {
    const h = pos && pos.hips, b = pos && pos[j];
    if (!h || !b) return [0, 0, 0];
    return [h.x - b.x, 0, h.z - b.z];
  };
  return {
    M: makeArmorMaterials(theme, kademe),
    theme, dim, pos, kademe, eksen,
    fwd: dim.forward ?? 1,
    sw: dim.shoulderWidth,
    hipsY: yOf('hips'),
    chestY: yOf('chest'),
    neckY: yOf('neck'),
    headY: yOf('head'),
  };
}

/* ==================================================================== */
/*  Zırh (chest) görünümleri                                            */
/* ==================================================================== */

/** Göğüslüğün kapladığı dikey aralık — üç zırh görünümü de bunu kullanıyor. */
function torsoBox(c) {
  const top = c.neckY - c.dim.torso * 0.06;
  const bottom = c.hipsY + c.dim.torso * 0.12;
  const h = Math.max(top - bottom, c.dim.torso * 0.4);
  return { h, midY: (top + bottom) / 2 - c.chestY };
}

/**
 * Lamel zırh: dövülmüş göğüs plakası, karın bandı lamel dokulu.
 *
 * Gövde kutu değil, basık silindir: kutunun köşeleri gövdeyi saramıyordu ve
 * göğsün ön yüzü zırhın içinden dışarı taşıp çıplak bir V bırakıyordu.
 * Silindir gövdenin kesitini takip ettiği için her yandan kapatıyor.
 */
function chestLamel(c) {
  const { M, sw, fwd } = c;
  const out = {};
  const { h, midY } = torsoBox(c);
  const r = sw * 0.60;          // yarıçap (en)
  const zk = 0.74;              // derinlik/en oranı
  const g = new THREE.Group();

  const govde = (rt, rb, yh, sc = 1) => {
    const geo = new THREE.CylinderGeometry(rt * sc, rb * sc, yh, 14);
    geo.scale(1, 1, zk);
    return geo;
  };
  // Göğüs: geniş üst, daralan bel
  g.add(mesh(govde(r, r * 0.90, h * 0.58), M.plate, 0, midY + h * 0.20, 0));
  g.add(mesh(govde(r * 0.90, r * 0.82, h * 0.46), M.plate, 0, midY - h * 0.24, 0));
  // Karın bandı: lamel dokusu yalnızca burada
  g.add(mesh(govde(r * 0.92, r * 0.86, h * 0.26), M.lamellar, 0, midY - h * 0.28, 0));
  // Göğüs ortasında dikey sırt (kabartma yerine): meme gibi durmuyor
  g.add(mesh(plate(r * 0.30, h * 0.50, r * 0.30, 0.3), M.plain,
    0, midY + h * 0.22, fwd * r * zk * 0.80));
  // Kenarlıklar
  g.add(mesh(new THREE.TorusGeometry(r * 1.01, r * 0.055, 6, 18).rotateX(Math.PI / 2)
    .scale(1, 1, zk), M.gold, 0, midY + h * 0.48, 0));
  g.add(mesh(new THREE.TorusGeometry(r * 0.86, r * 0.05, 6, 18).rotateX(Math.PI / 2)
    .scale(1, 1, zk), M.gold, 0, midY - h * 0.46, 0));
  // Boyunluk: omuz ile boyun arasını kapatır
  g.add(mesh(govde(r * 0.62, r * 0.94, h * 0.16), M.plain, 0, midY + h * 0.50, 0));
  g.add(mesh(new THREE.TorusGeometry(r * 0.60, r * 0.06, 6, 16).rotateX(Math.PI / 2)
    .scale(1, 1, zk), M.gold, 0, midY + h * 0.57, 0));
  out.chest = { piece: g, joint: 'chest', offset: c.eksen('chest') };

  Object.assign(out, pauldronsPlate(c), tassetsPanel(c), thighGuards(c));
  return out;
}

/** Pul göğüslük: yuvarlak hatlı, tümüyle pullu; omuzlar yumuşak. */
function chestPul(c) {
  const { M, sw, fwd } = c;
  const out = {};
  const { h, midY } = torsoBox(c);
  const w = sw * 1.10, d = sw * 0.70;
  const g = new THREE.Group();

  // Silindirik gövde: plaka değil, örgü zırh gibi yumuşak
  const body = new THREE.CylinderGeometry(w * 0.52, w * 0.46, h * 0.92, 14);
  body.scale(1, 1, d / w * 1.30);
  g.add(mesh(body, M.lamellar, 0, midY, 0));
  // Göğüs üstü ikinci pul katmanı
  const yaka = new THREE.CylinderGeometry(w * 0.54, w * 0.52, h * 0.30, 14);
  yaka.scale(1, 1, d / w * 1.34);
  g.add(mesh(yaka, M.lamellar, 0, midY + h * 0.30, 0));
  // Deri kayışlar
  for (const s of [-1, 1]) {
    const kayis = plate(w * 0.12, h * 0.86, d * 0.14, 0.2);
    kayis.rotateZ(s * 0.10);
    g.add(mesh(kayis, M.leather, s * w * 0.24, midY + h * 0.02, fwd * d * 0.62));
  }
  g.add(mesh(new THREE.TorusGeometry(w * 0.50, w * 0.055, 6, 18).rotateX(Math.PI / 2),
    M.gold, 0, midY - h * 0.44, 0));
  g.add(mesh(new THREE.TorusGeometry(w * 0.29, w * 0.05, 6, 16).rotateX(Math.PI / 2),
    M.gold, 0, midY + h * 0.46, 0));
  out.chest = { piece: g, joint: 'chest', offset: c.eksen('chest') };

  Object.assign(out, pauldronsRound(c), tassetsSkirt(c));
  return out;
}

/** Deri zırh: ince yelek, kayışlar, omuzluk yok — hafif siluet. */
function chestDeri(c) {
  const { M, sw, fwd } = c;
  const out = {};
  const { h, midY } = torsoBox(c);
  const w = sw * 1.02, d = sw * 0.62;
  const g = new THREE.Group();

  g.add(mesh(plate(w, h * 0.86, d, 0.26), M.leatherLight, 0, midY + h * 0.02, 0));
  // Ön açıklık: iki yaka
  for (const s of [-1, 1]) {
    g.add(mesh(plate(w * 0.30, h * 0.80, d * 0.20, 0.3), M.leather,
      s * w * 0.30, midY + h * 0.04, fwd * d * 0.52));
  }
  // Göğüs kayışı ve toka
  g.add(mesh(plate(w * 1.04, h * 0.10, d * 1.04, 0.2), M.leather, 0, midY + h * 0.18, 0));
  g.add(mesh(plate(w * 0.14, h * 0.12, d * 0.12, 0.3), M.gold, 0, midY + h * 0.18, fwd * d * 0.56));
  // Bel kuşağı
  g.add(mesh(plate(w * 0.98, h * 0.14, d * 0.98, 0.2), M.dark, 0, midY - h * 0.38, 0));
  out.chest = { piece: g, joint: 'chest', offset: c.eksen('chest') };

  Object.assign(out, tassetsKemer(c), legWraps(c));
  return out;
}

/** Bez pantolon: hafif zırhlarda bacak tümüyle çıplak kalmasın. */
function legWraps(c) {
  const { M, dim } = c;
  const out = {};
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    const len = dim.thigh;
    const r = len * 0.24;
    const p = new THREE.CylinderGeometry(r * 1.15, r * 0.98, len * 0.92, 10);
    g.add(mesh(p, M.cloth, 0, 0, 0));
    g.add(mesh(new THREE.TorusGeometry(r * 1.02, r * 0.10, 6, 12).rotateX(Math.PI / 2),
      M.leather, 0, -len * 0.44, 0));
    out['thighGuard' + side] = { piece: g, joint: 'thigh' + side, offset: [0, -len * 0.46, 0] };
  }
  return out;
}

/* ---- Zırhların paylaştığı yardımcı parçalar ---- */

/** Kubbe omuzluk + altın halka (ağır zırhlar). */
function pauldronsPlate(c) {
  const { M, sw } = c;
  const out = {};
  for (const side of ['L', 'R']) {
    const s = side === 'L' ? -1 : 1;
    const g = new THREE.Group();
    const r = sw * 0.27;
    const cap = dome(r, 0.60, 14);
    cap.scale(1.02, 0.88, 1.06);
    g.add(mesh(cap, M.plate, 0, 0, 0));
    const skirt = dome(r * 0.92, 0.46, 14);
    skirt.scale(1.10, 0.46, 1.12);
    g.add(mesh(skirt, M.plain, 0, -r * 0.30, 0));
    g.add(mesh(new THREE.TorusGeometry(r * 0.90, r * 0.085, 6, 16).rotateX(Math.PI / 2),
      M.gold, 0, -r * 0.04, 0));
    out['pauldron' + side] = { piece: g, joint: 'arm' + side, offset: [s * r * 0.20, r * 0.12, 0] };
  }
  return out;
}

/** Küçük yuvarlak omuzluk (pul zırh). */
function pauldronsRound(c) {
  const { M, sw } = c;
  const out = {};
  for (const side of ['L', 'R']) {
    const s = side === 'L' ? -1 : 1;
    const g = new THREE.Group();
    const r = sw * 0.26;
    const cap = dome(r, 0.66, 12);
    cap.scale(1.0, 0.78, 1.0);
    g.add(mesh(cap, M.lamellar, 0, 0, 0));
    g.add(mesh(new THREE.TorusGeometry(r * 0.94, r * 0.07, 6, 14).rotateX(Math.PI / 2),
      M.gold, 0, -r * 0.10, 0));
    out['pauldron' + side] = { piece: g, joint: 'arm' + side, offset: [s * r * 0.18, r * 0.10, 0] };
  }
  return out;
}

/** Uyluk plakaları (ağır zırhlar). */
function thighGuards(c) {
  const { M, dim } = c;
  const out = {};
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    const len = dim.thigh;
    const r = len * 0.26;
    g.add(mesh(plate(r * 1.8, len * 0.46, r * 1.7, 0.22), M.plain, 0, 0, 0));
    g.add(mesh(plate(r * 1.9, len * 0.08, r * 1.8, 0.2), M.gold, 0, -len * 0.22, 0));
    out['thighGuard' + side] = { piece: g, joint: 'thigh' + side, offset: [0, -len * 0.42, 0] };
  }
  return out;
}

/** Dört panelli zırh eteği (lamel). */
function tassetsPanel(c) {
  const { M, dim, sw, fwd } = c;
  const g = new THREE.Group();
  const w = Math.max(dim.hipWidth * 2.1, sw * 0.78);
  const h = dim.thigh * 0.66;
  const spec = [
    { ry: 0, z: fwd * w * 0.36, x: 0, pw: w * 0.74 },
    { ry: Math.PI, z: -fwd * w * 0.36, x: 0, pw: w * 0.74 },
    { ry: Math.PI / 2, z: 0, x: w * 0.44, pw: w * 0.56 },
    { ry: -Math.PI / 2, z: 0, x: -w * 0.44, pw: w * 0.56 },
  ];
  for (const t of spec) {
    const panel = new THREE.Group();
    panel.position.set(t.x, 0, t.z);
    panel.rotation.y = t.ry;
    panel.add(mesh(plate(t.pw, h * 0.56, w * 0.15, 0.12), M.plate, 0, -h * 0.30, 0));
    panel.add(mesh(plate(t.pw * 0.9, h * 0.50, w * 0.14, 0.14), M.plate, 0, -h * 0.72, 0));
    panel.add(mesh(plate(t.pw * 0.94, h * 0.07, w * 0.17, 0.2), M.gold, 0, -h * 0.56, 0));
    g.add(panel);
  }
  g.add(mesh(plate(w * 0.98, h * 0.15, w * 0.74, 0.12), M.leather, 0, h * 0.04, 0));
  g.add(mesh(plate(w * 0.26, h * 0.18, w * 0.10, 0.3), M.gold, 0, h * 0.04, fwd * w * 0.38));
  return { tassets: { piece: g, joint: 'hips', offset: [0, dim.torso * 0.06, 0] } };
}

/** Pullu, dilimli etek (pul göğüslük). */
function tassetsSkirt(c) {
  const { M, dim, sw } = c;
  const g = new THREE.Group();
  const w = Math.max(dim.hipWidth * 2.1, sw * 0.78);
  const h = dim.thigh * 0.60;
  const n = 10;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    const panel = new THREE.Group();
    panel.position.set(Math.sin(a) * w * 0.42, 0, Math.cos(a) * w * 0.42);
    panel.rotation.y = a;
    panel.add(mesh(plate(w * 0.32, h * 0.78, w * 0.10, 0.20), M.lamellar, 0, -h * 0.40, 0));
    g.add(panel);
  }
  g.add(mesh(new THREE.CylinderGeometry(w * 0.46, w * 0.46, h * 0.16, 16), M.leather, 0, h * 0.02, 0));
  g.add(mesh(new THREE.TorusGeometry(w * 0.47, w * 0.045, 6, 18).rotateX(Math.PI / 2),
    M.gold, 0, -h * 0.06, 0));
  return { tassets: { piece: g, joint: 'hips', offset: [0, dim.torso * 0.06, 0] } };
}

/** Yalnızca kemer + iki deri sarkıt (deri zırh). */
function tassetsKemer(c) {
  const { M, dim, sw, fwd } = c;
  const g = new THREE.Group();
  const w = Math.max(dim.hipWidth * 2.0, sw * 0.72);
  const h = dim.thigh * 0.42;
  g.add(mesh(new THREE.CylinderGeometry(w * 0.44, w * 0.44, h * 0.24, 14), M.leather, 0, 0, 0));
  g.add(mesh(plate(w * 0.20, h * 0.28, w * 0.10, 0.3), M.gold, 0, 0, fwd * w * 0.42));
  for (const s of [-1, 1]) {
    g.add(mesh(plate(w * 0.24, h * 0.86, w * 0.09, 0.18), M.leatherLight,
      s * w * 0.24, -h * 0.48, fwd * w * 0.36));
  }
  return { tassets: { piece: g, joint: 'hips', offset: [0, dim.torso * 0.06, 0] } };
}

/* ==================================================================== */
/*  Kolluk (bracer) görünümleri                                         */
/* ==================================================================== */

/** Deri sargı: yalnızca ön kol, ince. */
function bracerDeri(c) {
  const { M, dim } = c;
  const out = {};
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    const len = dim.foreArm;
    const r = len * 0.26;
    g.add(mesh(new THREE.CylinderGeometry(r, r * 0.88, len * 0.66, 10), M.leatherLight, 0, 0, 0));
    for (let i = 0; i < 3; i++) {
      g.add(mesh(new THREE.TorusGeometry(r * 0.96, r * 0.07, 5, 12).rotateX(Math.PI / 2),
        M.leather, 0, len * (0.22 - i * 0.22), 0));
    }
    out['bracer' + side] = { piece: g, joint: 'foreArm' + side, offset: [0, -len * 0.52, 0] };
  }
  return out;
}

/** Plaka kolluk: ön kol zırhı + üst kol manşonu. */
function bracerPlaka(c) {
  const { M, dim } = c;
  const out = {};
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    const len = dim.foreArm;
    const r = len * 0.28;
    g.add(mesh(new THREE.CylinderGeometry(r, r * 0.84, len * 0.60, 10), M.plate, 0, 0, 0));
    g.add(mesh(new THREE.TorusGeometry(r * 0.90, r * 0.15, 6, 14).rotateX(Math.PI / 2),
      M.gold, 0, -len * 0.28, 0));
    out['bracer' + side] = { piece: g, joint: 'foreArm' + side, offset: [0, -len * 0.52, 0] };

    const s = new THREE.Group();
    const ul = dim.upperArm;
    const ur = ul * 0.20;
    s.add(mesh(new THREE.CylinderGeometry(ur * 0.98, ur * 0.86, ul * 0.62, 10), M.plate, 0, 0, 0));
    s.add(mesh(new THREE.TorusGeometry(ur * 0.92, ur * 0.13, 6, 14).rotateX(Math.PI / 2),
      M.gold, 0, -ul * 0.30, 0));
    out['sleeve' + side] = { piece: s, joint: 'arm' + side, offset: [0, -ul * 0.42, 0] };
  }
  return out;
}

/* ==================================================================== */
/*  Ayakkabı (boot) görünümleri                                         */
/* ==================================================================== */

/**
 * Ayağı saran gövde — iki ayakkabı görünümü de bundan türüyor.
 *
 * Ayak kemiği bilekte duruyor ve ayak ondan **aşağı** uzanıyor; bu yüzden
 * kabuk kemiğin altına yerleştiriliyor. Eskiden kemiğin üstüne konuyordu ve
 * çizme, ayağı saracağına üstünde ince bir tepsi gibi duruyordu.
 */
function footShell(c, M2, o = {}) {
  const { dim, fwd } = c;
  const ankle = (dim.footAnkle ?? 0.09) * (o.yukseklik ?? 1);
  const fwdLen = dim.footFwd ?? 0.13;
  const w = Math.max(dim.shin * 0.25, fwdLen * 0.70);
  /*
   * `footFwd` bilekten parmak *kemiğine* olan mesafe; asıl parmak uçları
   * bunun ötesine geçiyor. Kabuk 1.5 katıyla bitirildiğinde parmaklar
   * çizmenin önünden dışarı taşıyordu, bu yüzden ön uç 2.05 katta.
   */
  const heel = fwdLen * 0.55;
  const on = fwdLen * 2.05;
  const len = on + heel;
  const zc = fwd * (on - heel) / 2;
  const g = new THREE.Group();
  // Bilekten yere kadar gövde
  g.add(mesh(plate(w * 1.12, ankle * 1.16, len * 0.78, 0.12), M2.ust, 0, -ankle * 0.42, zc * 0.72));
  // Alçalan burun bölümü
  g.add(mesh(plate(w * 1.02, ankle * 0.78, len * 0.46, 0.18), M2.ust,
    0, -ankle * 0.62, fwd * on * 0.66));
  // Taban, ayağın altında
  g.add(mesh(plate(w * 1.18, ankle * 0.26, len * 1.02, 0.08), M2.taban, 0, -ankle * 0.94, zc));
  return { g, ankle, fwdLen, w, on };
}

/** Savaş çizmesi: yüksek konç, altın burun bandı, baldır zırhı. */
function bootCizme(c) {
  const { M, dim, fwd } = c;
  const out = {};
  for (const side of ['L', 'R']) {
    const { g, ankle, fwdLen, w, on } = footShell(c, { ust: M.leather, taban: M.dark });
    // Bileğin üstüne çıkan konç
    g.add(mesh(new THREE.CylinderGeometry(w * 0.66, w * 0.58, ankle * 1.10, 10),
      M.leather, 0, ankle * 0.50, 0));
    g.add(mesh(new THREE.TorusGeometry(w * 0.68, ankle * 0.10, 6, 12).rotateX(Math.PI / 2),
      M.gold, 0, ankle * 1.02, 0));
    // Burun bandı
    g.add(mesh(plate(w * 1.06, ankle * 0.30, fwdLen * 0.22, 0.2), M.gold,
      0, -ankle * 0.52, fwd * on * 0.86));
    out['boot' + side] = { piece: g, joint: 'foot' + side, offset: [0, 0, 0] };

    // Dizlik + baldır zırhı
    const k = new THREE.Group();
    const len = dim.shin;
    const r = len * 0.20;
    const knee = dome(r * 1.15, 0.62, 10);
    k.add(mesh(knee, M.plate, 0, 0, fwd * r * 0.20));
    // Dizden bileğe: eskisi baldırın ortasında bitip çıplak bir aralık bırakıyordu
    k.add(mesh(plate(r * 1.45, len * 0.78, r * 1.40, 0.2), M.plain, 0, -len * 0.50, fwd * r * 0.05));
    k.add(mesh(plate(r * 1.52, len * 0.07, r * 1.46, 0.2), M.gold, 0, -len * 0.86, 0));
    out['greave' + side] = { piece: k, joint: 'shin' + side, offset: [0, -len * 0.06, 0] };
  }
  return out;
}

/** Hafif ayakkabı: alçak, bez sargılı, baldır zırhı yok. */
function bootHafif(c) {
  const { M, fwd } = c;
  const out = {};
  for (const side of ['L', 'R']) {
    const { g, ankle, fwdLen, w, on } = footShell(c, { ust: M.leatherLight, taban: M.dark },
      { yukseklik: 0.86 });
    // Bilek sargısı
    g.add(mesh(new THREE.TorusGeometry(w * 0.56, ankle * 0.16, 6, 12).rotateX(Math.PI / 2),
      M.cloth, 0, ankle * 0.14, 0));
    g.add(mesh(plate(w * 0.98, ankle * 0.22, fwdLen * 0.18, 0.25), M.leather,
      0, -ankle * 0.50, fwd * on * 0.84));
    out['boot' + side] = { piece: g, joint: 'foot' + side, offset: [0, 0, 0] };
  }
  return out;
}

/* ==================================================================== */
/*  Miğfer (helmet) görünümleri                                         */
/* ==================================================================== */

const helmetY = (dim) => (dim.headHeight ?? dim.head * 2) * 0.60;

/**
 * Demir miğfer: açık yüzlü kubbe, yanaklıklar, tepelik.
 *
 * Kapalı miğfer kafayı bütünüyle yutup karakteri yüzsüz bırakıyordu; bu
 * yüzden kubbe yalnızca kafanın üstünü örtüyor.
 */
function helmetDemir(c) {
  const { M, dim, fwd } = c;
  const g = new THREE.Group();
  const r = dim.head;
  const cap = dome(r * 1.08, 0.42, 16);
  cap.scale(1, 1.05, 1);
  g.add(mesh(cap, M.plate, 0, r * 0.10, 0));
  g.add(mesh(new THREE.CylinderGeometry(r * 1.10, r * 1.10, r * 0.30, 16), M.gold, 0, r * 0.16, 0));
  for (const s of [-1, 1]) {
    g.add(mesh(plate(r * 0.24, r * 0.85, r * 0.70, 0.25), M.plate,
      s * r * 0.98, -r * 0.30, fwd * r * 0.10));
  }
  g.add(mesh(plate(r * 1.5, r * 0.60, r * 0.22, 0.25), M.plate, 0, -r * 0.25, -fwd * r * 1.00));
  g.add(mesh(new THREE.ConeGeometry(r * 0.17, r * 0.75, 6), M.gold, 0, r * 0.95, 0));
  return { helmet: { piece: g, joint: 'head', offset: [0, helmetY(dim), 0] } };
}

/** Tüylü başlık: geniş siperlik + arkaya sarkan tüy sorgucu. */
function helmetTuylu(c) {
  const { M, dim, fwd } = c;
  const g = new THREE.Group();
  const r = dim.head;
  const cap = dome(r * 1.02, 0.50, 14);
  g.add(mesh(cap, M.leather, 0, r * 0.06, 0));
  // Geniş siperlik
  const brim = new THREE.CylinderGeometry(r * 1.55, r * 1.62, r * 0.12, 18);
  g.add(mesh(brim, M.leatherLight, 0, r * 0.02, 0));
  g.add(mesh(new THREE.TorusGeometry(r * 1.06, r * 0.09, 6, 16).rotateX(Math.PI / 2),
    M.gold, 0, r * 0.14, 0));
  // Sorguç: arkaya doğru açılan üç tüy
  for (let i = 0; i < 3; i++) {
    const t = plate(r * 0.14, r * 1.5, r * 0.06, 0.4);
    t.rotateX(fwd * (0.5 + i * 0.16));
    g.add(mesh(t, M.cloth, (i - 1) * r * 0.22, r * 0.95, -fwd * r * 0.45));
  }
  g.add(mesh(new THREE.SphereGeometry(r * 0.20, 8, 6), M.gold, 0, r * 0.68, 0));
  return { helmet: { piece: g, joint: 'head', offset: [0, helmetY(dim), 0] } };
}

/** Savaş bandanası: alın bandı + arkaya sarkan iki uç. */
function helmetBandana(c) {
  const { M, dim, fwd } = c;
  const g = new THREE.Group();
  const r = dim.head;
  const band = new THREE.CylinderGeometry(r * 1.06, r * 1.06, r * 0.34, 16);
  g.add(mesh(band, M.cloth, 0, r * 0.24, 0));
  // Alın plakası
  g.add(mesh(plate(r * 0.70, r * 0.28, r * 0.14, 0.28), M.gold, 0, r * 0.26, fwd * r * 1.02));
  // Düğüm ve uçlar
  g.add(mesh(new THREE.SphereGeometry(r * 0.18, 8, 6), M.cloth, 0, r * 0.22, -fwd * r * 1.02));
  for (const s of [-1, 1]) {
    const t = plate(r * 0.16, r * 1.15, r * 0.05, 0.35);
    t.rotateZ(s * 0.20);
    t.rotateX(-fwd * 0.25);
    g.add(mesh(t, M.cloth, s * r * 0.20, -r * 0.35, -fwd * r * 1.02));
  }
  return { helmet: { piece: g, joint: 'head', offset: [0, helmetY(dim), 0] } };
}

/* ==================================================================== */
/*  Pelerin (cape) görünümleri                                          */
/* ==================================================================== */

/**
 * Sancak pelerini: omuzlarda dar başlayıp aşağı açılan, bel hizasında
 * biten kısa pelerin. (Gövde genişliğinde uzun bir levha siluetin
 * tamamını yutuyordu.)
 */
function capeSancak(c) {
  const { M, dim, sw, fwd, neckY, chestY } = c;
  const g = new THREE.Group();
  const wTop = sw * 0.62, wBot = sw * 0.92, h = dim.torso * 0.78;
  const geo = new THREE.BufferGeometry();
  const hw1 = wTop / 2, hw2 = wBot / 2, bulge = h * 0.10;
  geo.setAttribute('position', new THREE.Float32BufferAttribute([
    -hw1, 0, 0, hw1, 0, 0,
    -hw2, -h, -bulge, hw2, -h, -bulge,
  ], 3));
  geo.setIndex([0, 2, 1, 1, 2, 3]);
  geo.computeVertexNormals();
  g.add(mesh(geo, new THREE.MeshLambertMaterial({ color: M.cloth.color, side: THREE.DoubleSide })));
  g.add(mesh(plate(wTop * 1.1, h * 0.07, sw * 0.10, 0.25), M.gold, 0, 0, 0));
  const e = c.eksen('chest');
  return { cape: { piece: g, joint: 'chest',
    offset: [e[0], (neckY - chestY) * 0.78, e[2] - fwd * sw * 0.30] } };
}

/** Kürk pelerin: omuzlarda kalın kürk yakalık + kısa bez etek. */
function capeKurk(c) {
  const { M, dim, sw, fwd, neckY, chestY } = c;
  const g = new THREE.Group();
  const w = sw * 1.05, h = dim.torso * 0.52;
  // Kürk rulo: omuzdan omuza uzanan yatık silindir
  const roll = new THREE.CylinderGeometry(sw * 0.17, sw * 0.17, w, 10).rotateZ(Math.PI / 2);
  g.add(mesh(roll, M.fur, 0, 0, fwd * sw * 0.10));
  for (const s of [-1, 1]) {
    g.add(mesh(new THREE.SphereGeometry(sw * 0.19, 10, 8), M.fur, s * w * 0.5, 0, fwd * sw * 0.10));
  }
  // Kısa bez etek
  const geo = new THREE.BufferGeometry();
  const hw1 = w * 0.42, hw2 = w * 0.56;
  geo.setAttribute('position', new THREE.Float32BufferAttribute([
    -hw1, 0, 0, hw1, 0, 0,
    -hw2, -h, -h * 0.14, hw2, -h, -h * 0.14,
  ], 3));
  geo.setIndex([0, 2, 1, 1, 2, 3]);
  geo.computeVertexNormals();
  g.add(mesh(geo, new THREE.MeshLambertMaterial({ color: M.dark.color, side: THREE.DoubleSide }),
    0, -sw * 0.10, 0));
  // Göğüste toka
  g.add(mesh(new THREE.SphereGeometry(sw * 0.09, 8, 6), M.gold, 0, 0, fwd * sw * 0.28));
  const e = c.eksen('chest');
  return { cape: { piece: g, joint: 'chest',
    offset: [e[0], (neckY - chestY) * 0.82, e[2] - fwd * sw * 0.26] } };
}

/* ==================================================================== */
/*  Kalkan (shield) görünümleri — sol elde                              */
/* ==================================================================== */

/** Yuvarlak kalkan: bombeli disk, göbek çıkıntısı ve perçinler. */
function shieldYuvarlak(c) {
  const { M, dim, fwd } = c;
  const g = new THREE.Group();
  const r = dim.foreArm * 1.32;
  const disc = new THREE.CylinderGeometry(r, r * 0.96, r * 0.14, 20).rotateX(Math.PI / 2);
  g.add(mesh(disc, M.wood, 0, 0, 0));
  g.add(mesh(new THREE.TorusGeometry(r * 0.98, r * 0.07, 6, 20), M.gold, 0, 0, 0));
  const boss = dome(r * 0.28, 0.5, 12).rotateX(fwd * Math.PI / 2);
  g.add(mesh(boss, M.steel, 0, 0, fwd * r * 0.10));
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * Math.PI * 2;
    g.add(mesh(new THREE.SphereGeometry(r * 0.055, 6, 5), M.metal,
      Math.cos(a) * r * 0.66, Math.sin(a) * r * 0.66, fwd * r * 0.08));
  }
  // Kalkan ele değil kolun dışına gelsin
  return { shield: { piece: g, joint: 'handL', offset: [-r * 0.18, -r * 0.30, 0] } };
}

/** Kule kalkanı: uzun dikdörtgen plaka, dikey omurga. */
function shieldKule(c) {
  const { M, dim, fwd } = c;
  const g = new THREE.Group();
  const w = dim.foreArm * 2.0;
  const h = dim.foreArm * 3.4;
  g.add(mesh(plate(w, h, w * 0.10, 0.10), M.plate, 0, 0, 0));
  // Alt uç sivrilsin
  const tip = new THREE.ConeGeometry(w * 0.50, h * 0.26, 4).rotateY(Math.PI / 4);
  tip.scale(1, -1, 0.20);
  g.add(mesh(tip, M.plate, 0, -h * 0.62, 0));
  g.add(mesh(plate(w * 0.16, h * 0.94, w * 0.16, 0.2), M.gold, 0, 0, fwd * w * 0.07));
  g.add(mesh(plate(w * 0.96, h * 0.08, w * 0.13, 0.2), M.gold, 0, h * 0.40, 0));
  g.add(mesh(plate(w * 0.96, h * 0.08, w * 0.13, 0.2), M.gold, 0, -h * 0.34, 0));
  return { shield: { piece: g, joint: 'handL', offset: [-w * 0.22, -h * 0.18, 0] } };
}

/* ==================================================================== */
/*  Silah (sword) görünümleri — sağ elde                                */
/* ==================================================================== */

/**
 * Kabza + iz düğümlerini ekler.
 * Kılıç izi (SwordTrail) namlunun iki ucunu izliyor.
 */
function weaponNodes(g, grip, uzunluk) {
  const base = new THREE.Object3D();
  base.position.set(0, grip, 0);
  const tip = new THREE.Object3D();
  tip.position.set(0, grip + uzunluk, 0);
  g.add(base, tip);
  g.userData.weaponBase = base;
  g.userData.weaponTip = tip;
}

/** Tek el çelik kılıç: kısa kabza, düz namlu. */
function weaponKilic(c) {
  const { M, dim } = c;
  const u = dim.foreArm;
  const g = new THREE.Group();
  const grip = u * 0.95;
  const blade = u * 2.5;
  g.add(mesh(new THREE.CylinderGeometry(u * 0.12, u * 0.14, grip, 8), M.leather, 0, grip * 0.5, 0));
  g.add(mesh(new THREE.SphereGeometry(u * 0.18, 10, 8), M.gold, 0, 0, 0));
  g.add(mesh(plate(u * 1.05, u * 0.20, u * 0.28, 0.25), M.gold, 0, grip, 0));
  const bl = plate(u * 0.44, blade, u * 0.12, 0.06);
  bl.translate(0, grip + blade * 0.5 + u * 0.12, 0);
  g.add(mesh(bl, M.steel));
  const tip = new THREE.ConeGeometry(u * 0.30, u * 0.70, 4).rotateY(Math.PI / 4);
  tip.scale(1, 1, 0.4);
  tip.translate(0, grip + blade + u * 0.45, 0);
  g.add(mesh(tip, M.steel));
  weaponNodes(g, grip + u * 0.2, blade + u * 0.6);
  return { sword: { piece: g, joint: 'handR', offset: [0, -grip * 0.42, 0] } };
}

/** Çift el kılıcı: uzun kabza, geniş namlu, kan oluğu. */
function weaponBuyuk(c) {
  const { M, dim } = c;
  const u = dim.foreArm;
  const g = new THREE.Group();
  const grip = u * 1.30;
  const blade = u * 3.6;
  g.add(mesh(new THREE.CylinderGeometry(u * 0.13, u * 0.15, grip, 8), M.leather, 0, grip * 0.5, 0));
  for (let i = 0; i < 4; i++) {
    g.add(mesh(new THREE.TorusGeometry(u * 0.15, u * 0.04, 5, 10).rotateX(Math.PI / 2),
      M.gold, 0, grip * (0.20 + i * 0.19), 0));
  }
  g.add(mesh(new THREE.SphereGeometry(u * 0.21, 10, 8), M.gold, 0, 0, 0));
  g.add(mesh(plate(u * 1.55, u * 0.24, u * 0.32, 0.25), M.gold, 0, grip, 0));
  for (const s of [-1, 1]) {
    g.add(mesh(plate(u * 0.26, u * 0.52, u * 0.26, 0.3), M.gold, s * u * 0.68, grip + u * 0.20, 0));
  }
  const bl = plate(u * 0.58, blade, u * 0.14, 0.06);
  bl.translate(0, grip + blade * 0.5 + u * 0.16, 0);
  g.add(mesh(bl, M.steel));
  const fuller = plate(u * 0.16, blade * 0.9, u * 0.18);
  fuller.translate(0, grip + blade * 0.48 + u * 0.16, 0);
  g.add(mesh(fuller, M.metal));
  const tip = new THREE.ConeGeometry(u * 0.38, u * 0.95, 4).rotateY(Math.PI / 4);
  tip.scale(1, 1, 0.4);
  tip.translate(0, grip + blade + u * 0.60, 0);
  g.add(mesh(tip, M.steel));
  weaponNodes(g, grip + u * 0.2, blade + u * 0.65);
  return { sword: { piece: g, joint: 'handR', offset: [0, -grip * 0.42, 0] } };
}

/** Savaş baltası: ahşap sap, tek yüzlü ağız ve arkada sivri. */
function weaponBalta(c) {
  const { M, dim, fwd } = c;
  const u = dim.foreArm;
  const g = new THREE.Group();
  const sap = u * 3.2;
  g.add(mesh(new THREE.CylinderGeometry(u * 0.11, u * 0.13, sap, 8), M.wood, 0, sap * 0.5, 0));
  for (let i = 0; i < 3; i++) {
    g.add(mesh(new THREE.TorusGeometry(u * 0.13, u * 0.035, 5, 10).rotateX(Math.PI / 2),
      M.leather, 0, sap * (0.10 + i * 0.10), 0));
  }
  g.add(mesh(new THREE.SphereGeometry(u * 0.16, 8, 6), M.metal, 0, 0, 0));
  // Balta ağzı: sapın üst ucunda, ileriye bakan yarım ay
  const head = new THREE.Group();
  head.position.set(0, sap * 0.90, 0);
  const yz = new THREE.CylinderGeometry(u * 0.95, u * 0.95, u * 0.16, 16, 1, false,
    -Math.PI * 0.42, Math.PI * 0.84).rotateX(Math.PI / 2);
  const agiz = mesh(yz, M.steel, 0, 0, 0);
  agiz.rotation.y = fwd > 0 ? Math.PI / 2 : -Math.PI / 2;
  head.add(agiz);
  head.add(mesh(plate(u * 0.42, u * 0.85, u * 0.30, 0.15), M.metal, 0, 0, 0));
  // Arka sivri
  const spike = new THREE.ConeGeometry(u * 0.16, u * 0.70, 5).rotateZ(fwd > 0 ? Math.PI / 2 : -Math.PI / 2);
  head.add(mesh(spike, M.steel, 0, 0, -fwd * u * 0.55));
  g.add(head);
  // Sapın tepesindeki sivri uç
  g.add(mesh(new THREE.ConeGeometry(u * 0.12, u * 0.55, 6), M.steel, 0, sap + u * 0.25, 0));
  weaponNodes(g, sap * 0.70, sap * 0.40);
  return { sword: { piece: g, joint: 'handR', offset: [0, -sap * 0.30, 0] } };
}

/** Ejder mızrağı: uzun sap, yaprak uç, püskül. */
function weaponMizrak(c) {
  const { M, dim } = c;
  const u = dim.foreArm;
  const g = new THREE.Group();
  const sap = u * 5.0;
  g.add(mesh(new THREE.CylinderGeometry(u * 0.09, u * 0.10, sap, 8), M.wood, 0, sap * 0.5, 0));
  g.add(mesh(new THREE.CylinderGeometry(u * 0.13, u * 0.13, u * 0.55, 8), M.leather, 0, sap * 0.42, 0));
  g.add(mesh(new THREE.ConeGeometry(u * 0.13, u * 0.40, 6), M.metal, 0, -u * 0.18, 0));
  // Yaprak uç
  const uc = plate(u * 0.34, u * 1.25, u * 0.12, 0.55);
  uc.translate(0, sap + u * 0.55, 0);
  g.add(mesh(uc, M.steel));
  const sivri = new THREE.ConeGeometry(u * 0.17, u * 0.62, 4).rotateY(Math.PI / 4);
  sivri.scale(1, 1, 0.45);
  sivri.translate(0, sap + u * 1.45, 0);
  g.add(mesh(sivri, M.steel));
  // Boyunluk ve püskül
  g.add(mesh(new THREE.CylinderGeometry(u * 0.17, u * 0.14, u * 0.28, 8), M.gold, 0, sap - u * 0.05, 0));
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2;
    g.add(mesh(plate(u * 0.06, u * 0.55, u * 0.06, 0.3), M.cloth,
      Math.cos(a) * u * 0.12, sap - u * 0.42, Math.sin(a) * u * 0.12));
  }
  weaponNodes(g, sap * 0.55, sap * 0.50);
  return { sword: { piece: g, joint: 'handR', offset: [0, -sap * 0.34, 0] } };
}

/* ==================================================================== */
/*  Görünüm tabloları                                                   */
/* ==================================================================== */

/**
 * Grup -> görünüm adı -> üreteç.
 * Her grubun ilk girdisi o grubun varsayılanı.
 */
export const GORUNUMLER = {
  chest:  { lamel: chestLamel, pul: chestPul, deri: chestDeri },
  bracer: { plaka: bracerPlaka, deri: bracerDeri },
  boot:   { cizme: bootCizme, hafif: bootHafif },
  helmet: { demir: helmetDemir, tuylu: helmetTuylu, bandana: helmetBandana },
  cape:   { sancak: capeSancak, kurk: capeKurk },
  shield: { yuvarlak: shieldYuvarlak, kule: shieldKule },
  sword:  { buyuk: weaponBuyuk, kilic: weaponKilic, balta: weaponBalta, mizrak: weaponMizrak },
};

/** Bir grubun bir slota kaç parça koyabileceği — sökerken hepsi geziliyor. */
export const GRUP_SLOTLARI = {
  chest: ['chest', 'tassets', 'pauldronL', 'pauldronR', 'thighGuardL', 'thighGuardR'],
  bracer: ['bracerL', 'bracerR', 'sleeveL', 'sleeveR'],
  boot: ['bootL', 'bootR', 'greaveL', 'greaveR'],
  helmet: ['helmet'],
  cape: ['cape'],
  shield: ['shield'],
  sword: ['sword'],
};

/**
 * Tek bir görünüm grubunun parçalarını üretir.
 *
 * @param {string} grup     GORUNUMLER anahtarı
 * @param {?string} gorunum görünüm adı (yoksa grubun varsayılanı)
 * @param {object} ctx      makeContext çıktısı
 * @returns {Object<string, {piece: THREE.Object3D, joint: string, offset: number[]}>}
 */
export function buildVisual(grup, gorunum, ctx) {
  const tablo = GORUNUMLER[grup];
  if (!tablo) return {};
  const f = tablo[gorunum] || Object.values(tablo)[0];
  return f(ctx) || {};
}

/**
 * Bütün grupların varsayılan görünümüyle tam set — NPC ve önizleme için.
 *
 * @param {object} theme kingdom.armor
 * @param {object} dim   kemiklerden ölçülen boyutlar
 * @param {object} pos   eklemlerin dinlenme konumları
 */
export function buildArmorSet(theme, dim, pos) {
  const ctx = makeContext(theme, dim, pos, null);
  const out = {};
  // Kalkan varsayılan sette yok: iki elli silahla birlikte durması saçma olur
  for (const grup of ['chest', 'bracer', 'boot', 'helmet', 'cape', 'sword']) {
    Object.assign(out, buildVisual(grup, null, ctx));
  }
  return out;
}

/**
 * Yerde duran eşya için küçültülmüş model.
 *
 * Metin2'de yere düşen eşya kendi modeliyle görünür. Burada da öyle: eşyanın
 * görünüm üreteci küçük, sabit bir iskelet ölçüsüyle çalıştırılıp sonuç
 * kutusuna göre ölçekleniyor.
 *
 * @param {object} item  eşya örneği
 * @param {object} theme kingdom.armor
 * @param {number} boy   hedef en büyük kenar uzunluğu
 * @returns {?THREE.Object3D}
 */
export const DROP_THEME = { base: 0x9aa3ad, trim: 0xd9b45a, cloth: 0x8e3a3a };

export function buildDropModel(item, theme = DROP_THEME, boy = 0.42) {
  const grup = item.grup;
  if (!grup || !GORUNUMLER[grup]) return null;
  // Yerdeki model gerçek karakterden bağımsız: sabit, orantılı bir ölçü seti
  const dim = {
    forward: 1, torso: 0.60, head: 0.11, headHeight: 0.24,
    upperArm: 0.28, foreArm: 0.24, thigh: 0.40, shin: 0.42,
    footAnkle: 0.09, footFwd: 0.13, shoulderWidth: 0.36, hipWidth: 0.20,
  };
  const pos = {
    hips: { y: 0.95 }, chest: { y: 1.30 }, neck: { y: 1.52 }, head: { y: 1.60 },
  };
  const ctx = makeContext(theme, dim, pos, item.kademe);
  const parts = buildVisual(grup, item.gorunum, ctx);
  // Ana parça: grubun listesindeki ilk mevcut slot
  const sira = GRUP_SLOTLARI[grup] || Object.keys(parts);
  const ana = sira.map((s) => parts[s]).find(Boolean);
  if (!ana) return null;

  const g = new THREE.Group();
  ana.piece.position.set(0, 0, 0);
  ana.piece.quaternion.identity();
  g.add(ana.piece);
  // Kutunun merkezini orijine al, en büyük kenarı `boy` yap
  const kutu = new THREE.Box3().setFromObject(g);
  const olcu = kutu.getSize(new THREE.Vector3());
  const enBuyuk = Math.max(olcu.x, olcu.y, olcu.z) || 1;
  const merkez = kutu.getCenter(new THREE.Vector3());
  ana.piece.position.sub(merkez);
  g.scale.setScalar(boy / enBuyuk);
  return g;
}

/**
 * Yüz ve saç.
 *
 * Taban gövde dokusuz ve yüzsüz geliyor: kafa düz bir yumurta olarak
 * kalıyor ve karakter bu yüzden cansız görünüyor. Gözler, kaşlar ve saç
 * ayrı parçalar olarak kafa kemiğine takılıyor.
 *
 * @param {object} dim  kemiklerden ölçülen boyutlar
 * @param {object} [o]  { hair, skin }
 */
export function buildFace(dim, o = {}) {
  const hairColor = o.hair ?? 0x2b2018;
  const fwd = dim.forward ?? 1;             // modelin ileri yönü
  const g = new THREE.Group();
  const r = dim.head;                       // kafa yarıçapı
  const cy = (dim.headHeight ?? r * 2) * 0.50;   // kafa merkezi, kemiğe göre

  const hairMat = new THREE.MeshLambertMaterial({ color: hairColor });
  const eyeMat = new THREE.MeshLambertMaterial({ color: 0x241a12 });
  const whiteMat = new THREE.MeshLambertMaterial({ color: 0xe8e2d6 });

  // Saç: kafanın arkasını ve üstünü örten kabuk
  const cap = new THREE.SphereGeometry(r * 1.06, 14, 12, 0, Math.PI * 2, 0, Math.PI * 0.58);
  cap.scale(1, 1.02, 1.04);
  g.add(mesh(cap, hairMat, 0, cy + r * 0.10, 0));
  // Enseye inen saç kütlesi
  g.add(mesh(plate(r * 1.15, r * 1.05, r * 0.70, 0.30), hairMat, 0, cy - r * 0.10, -fwd * r * 0.62));
  // Topuz (Metin2 savaşçısının imzası)
  g.add(mesh(new THREE.SphereGeometry(r * 0.42, 10, 8), hairMat, 0, cy + r * 1.16, -fwd * r * 0.24));
  g.add(mesh(new THREE.CylinderGeometry(r * 0.14, r * 0.14, r * 0.55, 6),
    new THREE.MeshLambertMaterial({ color: 0x8a6f31 }), 0, cy + r * 0.92, -fwd * r * 0.22));

  // Gözler: beyaz + koyu bebek
  for (const s of [-1, 1]) {
    const eye = new THREE.SphereGeometry(r * 0.17, 8, 7);
    eye.scale(1, 0.72, 0.55);
    g.add(mesh(eye, whiteMat, s * r * 0.36, cy + r * 0.06, fwd * r * 0.86));
    g.add(mesh(new THREE.SphereGeometry(r * 0.085, 7, 6), eyeMat,
      s * r * 0.38, cy + r * 0.05, fwd * r * 0.94));
    // Kaş
    const brow = plate(r * 0.40, r * 0.09, r * 0.12, 0.3);
    brow.rotateZ(s * -0.16);
    g.add(mesh(brow, hairMat, s * r * 0.37, cy + r * 0.33, fwd * r * 0.88));
  }
  // Ağız çizgisi
  g.add(mesh(plate(r * 0.38, r * 0.055, r * 0.10, 0.3),
    new THREE.MeshLambertMaterial({ color: 0x8a5a48 }), 0, cy - r * 0.45, fwd * r * 0.86));

  return { piece: g, joint: 'head', offset: [0, 0, 0] };
}

/** Zırh slotu -> takılacağı eklem (varsayılan eşleme). */
export const ARMOR_SLOTS = {
  chest: 'chest',
  pauldronL: 'armL', pauldronR: 'armR',
  sleeveL: 'armL', sleeveR: 'armR',
  bracerL: 'foreArmL', bracerR: 'foreArmR',
  thighGuardL: 'thighL', thighGuardR: 'thighR',
  greaveL: 'shinL', greaveR: 'shinR',
  bootL: 'footL', bootR: 'footR',
  tassets: 'hips',
  helmet: 'head',
  cape: 'chest',
  shield: 'handL',
  sword: 'handR',
};
