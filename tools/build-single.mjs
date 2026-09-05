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
// Paket tek dosya olarak dağıtıldığı için yanında assets klasörü olmaz;
// karakter modeli aramasını baştan kapatıyoruz.
const js = 'globalThis.__METIN3_SINGLE_FILE__=true;\n' + bundle.outputFiles[0].text;

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
