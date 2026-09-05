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
  return {
    plate: L({ map: getTexture('armor', { a: theme.base, b: theme.trim }) }),
    plain: L({ color: new THREE.Color(theme.base).multiplyScalar(1.12) }),
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

  /* ---- Göğüslük: bel ile omuz arasını örter ---- */
  {
    const g = new THREE.Group();
    const top = neckY - dim.torso * 0.06;
    const bottom = hipsY + dim.torso * 0.12;
    const h = Math.max(top - bottom, dim.torso * 0.4);
    const w = sw * 1.18;
    const d = sw * 0.74;
    const midY = (top + bottom) / 2 - chestY;      // göğüs kemiğine göre

    g.add(mesh(plate(w, h, d, 0.18), M.plate, 0, midY, 0));
    // Göğüs plakası
    g.add(mesh(plate(w * 0.46, h * 0.36, d * 0.14, 0.3), M.gold, 0, midY + h * 0.22, d * 0.5));
    // Karın bandı
    g.add(mesh(plate(w * 0.92, h * 0.16, d * 1.02, 0.15), M.plain, 0, midY - h * 0.34, 0));
    // Boyunluk
    g.add(mesh(new THREE.TorusGeometry(w * 0.26, w * 0.055, 6, 16).rotateX(Math.PI / 2),
      M.gold, 0, midY + h * 0.52, 0));
    add('chest', 'chest', g, [0, 0, 0]);
  }

  /* ---- Omuzluklar: omuz ekleminin üstünde kubbe ---- */
  for (const side of ['L', 'R']) {
    const s = side === 'L' ? -1 : 1;
    const g = new THREE.Group();
    const r = sw * 0.38;
    const cap = dome(r, 0.62, 14);
    cap.scale(1.05, 0.95, 1.10);
    g.add(mesh(cap, M.plate, 0, 0, 0));
    const skirt = dome(r * 0.95, 0.5, 14);
    skirt.scale(1.14, 0.55, 1.16);
    g.add(mesh(skirt, M.plain, 0, -r * 0.34, 0));
    g.add(mesh(new THREE.TorusGeometry(r * 0.92, r * 0.11, 6, 16).rotateX(Math.PI / 2),
      M.gold, 0, -r * 0.06, 0));
    g.add(mesh(new THREE.ConeGeometry(r * 0.22, r * 0.7, 6), M.gold, s * r * 0.78, r * 0.34, 0));
    // Omuz ekleminin üstüne ve hafifçe dışına: gövdeye gömülmesin
    add('pauldron' + side, 'arm' + side, g, [s * r * 0.16, r * 0.30, 0]);
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
    g.add(mesh(knee, M.plate, 0, 0, r * 0.20));
    g.add(mesh(plate(r * 1.75, len * 0.52, r * 1.6, 0.2), M.plain, 0, -len * 0.34, r * 0.05));
    add('greave' + side, 'shin' + side, g, [0, -len * 0.06, 0]);
  }

  /* ---- Çizmeler: ayak ileriye (+Z) uzanır ---- */
  for (const side of ['L', 'R']) {
    const g = new THREE.Group();
    const f = Math.max(dim.foot, dim.shin * 0.24);
    g.add(mesh(plate(f * 1.0, f * 0.72, f * 2.0, 0.15), M.leather, 0, f * 0.30, f * 0.42));
    g.add(mesh(plate(f * 1.05, f * 0.26, f * 2.1, 0.1), M.gold, 0, f * 0.02, f * 0.42));
    add('boot' + side, 'foot' + side, g, [0, 0, 0]);
  }

  /* ---- Zırh eteği: kalçadan aşağı dört panel ---- */
  {
    const g = new THREE.Group();
    const w = Math.max(dim.hipWidth * 2.1, sw * 0.78);
    const h = dim.thigh * 0.66;
    const spec = [
      { ry: 0, z: w * 0.36, x: 0, pw: w * 0.74 },
      { ry: Math.PI, z: -w * 0.36, x: 0, pw: w * 0.74 },
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
    g.add(mesh(plate(w * 0.26, h * 0.18, w * 0.10, 0.3), M.gold, 0, h * 0.04, w * 0.38));
    // Kemer, kalça kemiğinin biraz üstünde
    add('tassets', 'hips', g, [0, dim.torso * 0.06, 0]);
  }

  /* ---- Miğfer ---- */
  {
    const g = new THREE.Group();
    const r = dim.head;
    const cap = dome(r * 1.10, 0.60, 16);
    cap.scale(1, 1.08, 1);
    g.add(mesh(cap, M.plate, 0, 0, 0));
    g.add(mesh(new THREE.CylinderGeometry(r * 1.12, r * 1.12, r * 0.28, 16), M.gold, 0, -r * 0.06, 0));
    g.add(mesh(plate(r * 0.68, r * 0.40, r * 0.20, 0.35), M.gold, 0, r * 0.04, r * 1.02));
    g.add(mesh(new THREE.ConeGeometry(r * 0.20, r * 0.85, 6), M.gold, 0, r * 1.10, 0));
    /*
     * Kafa kemiği boynun tepesinde; kafa hacmi onun üstünde. Miğferi kemiğin
     * hemen üstüne koymak onu yüzün önüne indiriyordu, bu yüzden ölçülen kafa
     * yüksekliğinin ortasına yerleştiriliyor.
     */
    add('helmet', 'head', g, [0, (dim.headHeight ?? r * 2) * 0.60, 0]);
  }

  /* ---- Pelerin: omuzlardan arkaya ---- */
  {
    const g = new THREE.Group();
    const w = sw * 1.15;
    const h = dim.torso * 1.30;
    const geo = new THREE.PlaneGeometry(w, h, 2, 4);
    geo.translate(0, -h * 0.5, 0);
    geo.rotateX(-0.12);
    g.add(mesh(geo, new THREE.MeshLambertMaterial({
      color: M.cloth.color, side: THREE.DoubleSide,
    }), 0, 0, 0));
    add('cape', 'chest', g, [0, (neckY - chestY) * 0.85, -sw * 0.36]);
  }

  /* ---- Çift el kılıç: kabza avuçta, namlu yukarı ---- */
  {
    const g = new THREE.Group();
    const u = dim.foreArm;
    const grip = u * 1.45;
    const blade = u * 4.6;
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

/** Zırh slotu -> takılacağı eklem (varsayılan eşleme). */
export const ARMOR_SLOTS = {
  chest: 'chest',
  pauldronL: 'armL', pauldronR: 'armR',
  bracerL: 'foreArmL', bracerR: 'foreArmR',
  thighGuardL: 'thighL', thighGuardR: 'thighR',
  greaveL: 'shinL', greaveR: 'shinR',
  bootL: 'footL', bootR: 'footR',
  tassets: 'hips',
  helmet: 'head',
  cape: 'chest',
  sword: 'handR',
};
