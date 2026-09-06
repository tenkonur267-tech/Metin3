/**
 * Warrior.js — Savaşçı sınıfı karakteri.
 *
 * Model ve animasyonlar tamamen kod içinde üretiliyor: gövde parçaları bir
 * eklem hiyerarşisine asılı, animasyonlar da bu eklemlerin dönüşlerini
 * zamana göre hesaplayan poz fonksiyonları. Dışarıdan model veya animasyon
 * dosyası gerekmiyor; karakter krallık renklerine göre yeniden boyanıyor.
 *
 * Siluet, klasik Doğu Asya ağır piyadesine dayanıyor: geniş omuzlar, kubbeli
 * omuzluklar, lamel gövde zırhı, dört panelli zırh eteği ve topuzlu saç.
 * Etek panelleri ile pelerin poz sisteminin dışında, hıza ve dönüşe tepki
 * veren ikincil hareketle sürülüyor — asıl canlılık hissini bu veriyor.
 */
import * as THREE from 'three';
import { getTexture } from '../core/Textures.js';

/* Animasyonun doğrudan sürdüğü eklemler. */
export const JOINTS = [
  'hips', 'spine', 'chest', 'neck', 'head',
  'shoulderL', 'armL', 'foreArmL', 'handL',
  'shoulderR', 'armR', 'foreArmR', 'handR',
  'thighL', 'shinL', 'footL',
  'thighR', 'shinR', 'footR',
];

/** Boş bir poz: her eklem için [rx, ry, rz] + kök ötelemesi. */
export function blankPose() {
  const p = { _rootY: 0, _rootZ: 0, _rootRoll: 0, _rootYaw: 0 };
  for (const j of JOINTS) p[j] = [0, 0, 0];
  return p;
}

export function lerpPose(out, a, b, t) {
  for (const j of JOINTS) {
    const A = a[j], B = b[j], O = out[j];
    O[0] = A[0] + (B[0] - A[0]) * t;
    O[1] = A[1] + (B[1] - A[1]) * t;
    O[2] = A[2] + (B[2] - A[2]) * t;
  }
  out._rootY = a._rootY + (b._rootY - a._rootY) * t;
  out._rootZ = a._rootZ + (b._rootZ - a._rootZ) * t;
  out._rootRoll = a._rootRoll + (b._rootRoll - a._rootRoll) * t;
  out._rootYaw = a._rootYaw + (b._rootYaw - a._rootYaw) * t;
  return out;
}

const smooth = THREE.MathUtils.smoothstep;
const TAU = Math.PI * 2;
const clamp = THREE.MathUtils.clamp;

/* ------------------------------------------------------------------ */
/* Bacak ters kinematiği (IK)                                          */
/*                                                                     */
/* Uyluk/baldır açılarını elle yazmak, ayağı sabit bir kalça            */
/* yüksekliğinden sarkaç gibi savurur: basma evresi oluşmaz, ayak       */
/* zeminde kayar. Onun yerine ayağın YÖRÜNGESİNİ yazıp açıları buradan  */
/* çözüyoruz — basma evresinde ayak yerde durur, gövde onun üstünden    */
/* geçer.                                                              */
/* ------------------------------------------------------------------ */
export const LEG = { thigh: 0.45, shin: 0.43, hipDrop: 0.07, restHipY: 0.98 };

/**
 * Yürüyüş parametreleri.
 *
 * `contact` bir bacağın döngü içinde yerde kaldığı oran. Kaymasız yürüyüş
 * için döngü başına kat edilen mesafe zorunlu olarak `stride / contact`
 * olur — faz hızı bundan türetildiği için kayma tanım gereği sıfırlanır.
 * `hipY` de adım boyunu sınırlar: ayak, kalçadan bacak boyundan uzağa
 * uzanamaz.
 */
export const GAIT = {
  walk: { stride: 0.70, contact: 0.62, lift: 0.13, hipY: 0.84, bob: 0.018 },
  run: { stride: 0.96, contact: 0.38, lift: 0.30, hipY: 0.80, bob: 0.050 },
};
for (const g of Object.values(GAIT)) g.cycleDistance = g.stride / g.contact;

/**
 * İki kemikli bacak IK'sı: ayağın uyluk eklemine göre hedefinden uyluk ve
 * baldır açılarını kosinüs teoremiyle çözer. Diz daima öne bükülür.
 * @param {number} dz  ayağın ileri/geri konumu (+ ileri)
 * @param {number} dy  ayağın düşey konumu (negatif = kalçanın altında)
 */
function legAngles(dz, dy) {
  const a = LEG.thigh, b = LEG.shin;
  let d = Math.hypot(dz, dy);
  const maxD = (a + b) * 0.998;
  const minD = Math.abs(a - b) + 0.03;
  if (d > maxD) { const k = maxD / d; dz *= k; dy *= k; d = maxD; }
  if (d < minD) d = minD;
  // Uyluk ekseni yerel -Y; +X dönüşü bacağı geriye atar
  const toFoot = Math.atan2(-dz, -dy);
  const cosA = clamp((a * a + d * d - b * b) / (2 * a * d), -1, 1);
  const cosG = clamp((a * a + b * b - d * d) / (2 * a * b), -1, 1);
  return { thigh: toFoot - Math.acos(cosA), shin: Math.PI - Math.acos(cosG) };
}

/** Ayak yörüngesi: basma evresinde yerde geriye, salınımda havada ileri. */
function footPath(ph, g) {
  const t = ((ph % TAU) + TAU) % TAU;
  const stance = TAU * g.contact;
  if (t < stance) {
    const u = t / stance;
    return { dz: g.stride * (0.5 - u), lift: 0, u, planted: true };
  }
  const u = (t - stance) / (TAU - stance);
  return { dz: g.stride * (u - 0.5), lift: g.lift * Math.sin(Math.PI * u), u, planted: false };
}

/** Ayak bileği: topuk vuruşu, düz basış, parmak itişi, salınımda toparlanma. */
function anklePitch(s) {
  if (s.planted) {
    return -0.16 + smooth(s.u, 0, 0.30) * 0.16 + smooth(s.u, 0.62, 1) * 0.50;
  }
  return 0.50 - smooth(s.u, 0, 0.45) * 0.66;
}

/** Bir bacağı IK ile yerleştirir; ayak yörünge bilgisini döndürür. */
function placeLeg(p, S, ph, g, hipY) {
  const s = footPath(ph, g);
  const H = hipY - LEG.hipDrop;
  const { thigh, shin } = legAngles(s.dz, -(H - s.lift));
  p['thigh' + S][0] = thigh;
  p['shin' + S][0] = shin;
  p['foot' + S][0] = anklePitch(s) - (thigh + shin);
  return s;
}

