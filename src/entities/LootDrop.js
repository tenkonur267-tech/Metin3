/**
 * LootDrop.js — Yere düşen eşya.
 *
 * Metin2'de eşyalar yere düşer ve üstüne gidilerek alınır. Burada da öyle:
 * dönen bir parça ve altında kademe renginde bir hale; oyuncu yaklaşınca
 * kendiliğinden toplanıyor (mobilde küçük bir hedefe dokunmak zor).
 */
import * as THREE from 'three';
import { getTexture } from '../core/Textures.js';
import { buildDropModel } from './ArmorSet.js';

const _v = new THREE.Vector3();

export class LootDrop {
  /**
   * @param {object} item  eşya örneği
   * @param {THREE.Vector3} pos
   * @param {Terrain} terrain
   */
  constructor(item, pos, terrain, theme = null) {
    this.item = item;
    this.terrain = terrain;
    this.alinabilir = false;     // düşme animasyonu bitmeden alınamaz
    this.alindi = false;
    this.time = 0;
    this.omur = 90;              // saniye; sonra kaybolur

    const renk = new THREE.Color(item.renk);
    this.group = new THREE.Group();
    this.group.name = 'loot-' + item.tplId;

    const y = terrain.heightAt(pos.x, pos.z);
    this.taban = y;
    this.group.position.set(pos.x, y + 1.2, pos.z);

    /*
     * Eşya parçası: Metin2'de yere düşen eşya kendi modeliyle görünür, bu
     * yüzden görünümü olan eşyalar için gerçek model küçültülerek konuyor.
     * Takıların modeli yok; onlar kademe renginde bir kristal olarak düşüyor.
     */
    const model = theme ? buildDropModel(item, theme) : buildDropModel(item);
    if (model) {
      this.mesh = model;
      this.mat = null;
      // Gerçek model dik dursun, yalnızca kendi ekseninde dönsün
      this.mesh.rotation.z = 0.35;
      this.gercekModel = true;
    } else {
      const geo = new THREE.OctahedronGeometry(0.16, 0);
      this.mat = new THREE.MeshLambertMaterial({
        color: renk,
        emissive: renk.clone().multiplyScalar(0.45),
      });
      this.mesh = new THREE.Mesh(geo, this.mat);
      this.mesh.castShadow = true;
    }
    this.group.add(this.mesh);

    // Zeminde kademe renginde hale
    const auraGeo = new THREE.PlaneGeometry(0.9, 0.9).rotateX(-Math.PI / 2);
    this.auraMat = new THREE.MeshBasicMaterial({
      map: getTexture('auraRing'),
      color: renk,
      transparent: true,
      opacity: 0.55,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });
    this.aura = new THREE.Mesh(auraGeo, this.auraMat);
    this.aura.renderOrder = 3;
    this.group.add(this.aura);

    this.vy = 2.4;               // yere düşerken küçük bir sıçrama
  }

  /**
   * @param {number} dt
   * @param {THREE.Vector3} playerPos
   * @returns {boolean} toplandıysa ya da ömrü bittiyse true
   */
  update(dt, playerPos) {
    this.time += dt;
    const g = this.group;

    // Düşüş
    const hedefY = this.taban + 0.42;
    if (g.position.y > hedefY) {
      this.vy -= 9.5 * dt;
      g.position.y = Math.max(hedefY, g.position.y + this.vy * dt);
    } else {
      this.alinabilir = true;
      g.position.y = hedefY + Math.sin(this.time * 2.2) * 0.06;
    }

    this.mesh.rotation.y += dt * 1.7;
    if (!this.gercekModel) this.mesh.rotation.x += dt * 0.8;
    this.aura.position.y = this.taban - g.position.y + 0.03;
    this.aura.rotation.y += dt * 0.9;
    this.auraMat.opacity = 0.42 + Math.sin(this.time * 3.0) * 0.12;

    // Ömür sonunda sönerek kaybol
    const kalan = this.omur - this.time;
    if (kalan < 4) {
      const k = Math.max(0, kalan / 4);
      this.mesh.traverse?.((o) => {
        if (!o.material) return;
        o.material.transparent = true;
        o.material.opacity = k;
      });
      if (this.mat) { this.mat.opacity = k; this.mat.transparent = true; }
      this.auraMat.opacity *= k;
      if (kalan <= 0) return true;
    }

    // Yaklaşınca toplanır
    if (this.alinabilir && !this.alindi) {
      const d = _v.set(playerPos.x - g.position.x, 0, playerPos.z - g.position.z).length();
      if (d < 1.4) { this.alindi = true; return true; }
    }
    return false;
  }

  addTo(scene) { scene.add(this.group); return this; }

  dispose(scene) {
    scene.remove(this.group);
    this.mesh.traverse((o) => {
      o.geometry?.dispose();
      if (Array.isArray(o.material)) o.material.forEach((m) => m.dispose());
      else o.material?.dispose();
    });
    this.aura.geometry.dispose();
    this.auraMat.dispose();
  }
}
