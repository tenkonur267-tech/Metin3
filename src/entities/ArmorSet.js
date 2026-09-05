/**
 * ArmorSet.js — Kemiklere takılan zırh parçaları.
 *
 * Parçalar prosedürel üretiliyor ve krallık paletine göre boyanıyor, böylece
 * aynı çıplak taban gövde üç krallıkta da farklı görünüyor. Her parça
 * bağımsız: takılıp çıkarılabiliyor, ileride envanterden gelen ekipmana
 * bağlanabilir.
 *
 * Tasarım uzayı
 * -------------
 * Parçalar **karakter uzayında** yazılıyor: Y yukarı, Z ileri, X sağ.
 * Kemiklerin yerel eksen düzeni rigden rige değiştiği için (kimi rigde kemik
 * +Y boyunca uzar, kimi rigde eksenler döner) parçayı kemiğin yerel
 * eksenlerine göre yazmak taşınabilir değil. RiggedCharacter.equip parçayı
 * kemiğin dinlenme yönelimiyle ters döndürerek takıyor; burada yalnızca
 * "yukarı", "ileri" ve "sağ" düşünmek yeterli.
 *
 * Konumlar kemiğin dinlenme noktasına göre, karakter uzayında veriliyor.
 */
import * as THREE from 'three';
import { getTexture } from '../core/Textures.js';

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

/** Krallık paletinden materyal seti. */
export function makeArmorMaterials(theme) {
  const L = (o) => new THREE.MeshLambertMaterial(o);
  const base = new THREE.Color(theme.base);
  return {
    /*
     * Lamel dokusu güçlü yatay çizgiler taşıyor. Her parçaya uygulandığında
     * karakter "çizgili silindirler yığını" gibi okunuyordu; bu yüzden doku
     * yalnızca geniş gövde yüzeylerinde kullanılıyor, geri kalan parçalar
     * düz renk + altın kenarlıkla çözülüyor.
     */
    lamellar: L({ map: getTexture('armor', { a: theme.base, b: theme.trim }) }),
    plate: L({ color: base.clone().multiplyScalar(1.05) }),
    plain: L({ color: base.clone().multiplyScalar(1.22) }),
    dark: L({ color: base.clone().multiplyScalar(0.62) }),
    gold: L({ color: new THREE.Color(theme.trim) }),
    cloth: L({ color: theme.cloth }),
    leather: L({ color: 0x4a3222 }),
    steel: L({ color: 0xc9d0d8 }),
    metal: L({ color: 0x8f98a3 }),
  };
}

/**
 * Zırh parçalarını üretir.
 *
 * @param {object} theme kingdom.armor
 * @param {object} dim   kemiklerden ölçülen boyutlar
 * @param {object} pos   eklemlerin dinlenme konumları (karakter uzayı)
 * @returns {Object<string, {piece: THREE.Object3D, joint: string, offset: number[]}>}
 */
