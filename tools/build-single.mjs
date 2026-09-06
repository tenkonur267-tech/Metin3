/**
 * build-single.mjs — Tek dosyalık dağıtım paketi.
 *
 * Oyun geliştirme sırasında derlemesiz çalışıyor (ES modülleri + import map),
 * ama tek bir HTML dosyası olarak paylaşmak istediğimizde her şeyin —
 * three.js dahil — gömülü olması gerekiyor. Bu betik iki çıktı üretir:
 *
 *   dist/index.html    tarayıcıda doğrudan açılabilen bağımsız sayfa
 *   dist/artifact.html <head>/<body> etiketi taşımayan gövde parçası
 *
 * Kullanım: node tools/build-single.mjs
 */
import * as esbuild from 'esbuild';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const r = (...p) => path.join(root, ...p);

/* 1. JavaScript'i tek pakete topla ('three' gömülü sürüme yönlendirilir) */
const bundle = await esbuild.build({
  entryPoints: [r('src/main.js')],
  bundle: true,
  format: 'esm',
  minify: true,
  target: ['es2020'],
  legalComments: 'none',
  alias: { three: r('vendor/three/three.module.js') },
  write: false,
});
/*
 * Tek dosya sürümünde yanında assets klasörü olmadığı için karakter modeli
 * ağdan istenemez; etkinse doğrudan pakete gömülüyor. Böylece paylaşılan
 * bağlantıda da oyundaki gerçek karakter görünüyor.
 */
let preamble = 'globalThis.__METIN3_SINGLE_FILE__=true;\n';
try {
  const cfg = JSON.parse(await fs.readFile(r('assets/characters/warrior.json'), 'utf8'));
  if (cfg.enabled && cfg.file) {
    const modelPath = r('assets/characters', cfg.file);
    const bytes = await fs.readFile(modelPath);
    const mime = cfg.file.endsWith('.glb') ? 'model/gltf-binary' : 'application/octet-stream';
    const embedded = { ...cfg, file: `data:${mime};base64,${bytes.toString('base64')}` };
    console.log(`  gömülü karakter: ${cfg.file} (${(bytes.length / 1048576).toFixed(2)} MB)`);
    // Animasyon kaynağı ayrı bir dosyaysa o da gömülür (mocap sürücüsü)
    if (cfg.animationFile) {
      const animBytes = await fs.readFile(r('assets/characters', cfg.animationFile));
      embedded.animationFile = `data:model/gltf-binary;base64,${animBytes.toString('base64')}`;
      console.log(`  gömülü animasyon: ${cfg.animationFile} `
        + `(${(animBytes.length / 1048576).toFixed(2)} MB)`);
    }
    preamble += `globalThis.__METIN3_CHARACTER__=${JSON.stringify(embedded)};\n`;
  }
} catch (err) {
  console.log('  gömülü karakter yok:', err.message);
}
const js = preamble + bundle.outputFiles[0].text;

/* 2. Stil ve oyun işaretlemesini al */
const css = await fs.readFile(r('styles/ui.css'), 'utf8');
const indexHtml = await fs.readFile(r('index.html'), 'utf8');
const markup = indexHtml.match(/<div id="game">[\s\S]*?<\/div>\s*(?=<script)/)?.[0]?.trim();
if (!markup) throw new Error('index.html içinde #game bloğu bulunamadı');

const TITLE = 'Metin3';
const head = `<title>${TITLE}</title>
<style>
${css}
</style>`;

/* 3a. Artifact için: <head>/<body> etiketi yok, içerik doğrudan yazılır */
const fragment = `${head}
${markup}
<script type="module">
${js}
</script>
`;

/* 3b. Bağımsız sayfa: aynı içerik tam bir HTML iskeletiyle */
const standalone = `<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
<meta name="theme-color" content="#0a0d12">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'><text y='26' font-size='26'>%E2%9A%94</text></svg>">
${head}
</head>
<body>
${markup}
<script type="module">
${js}
</script>
</body>
</html>
`;

await fs.mkdir(r('dist'), { recursive: true });
await fs.writeFile(r('dist/artifact.html'), fragment);
await fs.writeFile(r('dist/index.html'), standalone);

const kb = (s) => (Buffer.byteLength(s) / 1024).toFixed(0) + ' KB';
console.log(`dist/index.html    ${kb(standalone)}`);
console.log(`dist/artifact.html ${kb(fragment)}`);
console.log(`  js ${kb(js)} · css ${kb(css)}`);
