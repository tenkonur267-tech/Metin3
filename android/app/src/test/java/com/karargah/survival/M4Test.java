package com.karargah.survival;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.karargah.survival.engine.M4;

import org.junit.Test;

import java.util.Random;

/**
 * Matris matematiğinin doğruluğu. (Uygulama bilerek saf Java'dır; aksi halde
 * android.opengl.Matrix birim testlerinde boş döner ve mesh üretimi test
 * edilemez. Davranışın Android sürümüyle birebir aynı olduğu geliştirme
 * sırasında rastgele girdilerle karşılaştırılarak doğrulanmıştır.)
 */
public class M4Test {
    private static final Random R = new Random(42);

    private static float[] randomMatrix() {
        float[] m = new float[16];
        for (int i = 0; i < 16; i++) m[i] = R.nextFloat() * 4f - 2f;
        return m;
    }

    @Test
    public void birimMatrisNotrdur() {
        float[] id = M4.identity();
        float[] m = randomMatrix();
        float[] r = new float[16];
        M4.multiplyMM(r, 0, m, 0, id, 0);
        for (int i = 0; i < 16; i++) assertEquals(m[i], r[i], 1e-4f);
        M4.multiplyMM(r, 0, id, 0, m, 0);
        for (int i = 0; i < 16; i++) assertEquals(m[i], r[i], 1e-4f);
    }

    @Test
    public void tersMatrisGeriDonduruyor() {
        for (int t = 0; t < 50; t++) {
            float[] m = M4.identity();
            M4.translateM(m, 0, R.nextFloat() * 10f, R.nextFloat() * 10f, R.nextFloat() * 10f);
            M4.rotateM(m, 0, R.nextFloat() * 360f, 0.3f, 1f, 0.2f);
            M4.scaleM(m, 0, 0.5f + R.nextFloat(), 0.5f + R.nextFloat(), 0.5f + R.nextFloat());
            float[] inv = new float[16];
            assertTrue("ters alınabilmeli", M4.invertM(inv, 0, m, 0));
            float[] r = new float[16];
            M4.multiplyMM(r, 0, m, 0, inv, 0);
            float[] id = M4.identity();
            for (int i = 0; i < 16; i++) assertEquals("m * m^-1 birim olmalı", id[i], r[i], 2e-3f);
        }
    }

    @Test
    public void donusUzunlugukorur() {
        float[] m = M4.identity();
        M4.rotateM(m, 0, 37f, 0f, 1f, 0f);
        float[] out = new float[4];
        M4.transformPoint(out, m, 3f, 0f, 4f);
        float len = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        assertEquals("dönüş uzunluğu değiştirmemeli", 5f, len, 1e-3f);
    }

    @Test
    public void trsBeklenenYerdeKonumlandirir() {
        float[] m = new float[16];
        M4.trs(m, 5f, 2f, -3f, (float) Math.PI / 2f, 1f);
        float[] out = new float[4];
        // +Z yönü, 90 derece yaw ile +X'e döner
        M4.transformPoint(out, m, 0f, 0f, 1f);
        assertEquals(6f, out[0], 1e-3f);
        assertEquals(2f, out[1], 1e-3f);
        assertEquals(-3f, out[2], 1e-3f);
    }

    @Test
    public void kameraMatrisiOnuGoruyor() {
        float[] view = new float[16], proj = new float[16], vp = new float[16];
        M4.perspectiveM(proj, 0, 60f, 1.6f, 0.5f, 300f);
        M4.setLookAtM(view, 0, 0f, 5f, -10f, 0f, 1f, 0f, 0f, 1f, 0f);
        M4.multiplyMM(vp, 0, proj, 0, view, 0);

        float[] tmp = new float[4], screen = new float[2];
        assertTrue("önümüzdeki nokta ekrana düşmeli",
                M4.project(vp, 0f, 1f, 0f, 1600, 900, tmp, screen));
        assertTrue("ekranın ortasına yakın olmalı",
                Math.abs(screen[0] - 800) < 40 && Math.abs(screen[1] - 450) < 60);
        assertTrue("arkamızdaki nokta görünmemeli",
                !M4.project(vp, 0f, 1f, -40f, 1600, 900, tmp, screen));
    }

    @Test
    public void ekrandanZeminePisirmeCalisir() {
        float[] view = new float[16], proj = new float[16], vp = new float[16], inv = new float[16];
        M4.perspectiveM(proj, 0, 58f, 1.6f, 0.5f, 320f);
        M4.setLookAtM(view, 0, 0f, 12f, -12f, 0f, 1f, 0f, 0f, 1f, 0f);
        M4.multiplyMM(vp, 0, proj, 0, view, 0);
        assertTrue(M4.invertM(inv, 0, vp, 0));

        // bilinen bir dünya noktasını ekrana yansıt, sonra geri çöz
        float wx = 4.5f, wz = 2.5f;
        float[] tmp = new float[4], screen = new float[2];
        assertTrue(M4.project(vp, wx, 0.35f, wz, 1600, 900, tmp, screen));
        float[] a = new float[4], b = new float[4], xz = new float[2];
        assertTrue(M4.unprojectToPlane(inv, screen[0], screen[1], 1600, 900, 0.35f, a, b, xz));
        assertEquals("geri çözüm aynı noktayı vermeli", wx, xz[0], 0.05f);
        assertEquals("geri çözüm aynı noktayı vermeli", wz, xz[1], 0.05f);
    }
}
