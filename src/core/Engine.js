/**
 * Engine.js — Render altyapısı.
 *
 * Mobil cihazlar hedef olduğu için kalite kademeleri cihaza göre seçiliyor:
 * piksel oranı, gölge çözünürlüğü, arazi bölüntüsü ve görüş mesafesi buna
 * göre değişiyor. Güneş ışığı oyuncuyu takip eden dar bir gölge kutusu
 * kullanıyor; böylece küçük gölge haritası bile keskin kalıyor.
 */
import * as THREE from 'three';

/** Cihaz sınıfı tahmini. */
export function detectQuality() {
  const ua = navigator.userAgent || '';
  const isMobile = /Android|iPhone|iPad|iPod|Mobile/i.test(ua);
  const cores = navigator.hardwareConcurrency || (isMobile ? 4 : 8);
  const mem = navigator.deviceMemory || (isMobile ? 4 : 8);
  const px = window.devicePixelRatio || 1;

  let tier;
  if (!isMobile && cores >= 8) tier = 'high';
  else if (cores >= 6 && mem >= 4) tier = 'medium';
  else tier = 'low';

  const presets = {
    low: {
      tier: 'low', pixelRatio: Math.min(px, 1.3), shadows: true, shadowSize: 1024,
      terrainSegments: 128, viewDistance: 380, treeCount: 700, antialias: false,
      shadowCam: 34,
    },
    medium: {
      tier: 'medium', pixelRatio: Math.min(px, 1.75), shadows: true, shadowSize: 1536,
      terrainSegments: 176, viewDistance: 520, treeCount: 1400, antialias: false,
      shadowCam: 42,
    },
    high: {
      tier: 'high', pixelRatio: Math.min(px, 2), shadows: true, shadowSize: 2048,
      terrainSegments: 220, viewDistance: 760, treeCount: 2400, antialias: true,
      shadowCam: 55,
    },
  };
  return presets[tier];
}

/* Gökyüzü kubbesi: ufuk-zenit gradyanı + güneş halesi. */
const SKY_VERT = `
varying vec3 vDir;
void main() {
  vDir = normalize(position);
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}`;

const SKY_FRAG = `
uniform vec3 uTop;
uniform vec3 uHorizon;
uniform vec3 uBottom;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
varying vec3 vDir;
void main() {
  vec3 d = normalize(vDir);
  float h = d.y;
  vec3 col;
  if (h > 0.0) {
    col = mix(uHorizon, uTop, pow(clamp(h, 0.0, 1.0), 0.55));
  } else {
    col = mix(uHorizon, uBottom, pow(clamp(-h, 0.0, 1.0), 0.5));
  }
  float sun = max(dot(d, normalize(uSunDir)), 0.0);
  col += uSunColor * pow(sun, 220.0) * 1.6;          // disk
  col += uSunColor * pow(sun, 12.0) * 0.22;          // hale
  gl_FragColor = vec4(col, 1.0);
}`;

