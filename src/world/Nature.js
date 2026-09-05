/**
 * Nature.js — Bitki örtüsü ve kayalar.
 *
 * Binlerce nesne InstancedMesh ile tek çizim çağrısında basılıyor.
 * Dağıtım biyoma duyarlı: karlı bölgede köknar, çölde kuru ağaç ve kaktüs,
 * yeşillikte geniş yapraklı ağaç. Köy içleri ve yollar boş bırakılıyor.
 */
import * as THREE from 'three';
import { mergeGeometries } from '../core/GeoUtils.js';
import { KINGDOMS } from '../data/kingdoms.js';

function rand(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

/* ---------------- Ağaç modelleri ---------------- */
function pineGeometry() {
  const parts = [];
  const trunk = new THREE.CylinderGeometry(0.16, 0.28, 2.2, 6);
  trunk.translate(0, 1.1, 0);
  parts.push({ g: trunk, c: 0x4a3524 });
  for (let i = 0; i < 4; i++) {
    const r = 1.55 - i * 0.32;
    const h = 1.7 - i * 0.16;
    const cone = new THREE.ConeGeometry(r, h, 8);
    cone.translate(0, 2.0 + i * 0.95, 0);
    parts.push({ g: cone, c: i % 2 ? 0x27492c : 0x1f3c25 });
  }
  return colorize(parts);
}

function broadleafGeometry() {
  const parts = [];
  const trunk = new THREE.CylinderGeometry(0.18, 0.34, 3.0, 6);
  trunk.translate(0, 1.5, 0);
  parts.push({ g: trunk, c: 0x5b402a });
  const blobs = [
    [0, 4.1, 0, 1.85], [-1.0, 3.6, 0.4, 1.25],
    [1.05, 3.7, -0.35, 1.3], [0.2, 4.9, 0.7, 1.1],
  ];
  for (const [x, y, z, r] of blobs) {
    const s = new THREE.SphereGeometry(r, 7, 6);
    s.scale(1, 0.85, 1);
    s.translate(x, y, z);
    parts.push({ g: s, c: 0x3f6b30 });
  }
  return colorize(parts);
}

function deadTreeGeometry() {
  const parts = [];
  const trunk = new THREE.CylinderGeometry(0.14, 0.32, 3.4, 6);
  trunk.translate(0, 1.7, 0);
  parts.push({ g: trunk, c: 0x6b5637 });
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2;
    const b = new THREE.CylinderGeometry(0.05, 0.11, 1.5, 5);
    b.rotateZ(0.85);
    b.rotateY(a);
    b.translate(Math.sin(a) * 0.55, 2.9 + (i % 2) * 0.45, Math.cos(a) * 0.55);
    parts.push({ g: b, c: 0x6b5637 });
  }
  return colorize(parts);
}

function bambooGeometry() {
  const parts = [];
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2;
    const h = 3.2 + (i % 3) * 0.8;
    const c = new THREE.CylinderGeometry(0.075, 0.09, h, 5);
    c.translate(Math.sin(a) * 0.32, h / 2, Math.cos(a) * 0.32);
    parts.push({ g: c, c: 0x6f8f3a });
    for (let k = 0; k < 3; k++) {
      const leaf = new THREE.ConeGeometry(0.42, 0.9, 4);
      leaf.rotateZ(1.1);
      leaf.rotateY(a + k * 2.1);
      leaf.translate(Math.sin(a) * 0.32, h - 0.4 - k * 0.5, Math.cos(a) * 0.32);
      parts.push({ g: leaf, c: 0x557f2e });
    }
  }
  return colorize(parts);
}

function rockGeometry(seed = 1) {
  const g = new THREE.IcosahedronGeometry(1, 1);
  const p = g.attributes.position;
  const r = rand(seed * 977);
  for (let i = 0; i < p.count; i++) {
    const s = 0.62 + r() * 0.65;
    p.setXYZ(i, p.getX(i) * s, p.getY(i) * s * 0.72, p.getZ(i) * s);
  }
  g.computeVertexNormals();
  g.translate(0, 0.45, 0);
  return colorize([{ g, c: 0x847e73 }]);
}

function bushGeometry(color = 0x36592a) {
  const parts = [];
  for (let i = 0; i < 3; i++) {
    const a = (i / 3) * Math.PI * 2;
    const s = new THREE.SphereGeometry(0.5 - i * 0.06, 6, 5);
    s.scale(1, 0.75, 1);
    s.translate(Math.sin(a) * 0.24, 0.36 + (i % 2) * 0.12, Math.cos(a) * 0.24);
    parts.push({ g: s, c: color });
  }
  return colorize(parts);
}

/** Parça listesini renk niteliği taşıyan tek geometriye çevirir. */
function colorize(parts) {
  const geos = [];
  for (const { g, c } of parts) {
    const ng = g.index ? g.toNonIndexed() : g;
    const count = ng.attributes.position.count;
    const col = new THREE.Color(c);
    const arr = new Float32Array(count * 3);
    for (let i = 0; i < count; i++) {
      arr[i * 3] = col.r; arr[i * 3 + 1] = col.g; arr[i * 3 + 2] = col.b;
    }
    ng.setAttribute('color', new THREE.BufferAttribute(arr, 3));
    if (!ng.attributes.uv) {
      ng.setAttribute('uv', new THREE.Float32BufferAttribute(new Float32Array(count * 2), 2));
    }
    geos.push(ng);
  }
  // mergeGeometries renk niteliğini taşımadığı için burada elle birleştiriyoruz
  let total = 0;
  for (const g of geos) total += g.attributes.position.count;
  const out = new THREE.BufferGeometry();
  for (const [name, size] of [['position', 3], ['normal', 3], ['uv', 2], ['color', 3]]) {
    const arr = new Float32Array(total * size);
    let off = 0;
    for (const g of geos) {
      if (!g.attributes[name]) g.computeVertexNormals();
      arr.set(g.attributes[name].array, off);
      off += g.attributes[name].array.length;
    }
    out.setAttribute(name, new THREE.BufferAttribute(arr, size));
  }
  out.computeBoundingSphere();
  for (const g of geos) g.dispose();
  return out;
}

