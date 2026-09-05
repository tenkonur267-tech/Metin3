/**
 * Terrain.js — Dünya arazisi.
 *
 * Tek bir yüksek çözünürlüklü zemin: değer gürültüsünden (value noise) türeyen
 * tepeler, harita kenarlarında doğal sınır oluşturan dağlar ve köylerin
 * oturduğu düzleştirilmiş alanlar. Doku geçişleri (çim / kum / kar / yol)
 * bir karışım haritası ve özelleştirilmiş Lambert gölgelendiricisiyle yapılır.
 */
import * as THREE from 'three';
import { getTexture } from '../core/Textures.js';
import { KINGDOMS } from '../data/kingdoms.js';

/* ---------------- Değer gürültüsü ---------------- */
function hash2(x, y) {
  let h = x * 374761393 + y * 668265263;
  h = (h ^ (h >> 13)) * 1274126177;
  return ((h ^ (h >> 16)) >>> 0) / 4294967296;
}
function smooth(t) { return t * t * (3 - 2 * t); }
function valueNoise(x, y) {
  const xi = Math.floor(x), yi = Math.floor(y);
  const xf = x - xi, yf = y - yi;
  const u = smooth(xf), v = smooth(yf);
  const a = hash2(xi, yi), b = hash2(xi + 1, yi);
  const c = hash2(xi, yi + 1), d = hash2(xi + 1, yi + 1);
  return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
}
function fbm(x, y, octaves = 4, lac = 2.03, gain = 0.5) {
  let amp = 1, freq = 1, sum = 0, norm = 0;
  for (let i = 0; i < octaves; i++) {
    sum += amp * valueNoise(x * freq, y * freq);
    norm += amp;
    amp *= gain;
    freq *= lac;
  }
  return sum / norm;
}

/* ---------------- Yol ağı ---------------- */
/** Köyleri birbirine bağlayan ana yolların merkez çizgileri. */
function roadSegments() {
  const [a, b, c] = KINGDOMS.map((k) => k.center);
  const hub = { x: 0, z: 30 };
  return [
    [a, hub], [b, hub], [c, hub],
  ];
}

function distToSegment(px, pz, s) {
  const [a, b] = s;
  const vx = b.x - a.x, vz = b.z - a.z;
  const wx = px - a.x, wz = pz - a.z;
  const len2 = vx * vx + vz * vz;
  let t = len2 > 0 ? (wx * vx + wz * vz) / len2 : 0;
  t = Math.max(0, Math.min(1, t));
  const dx = px - (a.x + vx * t), dz = pz - (a.z + vz * t);
  return Math.hypot(dx, dz);
}

export class Terrain {
  /**
   * @param {object} opts
   * @param {number} opts.size      arazi kenar uzunluğu
   * @param {number} opts.segments  ızgara bölüntüsü (kaliteye göre)
   */
  constructor(opts = {}) {
    this.size = opts.size ?? 1400;
    this.segments = opts.segments ?? 200;
    this.half = this.size / 2;
    this.villageFlatR = 120;   // köy düzlüğünün yarıçapı
    this.villageBlendR = 90;   // düzlükten araziye geçiş bandı
    // Kara, su seviyesinin belirgin şekilde üstünde dursun: yalnızca en
    // alçak çukurlar göl olur, köyler ve yollar hep kuru kalır.
    this.landLift = 12;
    this.waterLevel = -12;
    this.roads = roadSegments();

    this._buildHeightField();
    this.mesh = this._buildMesh();
    this.water = this._buildWater();
  }

  /* -------- Yükseklik alanı -------- */
  _buildHeightField() {
    const n = this.segments + 1;
    this.gridN = n;
    this.heights = new Float32Array(n * n);
    for (let j = 0; j < n; j++) {
      for (let i = 0; i < n; i++) {
        const x = -this.half + (i / this.segments) * this.size;
        const z = -this.half + (j / this.segments) * this.size;
        this.heights[j * n + i] = this._rawHeight(x, z);
      }
    }
  }

