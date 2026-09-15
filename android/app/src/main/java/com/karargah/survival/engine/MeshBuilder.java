package com.karargah.survival.engine;

import android.opengl.Matrix;

import java.util.ArrayList;

/**
 * Prosedürel mesh üretici. Kutu/silindir/küre gibi ilkel şekilleri bir dönüşüm
 * yığınıyla üst üste koyup tek bir Mesh'e derler. Tüm oyun modelleri bununla
 * üretilir; projede hiç harici 3B model dosyası yoktur.
 */
public class MeshBuilder {
    private float[] verts = new float[4096];
    private int vCount;
    private int[] indices = new int[6144];
    private int iCount;

    private final ArrayList<float[]> stack = new ArrayList<>();
    private float[] mat = M4.identity();

    private float cr = 1f, cg = 1f, cb = 1f;
    private float bone = 0f;
    private float maxR, minY = 1e9f, maxY = -1e9f;

    public MeshBuilder color(int argb) {
        cr = ((argb >> 16) & 0xFF) / 255f;
        cg = ((argb >> 8) & 0xFF) / 255f;
        cb = (argb & 0xFF) / 255f;
        return this;
    }

    public MeshBuilder color(float r, float g, float b) {
        cr = r; cg = g; cb = b;
        return this;
    }

    /** Rengi rastgele biraz koyulaştırıp/açarak düz yüzeylere doku hissi verir. */
    public MeshBuilder shade(int argb, float amount) {
        float f = 1f + MathX.rnd(-amount, amount);
        cr = MathX.clamp(((argb >> 16) & 0xFF) / 255f * f, 0f, 1f);
        cg = MathX.clamp(((argb >> 8) & 0xFF) / 255f * f, 0f, 1f);
        cb = MathX.clamp((argb & 0xFF) / 255f * f, 0f, 1f);
        return this;
    }

    public MeshBuilder bone(int index) {
        bone = index;
        return this;
    }

    public MeshBuilder push() {
        float[] copy = new float[16];
        System.arraycopy(mat, 0, copy, 0, 16);
        stack.add(copy);
        return this;
    }

    public MeshBuilder pop() {
        mat = stack.remove(stack.size() - 1);
        return this;
    }

    public MeshBuilder translate(float x, float y, float z) {
        Matrix.translateM(mat, 0, x, y, z);
        return this;
    }

    public MeshBuilder rotateY(float deg) {
        Matrix.rotateM(mat, 0, deg, 0, 1, 0);
        return this;
    }

    public MeshBuilder rotateX(float deg) {
        Matrix.rotateM(mat, 0, deg, 1, 0, 0);
        return this;
    }

    public MeshBuilder rotateZ(float deg) {
        Matrix.rotateM(mat, 0, deg, 0, 0, 1);
        return this;
    }

    public MeshBuilder scale(float s) {
        Matrix.scaleM(mat, 0, s, s, s);
        return this;
    }

    public MeshBuilder scale(float x, float y, float z) {
        Matrix.scaleM(mat, 0, x, y, z);
        return this;
    }

    public MeshBuilder resetTransform() {
        M4.setIdentity(mat);
        stack.clear();
        return this;
    }

    // ---- ilkel şekiller -------------------------------------------------

    /** Merkezi (0,0,0) olan kutu. */
    public MeshBuilder box(float sx, float sy, float sz) {
        return boxAt(0, 0, 0, sx, sy, sz);
    }

    /** Tabanı y=0'da duran kutu. */
    public MeshBuilder boxGround(float sx, float sy, float sz) {
        return boxAt(0, sy * 0.5f, 0, sx, sy, sz);
    }