/* ------------------------------------------------------------------ */
/* Poz kütüphanesi                                                      */
/* ------------------------------------------------------------------ */

/** İki elin de kabzayı kavradığı temel duruş — çift el kılıç için. */
function twoHandGrip(p) {
  p.shoulderL[2] = 0.30;
  p.armL[0] = -0.95; p.armL[1] = 0.45; p.armL[2] = 0.55;
  p.foreArmL[0] = -1.15; p.foreArmL[1] = -0.30;
}

export const POSES = {
  /** Dinlenme: kılıç omuzda, ağır nefes alışverişi. */
  idle(p, t, ctx) {
    const b = Math.sin(t * 1.55);
    const b2 = Math.sin(t * 1.55 + 0.7);
    p._rootY = b * 0.014;
    p.spine[0] = 0.05 + b * 0.022;
    p.chest[0] = -0.03 + b2 * 0.018;
    p.head[0] = -0.02 + Math.sin(t * 0.6) * 0.04;
    p.head[1] = Math.sin(t * 0.37) * 0.26;

    // Ağır zırh omuzları dışa iter
    p.shoulderL[2] = 0.22;
    p.shoulderR[2] = -0.22;
    p.armL[2] = 0.26 + b * 0.022;
    p.armR[2] = -0.26 - b * 0.022;
    p.armL[0] = -0.12;
    p.foreArmL[0] = -0.42;

    if (ctx.twoHanded) {
      // Kılıç sağ omuza yaslı
      p.armR[0] = -2.28;
      p.armR[1] = -0.28;
      p.armR[2] = -0.55;
      p.foreArmR[0] = -1.25;
      p.foreArmR[1] = -0.35;
      p.armL[0] = -0.30 + b * 0.03;
      p.foreArmL[0] = -0.55;
    } else {
      p.armR[0] = -0.16;
      p.foreArmR[0] = -0.52;
      p.foreArmR[1] = -0.28;
    }

    p.thighL[0] = 0.02; p.thighR[0] = -0.02;
    p.thighL[2] = 0.09; p.thighR[2] = -0.09;
    p.shinL[0] = -0.06; p.shinR[0] = -0.06;
    p.footL[2] = -0.09; p.footR[2] = 0.09;
  },

  /** Savaş duruşu: yandan, kılıç önde, dizler bükük. */
  combatIdle(p, t, ctx) {
    const b = Math.sin(t * 2.6);
    p._rootY = -0.06 + b * 0.014;
    p._rootYaw = 0.34;
    p.spine[0] = 0.16;
    p.chest[1] = -0.22;
    p.head[1] = 0.30;

    p.shoulderL[2] = 0.24; p.shoulderR[2] = -0.24;
    p.armR[0] = -0.85 + b * 0.04;
    p.armR[2] = -0.35;
    p.foreArmR[0] = -1.05;
    p.foreArmR[1] = -0.35;
    if (ctx.twoHanded) twoHandGrip(p);
    else { p.armL[0] = -0.55; p.armL[2] = 0.45; p.foreArmL[0] = -1.35; }

    p.thighL[0] = 0.34; p.thighR[0] = -0.30;
    p.thighL[2] = 0.14; p.thighR[2] = -0.14;
    p.shinL[0] = -0.42; p.shinR[0] = -0.36;
  },

  /**
   * Yürüme. `ph` bacak döngüsünün fazı (radyan) — zamana değil kat edilen
   * yola bağlı ilerler, böylece ayaklar zeminde kaymaz.
   */
  walk(p, ph, ctx) {
    const g = GAIT.walk;
    const sw = Math.sin(ph), cw = Math.cos(ph);
    // Kalça iki adımda bir alçalıp yükselir; IK bunu diz bükerek soğurur,
    // ayak yine yerde kalır.
    const hipY = g.hipY + Math.cos(ph * 2) * g.bob;
    p._rootY = hipY - LEG.restHipY;
    p._rootZ = 0;
    p._rootRoll = cw * 0.03;

    p.spine[0] = 0.10;
    p.chest[1] = -sw * 0.11;
    p.hips[1] = sw * 0.11;
    p.head[1] = sw * 0.05;

    p.shoulderL[2] = 0.22; p.shoulderR[2] = -0.22;
    p.armL[0] = -sw * 0.48; p.armL[2] = 0.24;
    p.foreArmL[0] = -0.42 - Math.max(0, -sw) * 0.26;

    if (ctx.twoHanded) {
      p.armR[0] = -2.30;
      p.armR[1] = -0.30;
      p.armR[2] = -0.55;
      p.foreArmR[0] = -1.20;
      p.foreArmR[1] = -0.35;
      p.armL[0] = -0.34 - sw * 0.16;
      p.foreArmL[0] = -0.58;
    } else {
      p.armR[0] = sw * 0.48; p.armR[2] = -0.24;
      p.foreArmR[0] = -0.50 - Math.max(0, sw) * 0.26;
      p.foreArmR[1] = -0.28;
    }

    placeLeg(p, 'L', ph, g, hipY);
    placeLeg(p, 'R', ph + Math.PI, g, hipY);
    p.thighL[2] = 0.04; p.thighR[2] = -0.04;
  },

  /** Koşma: geniş adım, öne eğik gövde. Fazı walk ile aynı mantıkta. */
  run(p, ph, ctx) {
    const g = GAIT.run;
    const sw = Math.sin(ph), cw = Math.cos(ph);
    const hipY = g.hipY + Math.cos(ph * 2) * g.bob;
    p._rootY = hipY - LEG.restHipY;
    p._rootZ = 0;                 // öne eğilme gövdeden gelir, kalçadan değil
    p._rootRoll = cw * 0.05;

    p.spine[0] = 0.30;
    p.chest[0] = 0.08;
    p.chest[1] = -sw * 0.22;
    p.hips[1] = sw * 0.22;
    p.head[0] = -0.32;

    p.shoulderL[2] = 0.26; p.shoulderR[2] = -0.26;
    p.armL[0] = -sw * 1.00; p.armL[2] = 0.30;
    p.foreArmL[0] = -1.05;

    if (ctx.twoHanded) {
      // Ağır kılıç koşarken omza yatık taşınır
      p.armR[0] = -2.35 - sw * 0.10;
      p.armR[1] = -0.35;
      p.armR[2] = -0.62;
      p.foreArmR[0] = -1.15;
      p.foreArmR[1] = -0.40;
      p.armL[0] = -0.50 - sw * 0.34;
      p.foreArmL[0] = -0.90;
    } else {
      p.armR[0] = sw * 1.00; p.armR[2] = -0.30;
      p.foreArmR[0] = -1.15;
      p.foreArmR[1] = -0.32;
    }

    placeLeg(p, 'L', ph, g, hipY);
    placeLeg(p, 'R', ph + Math.PI, g, hipY);
    p.thighL[2] = 0.05; p.thighR[2] = -0.05;
  },

  /**
   * Kılıç savurma. u ∈ [0,1] vuruş ilerlemesi.
   * variant 0: sağ omuzdan çapraz kesiş
   * variant 1: ters yönde yatay savurma
   * variant 2: yukarıdan aşağı ağır darbe
   */
  attack(p, u, variant, ctx) {
    const wind = smooth(u, 0, 0.30);
    const strike = smooth(u, 0.28, 0.50);
    const recover = smooth(u, 0.54, 1.0);
    const act = wind * (1 - strike) + strike * (1 - recover);

    if (variant === 1) {
      p._rootYaw = (wind * 0.55 - strike * 1.05) * -1;
      p.hips[1] = (wind * 0.40 - strike * 0.75) * -1;
      p.chest[1] = (wind * 0.55 - strike * 1.05) * -1;
      p.armR[0] = -0.65 - wind * 0.55 + strike * 1.55;
      p.armR[2] = -0.50 + wind * 0.75 - strike * 1.45;
      p.foreArmR[0] = -0.95 + strike * 0.55;
      if (ctx.twoHanded) twoHandGrip(p);
      else { p.armL[0] = -0.45; p.armL[2] = 0.55; p.foreArmL[0] = -1.25; }
    } else if (variant === 2) {
      p.spine[0] = -wind * 0.38 + strike * 0.58;
      p.chest[0] = -wind * 0.26 + strike * 0.40;
      p.armR[0] = -2.75 * wind + strike * 3.55;
      p.armR[2] = -0.22;
      p.armL[0] = -2.55 * wind + strike * 3.30;
      p.armL[2] = 0.22;
      p.foreArmR[0] = -0.50 - wind * 0.65 + strike * 0.75;
      p.foreArmL[0] = -0.50 - wind * 0.60 + strike * 0.70;
      p._rootZ = act * 0.22;
    } else {
      p._rootYaw = wind * 0.42 - strike * 0.62;
      p.hips[1] = wind * 0.38 - strike * 0.68;
      p.chest[1] = wind * 0.58 - strike * 1.10;
      p.armR[0] = -1.75 * wind + strike * 2.45;
      p.armR[2] = -0.55 - wind * 0.40 + strike * 0.65;
      p.foreArmR[0] = -0.80 - wind * 0.60 + strike * 0.95;
      if (ctx.twoHanded) twoHandGrip(p);
      else { p.armL[0] = -0.30 - wind * 0.40; p.armL[2] = 0.40; p.foreArmL[0] = -1.00; }
    }

    p.spine[0] += 0.07 + act * 0.07;
    p.head[1] = -p.chest[1] * 0.45;
    p.shoulderL[2] += 0.22; p.shoulderR[2] += -0.22;

    // Hamle duruşu
    p.thighL[0] = 0.40 * act + 0.06;
    p.thighR[0] = -0.48 * act - 0.06;
    p.thighL[2] = 0.14; p.thighR[2] = -0.14;
    p.shinL[0] = -0.42 * act - 0.06;
    p.shinR[0] = -0.52 * act - 0.06;
    p._rootZ += act * 0.18;
    p._rootY = -act * 0.06;
  },

  /** Kılıç fırtınası: gövde tam tur döner, kılıç yatay süpürür. */
  spin(p, u, ctx) {
    const turns = 2;
    const spin = smooth(u, 0.10, 0.86);
    p._rootYaw = spin * Math.PI * 2 * turns;
    const lift = Math.sin(Math.min(1, u / 0.92) * Math.PI);

    p.spine[0] = 0.10 + lift * 0.10;
    p._rootY = lift * 0.16;
    p.chest[1] = lift * 0.30;
    p.head[1] = -lift * 0.5;

    p.shoulderL[2] = 0.30; p.shoulderR[2] = -0.30;
    p.armR[0] = -1.35 - lift * 0.15;
    p.armR[2] = -1.15 - lift * 0.25;
    p.foreArmR[0] = -0.28;
    if (ctx.twoHanded) {
      p.armL[0] = -1.30; p.armL[1] = 0.55; p.armL[2] = 1.05;
      p.foreArmL[0] = -0.50;
    } else {
      p.armL[0] = -0.85; p.armL[2] = 1.10; p.foreArmL[0] = -0.95;
    }

    p.thighL[0] = 0.22 + lift * 0.35;
    p.thighR[0] = -0.18 - lift * 0.30;
    p.shinL[0] = -0.45 - lift * 0.45;
    p.shinR[0] = -0.30 - lift * 0.35;
  },

  /** Üç yönlü kesiş: üç hızlı, farklı açıda darbe. */
  tripleCut(p, u, ctx) {
    const seg = Math.min(2, Math.floor(u * 3));
    const lu = (u * 3) - seg;
    const wind = smooth(lu, 0, 0.34);
    const strike = smooth(lu, 0.30, 0.72);
    const dir = seg === 1 ? -1 : 1;
    const vertical = seg === 2;

    p.spine[0] = 0.12 + (vertical ? -wind * 0.30 + strike * 0.45 : 0);
    p._rootYaw = dir * (wind * 0.40 - strike * 0.70);
    p.chest[1] = dir * (wind * 0.55 - strike * 1.00);

    if (vertical) {
      p.armR[0] = -2.60 * wind + strike * 3.40;
      p.armR[2] = -0.24;
      p.foreArmR[0] = -0.55 - wind * 0.55 + strike * 0.70;
    } else {
      p.armR[0] = -1.50 * wind + strike * 2.30;
      p.armR[2] = -0.55 - dir * (wind * 0.55 - strike * 1.05);
      p.foreArmR[0] = -0.85 - wind * 0.50 + strike * 0.85;
    }
    if (ctx.twoHanded) twoHandGrip(p);
    else { p.armL[0] = -0.40; p.armL[2] = 0.48; p.foreArmL[0] = -1.15; }

    p.shoulderL[2] += 0.22; p.shoulderR[2] += -0.22;
    const act = Math.sin(lu * Math.PI);
    p.thighL[0] = 0.36 * act + 0.06;
    p.thighR[0] = -0.44 * act - 0.06;
    p.shinL[0] = -0.40 * act - 0.06;
    p.shinR[0] = -0.48 * act - 0.06;
    p._rootZ = act * 0.20 + seg * 0.10;
  },

  /** İleri atılma / kaçınma. */
  dash(p, u) {
    const k = Math.sin(Math.min(1, u) * Math.PI);
    p.spine[0] = 0.45 * k;
    p.chest[0] = 0.15 * k;
    p.head[0] = -0.45 * k;
    p._rootZ = 0.30 * k;
    p._rootY = -0.14 * k;
    p.armL[0] = -1.55 * k; p.armL[2] = 0.55 * k;
    p.armR[0] = -1.30 * k; p.armR[2] = -0.55 * k;
    p.foreArmL[0] = -1.10 * k; p.foreArmR[0] = -1.20 * k;
    p.thighL[0] = 1.05 * k; p.thighR[0] = -0.55 * k;
    p.shinL[0] = -1.35 * k; p.shinR[0] = -0.30 * k;
  },

  /** Darbe alma. */
  hit(p, u) {
    const k = Math.sin(Math.min(1, u) * Math.PI);
    p.spine[0] = -0.38 * k;
    p.chest[0] = -0.24 * k;
    p.head[0] = -0.34 * k;
    p.armL[0] = -0.75 * k; p.armL[2] = 0.55 * k;
    p.armR[0] = -0.70 * k; p.armR[2] = -0.55 * k;
    p.foreArmL[0] = -1.25 * k; p.foreArmR[0] = -1.25 * k;
    p.thighL[0] = -0.22 * k; p.thighR[0] = -0.22 * k;
    p._rootZ = -0.26 * k;
  },

  /** Ölüm: dizler çöker, gövde yana devrilir. */
  die(p, u) {
    const k = smooth(u, 0, 1);
    p._rootY = -0.78 * k;
    p._rootRoll = -1.40 * k;
    p.spine[0] = 0.52 * k;
    p.chest[0] = 0.36 * k;
    p.head[0] = 0.42 * k;
    p.thighL[0] = 1.55 * k; p.thighR[0] = 1.35 * k;
    p.shinL[0] = -2.05 * k; p.shinR[0] = -1.85 * k;
    p.armL[0] = -0.52 * k; p.armL[2] = 0.95 * k;
    p.armR[0] = -0.42 * k; p.armR[2] = -0.85 * k;
  },

  /** Zıplama / havada olma. */
  jump(p, vy) {
    const up = THREE.MathUtils.clamp(vy / 8, -1, 1);
    p.spine[0] = 0.14 - up * 0.16;
    p.armL[0] = -1.15 - up * 0.65; p.armL[2] = 0.55;
    p.armR[0] = -0.95 - up * 0.55; p.armR[2] = -0.55;
    p.foreArmL[0] = -0.75; p.foreArmR[0] = -0.85;
    p.thighL[0] = 0.60 + up * 0.32;
    p.thighR[0] = 0.20 - up * 0.22;
    p.shinL[0] = -0.95; p.shinR[0] = -0.55;
  },
};

