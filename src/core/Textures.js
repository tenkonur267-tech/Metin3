/**
 * Textures.js — Prosedürel doku üretimi.
 *
 * Oyun hiçbir harici görsel dosyaya bağlı değil: bütün dokular canvas 2D
 * ile çalışma anında üretilir. Bu sayede depo hafif kalır ve oyun
 * çevrimdışı / dosya sunucusu olmadan da açılır.
 */
import * as THREE from 'three';

const cache = new Map();

function canvas(size) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  return c;
}

function finish(c, { repeat = 1, srgb = true, aniso = 8 } = {}) {
  const tex = new THREE.CanvasTexture(c);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(repeat, repeat);
  tex.anisotropy = aniso;
  if (srgb) tex.colorSpace = THREE.SRGBColorSpace;
  tex.needsUpdate = true;
  return tex;
}

/** Deterministik pseudo-random (aynı doku her açılışta aynı görünsün diye). */
function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

function noiseFill(ctx, size, base, amount, seed = 7) {
  const rand = rng(seed);
  const img = ctx.getImageData(0, 0, size, size);
  const d = img.data;
  for (let i = 0; i < d.length; i += 4) {
    const n = (rand() - 0.5) * amount;
    d[i] = THREE.MathUtils.clamp(d[i] + n, 0, 255);
    d[i + 1] = THREE.MathUtils.clamp(d[i + 1] + n, 0, 255);
    d[i + 2] = THREE.MathUtils.clamp(d[i + 2] + n, 0, 255);
  }
  ctx.putImageData(img, 0, 0);
  void base;
}

/* ------------------------------------------------------------------ */
/* Çatı kiremiti — Doğu Asya mimarisinin imzası olan yarım silindir sıralar */
/* ------------------------------------------------------------------ */
function roofTiles(colorA, colorB) {
  const size = 256;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = colorB;
  ctx.fillRect(0, 0, size, size);

  const rows = 8;
  const rowH = size / rows;
  for (let r = 0; r < rows; r++) {
    const y = r * rowH;
    // Kiremit sırası gövdesi
    const g = ctx.createLinearGradient(0, y, 0, y + rowH);
    g.addColorStop(0, colorA);
    g.addColorStop(0.55, colorB);
    g.addColorStop(1, 'rgba(0,0,0,0.55)');
    ctx.fillStyle = g;
    ctx.fillRect(0, y, size, rowH);

    // Dikey oluklar
    const cols = 10;
    const colW = size / cols;
    for (let i = 0; i < cols; i++) {
      const x = i * colW + (r % 2 ? colW * 0.5 : 0);
      const gg = ctx.createLinearGradient(x, 0, x + colW, 0);
      gg.addColorStop(0, 'rgba(0,0,0,0.35)');
      gg.addColorStop(0.5, 'rgba(255,255,255,0.22)');
      gg.addColorStop(1, 'rgba(0,0,0,0.35)');
      ctx.fillStyle = gg;
      ctx.fillRect(x, y, colW * 0.92, rowH * 0.92);
    }
    // Sıra alt gölgesi
    ctx.fillStyle = 'rgba(0,0,0,0.4)';
    ctx.fillRect(0, y + rowH - 3, size, 3);
  }
  noiseFill(ctx, size, null, 18, 11);
  return c;
}

/* ------------------------------------------------------------------ */
/* Ahşap — kiriş, sütun ve zeminler için                               */
/* ------------------------------------------------------------------ */
function wood(base, dark) {
  const size = 256;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = base;
  ctx.fillRect(0, 0, size, size);
  const rand = rng(23);
  for (let i = 0; i < 90; i++) {
    const y = rand() * size;
    ctx.strokeStyle = `rgba(0,0,0,${0.04 + rand() * 0.12})`;
    ctx.lineWidth = 0.5 + rand() * 2.2;
    ctx.beginPath();
    ctx.moveTo(0, y);
    for (let x = 0; x <= size; x += 16) {
      ctx.lineTo(x, y + Math.sin((x / size) * Math.PI * 2 + i) * 2.5);
    }
    ctx.stroke();
  }
  // Budaklar
  for (let i = 0; i < 4; i++) {
    const x = rand() * size, y = rand() * size;
    const r = 4 + rand() * 8;
    const g = ctx.createRadialGradient(x, y, 1, x, y, r);
    g.addColorStop(0, dark);
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }
  noiseFill(ctx, size, null, 14, 5);
  return c;
}

