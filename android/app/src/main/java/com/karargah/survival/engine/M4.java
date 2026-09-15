package com.karargah.survival.engine;

import android.opengl.Matrix;

/** float[16] matrisler üzerinde çalışan yardımcılar (sütun-öncelikli, OpenGL düzeni). */
public final class M4 {
    private M4() {}

    public static float[] identity() {
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        return m;
    }

    public static void setIdentity(float[] m) {
        Matrix.setIdentityM(m, 0);
    }

    /** Öteleme + Y ekseni dönüşü + ölçek. Oyundaki nesnelerin çoğu bunu kullanır. */
    public static void trs(float[] out, float x, float y, float z, float yaw,
                           float sx, float sy, float sz) {
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        out[0] = c * sx;  out[1] = 0;      out[2] = -s * sx; out[3] = 0;
        out[4] = 0;       out[5] = sy;     out[6] = 0;       out[7] = 0;
        out[8] = s * sz;  out[9] = 0;      out[10] = c * sz; out[11] = 0;
        out[12] = x;      out[13] = y;     out[14] = z;      out[15] = 1;
    }

    public static void trs(float[] out, float x, float y, float z, float yaw, float scale) {
        trs(out, x, y, z, yaw, scale, scale, scale);
    }

    /** Tam dönüşlü (yaw-pitch-roll) TRS. */
    public static void trsFull(float[] out, float x, float y, float z,
                               float rx, float ry, float rz, float sx, float sy, float sz) {
        Matrix.setIdentityM(out, 0);
        Matrix.translateM(out, 0, x, y, z);
        if (ry != 0) Matrix.rotateM(out, 0, ry / MathX.DEG, 0, 1, 0);
        if (rx != 0) Matrix.rotateM(out, 0, rx / MathX.DEG, 1, 0, 0);
        if (rz != 0) Matrix.rotateM(out, 0, rz / MathX.DEG, 0, 0, 1);
        Matrix.scaleM(out, 0, sx, sy, sz);
    }

    public static void mul(float[] out, float[] a, float[] b) {
        Matrix.multiplyMM(out, 0, a, 0, b, 0);
    }

    public static boolean invert(float[] out, float[] m) {
        return Matrix.invertM(out, 0, m, 0);
    }

    public static void perspective(float[] out, float fovDeg, float aspect, float near, float far) {
        Matrix.perspectiveM(out, 0, fovDeg, aspect, near, far);
    }

    public static void lookAt(float[] out, float ex, float ey, float ez,
                              float cx, float cy, float cz) {
        Matrix.setLookAtM(out, 0, ex, ey, ez, cx, cy, cz, 0f, 1f, 0f);
    }

    /** Noktayı matrisle çarpar; sonuç out[0..2], out[3] = w. */
    public static void transformPoint(float[] out4, float[] m, float x, float y, float z) {
        out4[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out4[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out4[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
        out4[3] = m[3] * x + m[7] * y + m[11] * z + m[15];
    }

    /**
     * Dünya noktasını ekran pikseline yansıtır.
     * @return z (w) pozitifse görünür (ekranın önünde).
     */
    public static boolean project(float[] viewProj, float x, float y, float z,
                                  int vw, int vh, float[] tmp4, float[] outXY) {
        transformPoint(tmp4, viewProj, x, y, z);
        float w = tmp4[3];
        if (w <= 0.0001f) return false;
        outXY[0] = (tmp4[0] / w * 0.5f + 0.5f) * vw;
        outXY[1] = (1f - (tmp4[1] / w * 0.5f + 0.5f)) * vh;
        return true;
    }

    /**
     * Ekran pikselinden y=planeY düzlemine ışın atar.
     * @return true ise outXZ dolduruldu.
     */
    public static boolean unprojectToPlane(float[] invViewProj, float sx, float sy,
                                           int vw, int vh, float planeY,
                                           float[] tmpA, float[] tmpB, float[] outXZ) {
        float ndcX = (sx / vw) * 2f - 1f;
        float ndcY = 1f - (sy / vh) * 2f;
        transformPoint(tmpA, invViewProj, ndcX, ndcY, -1f);
        transformPoint(tmpB, invViewProj, ndcX, ndcY, 1f);
        if (Math.abs(tmpA[3]) < 1e-6f || Math.abs(tmpB[3]) < 1e-6f) return false;
        float ax = tmpA[0] / tmpA[3], ay = tmpA[1] / tmpA[3], az = tmpA[2] / tmpA[3];
        float bx = tmpB[0] / tmpB[3], by = tmpB[1] / tmpB[3], bz = tmpB[2] / tmpB[3];
        float dy = by - ay;
        if (Math.abs(dy) < 1e-6f) return false;
        float t = (planeY - ay) / dy;
        if (t < 0f || t > 1.2f) return false;
        outXZ[0] = ax + (bx - ax) * t;
        outXZ[1] = az + (bz - az) * t;
        return true;
    }
}
