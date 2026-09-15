package com.karargah.survival.engine;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Havuzlanmış parçacık sistemi. Nokta sprite'ları ile çizilir; kan, kıvılcım,
 * duman, patlama ve namlu alevi için kullanılır.
 */
public class Particles {
    public static final int BLEND_ALPHA = 0;
    public static final int BLEND_ADD = 1;

    private static final int FLOATS = 8; // x,y,z, r,g,b,a, size
    private final int max;
    private final float[] px, py, pz, vx, vy, vz;
    private final float[] life, maxLife, size, sizeEnd;
    private final float[] cr, cg, cb, ca;
    private final float[] gravity, drag;
    private final int[] blend;
    private final boolean[] alive;
    private int cursor;

    private final float[] buffer;
    private final FloatBuffer glBuffer;
    private int vao, vbo;
    private boolean glReady;

    public Particles(int max) {
        this.max = max;
        px = new float[max]; py = new float[max]; pz = new float[max];
        vx = new float[max]; vy = new float[max]; vz = new float[max];
        life = new float[max]; maxLife = new float[max];
        size = new float[max]; sizeEnd = new float[max];
        cr = new float[max]; cg = new float[max]; cb = new float[max]; ca = new float[max];
        gravity = new float[max]; drag = new float[max];
        blend = new int[max];
        alive = new boolean[max];
        buffer = new float[max * FLOATS];
        glBuffer = ByteBuffer.allocateDirect(max * FLOATS * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public void initGl() {
        int[] tmp = new int[1];
        GLES30.glGenVertexArrays(1, tmp, 0);
        vao = tmp[0];
        GLES30.glGenBuffers(1, tmp, 0);
        vbo = tmp[0];
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, max * FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, FLOATS * 4, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, FLOATS * 4, 12);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, FLOATS * 4, 28);
        GLES30.glBindVertexArray(0);
        glReady = true;
    }

    public void clear() {
        for (int i = 0; i < max; i++) alive[i] = false;
    }

    public void spawn(float x, float y, float z, float vX, float vY, float vZ,
                      int color, float alpha, float startSize, float endSize,
                      float lifeSec, float grav, float dragK, int blendMode) {
        int start = cursor;
        for (int n = 0; n < max; n++) {
            int i = (start + n) % max;
            if (!alive[i]) {
                cursor = (i + 1) % max;
                alive[i] = true;
                px[i] = x; py[i] = y; pz[i] = z;
                vx[i] = vX; vy[i] = vY; vz[i] = vZ;
                cr[i] = ((color >> 16) & 0xFF) / 255f;
                cg[i] = ((color >> 8) & 0xFF) / 255f;
                cb[i] = (color & 0xFF) / 255f;
                ca[i] = alpha;
                size[i] = startSize;
                sizeEnd[i] = endSize;
                life[i] = lifeSec;
                maxLife[i] = lifeSec;
                gravity[i] = grav;
                drag[i] = dragK;
                blend[i] = blendMode;
                return;
            }
        }
    }

    // ---- hazır efektler -------------------------------------------------

    public void blood(float x, float y, float z, float dirX, float dirZ, int count) {
        for (int i = 0; i < count; i++) {
            spawn(x, y, z,
                    dirX * MathX.rnd(0.5f, 3f) + MathX.rnd(-1.6f, 1.6f),
                    MathX.rnd(1.2f, 4.2f),
                    dirZ * MathX.rnd(0.5f, 3f) + MathX.rnd(-1.6f, 1.6f),
                    MathX.chance(0.5f) ? 0x8E1B1B : 0xB52626, 0.95f,
                    0.055f, 0.02f, MathX.rnd(0.35f, 0.7f), -11f, 1.2f, BLEND_ALPHA);
        }
    }

    public void muzzleFlash(float x, float y, float z, float dirX, float dirZ) {
        for (int i = 0; i < 5; i++) {
            spawn(x, y, z,
                    dirX * MathX.rnd(4f, 11f) + MathX.rnd(-1.2f, 1.2f),
                    MathX.rnd(-0.5f, 1.1f),
                    dirZ * MathX.rnd(4f, 11f) + MathX.rnd(-1.2f, 1.2f),
                    i < 2 ? 0xFFF0B0 : 0xFF9A30, 0.95f,
                    0.11f, 0.01f, MathX.rnd(0.05f, 0.13f), 0f, 5f, BLEND_ADD);
        }
    }

    public void sparks(float x, float y, float z, int count, int color) {
        for (int i = 0; i < count; i++) {
            spawn(x, y, z,
                    MathX.rnd(-4f, 4f), MathX.rnd(0.5f, 5f), MathX.rnd(-4f, 4f),
                    color, 1f, 0.05f, 0.005f, MathX.rnd(0.2f, 0.55f), -9f, 1.4f, BLEND_ADD);
        }
    }