/* ------------------------------------------------------------------ */
/* Malzeme seti                                                         */
/* ------------------------------------------------------------------ */
function mkMat(theme) {
  const armorTex = getTexture('armor', { a: theme.base, b: theme.trim });
  const L = (o) => new THREE.MeshLambertMaterial(o);
  return {
    armor: L({ map: armorTex }),
    armorPlain: L({ color: new THREE.Color(theme.base).multiplyScalar(1.15) }),
    metal: L({ color: 0x8f98a3 }),
    steel: L({ color: 0xc9d0d8 }),
    gold: L({ color: new THREE.Color(theme.trim) }),
    cloth: L({ color: theme.cloth }),
    clothDark: L({ color: new THREE.Color(theme.cloth).multiplyScalar(0.55) }),
    skin: L({ color: 0xd8a97e }),
    hair: L({ color: 0x33261b }),
    leather: L({ color: 0x4a3222 }),
    leatherDark: L({ color: 0x2c1d12 }),
  };
}

function part(geo, mat, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(geo, mat);
  m.position.set(x, y, z);
  m.castShadow = true;
  return m;
}

/** Köşeleri kırılmış kutu — düz kutulardan daha "dövülmüş" bir plaka verir. */
function plate(w, h, d, bevel = 0.18) {
  const g = new THREE.BoxGeometry(w, h, d, 1, 1, 1);
  const p = g.attributes.position;
  const v = new THREE.Vector3();
  for (let i = 0; i < p.count; i++) {
    v.fromBufferAttribute(p, i);
    // Üst ve alt köşeleri içe çek
    const ty = Math.abs(v.y) / (h / 2);
    if (ty > 0.9) {
      v.x *= 1 - bevel;
      v.z *= 1 - bevel;
    }
    p.setXYZ(i, v.x, v.y, v.z);
  }
  g.computeVertexNormals();
  return g;
}

