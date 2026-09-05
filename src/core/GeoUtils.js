/**
 * GeoUtils.js — Geometri birleştirme.
 *
 * Bir köy yüzlerce kutu ve çatıdan oluşuyor. Her birini ayrı mesh olarak
 * çizmek mobilde çizim çağrısı (draw call) sayısını uçurur; bu yüzden aynı
 * materyali paylaşan geometrileri tek bir tampona topluyoruz.
 *
 * three'nin BufferGeometryUtils'i examples/jsm altında olduğu ve depoya ek
 * bağımlılık getireceği için gereken minimum birleştirme burada.
 */
import * as THREE from 'three';

const ATTRS = ['position', 'normal', 'uv'];

function toNonIndexed(geo) {
  return geo.index ? geo.toNonIndexed() : geo;
}

/** Eksik uv/normal niteliklerini tamamlar. */
function ensureAttributes(geo) {
  if (!geo.attributes.normal) geo.computeVertexNormals();
  if (!geo.attributes.uv) {
    const count = geo.attributes.position.count;
    geo.setAttribute('uv', new THREE.Float32BufferAttribute(new Float32Array(count * 2), 2));
  }
  return geo;
}

/**
 * Dünya ölçeğinde düzlemsel UV üretir.
 *
 * Kutu ve prizmaların varsayılan UV'si her yüzü 0..1'e sıkıştırır; 56 birimlik
 * bir sur duvarında bu, taş dokusunun tanınmayacak kadar esnemesi demek.
 * Burada her köşe, normalinin baskın eksenine göre dünya birimiyle
 * haritalanıyor: doku her yerde aynı fiziksel boyutta görünüyor ve bitişik
 * parçalar arasında sürekli kalıyor.
 *
 * @param {THREE.BufferGeometry} geo
 * @param {number} scale  bir doku tekrarının kapladığı dünya birimi
 */
export function applyPlanarUV(geo, scale = 2) {
  if (!geo.attributes.normal) geo.computeVertexNormals();
  const pos = geo.attributes.position;
  const nor = geo.attributes.normal;
  const uv = new Float32Array(pos.count * 2);
  const inv = 1 / scale;
  for (let i = 0; i < pos.count; i++) {
    const nx = Math.abs(nor.getX(i));
    const ny = Math.abs(nor.getY(i));
    const nz = Math.abs(nor.getZ(i));
    let u, v;
    if (ny >= nx && ny >= nz) {        // yatay yüzey
      u = pos.getX(i); v = pos.getZ(i);
    } else if (nx >= nz) {             // X'e bakan yüzey
      u = pos.getZ(i); v = pos.getY(i);
    } else {                           // Z'ye bakan yüzey
      u = pos.getX(i); v = pos.getY(i);
    }
    uv[i * 2] = u * inv;
    uv[i * 2 + 1] = v * inv;
  }
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  return geo;
}

/** Silindirin varsayılan UV'sini dünya ölçeğine getirir. */
export function scaleCylinderUV(geo, radius, height, scale = 2) {
  const uv = geo.attributes.uv;
  if (!uv) return geo;
  const su = (2 * Math.PI * radius) / scale;
  const sv = height / scale;
  for (let i = 0; i < uv.count; i++) {
    uv.setXY(i, uv.getX(i) * su, uv.getY(i) * sv);
  }
  uv.needsUpdate = true;
  return geo;
}

/**
 * Aynı materyali paylaşan geometrileri tek geometriye birleştirir.
 * @param {THREE.BufferGeometry[]} geometries
 * @returns {THREE.BufferGeometry}
 */
export function mergeGeometries(geometries) {
  const list = geometries.map((g) => ensureAttributes(toNonIndexed(g)));
  let total = 0;
  for (const g of list) total += g.attributes.position.count;

  const out = new THREE.BufferGeometry();
  for (const name of ATTRS) {
    const itemSize = name === 'uv' ? 2 : 3;
    const arr = new Float32Array(total * itemSize);
    let offset = 0;
    for (const g of list) {
      const src = g.attributes[name].array;
      arr.set(src, offset);
      offset += src.length;
    }
    out.setAttribute(name, new THREE.BufferAttribute(arr, itemSize));
  }
  out.computeBoundingSphere();
  out.computeBoundingBox();

  // Ara geometrileri bırak (toNonIndexed kopyaları)
  for (let i = 0; i < list.length; i++) {
    if (list[i] !== geometries[i]) list[i].dispose();
    geometries[i].dispose();
  }
  return out;
}

/**
 * { geo, mat } parçalarını materyale göre gruplayıp mesh listesi üretir.
 * @param {{geo:THREE.BufferGeometry, mat:THREE.Material}[]} parts
 * @param {THREE.Matrix4} [matrix] hepsine uygulanacak dönüşüm
 * @returns {THREE.Mesh[]}
 */
export function buildMeshes(parts, matrix) {
  const byMat = new Map();
  for (const { geo, mat } of parts) {
    if (matrix) geo.applyMatrix4(matrix);
    if (!byMat.has(mat)) byMat.set(mat, []);
    byMat.get(mat).push(geo);
  }
  const meshes = [];
  for (const [mat, geos] of byMat) {
    const merged = mergeGeometries(geos);
    const mesh = new THREE.Mesh(merged, mat);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    meshes.push(mesh);
  }
  return meshes;
}

/** Parça listesini verilen konum/dönüşle dönüştürüp hedef listeye ekler. */
export function placeParts(target, parts, x, y, z, ry = 0, scale = 1) {
  const m = new THREE.Matrix4();
  const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, ry, 0));
  m.compose(new THREE.Vector3(x, y, z), q, new THREE.Vector3(scale, scale, scale));
  for (const p of parts) {
    p.geo.applyMatrix4(m);
    target.push(p);
  }
  return target;
}
