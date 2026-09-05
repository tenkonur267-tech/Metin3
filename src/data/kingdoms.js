/**
 * kingdoms.js — Üç krallığın kimliği.
 *
 * Metin2 evrenindeki üç imparatorluk: Shinsoo (kırmızı), Chunjo (sarı) ve
 * Jinno (mavi). Her krallığın kendi başlangıç köyü, iklimi ve renk paleti
 * var; köy üreticisi bu tanımları okuyarak dünyayı kurar.
 */

export const KINGDOMS = [
  {
    id: 'shinsoo',
    name: 'Shinsoo',
    title: 'Kızıl Ejderha İmparatorluğu',
    village: 'Yeonan Köyü',
    motto: 'Cesaret ateşten doğar.',
    color: 0xc0392b,
    colorHex: '#c0392b',
    biome: 'grass',
    // Dünya üzerindeki köy merkezi
    center: { x: 0, z: -330 },
    // Kapının baktığı yön (radyan, +Z eksenine göre)
    facing: 0,
    theme: {
      roofA: '#7d2f30', roofB: '#3d1516',
      woodA: '#6b4326', woodB: '#2e1a0c',
      stoneA: '#9a9083', stoneB: '#5f584c',
      plaster: '#e0d3bb',
      paper: '#f2e7cc',
      pillar: 0x8e2f2a,
      accent: 0xc0392b,
      trim: 0xd9b45a,
      lantern: 0xff6b4a,
    },
    // Karakter zırh paleti
    armor: { base: '#7a2230', trim: '#d9b45a', cloth: 0x8e1f2a },
  },
  {
    id: 'chunjo',
    name: 'Chunjo',
    title: 'Altın Kaplan İmparatorluğu',
    village: 'Bakra Köyü',
    motto: 'Bilgelik altından değerlidir.',
    color: 0xd4a017,
    colorHex: '#d4a017',
    biome: 'sand',
    center: { x: 300, z: 210 },
    facing: Math.PI * 0.75,
    theme: {
      roofA: '#8a6a2a', roofB: '#3f2f10',
      woodA: '#7a5a34', woodB: '#3a2712',
      stoneA: '#b3a483', stoneB: '#736750',
      plaster: '#efe2c0',
      paper: '#f7eed2',
      pillar: 0xa8722a,
      accent: 0xd4a017,
      trim: 0xf0d98a,
      lantern: 0xffd166,
    },
    armor: { base: '#8a6a1f', trim: '#f0d98a', cloth: 0xd4a017 },
  },
  {
    id: 'jinno',
    name: 'Jinno',
    title: 'Mavi Kaplumbağa İmparatorluğu',
    village: 'Joan Köyü',
    motto: 'Sabır, buzu bile deler.',
    color: 0x2e73b8,
    colorHex: '#2e73b8',
    biome: 'snow',
    center: { x: -300, z: 210 },
    facing: -Math.PI * 0.75,
    theme: {
      roofA: '#33587e', roofB: '#16243a',
      woodA: '#5a5347', woodB: '#241f18',
      stoneA: '#9aa3ad', stoneB: '#5d656e',
      plaster: '#dfe6ee',
      paper: '#eaf1f8',
      pillar: 0x2b5f96,
      accent: 0x2e73b8,
      trim: 0xbcd6ee,
      lantern: 0x7fc4ff,
    },
    armor: { base: '#204a72', trim: '#bcd6ee', cloth: 0x2e73b8 },
  },
];

export function getKingdom(id) {
  return KINGDOMS.find((k) => k.id === id) || KINGDOMS[0];
}