export class Engine {
  constructor(canvas, quality) {
    this.quality = quality || detectQuality();
    const q = this.quality;

    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: q.antialias,
      powerPreference: 'high-performance',
      stencil: false,
    });
    this.renderer.setPixelRatio(q.pixelRatio);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    if (q.shadows) {
      this.renderer.shadowMap.enabled = true;
      this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    }

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(58, 1, 0.1, q.viewDistance * 2.6);

    this._buildSky();
    this._buildLights();

    this.clock = new THREE.Clock();
    this._onResize = () => this.resize();
    window.addEventListener('resize', this._onResize);
    window.addEventListener('orientationchange', this._onResize);
    this.resize();
  }

  _buildSky() {
    const q = this.quality;
    this.skyUniforms = {
      uTop: { value: new THREE.Color(0x2f6fb5) },
      uHorizon: { value: new THREE.Color(0xbcd4e6) },
      uBottom: { value: new THREE.Color(0x4a4a44) },
      uSunDir: { value: new THREE.Vector3(0.45, 0.62, 0.64).normalize() },
      uSunColor: { value: new THREE.Color(0xfff0d0) },
    };
    const geo = new THREE.SphereGeometry(q.viewDistance * 2.2, 24, 16);
    const mat = new THREE.ShaderMaterial({
      uniforms: this.skyUniforms,
      vertexShader: SKY_VERT,
      fragmentShader: SKY_FRAG,
      side: THREE.BackSide,
      depthWrite: false,
      fog: false,
    });
    this.sky = new THREE.Mesh(geo, mat);
    this.sky.frustumCulled = false;
    this.scene.add(this.sky);

    this.fogColor = new THREE.Color(0xbcd4e6);
    this.scene.fog = new THREE.Fog(this.fogColor, q.viewDistance * 0.30, q.viewDistance);
  }

  _buildLights() {
    const q = this.quality;
    this.hemi = new THREE.HemisphereLight(0xcfe4f5, 0x5c5340, 1.15);
    this.scene.add(this.hemi);

    this.sun = new THREE.DirectionalLight(0xfff2d8, 2.1);
    this.sun.position.set(90, 140, 120);
    if (q.shadows) {
      this.sun.castShadow = true;
      this.sun.shadow.mapSize.set(q.shadowSize, q.shadowSize);
      const c = this.sun.shadow.camera;
      c.near = 1; c.far = 420;
      c.left = -q.shadowCam; c.right = q.shadowCam;
      c.top = q.shadowCam; c.bottom = -q.shadowCam;
      this.sun.shadow.bias = -0.0006;
      this.sun.shadow.normalBias = 0.045;
    }
    this.scene.add(this.sun);
    this.scene.add(this.sun.target);

    this.ambient = new THREE.AmbientLight(0xffffff, 0.22);
    this.scene.add(this.ambient);
  }

  /** Gölge kutusunu ve gökyüzünü oyuncunun etrafında tutar. */
  follow(target) {
    const d = this.sun.shadow?.camera ? 1 : 1;
    this.sun.target.position.copy(target);
    this.sun.position.set(
      target.x + 90 * d, target.y + 140 * d, target.z + 120 * d);
    this.sun.target.updateMatrixWorld();
    this.sky.position.set(target.x, 0, target.z);
  }

  /** Krallık biyomuna göre atmosfer tonu. */
  setAtmosphere(biome) {
    const presets = {
      grass: { top: 0x2f6fb5, hor: 0xc3d8e8, fog: 0xc3d8e8, hemiSky: 0xcfe4f5, hemiGr: 0x5c5340, sun: 0xfff2d8 },
      sand: { top: 0x4a86c4, hor: 0xe6d6ae, fog: 0xe3d3ab, hemiSky: 0xf0e2c0, hemiGr: 0x6e5c3a, sun: 0xfff0c0 },
      snow: { top: 0x5f8fbd, hor: 0xdde8f2, fog: 0xdae6f2, hemiSky: 0xe4eef8, hemiGr: 0x6a707a, sun: 0xf2f6ff },
    };
    const p = presets[biome] || presets.grass;
    this.skyUniforms.uTop.value.setHex(p.top);
    this.skyUniforms.uHorizon.value.setHex(p.hor);
    this.fogColor.setHex(p.fog);
    this.scene.fog.color.copy(this.fogColor);
    this.hemi.color.setHex(p.hemiSky);
    this.hemi.groundColor.setHex(p.hemiGr);
    this.sun.color.setHex(p.sun);
  }

  resize() {
    const w = window.innerWidth;
    const h = window.innerHeight;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    // Dar ekranlarda görüş açısını biraz aç ki karakter sıkışmasın
    this.camera.fov = this.camera.aspect < 0.75 ? 68 : 58;
    this.camera.updateProjectionMatrix();
  }

  render() {
    this.renderer.render(this.scene, this.camera);
  }

  dispose() {
    window.removeEventListener('resize', this._onResize);
    window.removeEventListener('orientationchange', this._onResize);
    this.renderer.dispose();
  }
}