/** Kubbe kabuğu — omuzluk ve miğfer için. */
function dome(r, cut = 0.58, seg = 12) {
  const g = new THREE.SphereGeometry(r, seg, Math.max(6, seg - 4), 0, Math.PI * 2, 0, Math.PI * cut);
  return g;
}

export class Warrior {
  /**
   * @param {object} armorTheme  kingdom.armor
   * @param {object} opts { scale, weapon: 'twohand'|'sword', shield }
   */
  constructor(armorTheme, opts = {}) {
    this.scale = opts.scale ?? 1;
    this.weaponType = opts.weapon ?? 'twohand';
    this.hasShield = opts.shield ?? (this.weaponType === 'sword');
    this.twoHanded = this.weaponType === 'twohand';
    this.mats = mkMat(armorTheme);
    this.root = new THREE.Group();
    this.root.name = 'warrior';
    this.j = {};
    this.dyn = { cape: 0, capeV: 0, tasset: [0, 0, 0, 0], tassetV: [0, 0, 0, 0] };
    this._build();

    this.state = 'idle';
    this.stateTime = 0;
    this.animTime = 0;      // döngüsel olmayan pozlar (nefes alma vb.) için

    /*
     * Bacak döngüsünün fazı. Zamanla değil, kat edilen yolla ilerler:
     * bir tam döngüde (iki adım) kat edilen mesafe pozun bacak açılımından
     * geliyor (adım = 2 · bacak boyu · sin(kalça açısı)). Faz hızı buna
     * bölünmezse ayaklar zeminde kayar — yürüyüşü bozan şey buydu.
     */
    this.locoPhase = 0;
    this.cycleDistance = {
      walk: GAIT.walk.cycleDistance * this.scale,
      run: GAIT.run.cycleDistance * this.scale,
    };
    this.attackVariant = 0;
    this.durations = {
      attack: 0.58, spin: 1.05, tripleCut: 1.15, dash: 0.42, hit: 0.36, die: 0.9,
    };
    this.blend = 1;
    this.blendDur = 0.13;
    this._poseA = blankPose();
    this._poseB = blankPose();
    this._poseOut = blankPose();
    this._ctx = { twoHanded: this.twoHanded };
    this.vy = 0;
    this._prevYaw = 0;
  }

