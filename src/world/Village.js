/**
 * Village.js — Krallık köyü üreticisi.
 *
 * Düzen, Metin2 başlangıç köylerinin mantığını izler: on iki kenarlı bir
 * sur, tek bir görkemli ana kapı, kapıdan merkeze uzanan geniş bir cadde,
 * meydanın ortasında çok katlı bir tapınak pagodası ve caddeleri saran
 * ev blokları.
 *
 * Bütün yapılar materyale göre birleştirilerek birkaç mesh'e indirgenir;
 * ayrıca oyuncunun içinden geçemeyeceği çarpışma hacimleri toplanır.
 */
import * as THREE from 'three';
import {
  makeMaterials, buildHouse, buildPagoda, buildGate, buildWallSegment,
  buildWatchtower, buildStoneLantern, buildLanternPost, buildBanner,
  buildFence, buildWell,
} from './Architecture.js';
import { buildMeshes, placeParts } from '../core/GeoUtils.js';

const TAU = Math.PI * 2;

export class Village {
  /**
   * @param {object} kingdom  KINGDOMS girdisi
   * @param {Terrain} terrain
   */
  constructor(kingdom, terrain) {
    this.kingdom = kingdom;
    this.terrain = terrain;
    this.center = new THREE.Vector3(kingdom.center.x, 0, kingdom.center.z);
    this.center.y = terrain.heightAt(this.center.x, this.center.z);
    this.radius = 105;
    this.mats = makeMaterials(kingdom.theme);

    this.colliders = [];   // { kind:'circle'|'box', ... } dünya koordinatlarında
    this.guardPosts = [];  // NPC muhafız konumları
    this.lights = [];      // fener ışık noktaları
    this.group = new THREE.Group();
    this.group.name = `village-${kingdom.id}`;

    this._build();
  }

  /* -------- yardımcılar -------- */
  _circle(x, z, r) { this.colliders.push({ kind: 'circle', x, z, r }); }
  _box(x, z, hw, hd, ry) { this.colliders.push({ kind: 'box', x, z, hw, hd, ry }); }

  /** Köyün yerel koordinatını dünya koordinatına çevirir. */
  local(x, z) {
    return { x: this.center.x + x, z: this.center.z + z };
  }