/* ------------------------------------------------------------------ */
/* Taş duvar — sur, temel ve kule gövdeleri                            */
/* ------------------------------------------------------------------ */
function stoneWall(base, mortar) {
  const size = 256;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = mortar;
  ctx.fillRect(0, 0, size, size);
  const rand = rng(41);
  const rows = 7;
  const rowH = size / rows;
  for (let r = 0; r < rows; r++) {
    const y = r * rowH;
    let x = (r % 2 ? -rowH * 0.7 : 0);
    while (x < size) {
      const w = rowH * (1.1 + rand() * 1.3);
      const shade = 0.78 + rand() * 0.35;
      ctx.fillStyle = shadeColor(base, shade);
      roundRect(ctx, x + 2, y + 2, w - 4, rowH - 4, 3);
      ctx.fill();
      // üst ışık kenarı
      ctx.fillStyle = 'rgba(255,255,255,0.14)';
      ctx.fillRect(x + 3, y + 3, w - 6, 2);
      // alt gölge
      ctx.fillStyle = 'rgba(0,0,0,0.28)';
      ctx.fillRect(x + 3, y + rowH - 6, w - 6, 3);
      x += w;
    }
  }
  noiseFill(ctx, size, null, 22, 3);
  return c;
}

/* ------------------------------------------------------------------ */
/* Sıva / kerpiç duvar dolgusu                                         */
/* ------------------------------------------------------------------ */
function plaster(base) {
  const size = 128;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = base;
  ctx.fillRect(0, 0, size, size);
  const rand = rng(97);
  for (let i = 0; i < 260; i++) {
    ctx.fillStyle = `rgba(0,0,0,${rand() * 0.06})`;
    ctx.beginPath();
    ctx.arc(rand() * size, rand() * size, rand() * 6, 0, Math.PI * 2);
    ctx.fill();
  }
  noiseFill(ctx, size, null, 16, 13);
  return c;
}

/* ------------------------------------------------------------------ */
/* Kağıt kapı (shoji) — ızgaralı yarı saydam panel                     */
/* ------------------------------------------------------------------ */
function paperPanel(paperCol, frameCol) {
  const size = 128;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = paperCol;
  ctx.fillRect(0, 0, size, size);
  noiseFill(ctx, size, null, 10, 71);
  ctx.strokeStyle = frameCol;
  ctx.lineWidth = 5;
  ctx.strokeRect(2.5, 2.5, size - 5, size - 5);
  ctx.lineWidth = 3;
  for (let i = 1; i < 4; i++) {
    ctx.beginPath();
    ctx.moveTo((size / 4) * i, 0);
    ctx.lineTo((size / 4) * i, size);
    ctx.stroke();
  }
  for (let i = 1; i < 5; i++) {
    ctx.beginPath();
    ctx.moveTo(0, (size / 5) * i);
    ctx.lineTo(size, (size / 5) * i);
    ctx.stroke();
  }
  return c;
}

/* ------------------------------------------------------------------ */
/* Zemin dokuları                                                      */
/* ------------------------------------------------------------------ */
function groundGrass() {
  const size = 256;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#4a6b32';
  ctx.fillRect(0, 0, size, size);
  const rand = rng(17);
  // Geniş renk yamaları — tek düze bir yeşil yerine canlı bir zemin
  for (let i = 0; i < 26; i++) {
    const x = rand() * size, y = rand() * size, r = 18 + rand() * 46;
    const g = ctx.createRadialGradient(x, y, 1, x, y, r);
    g.addColorStop(0, `hsla(${78 + rand() * 30}, 34%, ${22 + rand() * 12}%, 0.55)`);
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }
  for (let i = 0; i < 4200; i++) {
    const x = rand() * size, y = rand() * size;
    const l = 2 + rand() * 4;
    ctx.strokeStyle = `hsl(${86 + rand() * 26}, ${26 + rand() * 22}%, ${19 + rand() * 22}%)`;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x + (rand() - 0.5) * 3, y - l);
    ctx.stroke();
  }
  noiseFill(ctx, size, null, 14, 29);
  return c;
}