  _rawHeight(x, z) {
    const s = 0.0016;
    // Ana tepeler
    let h = this.landLift + (fbm(x * s, z * s, 5) - 0.5) * 46;
    // Geniş ölçekli dalgalanma
    h += (fbm(x * s * 0.31 + 100, z * s * 0.31 - 40, 3) - 0.5) * 34;
    // Küçük detay
    h += (fbm(x * s * 4.1 - 300, z * s * 4.1 + 220, 3) - 0.5) * 3.2;

    // Kenarlarda yükselen dağ kuşağı — haritanın doğal sınırı
    const edge = Math.max(Math.abs(x), Math.abs(z)) / this.half;
    if (edge > 0.62) {
      const t = (edge - 0.62) / 0.38;
      h += Math.pow(t, 2.1) * 150 * (0.7 + fbm(x * 0.006, z * 0.006, 3) * 0.6);
    }

    // Köy alanlarını düzleştir
    for (const k of KINGDOMS) {
      const d = Math.hypot(x - k.center.x, z - k.center.z);
      const R = this.villageFlatR, B = this.villageBlendR;
      if (d < R + B) {
        const target = this._villageBaseHeight(k);
        const t = d <= R ? 1 : 1 - smooth((d - R) / B);
        h = h * (1 - t) + target * t;
      }
    }

    // Yolları düzleştir ve hafifçe oy
    for (const seg of this.roads) {
      const d = distToSegment(x, z, seg);
      if (d < 22) {
        const t = 1 - smooth(Math.min(1, d / 22));
        const target = this._roadHeight(x, z, seg);
        h = h * (1 - t * 0.85) + target * (t * 0.85);
      }
    }
    return h;
  }

  /**
   * Bir köyün oturduğu sabit taban yüksekliği.
   * Köyler su seviyesinin çok üstünde, hafif yükseltilmiş düzlüklerde kurulur.
   */
  _villageBaseHeight(k) {
    if (k._baseH === undefined) {
      const s = 0.0016;
      const variation = (fbm(k.center.x * s, k.center.z * s, 5) - 0.5) * 12;
      k._baseH = this.landLift + 4 + variation;
    }
    return k._baseH;
  }

  /** Yolun o noktadaki hedef yüksekliği: uçlar arasında yumuşak geçiş. */
  _roadHeight(x, z, seg) {
    const [a, b] = seg;
    const vx = b.x - a.x, vz = b.z - a.z;
    const len2 = vx * vx + vz * vz;
    let t = len2 > 0 ? ((x - a.x) * vx + (z - a.z) * vz) / len2 : 0;
    t = Math.max(0, Math.min(1, t));
    const ha = this._endpointHeight(a);
    const hb = this._endpointHeight(b);
    return ha + (hb - ha) * smooth(t);
  }

  _endpointHeight(p) {
    const k = KINGDOMS.find((kk) => kk.center.x === p.x && kk.center.z === p.z);
    if (k) return this._villageBaseHeight(k);
    const s = 0.0016;
    return this.landLift + 2 + (fbm(p.x * s, p.z * s, 5) - 0.5) * 46 * 0.4;
  }

  /** Dünya koordinatında zemin yüksekliği (bilineer örnekleme). */
  heightAt(x, z) {
    const n = this.gridN;
    const fx = ((x + this.half) / this.size) * this.segments;
    const fz = ((z + this.half) / this.size) * this.segments;
    const i = Math.floor(fx), j = Math.floor(fz);
    if (i < 0 || j < 0 || i >= n - 1 || j >= n - 1) {
      return this._rawHeight(
        THREE.MathUtils.clamp(x, -this.half, this.half),
        THREE.MathUtils.clamp(z, -this.half, this.half));
    }
    const tx = fx - i, tz = fz - j;
    const h = this.heights;
    const h00 = h[j * n + i], h10 = h[j * n + i + 1];
    const h01 = h[(j + 1) * n + i], h11 = h[(j + 1) * n + i + 1];
    return (h00 * (1 - tx) + h10 * tx) * (1 - tz) + (h01 * (1 - tx) + h11 * tx) * tz;
  }

  /** Zemin normali — eğimli yerlerde karakteri yatırmak için. */
  normalAt(x, z, eps = 1.5) {
    const hL = this.heightAt(x - eps, z), hR = this.heightAt(x + eps, z);
    const hD = this.heightAt(x, z - eps), hU = this.heightAt(x, z + eps);
    return new THREE.Vector3(hL - hR, 2 * eps, hD - hU).normalize();
  }

