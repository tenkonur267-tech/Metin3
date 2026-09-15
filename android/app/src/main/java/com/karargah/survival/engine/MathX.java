package com.karargah.survival.engine;

import java.util.Random;

/** Oyun genelinde kullanılan küçük matematik yardımcıları. */
public final class MathX {
    public static final float PI = (float) Math.PI;
    public static final float TAU = (float) (Math.PI * 2.0);
    public static final float DEG = (float) (Math.PI / 180.0);

    private static final Random RND = new Random();

    /** Testlerin yinelenebilir olması için rastgeleliği sabitler. */
    public static void setSeed(long seed) {
        RND.setSeed(seed);
    }

    private MathX() {}

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clampI(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Kare/frame süresinden bağımsız yumuşatma. */
    public static float damp(float a, float b, float rate, float dt) {
        return lerp(a, b, 1f - (float) Math.exp(-rate * dt));
    }

    public static float len(float x, float z) {
        return (float) Math.sqrt(x * x + z * z);
    }

    public static float dist2(float ax, float az, float bx, float bz) {
        float dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz;
    }

    public static float dist(float ax, float az, float bx, float bz) {
        return (float) Math.sqrt(dist2(ax, az, bx, bz));
    }

    public static float dist3(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = ax - bx, dy = ay - by, dz = az - bz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** İki açı arasındaki en kısa farkı (-PI..PI) verir. */
    public static float angleDiff(float from, float to) {
        float d = to - from;
        while (d > PI) d -= TAU;
        while (d < -PI) d += TAU;
        return d;
    }

    public static float approachAngle(float cur, float target, float maxStep) {
        float d = angleDiff(cur, target);
        if (d > maxStep) d = maxStep;
        if (d < -maxStep) d = -maxStep;
        return cur + d;
    }

    public static float rnd() {
        return RND.nextFloat();
    }

    public static float rnd(float a, float b) {
        return a + RND.nextFloat() * (b - a);
    }

    public static int rndInt(int nExclusive) {
        return nExclusive <= 0 ? 0 : RND.nextInt(nExclusive);
    }

    public static boolean chance(float p) {
        return RND.nextFloat() < p;
    }

    /** Deterministik 2B gürültü (arazi rengi/yüksekliği için). */
    public static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    public static float smoothNoise(float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float xf = x - xi, yf = y - yi;
        float u = xf * xf * (3 - 2 * xf);
        float v = yf * yf * (3 - 2 * yf);
        float a = hash(xi, yi), b = hash(xi + 1, yi);
        float c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
        return lerp(lerp(a, b, u), lerp(c, d, u), v);
    }

    /** ARGB rengini 0..1 aralığındaki rgb dizisine çevirir. */
    public static void colorToRgb(int argb, float[] out) {
        out[0] = ((argb >> 16) & 0xFF) / 255f;
        out[1] = ((argb >> 8) & 0xFF) / 255f;
        out[2] = (argb & 0xFF) / 255f;
    }

    public static int mixColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) lerp(ar, br, t), g = (int) lerp(ag, bg, t), bl = (int) lerp(ab, bb, t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