function groundSand() {
  const size = 256;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#c2a468';
  ctx.fillRect(0, 0, size, size);
  const rand = rng(53);
  for (let i = 0; i < 60; i++) {
    const k = 0.82 + rand() * 0.34;
    ctx.strokeStyle = `rgba(${Math.round(168 * k)},${Math.round(140 * k)},${Math.round(92 * k)},0.35)`;
    ctx.lineWidth = 1 + rand() * 4;
    ctx.beginPath();
    const y = rand() * size;
    ctx.moveTo(0, y);
    for (let x = 0; x <= size; x += 12) ctx.lineTo(x, y + Math.sin(x * 0.05 + i) * 6);
    ctx.stroke();
  }
  noiseFill(ctx, size, null, 16, 61);
  return c;
}

function groundSnow() {
  const size = 256;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#e8eef5';
  ctx.fillRect(0, 0, size, size);
  const rand = rng(83);
  for (let i = 0; i < 420; i++) {
    const k = 0.90 + rand() * 0.10;
    ctx.fillStyle = `rgba(${Math.round(206 * k)},${Math.round(220 * k)},${Math.round(238 * k)},0.55)`;
    ctx.beginPath();
    ctx.arc(rand() * size, rand() * size, 1.5 + rand() * 6, 0, Math.PI * 2);
    ctx.fill();
  }
  // Parıltı taneleri
  for (let i = 0; i < 260; i++) {
    ctx.fillStyle = 'rgba(255,255,255,0.75)';
    ctx.fillRect(rand() * size, rand() * size, 1.2, 1.2);
  }
  noiseFill(ctx, size, null, 9, 91);
  return c;
}

function groundPath() {
  const size = 256;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#8a7454';
  ctx.fillRect(0, 0, size, size);
  const rand = rng(37);
  // Çakıllar: kanallar tek bir parlaklık değişkeninden türetiliyor, yoksa
  // bağımsız rastgelelik doygun kırmızı/mor lekeler üretiyor.
  for (let i = 0; i < 900; i++) {
    const x = rand() * size, y = rand() * size, r = 0.7 + rand() * 2.6;
    const k = 0.72 + rand() * 0.55;
    ctx.fillStyle = `rgba(${Math.round(126 * k)},${Math.round(108 * k)},${Math.round(80 * k)},0.55)`;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }
  // Tekerlek izleri
  for (let i = 0; i < 5; i++) {
    ctx.strokeStyle = 'rgba(70,58,42,0.16)';
    ctx.lineWidth = 6 + rand() * 10;
    ctx.beginPath();
    const x0 = rand() * size;
    ctx.moveTo(x0, 0);
    for (let y = 0; y <= size; y += 16) ctx.lineTo(x0 + Math.sin(y * 0.03 + i) * 7, y);
    ctx.stroke();
  }
  noiseFill(ctx, size, null, 18, 43);
  return c;
}

