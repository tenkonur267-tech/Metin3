package com.karargah.survival.engine;

/**
 * 4x4 matris yardımcıları (sütun öncelikli, OpenGL düzeni).
 *
 * Bilerek saf Java: android.opengl.Matrix'e bağlı kalınsaydı mesh üretimi ve
 * kamera matematiği birim testlerinde çalışmazdı. Davranış, Android'in
 * Matrix sınıfıyla birebir aynıdır (M4Test bunu doğrular).
 */
public final class M4 {
    private M4() {}

    private static final ThreadLocal<float[]> TMP = ThreadLocal.withInitial(() -> new float[32]);

    public static float[] identity() {
        float[] m = new float[16];
        setIdentity(m);
        return m;
    }

    public static void setIdentity(float[] m) {
        setIdentity(m, 0);
    }

    public static void setIdentity(float[] m, int o) {
        for (int i = 0; i < 16; i++) m[o + i] = 0f;
        m[o] = 1f;
        m[o + 5] = 1f;
        m[o + 10] = 1f;
        m[o + 15] = 1f;
    }

    /** r = a * b (r, a veya b ile aynı dizi olmamalı). */
    public static void multiplyMM(float[] r, int ro, float[] a, int ao, float[] b, int bo) {
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[ao + k * 4 + row] * b[bo + c * 4 + k];
                }
                r[ro + c * 4 + row] = sum;
            }
        }
    }

    public static void mul(float[] out, float[] a, float[] b) {
        multiplyMM(out, 0, a, 0, b, 0);
    }

    /** m = m * T(x,y,z) — yerinde öteleme. */
    public static void translateM(float[] m, int o, float x, float y, float z) {
        for (int i = 0; i < 4; i++) {
            m[o + 12 + i] += m[o + i] * x + m[o + 4 + i] * y + m[o + 8 + i] * z;
        }
    }

    /** m = m * S(x,y,z) — yerinde ölçekleme. */
    public static void scaleM(float[] m, int o, float x, float y, float z) {
        for (int i = 0; i < 4; i++) {
            m[o + i] *= x;
            m[o + 4 + i] *= y;
            m[o + 8 + i] *= z;
        }
    }

    /** m = m * R(açı, eksen) — açı derece cinsinden, yerinde döndürme. */
    public static void rotateM(float[] m, int o, float angleDeg, float x, float y, float z) {
        float[] tmp = TMP.get();
        setRotate(tmp, 0, angleDeg, x, y, z);
        multiplyMM(tmp, 16, m, o, tmp, 0);
        System.arraycopy(tmp, 16, m, o, 16);
    }

    public static void setRotate(float[] m, int o, float angleDeg, float x, float y, float z) {
        float a = angleDeg * MathX.DEG;
        float s = (float) Math.sin(a);
        float c = (float) Math.cos(a);
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len > 1e-8f && Math.abs(len - 1f) > 1e-6f) {
            x /= len;
            y /= len;
            z /= len;
        }
        float t = 1f - c;
        m[o] = x * x * t + c;
        m[o + 1] = y * x * t + z * s;
        m[o + 2] = z * x * t - y * s;
        m[o + 3] = 0f;
        m[o + 4] = x * y * t - z * s;
        m[o + 5] = y * y * t + c;
        m[o + 6] = z * y * t + x * s;
        m[o + 7] = 0f;
        m[o + 8] = x * z * t + y * s;
        m[o + 9] = y * z * t - x * s;
        m[o + 10] = z * z * t + c;
        m[o + 11] = 0f;
        m[o + 12] = 0f;
        m[o + 13] = 0f;
        m[o + 14] = 0f;
        m[o + 15] = 1f;
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

    /** Tam dönüşlü (pitch-yaw-roll, radyan) TRS. */
    public static void trsFull(float[] out, float x, float y, float z,
                               float rx, float ry, float rz, float sx, float sy, float sz) {
        setIdentity(out, 0);
        translateM(out, 0, x, y, z);
        if (ry != 0) rotateM(out, 0, ry / MathX.DEG, 0, 1, 0);
        if (rx != 0) rotateM(out, 0, rx / MathX.DEG, 1, 0, 0);
        if (rz != 0) rotateM(out, 0, rz / MathX.DEG, 0, 0, 1);
        scaleM(out, 0, sx, sy, sz);
    }

    public static boolean invert(float[] out, float[] m) {
        return invertM(out, 0, m, 0);
    }

    /** Genel 4x4 ters alma (kofaktör yöntemi). */
    public static boolean invertM(float[] out, int oo, float[] m, int mo) {
        float m00 = m[mo], m01 = m[mo + 4], m02 = m[mo + 8], m03 = m[mo + 12];
        float m10 = m[mo + 1], m11 = m[mo + 5], m12 = m[mo + 9], m13 = m[mo + 13];
        float m20 = m[mo + 2], m21 = m[mo + 6], m22 = m[mo + 10], m23 = m[mo + 14];
        float m30 = m[mo + 3], m31 = m[mo + 7], m32 = m[mo + 11], m33 = m[mo + 15];

        float b00 = m00 * m11 - m01 * m10;
        float b01 = m00 * m12 - m02 * m10;
        float b02 = m00 * m13 - m03 * m10;
        float b03 = m01 * m12 - m02 * m11;
        float b04 = m01 * m13 - m03 * m11;
        float b05 = m02 * m13 - m03 * m12;
        float b06 = m20 * m31 - m21 * m30;
        float b07 = m20 * m32 - m22 * m30;
        float b08 = m20 * m33 - m23 * m30;
        float b09 = m21 * m32 - m22 * m31;
        float b10 = m21 * m33 - m23 * m31;
        float b11 = m22 * m33 - m23 * m32;

        float det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (Math.abs(det) < 1e-12f) return false;
        float id = 1f / det;

        out[oo] = (m11 * b11 - m12 * b10 + m13 * b09) * id;
        out[oo + 1] = (-m10 * b11 + m12 * b08 - m13 * b07) * id;
        out[oo + 2] = (m10 * b10 - m11 * b08 + m13 * b06) * id;
        out[oo + 3] = (-m10 * b09 + m11 * b07 - m12 * b06) * id;

        out[oo + 4] = (-m01 * b11 + m02 * b10 - m03 * b09) * id;
        out[oo + 5] = (m00 * b11 - m02 * b08 + m03 * b07) * id;
        out[oo + 6] = (-m00 * b10 + m01 * b08 - m03 * b06) * id;
        out[oo + 7] = (m00 * b09 - m01 * b07 + m02 * b06) * id;

        out[oo + 8] = (m31 * b05 - m32 * b04 + m33 * b03) * id;
        out[oo + 9] = (-m30 * b05 + m32 * b02 - m33 * b01) * id;
        out[oo + 10] = (m30 * b04 - m31 * b02 + m33 * b00) * id;
        out[oo + 11] = (-m30 * b03 + m31 * b01 - m32 * b00) * id;

        out[oo + 12] = (-m21 * b05 + m22 * b04 - m23 * b03) * id;
        out[oo + 13] = (m20 * b05 - m22 * b02 + m23 * b01) * id;
        out[oo + 14] = (-m20 * b04 + m21 * b02 - m23 * b00) * id;
        out[oo + 15] = (m20 * b03 - m21 * b01 + m22 * b00) * id;
        return true;
    }

    public static void perspective(float[] out, float fovDeg, float aspect, float near, float far) {
        perspectiveM(out, 0, fovDeg, aspect, near, far);
    }

    public static void perspectiveM(float[] m, int o, float fovyDeg, float aspect,
                                    float near, float far) {
        float f = 1f / (float) Math.tan(fovyDeg * (Math.PI / 360.0));
        float rangeReciprocal = 1f / (near - far);
        for (int i = 0; i < 16; i++) m[o + i] = 0f;
        m[o] = f / aspect;
        m[o + 5] = f;
        m[o + 10] = (far + near) * rangeReciprocal;
        m[o + 11] = -1f;
        m[o + 14] = 2f * far * near * rangeReciprocal;
    }

    public static void lookAt(float[] out, float ex, float ey, float ez,
                              float cx, float cy, float cz) {
        setLookAtM(out, 0, ex, ey, ez, cx, cy, cz, 0f, 1f, 0f);
    }

    public static void setLookAtM(float[] m, int o, float ex, float ey, float ez,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz) {
        float fx = cx - ex, fy = cy - ey, fz = cz - ez;
        float rlf = 1f / (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx *= rlf; fy *= rlf; fz *= rlf;

        float sx = fy * uz - fz * uy;
        float sy = fz * ux - fx * uz;
        float sz = fx * uy - fy * ux;
        float rls = 1f / (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx *= rls; sy *= rls; sz *= rls;

        float upx = sy * fz - sz * fy;
        float upy = sz * fx - sx * fz;
        float upz = sx * fy - sy * fx;

        m[o] = sx;      m[o + 1] = upx;   m[o + 2] = -fx;   m[o + 3] = 0f;
        m[o + 4] = sy;  m[o + 5] = upy;   m[o + 6] = -fy;   m[o + 7] = 0f;
        m[o + 8] = sz;  m[o + 9] = upz;   m[o + 10] = -fz;  m[o + 11] = 0f;
        m[o + 12] = 0f; m[o + 13] = 0f;   m[o + 14] = 0f;   m[o + 15] = 1f;
        translateM(m, o, -ex, -ey, -ez);
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
     * @return w pozitifse (kameranın önündeyse) true
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