/* ---------------- Dağıtım ---------------- */
export class Nature {
  /**
   * @param {Terrain} terrain
   * @param {object} opts { count, colliders }
   */
  constructor(terrain, opts = {}) {
    this.terrain = terrain;
    this.count = opts.count ?? 1200;
    this.group = new THREE.Group();
    this.group.name = 'nature';
    this.colliders = [];
    this._build();
  }

  _biomeAt(x, z) {
    let best = 'grass', bestD = Infinity;
    for (const k of KINGDOMS) {
      const d = Math.hypot(x - k.center.x, z - k.center.z);
      if (d < bestD) { bestD = d; best = k.biome; }
    }
    // Krallıktan uzaklaştıkça ortak yeşil kuşağa dön
    return bestD > 300 ? 'grass' : best;
  }

  _blocked(x, z) {
    for (const k of KINGDOMS) {
      if (Math.hypot(x - k.center.x, z - k.center.z) < 118) return true;
    }
    for (const seg of this.terrain.roads) {
      const [a, b] = seg;
      const vx = b.x - a.x, vz = b.z - a.z;
      const len2 = vx * vx + vz * vz;
      let t = len2 > 0 ? ((x - a.x) * vx + (z - a.z) * vz) / len2 : 0;
      t = Math.max(0, Math.min(1, t));
      const d = Math.hypot(x - (a.x + vx * t), z - (a.z + vz * t));
      if (d < 13) return true;
    }
    return false;
  }

  _build() {
    const mat = new THREE.MeshLambertMaterial({ vertexColors: true });
    const kinds = {
      pine: { geo: pineGeometry(), biomes: ['snow', 'grass'], w: { snow: 6, grass: 1, sand: 0 }, r: 0.7 },
      broadleaf: { geo: broadleafGeometry(), biomes: ['grass'], w: { snow: 0, grass: 5, sand: 0.3 }, r: 0.8 },
      dead: { geo: deadTreeGeometry(), biomes: ['sand'], w: { snow: 0.6, grass: 0.4, sand: 5 }, r: 0.5 },
      bamboo: { geo: bambooGeometry(), biomes: ['grass'], w: { snow: 0, grass: 1.6, sand: 0 }, r: 0.6 },
      rock: { geo: rockGeometry(3), biomes: ['grass', 'sand', 'snow'], w: { snow: 2, grass: 1.4, sand: 2.4 }, r: 0.9 },
      bush: { geo: bushGeometry(), biomes: ['grass', 'snow'], w: { snow: 1.2, grass: 2.6, sand: 0.8 }, r: 0 },
    };

    const buckets = {};
    for (const k of Object.keys(kinds)) buckets[k] = [];

    const r = rand(20240915);
    const half = this.terrain.half - 40;
    let tries = 0;
    let placed = 0;
    while (placed < this.count && tries < this.count * 12) {
      tries++;
      const x = (r() * 2 - 1) * half;
      const z = (r() * 2 - 1) * half;
      if (this._blocked(x, z)) continue;

      const y = this.terrain.heightAt(x, z);
      if (y < this.terrain.waterLevel + 0.6) continue;      // su altı
      if (this.terrain.slopeAt(x, z) > 0.34) continue;      // dik yamaç
      if (y > 95) continue;                                  // yüksek zirveler çıplak

      const biome = this._biomeAt(x, z);
      // Ağırlıklı tür seçimi
      let total = 0;
      for (const k of Object.keys(kinds)) total += kinds[k].w[biome] || 0;
      if (total <= 0) continue;
      let pick = r() * total;
      let chosen = null;
      for (const k of Object.keys(kinds)) {
        pick -= kinds[k].w[biome] || 0;
        if (pick <= 0) { chosen = k; break; }
      }
      if (!chosen) continue;

      const scale = 0.72 + r() * 0.75;
      buckets[chosen].push({ x, y, z, ry: r() * Math.PI * 2, s: scale });
      const cr = kinds[chosen].r * scale;
      if (cr > 0.2) this.colliders.push({ kind: 'circle', x, z, r: cr });
      placed++;
    }

    const dummy = new THREE.Object3D();
    for (const [name, cfg] of Object.entries(kinds)) {
      const items = buckets[name];
      if (!items.length) { cfg.geo.dispose(); continue; }
      const inst = new THREE.InstancedMesh(cfg.geo, mat, items.length);
      inst.name = 'nature-' + name;
      inst.castShadow = name !== 'bush';
      inst.receiveShadow = true;
      items.forEach((it, i) => {
        dummy.position.set(it.x, it.y, it.z);
        dummy.rotation.set(0, it.ry, 0);
        dummy.scale.setScalar(it.s);
        dummy.updateMatrix();
        inst.setMatrixAt(i, dummy.matrix);
      });
      inst.instanceMatrix.needsUpdate = true;
      inst.frustumCulled = false;
      this.group.add(inst);
    }
    this.stats = Object.fromEntries(
      Object.entries(buckets).map(([k, v]) => [k, v.length]));
  }

  addTo(scene) { scene.add(this.group); return this; }
}

export { mergeGeometries };