  _build() {
    const parts = [];
    const K = this.kingdom;
    const gateAngle = K.facing;

    /* ---- Sur duvarı: on iki kenarlı halka ---- */
    const sides = 12;
    const R = this.radius;
    const sideLen = 2 * R * Math.tan(Math.PI / sides) + 0.6;
    // Kapının yerleşeceği kenar indeksi
    let gateSide = Math.round(((gateAngle + TAU) % TAU) / (TAU / sides)) % sides;

    for (let i = 0; i < sides; i++) {
      const a = (i / sides) * TAU;
      const x = Math.sin(a) * R;
      const z = Math.cos(a) * R;
      if (i === gateSide) continue; // burası kapı
      placeParts(parts, buildWallSegment(this.mats, sideLen, 4.2), x, 0, z, a);
      this._box(this.center.x + x, this.center.z + z, sideLen / 2, 1.1, a);

      // Her ikinci köşede gözetleme kulesi
      if (i % 2 === 0) {
        const ca = ((i + 0.5) / sides) * TAU;
        const cx = Math.sin(ca) * R, cz = Math.cos(ca) * R;
        placeParts(parts, buildWatchtower(this.mats, 7), cx, 0, cz, ca);
        this._circle(this.center.x + cx, this.center.z + cz, 2.4);
      }
    }

    /* ---- Ana kapı ---- */
    const ga = (gateSide / sides) * TAU;
    const gx = Math.sin(ga) * R, gz = Math.cos(ga) * R;
    placeParts(parts, buildGate(this.mats, { width: 14, height: 7.5, depth: 3 }), gx, 0, gz, ga);
    // Kapı sütunları çarpışma alanı (ortadan geçilebilsin diye iki ayrı kutu)
    for (const s of [-1, 1]) {
      const ox = Math.cos(ga) * s * 6.5, oz = -Math.sin(ga) * s * 6.5;
      this._box(this.center.x + gx + ox, this.center.z + gz + oz, 1.2, 1.6, ga);
    }
    // Kapı yanları duvara bağlansın
    for (const s of [-1, 1]) {
      const ox = Math.cos(ga) * s * 11.5, oz = -Math.sin(ga) * s * 11.5;
      placeParts(parts, buildWallSegment(this.mats, sideLen / 2 - 6, 4.2),
        gx + ox, 0, gz + oz, ga);
      this._box(this.center.x + gx + ox, this.center.z + gz + oz, sideLen / 4 - 3, 1.1, ga);
    }
    // Kapı önü sancakları ve muhafız noktaları
    for (const s of [-1, 1]) {
      const ox = Math.cos(ga) * s * 9.5, oz = -Math.sin(ga) * s * 9.5;
      const inx = Math.sin(ga) * -8, inz = Math.cos(ga) * -8;
      placeParts(parts, buildBanner(this.mats, 6.5), gx + ox + inx, 0, gz + oz + inz, ga);
      this._circle(this.center.x + gx + ox + inx, this.center.z + gz + oz + inz, 0.5);
      this.guardPosts.push({
        x: this.center.x + gx + ox * 0.6 + inx * 1.8,
        z: this.center.z + gz + oz * 0.6 + inz * 1.8,
        ry: ga + Math.PI,
      });
    }

    /* ---- Merkez: tapınak pagodası ---- */
    placeParts(parts, buildPagoda(this.mats, { levels: 5, baseW: 10, baseH: 3.4 }), 0, 0, 0, gateAngle);
    this._circle(this.center.x, this.center.z, 7.6);

    /* ---- Meydan çevresi: kuyu, taş fenerler, sancaklar ---- */
    const plazaR = 26;
    placeParts(parts, buildWell(this.mats), Math.sin(ga + 1.2) * 15, 0, Math.cos(ga + 1.2) * 15, 0);
    this._circle(this.center.x + Math.sin(ga + 1.2) * 15, this.center.z + Math.cos(ga + 1.2) * 15, 1.4);

    for (let i = 0; i < 12; i++) {
      const a = (i / 12) * TAU + 0.26;
      const x = Math.sin(a) * plazaR, z = Math.cos(a) * plazaR;
      placeParts(parts, buildStoneLantern(this.mats), x, 0, z, a);
      this._circle(this.center.x + x, this.center.z + z, 0.55);
      if (i % 3 === 0) {
        this.lights.push({ x: this.center.x + x, z: this.center.z + z, y: 1.7 });
      }
    }

    /* ---- Ana cadde: kapıdan meydana fener sırası ---- */
    const roadDir = { x: Math.sin(ga), z: Math.cos(ga) };
    for (let d = plazaR + 10; d < R - 14; d += 12) {
      for (const s of [-1, 1]) {
        const ox = Math.cos(ga) * s * 7.5, oz = -Math.sin(ga) * s * 7.5;
        const x = roadDir.x * d + ox, z = roadDir.z * d + oz;
        placeParts(parts, buildLanternPost(this.mats), x, 0, z, ga + (s < 0 ? Math.PI : 0));
        this._circle(this.center.x + x, this.center.z + z, 0.4);
        this.lights.push({ x: this.center.x + x + Math.cos(ga) * s * 0.9, z: this.center.z + z, y: 2.75 });
      }
    }

    /* ---- Ev blokları: iki halka ---- */
    const rings = [
      { r: 48, count: 9, w: 7.0, d: 5.5, wallH: 3.2, roofH: 2.6 },
      { r: 76, count: 13, w: 6.0, d: 5.0, wallH: 2.9, roofH: 2.3 },
    ];
    for (const ring of rings) {
      for (let i = 0; i < ring.count; i++) {
        const a = (i / ring.count) * TAU + (ring.r > 60 ? 0.24 : 0);
        // Ana caddeyi boş bırak
        let delta = Math.abs(((a - ga + Math.PI * 3) % TAU) - Math.PI);
        if (delta > Math.PI - 0.30) continue;

        const jitter = (Math.sin(i * 12.9898 + ring.r) * 43758.5453) % 1;
        const rr = ring.r + jitter * 5;
        const x = Math.sin(a) * rr, z = Math.cos(a) * rr;
        const ry = a + Math.PI; // kapı merkeze baksın
        placeParts(parts, buildHouse(this.mats, {
          w: ring.w, d: ring.d, wallH: ring.wallH, roofH: ring.roofH,
          porch: i % 2 === 0,
        }), x, 0, z, ry);
        this._box(this.center.x + x, this.center.z + z, ring.w / 2 + 0.5, ring.d / 2 + 0.5, ry);

        // Evler arasına çit
        if (i % 3 === 1) {
          const fa = a + TAU / ring.count * 0.5;
          placeParts(parts, buildFence(this.mats, 5),
            Math.sin(fa) * rr, 0, Math.cos(fa) * rr, fa + Math.PI / 2);
        }
      }
    }

    /* ---- Meydan kenarında büyük dükkânlar ---- */
    for (let i = 0; i < 4; i++) {
      const a = ga + Math.PI * 0.5 + (i - 1.5) * 0.55;
      const rr = 34;
      const x = Math.sin(a) * rr, z = Math.cos(a) * rr;
      placeParts(parts, buildHouse(this.mats, {
        w: 8.5, d: 6.5, wallH: 3.6, roofH: 3.0, porch: true,
      }), x, 0, z, a + Math.PI);
      this._box(this.center.x + x, this.center.z + z, 5.0, 4.0, a + Math.PI);
    }

    /* ---- Meshleri kur ---- */
    const meshes = buildMeshes(parts);
    for (const m of meshes) this.group.add(m);
    this.group.position.copy(this.center);

    /* ---- Oyuncu doğuş noktası: kapının hemen içi ---- */
    const sp = 0.62;
    this.spawn = new THREE.Vector3(
      this.center.x + roadDir.x * R * sp,
      0,
      this.center.z + roadDir.z * R * sp,
    );
    this.spawn.y = this.terrain.heightAt(this.spawn.x, this.spawn.z);
    this.spawnFacing = ga + Math.PI;
  }

  addTo(scene) {
    scene.add(this.group);
    return this;
  }
}