  _joint(name, parent, x, y, z) {
    const g = new THREE.Group();
    g.position.set(x, y, z);
    parent.add(g);
    this.j[name] = g;
    return g;
  }

  _build() {
    const M = this.mats;
    const root = this.root;

    /*
     * Zırh parçaları görünüm gruplarına ayrılıyor.
     *
     * Bu savaşçı, dış model yüklenemediğinde devreye giren yedek. Eskiden
     * zırhı gövdesine gömülüydü: ekipman çıkarılsa bile üstünde kalıyordu.
     * Artık her parça bir gruba yazılıyor ve altında ten renginde bir gövde
     * katmanı duruyor; grup gizlendiğinde altındaki gövde görünüyor.
     */
    this.parca = { chest: [], helmet: [], bracer: [], boot: [], cape: [] };
    const zirh = (grup, parent, obj) => { parent.add(obj); this.parca[grup].push(obj); return obj; };

    /* ---- Gövde zinciri ---- */
    const hips = this._joint('hips', root, 0, 0.98, 0);
    const spine = this._joint('spine', hips, 0, 0.11, 0);
    const chest = this._joint('chest', spine, 0, 0.21, 0);
    const neck = this._joint('neck', chest, 0, 0.32, 0);
    const head = this._joint('head', neck, 0, 0.11, 0);

    /* ---- Kalça, kemer, zırh eteği ---- */
    // Altta ten katmanı: zırh çıkınca boşluk kalmasın
    hips.add(part(plate(0.34, 0.21, 0.23, 0.2), M.skin, 0, 0.01, 0));
    zirh('chest', hips, part(plate(0.40, 0.22, 0.27), M.armor, 0, 0.01, 0));
    // Geniş deri kemer + altın toka
    zirh('chest', hips, part(plate(0.44, 0.11, 0.30, 0.10), M.leather, 0, 0.12, 0));
    zirh('chest', hips, part(plate(0.14, 0.13, 0.06, 0.30), M.gold, 0, 0.12, 0.16));
    // Kalça yanları
    for (const s of [-1, 1]) {
      zirh('chest', hips, part(plate(0.09, 0.17, 0.24, 0.25), M.armorPlain, s * 0.21, 0.02, 0));
    }

    // Dört panelli zırh eteği: ikincil hareketle sallanır
    this.tassets = [];
    const tassetSpec = [
      { ry: 0, w: 0.30, h: 0.42, z: 0.145, x: 0 },            // ön
      { ry: Math.PI, w: 0.30, h: 0.42, z: -0.145, x: 0 },     // arka
      { ry: Math.PI / 2, w: 0.24, h: 0.38, z: 0, x: 0.185 },  // sağ
      { ry: -Math.PI / 2, w: 0.24, h: 0.38, z: 0, x: -0.185 },// sol
    ];
    for (const t of tassetSpec) {
      const pivot = new THREE.Group();
      pivot.position.set(t.x, -0.03, t.z);
      pivot.rotation.y = t.ry;
      // Üst üste binen iki lamel sırası
      pivot.add(part(plate(t.w, t.h * 0.58, 0.06, 0.12), M.armor, 0, -t.h * 0.29, 0));
      pivot.add(part(plate(t.w * 0.92, t.h * 0.52, 0.055, 0.14), M.armor, 0, -t.h * 0.72, 0));
      pivot.add(part(plate(t.w * 0.96, 0.045, 0.07, 0.2), M.gold, 0, -t.h * 0.55, 0));
      zirh('chest', hips, pivot);
      this.tassets.push(pivot);
    }
    // Etek altı kumaş — zırhın parçası
    const skirt = new THREE.CylinderGeometry(0.19, 0.30, 0.34, 10, 1, true);
    zirh('chest', hips, part(skirt, M.clothDark, 0, -0.20, 0));

    /* ---- Gövde: önce ten katmanı, sonra zırh ---- */
    spine.add(part(plate(0.33, 0.19, 0.21, 0.16), M.skin, 0, 0.07, 0));
    chest.add(part(plate(0.40, 0.43, 0.24, 0.20), M.skin, 0, 0.13, 0));
    // Karın lamelleri
    zirh('chest', spine, part(plate(0.40, 0.19, 0.26, 0.12), M.armor, 0, 0.07, 0));
    // Göğüs kafesi: öne doğru genişleyen ağır plaka
    zirh('chest', chest, part(plate(0.50, 0.44, 0.31, 0.16), M.armor, 0, 0.13, 0));
    // Göğüs plakası ve boyun koruması
    zirh('chest', chest, part(plate(0.30, 0.22, 0.05, 0.30), M.gold, 0, 0.19, 0.165));
    zirh('chest', chest, part(plate(0.20, 0.07, 0.04, 0.3), M.gold, 0, 0.05, 0.17));
    // Yaka / gerdanlık
    zirh('chest', chest, part(new THREE.TorusGeometry(0.15, 0.045, 6, 14).rotateX(Math.PI / 2),
      M.gold, 0, 0.33, 0.01));
    // Sırt plakası
    zirh('chest', chest, part(plate(0.42, 0.38, 0.05, 0.2), M.armorPlain, 0, 0.13, -0.16));

    // Pelerin: iki parçalı, ikincil hareketle savrulur
    const capePivot = new THREE.Group();
    capePivot.position.set(0, 0.31, -0.18);
    const capeGeo = new THREE.PlaneGeometry(0.54, 0.62, 3, 3);
    capeGeo.translate(0, -0.31, 0);
    const capeMat = new THREE.MeshLambertMaterial({
      color: M.cloth.color, side: THREE.DoubleSide,
    });
    capePivot.add(part(capeGeo, capeMat));
    const capeLower = new THREE.Group();
    capeLower.position.set(0, -0.60, 0);
    const capeGeo2 = new THREE.PlaneGeometry(0.62, 0.58, 3, 3);
    capeGeo2.translate(0, -0.29, 0);
    capeLower.add(part(capeGeo2, capeMat));
    capePivot.add(capeLower);
    zirh('cape', chest, capePivot);
    this.cape = capePivot;
    this.capeLower = capeLower;

    /* ---- Boyun ve kafa ---- */
    neck.add(part(new THREE.CylinderGeometry(0.075, 0.085, 0.14, 8), M.skin, 0, 0, 0));
    const skull = new THREE.SphereGeometry(0.138, 12, 10);
    skull.scale(1, 1.10, 1.04);
    head.add(part(skull, M.skin, 0, 0.05, 0));
    // Çene hattı
    head.add(part(plate(0.16, 0.10, 0.15, 0.35), M.skin, 0, -0.03, 0.015));
    // Saç kabuğu + arkada toplanmış saç + topuz
    const hairCap = new THREE.SphereGeometry(0.146, 12, 10, 0, Math.PI * 2, 0, Math.PI * 0.60);
    head.add(part(hairCap, M.hair, 0, 0.055, 0));
    head.add(part(plate(0.15, 0.15, 0.075, 0.35), M.hair, 0, 0.015, -0.095));
    head.add(part(new THREE.SphereGeometry(0.066, 8, 6), M.hair, 0, 0.215, -0.03));
    head.add(part(new THREE.CylinderGeometry(0.022, 0.022, 0.12, 6), M.gold, 0, 0.175, -0.03));
    // Alın bandı + altın plaka — miğfer yerine geçiyor
    zirh('helmet', head, part(new THREE.CylinderGeometry(0.147, 0.147, 0.05, 12),
      M.leatherDark, 0, 0.075, 0));
    zirh('helmet', head, part(plate(0.10, 0.07, 0.03, 0.4), M.gold, 0, 0.078, 0.135));
    // Yüz
    for (const s of [-1, 1]) {
      head.add(part(new THREE.SphereGeometry(0.019, 6, 5), M.hair, s * 0.052, 0.042, 0.125));
      // Kaşlar
      head.add(part(plate(0.05, 0.014, 0.02), M.hair, s * 0.055, 0.075, 0.128));
    }

    /* ---- Kollar ---- */
    for (const side of [-1, 1]) {
      const S = side < 0 ? 'L' : 'R';
      const sh = this._joint('shoulder' + S, chest, side * 0.255, 0.245, 0);

      // Katmanlı omuzluk: iki kabuk + altın bilezik + çivi
      const pad = dome(0.175, 0.60, 12);
      pad.scale(1.10, 0.95, 1.12);
      zirh('chest', sh, part(pad, M.armor, side * 0.035, 0.015, 0));
      const pad2 = dome(0.16, 0.52, 12);
      pad2.scale(1.20, 0.68, 1.20);
      zirh('chest', sh, part(pad2, M.armorPlain, side * 0.05, -0.075, 0));
      zirh('chest', sh, part(new THREE.TorusGeometry(0.152, 0.024, 6, 14).rotateX(Math.PI / 2),
        M.gold, side * 0.035, -0.005, 0));
      zirh('chest', sh, part(new THREE.ConeGeometry(0.045, 0.13, 6), M.gold, side * 0.14, 0.10, 0));

      const arm = this._joint('arm' + S, sh, side * 0.025, -0.06, 0);
      arm.add(part(new THREE.CylinderGeometry(0.068, 0.060, 0.31, 8), M.skin, 0, -0.155, 0));
      zirh('chest', arm, part(plate(0.15, 0.16, 0.15, 0.18), M.armorPlain, 0, -0.06, 0));

      const fore = this._joint('foreArm' + S, arm, 0, -0.31, 0);
      fore.add(part(new THREE.CylinderGeometry(0.056, 0.048, 0.28, 8), M.skin, 0, -0.14, 0));
      // Kolluk (bracer)
      zirh('bracer', fore, part(new THREE.CylinderGeometry(0.074, 0.062, 0.20, 8),
        M.armor, 0, -0.20, 0));
      zirh('bracer', fore, part(new THREE.TorusGeometry(0.066, 0.018, 5, 12).rotateX(Math.PI / 2),
        M.gold, 0, -0.29, 0));

      const hand = this._joint('hand' + S, fore, 0, -0.30, 0);
      hand.add(part(plate(0.070, 0.10, 0.055, 0.25), M.skin, 0, -0.05, 0));
      zirh('bracer', hand, part(plate(0.082, 0.11, 0.06, 0.25), M.leather, 0, -0.05, 0));
    }

    /* ---- Bacaklar ---- */
    for (const side of [-1, 1]) {
      const S = side < 0 ? 'L' : 'R';
      const thigh = this._joint('thigh' + S, hips, side * 0.125, -0.07, 0);
      // Ten bacak altta; pantolon zırhla birlikte gidiyor
      thigh.add(part(new THREE.CylinderGeometry(0.082, 0.070, 0.45, 8), M.skin, 0, -0.225, 0));
      zirh('chest', thigh, part(new THREE.CylinderGeometry(0.092, 0.078, 0.45, 8),
        M.cloth, 0, -0.225, 0));
      const shin = this._joint('shin' + S, thigh, 0, -0.45, 0);
      shin.add(part(new THREE.CylinderGeometry(0.066, 0.054, 0.43, 8), M.skin, 0, -0.215, 0));
      zirh('chest', shin, part(new THREE.CylinderGeometry(0.074, 0.060, 0.43, 8),
        M.cloth, 0, -0.215, 0));
      // Dizlik
      zirh('boot', shin, part(dome(0.088, 0.62, 8).rotateX(-0.5), M.armor, 0, -0.015, 0.015));
      // Baldır zırhı (greave)
      zirh('boot', shin, part(plate(0.15, 0.24, 0.14, 0.2), M.armorPlain, 0, -0.20, 0.005));
      // Çizme
      zirh('boot', shin, part(new THREE.CylinderGeometry(0.088, 0.082, 0.18, 8),
        M.leather, 0, -0.34, 0));
      const foot = this._joint('foot' + S, shin, 0, -0.43, 0);
      // Çıplak ayak: çizme çıkınca görünür
      foot.add(part(plate(0.10, 0.07, 0.23, 0.2), M.skin, 0, 0.035, 0.05));
      zirh('boot', foot, part(plate(0.12, 0.085, 0.27, 0.15), M.leather, 0, 0.04, 0.06));
      zirh('boot', foot, part(plate(0.125, 0.035, 0.28, 0.1), M.leatherDark, 0, 0.0, 0.06));
    }

    /* ---- Silahlar ---- */
    if (this.twoHanded) {
      this.weapon = this._buildGreatsword(M);
      this.j.handR.add(this.weapon);
      // Sırtta kın yok: kılıç zaten elde
    } else {
      this.weapon = this._buildSword(M);
      this.j.handR.add(this.weapon);
      if (this.hasShield) {
        this.shield = this._buildShield(M);
        this.j.handL.add(this.shield);
      }
      // Sırtta yedek kın
      const sheath = new THREE.Group();
      sheath.add(part(plate(0.08, 0.92, 0.05, 0.15), M.leatherDark, 0, -0.15, 0));
      sheath.add(part(plate(0.09, 0.08, 0.06, 0.2), M.gold, 0, 0.28, 0));
      sheath.position.set(-0.15, 0.22, -0.20);
      sheath.rotation.set(0.35, 0, -0.55);
      chest.add(sheath);
      this.sheath = sheath;
    }

    root.scale.setScalar(this.scale);
    this.height = 1.95 * this.scale;
  }