    public MeshBuilder boxAt(float cx, float cy, float cz, float sx, float sy, float sz) {
        float hx = sx * 0.5f, hy = sy * 0.5f, hz = sz * 0.5f;
        // her yüz ayrı köşelerle -> düz (flat) gölgelendirme
        addQuad(cx - hx, cy - hy, cz + hz, cx + hx, cy - hy, cz + hz,
                cx + hx, cy + hy, cz + hz, cx - hx, cy + hy, cz + hz, 0, 0, 1);
        addQuad(cx + hx, cy - hy, cz - hz, cx - hx, cy - hy, cz - hz,
                cx - hx, cy + hy, cz - hz, cx + hx, cy + hy, cz - hz, 0, 0, -1);
        addQuad(cx + hx, cy - hy, cz + hz, cx + hx, cy - hy, cz - hz,
                cx + hx, cy + hy, cz - hz, cx + hx, cy + hy, cz + hz, 1, 0, 0);
        addQuad(cx - hx, cy - hy, cz - hz, cx - hx, cy - hy, cz + hz,
                cx - hx, cy + hy, cz + hz, cx - hx, cy + hy, cz - hz, -1, 0, 0);
        addQuad(cx - hx, cy + hy, cz + hz, cx + hx, cy + hy, cz + hz,
                cx + hx, cy + hy, cz - hz, cx - hx, cy + hy, cz - hz, 0, 1, 0);
        addQuad(cx - hx, cy - hy, cz - hz, cx + hx, cy - hy, cz - hz,
                cx + hx, cy - hy, cz + hz, cx - hx, cy - hy, cz + hz, 0, -1, 0);
        return this;
    }

    /** Tabanı y=0'da, Y ekseni boyunca uzanan (kesik) koni/silindir. */
    public MeshBuilder cylinder(float rBottom, float rTop, float h, int seg) {
        float step = MathX.TAU / seg;
        for (int i = 0; i < seg; i++) {
            float a0 = i * step, a1 = (i + 1) * step;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float nx = (float) Math.cos(a0 + step * 0.5f);
            float nz = (float) Math.sin(a0 + step * 0.5f);
            addQuad(c0 * rBottom, 0, s0 * rBottom, c0 * rTop, h, s0 * rTop,
                    c1 * rTop, h, s1 * rTop, c1 * rBottom, 0, s1 * rBottom, nx, 0.15f, nz);
            if (rTop > 0.001f) {
                addTri(0, h, 0, c1 * rTop, h, s1 * rTop, c0 * rTop, h, s0 * rTop, 0, 1, 0);
            }
            if (rBottom > 0.001f) {
                addTri(0, 0, 0, c0 * rBottom, 0, s0 * rBottom, c1 * rBottom, 0, s1 * rBottom, 0, -1, 0);
            }
        }
        return this;
    }

