package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/**
 * Dünyadaki kaynak düğümleri: ağaçlar, kayalar, çalılar, variller ve sandıklar.
 *
 * <p>Düğümler hiçbir yerde saklanmaz — {@link WorldGen} serpme ızgarasından
 * hesaplanır. Yalnızca <em>tüketilmiş</em> olanlar bir tablo hâlinde tutulur ve
 * süresi dolunca kendiliğinden geri gelir. Böylece 100.000 birimlik haritada
 * milyonlarca ağaç olmasına rağmen bellekte sadece son kesilenler durur.
 */
public final class Harvest {

    private Harvest() {
    }

    /** Toplamak için düğüme bu kadar yaklaşmak gerekir. */
    public static final float REACH = 2.6f;
    /** Bir düğümü bitirmek için gereken iş (saniye cinsinden temel süre). */
    public static final float WORK_TREE = 3.4f;
    public static final float WORK_ROCK = 4.2f;
    public static final float WORK_BUSH = 1.1f;
    public static final float WORK_DEBRIS = 2.2f;

    /** Kesilen ağacın yeniden büyüme süresi (saniye). */
    public static final float REGROW_TREE = Balance.DAY_LENGTH * 0.75f;
    public static final float REGROW_ROCK = Balance.DAY_LENGTH * 1.1f;
    public static final float REGROW_BUSH = Balance.DAY_LENGTH * 0.3f;
    public static final float REGROW_DEBRIS = Balance.DAY_LENGTH * 0.9f;

    /** Serpme ızgarasının adımı — WorldRenderer ile aynı olmalı. */
    public static final float STEP = 6f;

    /** Düğümün kimliği: serpme hücresinden türetilir. */
    public static long key(int px, int pz) {
        return ((long) px << 32) ^ (pz & 0xFFFFFFFFL);
    }

    /** Bu düğüm hangi kaynağı verir? */
    public static int resourceOf(int prop) {
        switch (prop) {
            case WorldGen.PROP_TREE: return Balance.R_WOOD;
            case WorldGen.PROP_ROCK: return Balance.R_STONE;
            case WorldGen.PROP_GRASS: return Balance.R_FIBER;
            default: return Balance.R_SCRAP;      // varil ve sandık: hurda
        }
    }

    /** Bir düğümü bitirmek için gereken temel iş süresi. */
    public static float workOf(int prop) {
        switch (prop) {
            case WorldGen.PROP_TREE: return WORK_TREE;
            case WorldGen.PROP_ROCK: return WORK_ROCK;
            case WorldGen.PROP_GRASS: return WORK_BUSH;
            default: return WORK_DEBRIS;
        }
    }

    public static float regrowOf(int prop) {
        switch (prop) {
            case WorldGen.PROP_TREE: return REGROW_TREE;
            case WorldGen.PROP_ROCK: return REGROW_ROCK;
            case WorldGen.PROP_GRASS: return REGROW_BUSH;
            default: return REGROW_DEBRIS;
        }
    }

    /** Düğümden çıkan miktar (deterministik değil, aralık içinde). */
    public static int yieldOf(int prop) {
        switch (prop) {
            case WorldGen.PROP_TREE: return MathX.rndInt(7) + 8;      // 8..14 odun
            case WorldGen.PROP_ROCK: return MathX.rndInt(7) + 6;      // 6..12 taş
            case WorldGen.PROP_GRASS: return MathX.rndInt(4) + 2;     // 2..5 lif
            default: return MathX.rndInt(11) + 6;                     // 6..16 hurda
        }
    }

    /** Ağaç kesilirken yan ürün olarak biraz lif de düşer. */
    public static int fiberBonus(int prop) {
        return prop == WorldGen.PROP_TREE ? MathX.rndInt(3) + 1 : 0;
    }

    public static String verbOf(int prop) {
        switch (prop) {
            case WorldGen.PROP_TREE: return "Ağaç kesiliyor";
            case WorldGen.PROP_ROCK: return "Taş kırılıyor";
            case WorldGen.PROP_GRASS: return "Lif toplanıyor";
            default: return "Enkaz ayıklanıyor";
        }
    }

    public static String nameOf(int prop) {
        switch (prop) {
            case WorldGen.PROP_TREE: return "Ağaç";
            case WorldGen.PROP_ROCK: return "Kaya";
            case WorldGen.PROP_GRASS: return "Çalı";
            case WorldGen.PROP_BARREL: return "Varil";
            default: return "Sandık";
        }
    }

    private static final float[] scratchPos = new float[2];

    /** Kampın kaynak barındırmayan yarıçapı (sur + küçük bir pay). */
    public static float campClear(GameWorld w) {
        return w.baseRadius + 5f;
    }

    /** Düğümün dünya konumunu serpme hücresinden hesaplar (out[0]=x, out[1]=z). */
    public static void positionOf(int px, int pz, float[] out) {
        float jx = (WorldGen.rand01(px, pz, 501) - 0.5f) * STEP * 0.85f;
        float jz = (WorldGen.rand01(px, pz, 502) - 0.5f) * STEP * 0.85f;
        out[0] = (px + 0.5f) * STEP + jx;
        out[1] = (pz + 0.5f) * STEP + jz;
    }

    /**
     * Verilen noktanın çevresinde toplanabilir en yakın düğümü arar.
     * Bulursa out = {x, z, propTürü, px, pz} doldurulur ve uzaklık döner;
     * yoksa -1.
     */
    public static float nearest(GameWorld w, float x, float z, float range, float[] out) {
        int p0x = (int) Math.floor((x - range) / STEP);
        int p1x = (int) Math.floor((x + range) / STEP);
        int p0z = (int) Math.floor((z - range) / STEP);
        int p1z = (int) Math.floor((z + range) / STEP);
        float bestD = -1f;
        float[] pos = scratchPos;
        for (int pz = p0z; pz <= p1z; pz++) {
            for (int px = p0x; px <= p1x; px++) {
                positionOf(px, pz, pos);
                // Kampın içinde düğüm olmaz; surun hemen dışından itibaren başlar.
                if (MathX.len(pos[0], pos[1]) < campClear(w)) continue;
                if (WorldGen.blocked(pos[0], pos[1], 1f)) continue;
                int prop = WorldGen.propAt(px, pz, pos[0], pos[1]);
                if (prop == WorldGen.PROP_NONE) continue;
                if (w.isDepleted(key(px, pz))) continue;
                float d = MathX.dist(x, z, pos[0], pos[1]);
                if (d > range) continue;
                if (bestD < 0f || d < bestD) {
                    bestD = d;
                    out[0] = pos[0];
                    out[1] = pos[1];
                    out[2] = prop;
                    out[3] = px;
                    out[4] = pz;
                }
            }
        }
        return bestD;
    }
}