/* ------------------------------------------------------------------ */
/* Kumaş / bayrak — krallık sancakları                                 */
/* ------------------------------------------------------------------ */
function banner(bg, fg) {
  const w = 128, h = 256;
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const ctx = c.getContext('2d');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = fg;
  ctx.lineWidth = 6;
  ctx.strokeRect(10, 10, w - 20, h - 20);
  // Stilize mühür motifi
  ctx.save();
  ctx.translate(w / 2, h * 0.38);
  ctx.strokeStyle = fg;
  ctx.lineWidth = 7;
  ctx.beginPath();
  ctx.arc(0, 0, 30, 0, Math.PI * 2);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(-16, -16); ctx.lineTo(16, 16);
  ctx.moveTo(16, -16); ctx.lineTo(-16, 16);
  ctx.stroke();
  ctx.restore();
  // Alt püskül şeritleri
  ctx.fillStyle = fg;
  for (let i = 0; i < 5; i++) ctx.fillRect(16 + i * 20, h - 34, 10, 22);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

/* ------------------------------------------------------------------ */
/* Zırh / kumaş — karakter için                                        */
/* ------------------------------------------------------------------ */
function armorPlate(base, trim) {
  const size = 128;
  const c = canvas(size);
  const ctx = c.getContext('2d');
  ctx.fillStyle = base;
  ctx.fillRect(0, 0, size, size);
  // Lamel zırh şeritleri
  const rows = 6;
  const rowH = size / rows;
  for (let r = 0; r < rows; r++) {
    const y = r * rowH;
    const g = ctx.createLinearGradient(0, y, 0, y + rowH);
    g.addColorStop(0, 'rgba(255,255,255,0.20)');
    g.addColorStop(0.6, 'rgba(0,0,0,0.0)');
    g.addColorStop(1, 'rgba(0,0,0,0.42)');
    ctx.fillStyle = g;
    ctx.fillRect(0, y, size, rowH);
    ctx.strokeStyle = trim;
    ctx.lineWidth = 2;
    ctx.strokeRect(1, y + 1, size - 2, rowH - 2);
    // perçinler
    ctx.fillStyle = trim;
    for (let i = 0; i < 5; i++) {
      ctx.beginPath();
      ctx.arc(12 + i * 26, y + rowH * 0.5, 2.4, 0, Math.PI * 2);
      ctx.fill();
    }
  }
  noiseFill(ctx, size, null, 12, 67);
  return c;
}

/* ------------------------------------------------------------------ */
/* Yardımcılar                                                          */
/* ------------------------------------------------------------------ */
function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function shadeColor(hex, factor) {
  const col = new THREE.Color(hex);
  col.multiplyScalar(factor);
  return '#' + col.getHexString();
}

/* ------------------------------------------------------------------ */
/* Genel API — hepsi önbelleklenir                                     */
/* ------------------------------------------------------------------ */
export function getTexture(name, opts = {}) {
  const key = name + JSON.stringify(opts);
  if (cache.has(key)) return cache.get(key);
  let tex;
  switch (name) {
    case 'roof':      tex = finish(roofTiles(opts.a || '#5a6472', opts.b || '#2f3540'), { repeat: opts.repeat ?? 3 }); break;
    case 'wood':      tex = finish(wood(opts.a || '#6b4a2f', opts.b || '#33210f'), { repeat: opts.repeat ?? 2 }); break;
    case 'stone':     tex = finish(stoneWall(opts.a || '#8f8a7e', opts.b || '#5c574d'), { repeat: opts.repeat ?? 2 }); break;
    case 'plaster':   tex = finish(plaster(opts.a || '#d9cfb8'), { repeat: opts.repeat ?? 2 }); break;
    case 'paper':     tex = finish(paperPanel(opts.a || '#efe6cd', opts.b || '#5b3d24'), { repeat: opts.repeat ?? 1 }); break;
    case 'grass':     tex = finish(groundGrass(), { repeat: opts.repeat ?? 1 }); break;
    case 'sand':      tex = finish(groundSand(), { repeat: opts.repeat ?? 1 }); break;
    case 'snow':      tex = finish(groundSnow(), { repeat: opts.repeat ?? 1 }); break;
    case 'path':      tex = finish(groundPath(), { repeat: opts.repeat ?? 1 }); break;
    case 'armor':     tex = finish(armorPlate(opts.a || '#7a2230', opts.b || '#d9b45a'), { repeat: opts.repeat ?? 1 }); break;
    case 'banner':    tex = banner(opts.a || '#8e1f2a', opts.b || '#e8d59a'); break;
    default: throw new Error('Bilinmeyen doku: ' + name);
  }
  cache.set(key, tex);
  return tex;
}

export function disposeTextures() {
  for (const t of cache.values()) t.dispose();
  cache.clear();
}