    public void explosion(float x, float y, float z, float radius) {
        int n = (int) (26 + radius * 9);
        for (int i = 0; i < n; i++) {
            float a = MathX.rnd(0f, MathX.TAU);
            float sp = MathX.rnd(1.5f, 6f) * radius * 0.5f;
            spawn(x, y + MathX.rnd(0f, 0.6f),
                    z, (float) Math.cos(a) * sp, MathX.rnd(1f, 5f), (float) Math.sin(a) * sp,
                    i % 3 == 0 ? 0xFFE08A : 0xFF7A22, 0.9f,
                    radius * 0.28f, radius * 0.06f, MathX.rnd(0.25f, 0.6f), -3.5f, 2.2f, BLEND_ADD);
        }
        for (int i = 0; i < 12; i++) {
            float a = MathX.rnd(0f, MathX.TAU);
            spawn(x, y + 0.3f, z, (float) Math.cos(a) * MathX.rnd(0.4f, 2f),
                    MathX.rnd(0.8f, 2.4f), (float) Math.sin(a) * MathX.rnd(0.4f, 2f),
                    0x3A3530, 0.4f, radius * 0.35f, radius * 0.9f,
                    MathX.rnd(0.6f, 1.2f), 0.6f, 1.1f, BLEND_ALPHA);
        }
    }

    public void smoke(float x, float y, float z, int count, int color, float scale) {
        for (int i = 0; i < count; i++) {
            spawn(x + MathX.rnd(-0.2f, 0.2f), y, z + MathX.rnd(-0.2f, 0.2f),
                    MathX.rnd(-0.4f, 0.4f), MathX.rnd(0.5f, 1.4f), MathX.rnd(-0.4f, 0.4f),
                    color, 0.35f, scale * 0.4f, scale, MathX.rnd(0.8f, 1.6f), 0.4f, 1.3f, BLEND_ALPHA);
        }
    }

    public void dust(float x, float y, float z, int count) {
        for (int i = 0; i < count; i++) {
            spawn(x, y, z, MathX.rnd(-0.7f, 0.7f), MathX.rnd(0.2f, 1.2f), MathX.rnd(-0.7f, 0.7f),
                    0x9C8F76, 0.3f, 0.12f, 0.3f, MathX.rnd(0.3f, 0.8f), 0.2f, 1.6f, BLEND_ALPHA);
        }
    }

    public void update(float dt) {
        for (int i = 0; i < max; i++) {
            if (!alive[i]) continue;
            life[i] -= dt;
            if (life[i] <= 0f) {
                alive[i] = false;
                continue;
            }
            float d = Math.max(0f, 1f - drag[i] * dt);
            vx[i] *= d;
            vz[i] *= d;
            vy[i] += gravity[i] * dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
            if (py[i] < 0.02f && gravity[i] < 0f) {
                py[i] = 0.02f;
                vy[i] = -vy[i] * 0.25f;
                vx[i] *= 0.6f;
                vz[i] *= 0.6f;
            }
        }
    }

    /** İki geçişte çizer: önce alfa karışımlı, sonra eklemeli parçacıklar. */
    public void render(Renderer3D r) {
        if (!glReady) initGl();
        Shader s = r.particleShader();
        s.use();
        s.setMat4("uViewProj", r.viewProj);
        s.setFloat("uViewportH", r.viewportH * 0.9f);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glDepthMask(false);
        renderPass(s, BLEND_ALPHA);
        renderPass(s, BLEND_ADD);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glBindVertexArray(0);
    }

    private void renderPass(Shader s, int mode) {
        int n = 0;
        for (int i = 0; i < max; i++) {
            if (!alive[i] || blend[i] != mode) continue;
            float t = 1f - life[i] / maxLife[i];
            int o = n * FLOATS;
            buffer[o] = px[i];
            buffer[o + 1] = py[i];
            buffer[o + 2] = pz[i];
            buffer[o + 3] = cr[i];
            buffer[o + 4] = cg[i];
            buffer[o + 5] = cb[i];
            buffer[o + 6] = ca[i] * (1f - t * t);
            buffer[o + 7] = MathX.lerp(size[i], sizeEnd[i], t);
            n++;
        }
        if (n == 0) return;
        if (mode == BLEND_ADD) {
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE);
        } else {
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        }
        glBuffer.position(0);
        glBuffer.put(buffer, 0, n * FLOATS);
        glBuffer.position(0);
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * FLOATS * 4, glBuffer);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, n);
    }

    public void disposeGl() {
        glReady = false;
    }
}