  /** Tek el kılıç. */
  _buildSword(M) {
    const g = new THREE.Group();
    g.add(part(new THREE.CylinderGeometry(0.028, 0.032, 0.21, 8), M.leatherDark, 0, -0.10, 0));
    g.add(part(new THREE.SphereGeometry(0.045, 8, 6), M.gold, 0, -0.22, 0));
    g.add(part(plate(0.28, 0.05, 0.07, 0.3), M.gold, 0, 0.015, 0));
    g.add(part(plate(0.07, 0.08, 0.08, 0.3), M.gold, 0, 0.055, 0));
    const blade = plate(0.09, 0.98, 0.028, 0.05);
    blade.translate(0, 0.57, 0);
    g.add(part(blade, M.steel));
    const tip = new THREE.ConeGeometry(0.062, 0.17, 4).rotateY(Math.PI / 4);
    tip.translate(0, 1.14, 0);
    g.add(part(tip, M.steel));
    const fuller = plate(0.026, 0.84, 0.034);
    fuller.translate(0, 0.54, 0);
    g.add(part(fuller, M.metal));
    g.position.set(0, -0.07, 0.02);
    g.rotation.set(-0.15, 0, 0);
    this._attachWeaponMarkers(g, 0.10, 1.16);
    return g;
  }