    /** Merkezi (0,0,0) olan UV küre. */
    public MeshBuilder sphere(float r, int seg, int rings) {
        for (int y = 0; y < rings; y++) {
            float v0 = (float) y / rings, v1 = (float) (y + 1) / rings;
            float p0 = v0 * MathX.PI, p1 = v1 * MathX.PI;
            for (int x = 0; x < seg; x++) {
                float u0 = (float) x / seg, u1 = (float) (x + 1) / seg;
                float t0 = u0 * MathX.TAU, t1 = u1 * MathX.TAU;
                float[] a = sphPoint(r, t0, p0), b = sphPoint(r, t1, p0);
                float[] c = sphPoint(r, t1, p1), d = sphPoint(r, t0, p1);
                float nx = (a[0] + c[0]) * 0.5f, ny = (a[1] + c[1]) * 0.5f, nz = (a[2] + c[2]) * 0.5f;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1e-5f) nl = 1f;
                addQuad(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2],
                        nx / nl, ny / nl, nz / nl);
            }
        }
        return this;
    }

    private static float[] sphPoint(float r, float theta, float phi) {
        float sp = (float) Math.sin(phi);
        return new float[]{r * sp * (float) Math.cos(theta), r * (float) Math.cos(phi),
                r * sp * (float) Math.sin(theta)};
    }

    /** XZ düzleminde, y=0'da yatay disk. */
    public MeshBuilder disc(float r, int seg) {
        float step = MathX.TAU / seg;
        for (int i = 0; i < seg; i++) {
            float a0 = i * step, a1 = (i + 1) * step;
            addTri(0, 0, 0,
                    (float) Math.cos(a1) * r, 0, (float) Math.sin(a1) * r,
                    (float) Math.cos(a0) * r, 0, (float) Math.sin(a0) * r, 0, 1, 0);
        }
        return this;
    }

    /** XZ düzleminde dikdörtgen (y=0). */
    public MeshBuilder quadXZ(float sx, float sz) {
        float hx = sx * 0.5f, hz = sz * 0.5f;
        addQuad(-hx, 0, hz, hx, 0, hz, hx, 0, -hz, -hx, 0, -hz, 0, 1, 0);
        return this;
    }

    /** Dörtgen piramit (taban y=0). */
    public MeshBuilder pyramid(float base, float h) {
        float b = base * 0.5f;
        addTri(-b, 0, b, b, 0, b, 0, h, 0, 0, 0.6f, 1);
        addTri(b, 0, b, b, 0, -b, 0, h, 0, 1, 0.6f, 0);
        addTri(b, 0, -b, -b, 0, -b, 0, h, 0, 0, 0.6f, -1);
        addTri(-b, 0, -b, -b, 0, b, 0, h, 0, -1, 0.6f, 0);
        addQuad(-b, 0, -b, b, 0, -b, b, 0, b, -b, 0, b, 0, -1, 0);
        return this;
    }

    // ---- düşük seviye ---------------------------------------------------

    public MeshBuilder addQuad(float ax, float ay, float az, float bx, float by, float bz,
                               float cx, float cy, float cz, float dx, float dy, float dz,
                               float nx, float ny, float nz) {
        int base = vCount;
        vertex(ax, ay, az, nx, ny, nz);
        vertex(bx, by, bz, nx, ny, nz);
        vertex(cx, cy, cz, nx, ny, nz);
        vertex(dx, dy, dz, nx, ny, nz);
        index(base, base + 1, base + 2);
        index(base, base + 2, base + 3);
        return this;
    }

    public MeshBuilder addTri(float ax, float ay, float az, float bx, float by, float bz,
                              float cx, float cy, float cz, float nx, float ny, float nz) {
        int base = vCount;
        vertex(ax, ay, az, nx, ny, nz);
        vertex(bx, by, bz, nx, ny, nz);
        vertex(cx, cy, cz, nx, ny, nz);
        index(base, base + 1, base + 2);
        return this;
    }

    private void vertex(float x, float y, float z, float nx, float ny, float nz) {
        // konum: tam dönüşüm
        float px = mat[0] * x + mat[4] * y + mat[8] * z + mat[12];
        float py = mat[1] * x + mat[5] * y + mat[9] * z + mat[13];
        float pz = mat[2] * x + mat[6] * y + mat[10] * z + mat[14];
        // normal: sadece dönüş/ölçek kısmı (ölçekler üniforma yakın olduğundan yeterli)
        float tx = mat[0] * nx + mat[4] * ny + mat[8] * nz;
        float ty = mat[1] * nx + mat[5] * ny + mat[9] * nz;
        float tz = mat[2] * nx + mat[6] * ny + mat[10] * nz;
        float l = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (l < 1e-6f) { tx = 0; ty = 1; tz = 0; l = 1f; }

        ensureVerts(Mesh.FLOATS_PER_VERTEX);
        int o = vCount * Mesh.FLOATS_PER_VERTEX;
        verts[o] = px; verts[o + 1] = py; verts[o + 2] = pz;
        verts[o + 3] = tx / l; verts[o + 4] = ty / l; verts[o + 5] = tz / l;
        verts[o + 6] = cr; verts[o + 7] = cg; verts[o + 8] = cb;
        verts[o + 9] = bone;
        vCount++;

        float r = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (r > maxR) maxR = r;
        if (py < minY) minY = py;
        if (py > maxY) maxY = py;
    }

    private void index(int a, int b, int c) {
        ensureIndices(3);
        indices[iCount++] = a;
        indices[iCount++] = b;
        indices[iCount++] = c;
    }

    private void ensureVerts(int extra) {
        int need = vCount * Mesh.FLOATS_PER_VERTEX + extra;
        if (need > verts.length) {
            float[] bigger = new float[Math.max(need, verts.length * 2)];
            System.arraycopy(verts, 0, bigger, 0, vCount * Mesh.FLOATS_PER_VERTEX);
            verts = bigger;
        }
    }

    private void ensureIndices(int extra) {
        if (iCount + extra > indices.length) {
            int[] bigger = new int[Math.max(iCount + extra, indices.length * 2)];
            System.arraycopy(indices, 0, bigger, 0, iCount);
            indices = bigger;
        }
    }

    public int vertexCount() {
        return vCount;
    }

    public Mesh build() {
        float[] v = new float[vCount * Mesh.FLOATS_PER_VERTEX];
        System.arraycopy(verts, 0, v, 0, v.length);
        int[] idx = new int[iCount];
        System.arraycopy(indices, 0, idx, 0, iCount);
        if (vCount == 0) { minY = 0; maxY = 0; }
        return new Mesh(v, idx, maxR, minY, maxY);
    }
}