  /** Eğim (0 = düz, 1 = dik). Yürünebilirlik testi için. */
  slopeAt(x, z) {
    return 1 - this.normalAt(x, z).y;
  }

  /* -------- Karışım haritası: R=kum, G=kar, B=yol -------- */
  _buildBlendTexture(res = 512) {
    const c = document.createElement('canvas');
    c.width = c.height = res;
    const ctx = c.getContext('2d');
    const img = ctx.createImageData(res, res);
    const d = img.data;
    const chunjo = KINGDOMS.find((k) => k.biome === 'sand').center;
    const jinno = KINGDOMS.find((k) => k.biome === 'snow').center;

    for (let j = 0; j < res; j++) {
      for (let i = 0; i < res; i++) {
        const x = -this.half + (i / (res - 1)) * this.size;
        const z = -this.half + (j / (res - 1)) * this.size;
        const wob = (fbm(x * 0.004, z * 0.004, 3) - 0.5) * 130;

        const dSand = Math.hypot(x - chunjo.x, z - chunjo.z) + wob;
        const dSnow = Math.hypot(x - jinno.x, z - jinno.z) + wob;
        const sand = 1 - smooth(THREE.MathUtils.clamp((dSand - 150) / 260, 0, 1));
        const snow = 1 - smooth(THREE.MathUtils.clamp((dSnow - 150) / 260, 0, 1));

        // Yol: dar bir arabalık yol; kenarları çimene yumuşak geçer
        let road = 0;
        for (const seg of this.roads) {
          const dd = distToSegment(x, z, seg);
          const w = 4.5 + fbm(x * 0.02, z * 0.02, 2) * 2.5;
          road = Math.max(road, 1 - smooth(THREE.MathUtils.clamp((dd - w) / 4.5, 0, 1)));
        }
        // Tapınak avlusu taş döşeli; evlerin arası çimen kalsın diye dar.
        let paved = 0;
        for (const k of KINGDOMS) {
          const dd = Math.hypot(x - k.center.x, z - k.center.z);
          paved = Math.max(paved, 1 - smooth(THREE.MathUtils.clamp((dd - 27) / 11, 0, 1)));
          // Kapıdan meydana uzanan ana cadde de döşeli
          const gx = k.center.x + Math.sin(k.facing) * 105;
          const gz = k.center.z + Math.cos(k.facing) * 105;
          const dl = distToSegment(x, z, [k.center, { x: gx, z: gz }]);
          paved = Math.max(paved, 1 - smooth(THREE.MathUtils.clamp((dl - 6.5) / 4, 0, 1)));
        }

        const o = (j * res + i) * 4;
        d[o] = sand * 255;
        d[o + 1] = snow * 255;
        d[o + 2] = road * 255;
        d[o + 3] = paved * 255;
      }
    }
    // Döşeme ağırlığını ayrı bir dokuya taşı: canvas'ın alfa kanalı WebGL
    // yüklemesinde ön-çarpım yüzünden hassasiyet kaybedebiliyor.
    const paveCanvas = document.createElement('canvas');
    paveCanvas.width = paveCanvas.height = res;
    const pctx = paveCanvas.getContext('2d');
    const pimg = pctx.createImageData(res, res);
    for (let k = 0; k < res * res; k++) {
      pimg.data[k * 4] = d[k * 4 + 3];
      pimg.data[k * 4 + 1] = d[k * 4 + 3];
      pimg.data[k * 4 + 2] = d[k * 4 + 3];
      pimg.data[k * 4 + 3] = 255;
      d[k * 4 + 3] = 255;
    }
    pctx.putImageData(pimg, 0, 0);
    ctx.putImageData(img, 0, 0);

    const mk = (cv) => {
      const t = new THREE.CanvasTexture(cv);
      t.wrapS = t.wrapT = THREE.ClampToEdgeWrapping;
      t.needsUpdate = true;
      return t;
    };
    this.paveTexture = mk(paveCanvas);
    return mk(c);
  }