  /** Çift el büyük kılıç — savaşçının imza silahı. */
  _buildGreatsword(M) {
    const g = new THREE.Group();
    // Uzun kabza, iki elin sığacağı boyda
    g.add(part(new THREE.CylinderGeometry(0.033, 0.038, 0.40, 8), M.leatherDark, 0, -0.19, 0));
    // Kabza sargı halkaları
    for (let i = 0; i < 4; i++) {
      g.add(part(new THREE.TorusGeometry(0.037, 0.010, 5, 10).rotateX(Math.PI / 2),
        M.gold, 0, -0.06 - i * 0.09, 0));
    }
    // Topuz
    g.add(part(new THREE.SphereGeometry(0.058, 10, 8), M.gold, 0, -0.41, 0));
    g.add(part(new THREE.ConeGeometry(0.035, 0.09, 6).rotateX(Math.PI), M.gold, 0, -0.47, 0));
    // Geniş balçak + yan kanatlar
    g.add(part(plate(0.42, 0.065, 0.09, 0.25), M.gold, 0, 0.03, 0));
    for (const s of [-1, 1]) {
      g.add(part(plate(0.07, 0.14, 0.07, 0.3), M.gold, s * 0.185, 0.09, 0));
    }
    g.add(part(plate(0.11, 0.13, 0.11, 0.3), M.gold, 0, 0.09, 0));
    // Ricasso (balçak üstü kalın bölüm)
    g.add(part(plate(0.10, 0.16, 0.05, 0.1), M.metal, 0, 0.20, 0));
    // Geniş namlu
    const blade = plate(0.155, 1.30, 0.040, 0.06);
    blade.translate(0, 0.90, 0);
    g.add(part(blade, M.steel));
    // Kan oluğu
    const fuller = plate(0.045, 1.16, 0.048);
    fuller.translate(0, 0.86, 0);
    g.add(part(fuller, M.metal));
    // Uç
    const tip = new THREE.ConeGeometry(0.105, 0.26, 4).rotateY(Math.PI / 4);
    tip.scale(1, 1, 0.38);
    tip.translate(0, 1.66, 0);
    g.add(part(tip, M.steel));

    g.position.set(0, -0.10, 0.02);
    g.rotation.set(-0.12, 0, 0);
    this._attachWeaponMarkers(g, 0.24, 1.70);
    return g;
  }

  /** Kalkan — tek el kılıç kullanan varyant için. */
  _buildShield(M) {
    const g = new THREE.Group();
    const body = new THREE.CylinderGeometry(0.34, 0.34, 0.06, 16);
    body.scale(1, 1, 1.25);
    g.add(part(body.rotateX(Math.PI / 2), M.armor, 0, 0, 0));
    g.add(part(new THREE.TorusGeometry(0.34, 0.035, 6, 20), M.gold, 0, 0, 0));
    g.add(part(new THREE.SphereGeometry(0.09, 10, 8), M.gold, 0, 0, 0.05));
    g.position.set(0, -0.14, 0.06);
    g.rotation.set(1.35, 0, 0);
    return g;
  }

  /** Kılıç izi efekti için namlunun dip ve uç düğümleri. */
  _attachWeaponMarkers(g, baseY, tipY) {
    this.weaponBase = new THREE.Object3D();
    this.weaponBase.position.set(0, baseY, 0);
    g.add(this.weaponBase);
    this.weaponTip = new THREE.Object3D();
    this.weaponTip.position.set(0, tipY, 0);
    g.add(this.weaponTip);
  }

  /* ---------------- Durum makinesi ---------------- */
  setState(name, opts = {}) {
    if (this.state === name && !opts.force) return;
    this._capturePose(this._poseA);
    this.state = name;
    this.stateTime = 0;
    this.blend = 0;
    if (name === 'attack') this.attackVariant = opts.variant ?? 0;
  }

  /** Bir kez oynanıp biten hareketlerin süresi (yoksa 0 = döngüsel). */
  duration(name = this.state) {
    return this.durations[name] ?? 0;
  }

  _capturePose(target) {
    for (const j of JOINTS) {
      const g = this.j[j];
      target[j][0] = g.rotation.x;
      target[j][1] = g.rotation.y;
      target[j][2] = g.rotation.z;
    }
    target._rootY = this._curRootY || 0;
    target._rootZ = this._curRootZ || 0;
    target._rootRoll = this._curRootRoll || 0;
    target._rootYaw = this._curRootYaw || 0;
  }

  _evalPose(p, state, time) {
    for (const j of JOINTS) { p[j][0] = p[j][1] = p[j][2] = 0; }
    p._rootY = p._rootZ = p._rootRoll = p._rootYaw = 0;
    const c = this._ctx;
    switch (state) {
      case 'walk': POSES.walk(p, this.locoPhase, c); break;
      case 'run': POSES.run(p, this.locoPhase, c); break;
      case 'combatIdle': POSES.combatIdle(p, this.animTime, c); break;
      case 'attack': POSES.attack(p, Math.min(1, time / this.durations.attack), this.attackVariant, c); break;
      case 'spin': POSES.spin(p, Math.min(1, time / this.durations.spin), c); break;
      case 'tripleCut': POSES.tripleCut(p, Math.min(1, time / this.durations.tripleCut), c); break;
      case 'dash': POSES.dash(p, Math.min(1, time / this.durations.dash)); break;
      case 'hit': POSES.hit(p, Math.min(1, time / this.durations.hit)); break;
      case 'die': POSES.die(p, Math.min(1, time / this.durations.die)); break;
      case 'jump': POSES.jump(p, this.vy); break;
      default: POSES.idle(p, this.animTime, c);
    }
    return p;
  }

