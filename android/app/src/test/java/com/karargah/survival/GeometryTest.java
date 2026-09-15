package com.karargah.survival;

import static org.junit.Assert.assertTrue;

import com.karargah.survival.engine.MeshBuilder;

import org.junit.Test;

/**
 * Prosedürel mesh üreticisinin doğruluğu: normaller birim uzunlukta olmalı,
 * üçgen sarım yönü (winding) normalle uyuşmalı ve kapalı şekillerde yüzler
 * dışa bakmalı. Ters sarım, modellerin içten görünmesine yol açar.
 */
public class GeometryTest {
    private static final int FPV = 10;   // pos3 + normal3 + renk3 + kemik1

    private static void check(String ad, MeshBuilder b, float cx, float cy, float cz,
                              boolean closedShape) {
        float[] v = b.vertexData();
        int[] idx = b.indexData();
        assertTrue(ad + ": köşe üretilmeli", v.length > 0);
        assertTrue(ad + ": üçgen üretilmeli", idx.length >= 3 && idx.length % 3 == 0);

        for (int i = 0; i < v.length; i++) {
            assertTrue(ad + ": sayı bozuk", !Float.isNaN(v[i]) && !Float.isInfinite(v[i]));
        }
        for (int i = 0; i < v.length / FPV; i++) {
            float nx = v[i * FPV + 3], ny = v[i * FPV + 4], nz = v[i * FPV + 5];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            assertTrue(ad + ": normal birim olmalı (" + len + ")", Math.abs(len - 1f) < 0.02f);
            float bone = v[i * FPV + 9];
            assertTrue(ad + ": kemik indeksi geçerli olmalı", bone >= 0f && bone < 10f);
        }

        for (int t = 0; t < idx.length; t += 3) {
            int a = idx[t] * FPV, bb = idx[t + 1] * FPV, c = idx[t + 2] * FPV;
            float ux = v[bb] - v[a], uy = v[bb + 1] - v[a + 1], uz = v[bb + 2] - v[a + 2];
            float wx = v[c] - v[bb], wy = v[c + 1] - v[bb + 1], wz = v[c + 2] - v[bb + 2];
            float gx = uy * wz - uz * wy;
            float gy = uz * wx - ux * wz;
            float gz = ux * wy - uy * wx;
            float gl = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (gl < 1e-6f) continue;    // dejenere üçgen yoksay
            gx /= gl; gy /= gl; gz /= gl;
            float nx = v[a + 3], ny = v[a + 4], nz = v[a + 5];
            assertTrue(ad + ": sarım yönü normalle ters (üçgen " + (t / 3) + ")",
                    gx * nx + gy * ny + gz * nz > 0.2f);

            if (closedShape) {
                float mx = (v[a] + v[bb] + v[c]) / 3f - cx;
                float my = (v[a + 1] + v[bb + 1] + v[c + 1]) / 3f - cy;
                float mz = (v[a + 2] + v[bb + 2] + v[c + 2]) / 3f - cz;
                assertTrue(ad + ": yüz içe bakıyor (üçgen " + (t / 3) + ")",
                        gx * mx + gy * my + gz * mz > 0f);
            }
        }
    }

    @Test
    public void kutuDisaBakar() {
        check("kutu", new MeshBuilder().color(0xFFFFFF).box(2f, 3f, 4f), 0, 0, 0, true);
        check("zeminKutu", new MeshBuilder().color(0xFFFFFF).boxGround(2f, 2f, 2f), 0, 1f, 0, true);
    }

    @Test
    public void silindirVeKoniDisaBakar() {
        check("silindir", new MeshBuilder().color(0xFFFFFF).cylinder(1f, 1f, 3f, 12), 0, 1.5f, 0, true);
        check("kesikKoni", new MeshBuilder().color(0xFFFFFF).cylinder(1.2f, 0.4f, 2f, 10), 0, 0.7f, 0, true);
        check("koni", new MeshBuilder().color(0xFFFFFF).cylinder(1f, 0f, 2f, 9), 0, 0.5f, 0, true);
    }

    @Test
    public void kureDisaBakar() {
        check("küre", new MeshBuilder().color(0xFFFFFF).sphere(1.5f, 14, 8), 0, 0, 0, true);
    }

    @Test
    public void piramitDisaBakar() {
        check("piramit", new MeshBuilder().color(0xFFFFFF).pyramid(2f, 3f), 0, 0.75f, 0, true);
    }

    @Test
    public void duzYuzeylerTutarli() {
        check("disk", new MeshBuilder().color(0xFFFFFF).disc(2f, 16), 0, 0, 0, false);
        check("dörtgen", new MeshBuilder().color(0xFFFFFF).quadXZ(4f, 4f), 0, 0, 0, false);
    }

    @Test
    public void donusumVeKemikUygulanir() {
        MeshBuilder b = new MeshBuilder();
        b.color(0xFFFFFF).bone(3).push().translate(5f, 2f, -3f).rotateY(37f).scale(0.5f)
                .box(1f, 1f, 1f).pop();
        float[] v = b.vertexData();
        boolean near = false;
        for (int i = 0; i < v.length / FPV; i++) {
            float dx = v[i * FPV] - 5f, dy = v[i * FPV + 1] - 2f, dz = v[i * FPV + 2] + 3f;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < 0.8f) near = true;
            assertTrue("kemik indeksi taşınmalı", Math.abs(v[i * FPV + 9] - 3f) < 0.001f);
        }
        assertTrue("dönüşüm uygulanmalı", near);
        // ölçek uygulandıysa kenar 0.5 birim olmalı
        check("dönüştürülmüşKutu", b, 5f, 2f, -3f, true);
    }
}