  /* -------- Zemin mesh'i -------- */
  _buildMesh() {
    const geo = new THREE.PlaneGeometry(this.size, this.size, this.segments, this.segments);
    geo.rotateX(-Math.PI / 2);
    const pos = geo.attributes.position;
    const n = this.gridN;
    for (let j = 0; j < n; j++) {
      for (let i = 0; i < n; i++) {
        pos.setY(j * n + i, this.heights[j * n + i]);
      }
    }
    pos.needsUpdate = true;
    geo.computeVertexNormals();

    const tile = this.size / 6;   // bir doku tekrarı ≈ 6 dünya birimi
    const grass = getTexture('grass');
    const sand = getTexture('sand');
    const snow = getTexture('snow');
    const path = getTexture('path');
    const blend = this._buildBlendTexture();

    const mat = new THREE.MeshLambertMaterial({ map: grass });
    mat.onBeforeCompile = (shader) => {
      shader.uniforms.uSand = { value: sand };
      shader.uniforms.uSnow = { value: snow };
      shader.uniforms.uPath = { value: path };
      shader.uniforms.uBlend = { value: blend };
      shader.uniforms.uPave = { value: this.paveTexture };
      shader.uniforms.uPaveMap = { value: getTexture('stone', { a: '#a09884', b: '#6b6455', repeat: 1 }) };
      shader.uniforms.uTile = { value: tile };
      shader.uniforms.uMacro = { value: tile * 0.085 };

      shader.vertexShader = shader.vertexShader
        .replace('#include <common>', `#include <common>\nvarying vec2 vTerrainUv;`)
        .replace('#include <uv_vertex>', `#include <uv_vertex>\nvTerrainUv = uv;`);

      shader.fragmentShader = shader.fragmentShader
        .replace('#include <common>', `#include <common>
          uniform sampler2D uSand; uniform sampler2D uSnow;
          uniform sampler2D uPath; uniform sampler2D uBlend;
          uniform sampler2D uPave; uniform sampler2D uPaveMap;
          uniform float uTile; uniform float uMacro;
          varying vec2 vTerrainUv;`)
        .replace('#include <map_fragment>', `
          vec2 tuv = vTerrainUv * uTile;
          vec2 muv = vTerrainUv * uMacro;
          vec3 blendW = texture2D(uBlend, vTerrainUv).rgb;

          vec3 cGrass = texture2D(map, tuv).rgb;
          vec3 cSand  = texture2D(uSand, tuv).rgb;
          vec3 cSnow  = texture2D(uSnow, tuv).rgb;
          vec3 cPath  = texture2D(uPath, tuv * 1.7).rgb;

          vec3 terrainCol = cGrass;
          terrainCol = mix(terrainCol, cSand, blendW.r);
          terrainCol = mix(terrainCol, cSnow, blendW.g);
          terrainCol = mix(terrainCol, cPath, blendW.b);

          float pave = texture2D(uPave, vTerrainUv).r;
          vec3 cPave = texture2D(uPaveMap, tuv * 0.55).rgb;
          terrainCol = mix(terrainCol, cPave, pave);

          // Aynı dokunun ızgara gibi tekrar ettiği belli olmasın diye
          // çok büyük ölçekli ikinci bir örnekle parlaklığı dalgalandırıyoruz.
          float macro = dot(texture2D(map, muv).rgb, vec3(0.333));
          terrainCol *= 0.80 + macro * 0.62;

          diffuseColor.rgb *= terrainCol;
        `);
      this._shader = shader;
    };
    mat.customProgramCacheKey = () => 'terrain-splat-v3';

    const mesh = new THREE.Mesh(geo, mat);
    mesh.receiveShadow = true;
    mesh.name = 'terrain';
    return mesh;
  }

  /* -------- Su -------- */
  _buildWater() {
    const geo = new THREE.PlaneGeometry(this.size, this.size, 1, 1);
    geo.rotateX(-Math.PI / 2);
    const mat = new THREE.MeshLambertMaterial({
      color: 0x2f5f78,
      transparent: true,
      opacity: 0.78,
      depthWrite: false,
    });
    const mesh = new THREE.Mesh(geo, mat);
    mesh.position.y = this.waterLevel;
    mesh.renderOrder = -1;
    mesh.name = 'water';
    return mesh;
  }

  addTo(scene) {
    scene.add(this.mesh);
    scene.add(this.water);
    return this;
  }

  dispose() {
    this.mesh.geometry.dispose();
    this.mesh.material.dispose();
    this.water.geometry.dispose();
    this.water.material.dispose();
  }
}
