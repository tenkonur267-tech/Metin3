/**
 * Collision.js — Uzamsal ızgaralı çarpışma dünyası.
 *
 * Üç köy + binlerce ağaç, kare başına lineer taranamayacak kadar çok cisim
 * demek. Cisimler hücrelere dağıtılıyor ve sorgu yalnızca oyuncunun
 * bulunduğu hücre komşuluğuna bakıyor.
 */
export class CollisionWorld {
  constructor(cellSize = 16) {
    this.cell = cellSize;
    this.grid = new Map();
    this.count = 0;
  }

  _key(cx, cz) { return cx * 73856093 ^ cz * 19349663; }

  _insert(cx, cz, obj) {
    const k = this._key(cx, cz);
    let bucket = this.grid.get(k);
    if (!bucket) { bucket = []; this.grid.set(k, bucket); }
    bucket.push(obj);
  }

  /** Bir cismi kapsadığı bütün hücrelere ekler. */
  add(obj) {
    const ext = obj.kind === 'circle'
      ? obj.r
      : Math.hypot(obj.hw, obj.hd);
    const minX = Math.floor((obj.x - ext) / this.cell);
    const maxX = Math.floor((obj.x + ext) / this.cell);
    const minZ = Math.floor((obj.z - ext) / this.cell);
    const maxZ = Math.floor((obj.z + ext) / this.cell);
    for (let cx = minX; cx <= maxX; cx++) {
      for (let cz = minZ; cz <= maxZ; cz++) this._insert(cx, cz, obj);
    }
    this.count++;
  }

  addAll(list) { for (const o of list) this.add(o); return this; }

  /**
   * Verilen noktanın çevresindeki cisimleri toplar.
   * @returns {Array} tekrarsız cisim listesi (out yeniden kullanılır)
   */
  query(x, z, out = []) {
    out.length = 0;
    const cx = Math.floor(x / this.cell);
    const cz = Math.floor(z / this.cell);
    for (let i = -1; i <= 1; i++) {
      for (let j = -1; j <= 1; j++) {
        const bucket = this.grid.get(this._key(cx + i, cz + j));
        if (!bucket) continue;
        for (const o of bucket) {
          if (o._qtag === this._tick) continue;
          o._qtag = this._tick;
          out.push(o);
        }
      }
    }
    this._tick = (this._tick || 0) + 1;
    return out;
  }
}