  /**
   * @param {number} dt     saniye
   * @param {number} speed  yatay hız
   * @returns {string|null} biten hareketin adı ('attack-end' gibi)
   */
  update(dt, speed = 0) {
    this.stateTime += dt;
    this.animTime += dt;

    // Bacak fazını hıza göre ilerlet: hız / döngü mesafesi = saniyedeki döngü
    const cd = this.cycleDistance[this.state];
    if (cd) {
      // Durur gibi olurken bile adım tamamlansın diye küçük bir taban hız
      const v = Math.max(speed, 0.35);
      this.locoPhase += dt * (v / cd) * Math.PI * 2;
      if (this.locoPhase > Math.PI * 2) this.locoPhase -= Math.PI * 2;
    } else {
      // Hareket bitince faz sıfıra yakın bir yere dönsün ki sonraki
      // yürüyüş ayakların yanyana olduğu noktadan başlasın
      this.locoPhase *= 1 - Math.min(1, dt * 6);
    }

    if (this.blend < 1) this.blend = Math.min(1, this.blend + dt / this.blendDur);

    const target = this._evalPose(this._poseB, this.state, this.stateTime);
    const p = this.blend >= 1
      ? target
      : lerpPose(this._poseOut, this._poseA, target, this.blend);

    for (const j of JOINTS) {
      this.j[j].rotation.set(p[j][0], p[j][1], p[j][2]);
    }
    this._curRootY = p._rootY;
    this._curRootZ = p._rootZ;
    this._curRootRoll = p._rootRoll;
    this._curRootYaw = p._rootYaw;
    this.j.hips.position.y = 0.98 + p._rootY;
    this.j.hips.position.z = p._rootZ;
    this.j.hips.rotation.z += p._rootRoll;
    this.j.hips.rotation.y += p._rootYaw;

    this._updateSecondary(dt, speed, p._rootYaw);

    const d = this.durations[this.state];
    if (d && this.stateTime >= d) return this.state + '-end';
    return null;
  }

  /**
   * İkincil hareket: pelerin ve zırh eteği doğrudan animasyonda değil,
   * hıza ve gövde dönüşüne gecikmeli tepki veren yaylarla sürülüyor.
   */
  _updateSecondary(dt, speed, rootYaw) {
    const k = Math.min(1, dt * 60);

    // Pelerin: hız arttıkça geriye savrulur, üstüne salınım biner
    const capeTarget = Math.min(0.95, speed * 0.10) + Math.max(0, -this.vy) * 0.012;
    this.dyn.capeV += (capeTarget - this.dyn.cape) * 22 * dt;
    this.dyn.capeV *= 1 - Math.min(0.9, 7 * dt);
    this.dyn.cape += this.dyn.capeV * k;
    const wob = Math.sin(this.animTime * 5.2) * (0.03 + speed * 0.006);
    this.cape.rotation.x = 0.10 + this.dyn.cape + wob;
    this.cape.rotation.z = Math.sin(this.animTime * 3.4) * 0.05;
    this.capeLower.rotation.x = this.dyn.cape * 0.55 + wob * 1.6;

    // Etek panelleri: yürüyüş salınımı + dönüşe karşı direnç
    const yawDelta = rootYaw - this._prevYaw;
    this._prevYaw = rootYaw;
    for (let i = 0; i < this.tassets.length; i++) {
      const isFrontBack = i < 2;
      const swing = isFrontBack
        ? Math.sin(this.locoPhase + i * Math.PI) * speed * 0.028
        : Math.sin(this.locoPhase * 0.5 + i) * speed * 0.016;
      const target = swing - yawDelta * 1.4;
      this.dyn.tassetV[i] += (target - this.dyn.tasset[i]) * 26 * dt;
      this.dyn.tassetV[i] *= 1 - Math.min(0.9, 9 * dt);
      this.dyn.tasset[i] += this.dyn.tassetV[i] * k;
      const a = THREE.MathUtils.clamp(this.dyn.tasset[i], -0.55, 0.55);
      this.tassets[i].rotation.x = a;
    }
  }

  /** Saldırının hasar penceresinde miyiz? */
  get inDamageWindow() {
    const s = this.state;
    if (s === 'attack') {
      const u = this.stateTime / this.durations.attack;
      return u > 0.28 && u < 0.54;
    }
    if (s === 'spin') {
      const u = this.stateTime / this.durations.spin;
      return u > 0.12 && u < 0.86;
    }
    if (s === 'tripleCut') {
      const u = (this.stateTime / this.durations.tripleCut * 3) % 1;
      return u > 0.30 && u < 0.72;
    }
    return false;
  }

  /** Kılıç izi efektinin çizilmesi gereken durumlar. */
  get isSwinging() {
    return this.state === 'attack' || this.state === 'spin' || this.state === 'tripleCut';
  }

  /**
   * Kuşanılan eşyalara göre görünümü günceller.
   *
   * Bu prosedürel savaşçı yalnızca yedek: zırhı gövdesine gömülü olduğu için
   * göğüslük/miğfer/çizme çıkarılamıyor. Silah, kalkan ve pelerin gerçek
   * ekipmanı izliyor; kalanı için karakterin "hep zırhlı" olduğu kabul
   * ediliyor. (Gerçek ekipman görünümleri RiggedCharacter'da.)
   *
   * @param {Object<string, ?object>} gorsel
   */
  applyEquipmentVisuals(gorsel = {}) {
    if (this.weapon) this.weapon.visible = !!gorsel.sword;
    if (this.sheath) this.sheath.visible = !!gorsel.sword;
    if (this.shield) this.shield.visible = !!gorsel.shield;
    for (const [grup, parcalar] of Object.entries(this.parca || {})) {
      const acik = !!gorsel[grup];
      for (const o of parcalar) o.visible = acik;
    }
    this.silahVar = !!gorsel.sword;
  }

  getWeaponTipWorld(out = new THREE.Vector3()) {
    return this.weaponTip.getWorldPosition(out);
  }

  getWeaponBaseWorld(out = new THREE.Vector3()) {
    return this.weaponBase.getWorldPosition(out);
  }

  addTo(parent) { parent.add(this.root); return this; }
}