export function buildArmorSet(theme, dim, pos) {
  const M = makeArmorMaterials(theme);
  const out = {};
  const add = (slot, joint, piece, offset) => { out[slot] = { piece, joint, offset }; };

  const yOf = (j) => (pos && pos[j] ? pos[j].y : 0);
  const hipsY = yOf('hips');
  const chestY = yOf('chest');
  const neckY = yOf('neck');
  const headY = yOf('head');
  const sw = dim.shoulderWidth;
  const fwd = dim.forward ?? 1;    // modelin ileri yönü (+1 ya da -1)

  /* ---- Göğüslük: bel ile omuz arasını örter ---- */
  {
    const g = new THREE.Group();
    const top = neckY - dim.torso * 0.06;
    const bottom = hipsY + dim.torso * 0.12;
    const h = Math.max(top - bottom, dim.torso * 0.4);
    const w = sw * 1.18;
    const d = sw * 0.74;
    const midY = (top + bottom) / 2 - chestY;      // göğüs kemiğine göre

    // Gövde iki kademeli: göğüs geniş, bel dar — düz kutu yerine siluet
    const upper = plate(w, h * 0.56, d, 0.20);
    upper.translate(0, midY + h * 0.22, 0);
    g.add(mesh(upper, M.plate));
    const lower = plate(w * 0.82, h * 0.48, d * 0.88, 0.24);
    lower.translate(0, midY - h * 0.24, 0);
    g.add(mesh(lower, M.plate));
    // Göğüs kabartısı
    const bulge = new THREE.SphereGeometry(w * 0.30, 12, 8);
    bulge.scale(1.35, 0.85, 0.55);
    bulge.translate(0, midY + h * 0.24, fwd * d * 0.40);
    g.add(mesh(bulge, M.plain));
    // Yalnızca karın bölgesinde lamel bandı
    g.add(mesh(plate(w * 0.80, h * 0.26, d * 0.90, 0.18), M.lamellar, 0, midY - h * 0.30, 0));
    // Altın kenarlıklar
    g.add(mesh(plate(w * 1.02, h * 0.06, d * 1.03, 0.2), M.gold, 0, midY + h * 0.50, 0));
    g.add(mesh(plate(w * 0.84, h * 0.05, d * 0.92, 0.2), M.gold, 0, midY - h * 0.47, 0));
    // Boyunluk
    g.add(mesh(new THREE.TorusGeometry(w * 0.24, w * 0.05, 6, 16).rotateX(Math.PI / 2),
      M.gold, 0, midY + h * 0.52, 0));
    add('chest', 'chest', g, [0, 0, 0]);
  }

  /* ---- Omuzluklar: omuz ekleminin üstünde kubbe ---- */
  for (const side of ['L', 'R']) {
    const s = side === 'L' ? -1 : 1;
    const g = new THREE.Group();
    const r = sw * 0.32;
    const cap = dome(r, 0.60, 14);
    cap.scale(1.02, 0.88, 1.06);
    g.add(mesh(cap, M.plate, 0, 0, 0));
    const skirt = dome(r * 0.92, 0.46, 14);
    skirt.scale(1.10, 0.46, 1.12);
    g.add(mesh(skirt, M.plain, 0, -r * 0.30, 0));
    g.add(mesh(new THREE.TorusGeometry(r * 0.90, r * 0.085, 6, 16).rotateX(Math.PI / 2),
      M.gold, 0, -r * 0.04, 0));
    // Omuz ekleminin üstüne ve hafifçe dışına: gövdeye gömülmesin
    add('pauldron' + side, 'arm' + side, g, [s * r * 0.16, r * 0.30, 0]);
  }

  /* ---- Üst kol zırhı: omuzluk ile kolluk arasını kapatır ---- */
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    const len = dim.upperArm;
    const r = len * 0.20;
    g.add(mesh(new THREE.CylinderGeometry(r * 0.98, r * 0.86, len * 0.62, 10), M.plate, 0, 0, 0));
    g.add(mesh(new THREE.TorusGeometry(r * 0.92, r * 0.13, 6, 14).rotateX(Math.PI / 2),
      M.gold, 0, -len * 0.30, 0));
    add('sleeve' + side, 'arm' + side, g, [0, -len * 0.42, 0]);
  }

  /* ---- Kolluklar: ön kolun bilek yarısını sarar ---- */
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    const len = dim.foreArm;
    const r = len * 0.28;
    const c = new THREE.CylinderGeometry(r, r * 0.84, len * 0.60, 10);
    g.add(mesh(c, M.plate, 0, 0, 0));
    g.add(mesh(new THREE.TorusGeometry(r * 0.90, r * 0.15, 6, 14).rotateX(Math.PI / 2),
      M.gold, 0, -len * 0.28, 0));
    // Kol aşağı sarktığında dirsekten bileğe doğru: karakter uzayında aşağı
    add('bracer' + side, 'foreArm' + side, g, [0, -len * 0.52, 0]);
  }

  /* ---- Uyluk zırhı ---- */
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    const len = dim.thigh;
    const r = len * 0.26;
    g.add(mesh(plate(r * 1.8, len * 0.46, r * 1.7, 0.22), M.plain, 0, 0, 0));
    g.add(mesh(plate(r * 1.9, len * 0.08, r * 1.8, 0.2), M.gold, 0, -len * 0.22, 0));
    add('thighGuard' + side, 'thigh' + side, g, [0, -len * 0.42, 0]);
  }

  /* ---- Dizlik + baldır zırhı ---- */
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    const len = dim.shin;
    const r = len * 0.20;
    const knee = dome(r * 1.15, 0.62, 10);
    g.add(mesh(knee, M.plate, 0, 0, fwd * r * 0.20));
    g.add(mesh(plate(r * 1.75, len * 0.52, r * 1.6, 0.2), M.plain, 0, -len * 0.34, fwd * r * 0.05));
    add('greave' + side, 'shin' + side, g, [0, -len * 0.06, 0]);
  }

  /* ---- Çizmeler ---- */
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    // Ölçüler ayak kemiğinden: bilek yüksekliği ve bilekten parmağa mesafe.
    const ankle = dim.footAnkle ?? 0.09;      // bileğin yerden yüksekliği
    const fwdLen = dim.footFwd ?? 0.13;       // bilekten parmak ucuna
    const w = Math.max(dim.shin * 0.30, fwdLen * 0.78);
    const heel = fwdLen * 0.62;
    const len = fwdLen * 1.75 + heel;
    const zc = (fwdLen * 1.30 - heel) / 2;    // gövde merkezi

    // Ayağı yerden bileğe kadar tümüyle saran gövde
    g.add(mesh(plate(w * 1.20, ankle * 1.25, len, 0.12), M.leather, 0, ankle * 0.48, fwd * zc));
    // Taban
    g.add(mesh(plate(w * 1.26, ankle * 0.30, len * 1.04, 0.08), M.dark, 0, -ankle * 0.02, fwd * zc));
    // Bilek üstü konç
    g.add(mesh(new THREE.CylinderGeometry(w * 0.62, w * 0.55, ankle * 0.95, 8),
      M.leather, 0, ankle * 1.20, 0));
    // Altın burun bandı
    g.add(mesh(plate(w * 1.16, ankle * 0.30, fwdLen * 0.22, 0.2), M.gold,
      0, ankle * 0.55, fwd * fwdLen * 1.14));
    add('boot' + side, 'foot' + side, g, [0, 0, 0]);
  }

  /* ---- Zırh eteği: kalçadan aşağı dört panel ---- */
  {
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
    // Kemer, kalça kemiğinin biraz üstünde
    add('tassets', 'hips', g, [0, dim.torso * 0.06, 0]);
  }

  /* ---- Miğfer ---- */
  {
    const g = new THREE.Group();
    /*
     * Açık miğfer: kubbe yalnızca kafanın üstünü örtüyor, alın bandı ve yan
     * plakalar yüzü çerçeveliyor. Kapalı miğfer kafayı bütünüyle yutup
     * karakteri yüzsüz bırakıyordu.
     */
    const r = dim.head;
    const cap = dome(r * 1.08, 0.42, 16);
    cap.scale(1, 1.05, 1);
    g.add(mesh(cap, M.plate, 0, r * 0.10, 0));
    // Alın bandı
    g.add(mesh(new THREE.CylinderGeometry(r * 1.10, r * 1.10, r * 0.30, 16), M.gold, 0, r * 0.16, 0));
    // Yanaklık plakaları
    for (const s2 of [-1, 1]) {
      g.add(mesh(plate(r * 0.24, r * 0.85, r * 0.70, 0.25), M.plate,
        s2 * r * 0.98, -r * 0.30, fwd * r * 0.10));
    }
    // Ense koruması
    g.add(mesh(plate(r * 1.5, r * 0.60, r * 0.22, 0.25), M.plate, 0, -r * 0.25, -fwd * r * 1.00));
    // Tepelik
    g.add(mesh(new THREE.ConeGeometry(r * 0.17, r * 0.75, 6), M.gold, 0, r * 0.95, 0));
    /*
     * Kafa kemiği boynun tepesinde; kafa hacmi onun üstünde. Miğferi kemiğin
     * hemen üstüne koymak onu yüzün önüne indiriyordu, bu yüzden ölçülen kafa
     * yüksekliğinin ortasına yerleştiriliyor.
     */
    add('helmet', 'head', g, [0, (dim.headHeight ?? r * 2) * 0.60, 0]);
  }

  /* ---- Sırt pelerini ---- */
  {
    /*
     * Eskisi gövde genişliğinde, dize kadar inen düz bir levhaydı ve
     * siluetin tamamını yutuyordu. Yenisi omuzlarda dar başlayıp aşağı
     * doğru açılan, bel hizasında biten kısa bir pelerin.
     */
    const g = new THREE.Group();
    const wTop = sw * 0.62;
    const wBot = sw * 0.92;
    const h = dim.torso * 0.78;
    const geo = new THREE.BufferGeometry();
    const hw1 = wTop / 2, hw2 = wBot / 2;
    const bulge = h * 0.10;                 // hafif dışa kavis
    geo.setAttribute('position', new THREE.Float32BufferAttribute([
      -hw1, 0, 0, hw1, 0, 0,
      -hw2, -h, -bulge, hw2, -h, -bulge,
    ], 3));
    geo.setIndex([0, 2, 1, 1, 2, 3]);
    geo.computeVertexNormals();
    g.add(mesh(geo, new THREE.MeshLambertMaterial({
      color: M.cloth.color, side: THREE.DoubleSide,
    })));
    // Omuz bağlantısı
    g.add(mesh(plate(wTop * 1.1, h * 0.07, sw * 0.10, 0.25), M.gold, 0, 0, 0));
    add('cape', 'chest', g, [0, (neckY - chestY) * 0.78, -fwd * sw * 0.30]);
  }

  /* ---- Çift el kılıç: kabza avuçta, namlu yukarı ---- */
  {
    const g = new THREE.Group();
    const u = dim.foreArm;
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

    const base = new THREE.Object3D();
    base.position.set(0, grip + u * 0.2, 0);
    const tipNode = new THREE.Object3D();
    tipNode.position.set(0, grip + blade + u * 0.85, 0);
    g.add(base, tipNode);
    g.userData.weaponBase = base;
    g.userData.weaponTip = tipNode;

    // Avucun ortasında dursun; namlu karakter uzayında yukarı bakar
    add('sword', 'handR', g, [0, -grip * 0.42, 0]);
  }

  return out;
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
  sword: 'handR',
};
