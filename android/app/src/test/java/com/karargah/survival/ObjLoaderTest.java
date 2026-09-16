package com.karargah.survival;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.karargah.survival.engine.ObjLoader;

import org.junit.Test;

import java.lang.reflect.Method;

/**
 * Hazır model paketi boru hattı. Birim testinde GL yok, bu yüzden Mesh
 * kurulamaz; ayrıştırıcının ürettiği köşe verisini doğrudan denetliyoruz.
 */
public class ObjLoaderTest {

    @Test
    public void kosevRefleriDogruCozulur() throws Exception {
        Method m = ObjLoader.class.getDeclaredMethod("parseVertexRef",
                String.class, int.class, int.class);
        m.setAccessible(true);

        // "12" -> konum 11, normal yok
        int[] a = (int[]) m.invoke(null, "12", 100, 100);
        assertNotNull(a);
        assertEquals("OBJ indeksleri 1'den başlar", 11, a[0]);
        assertEquals("normal verilmemiş", -1, a[1]);

        // "12/3/5" -> konum 11, normal 4
        int[] b = (int[]) m.invoke(null, "12/3/5", 100, 100);
        assertEquals(11, b[0]);
        assertEquals(4, b[1]);

        // "12//5" -> doku atlanır, normal okunur
        int[] c = (int[]) m.invoke(null, "12//5", 100, 100);
        assertEquals(11, c[0]);
        assertEquals(4, c[1]);

        // negatif indeks sondan sayılır
        int[] d = (int[]) m.invoke(null, "-1", 10, 10);
        assertEquals("son köşe", 9, d[0]);
        int[] e = (int[]) m.invoke(null, "-3//-2", 10, 10);
        assertEquals(7, e[0]);
        assertEquals(8, e[1]);

        // bozuk girdi çökertmemeli
        assertTrue(m.invoke(null, "abc", 10, 10) == null);
    }

    @Test
    public void mtlRenkleriOkunur() throws Exception {
        Method m = ObjLoader.class.getDeclaredMethod("readMaterials",
                android.content.res.AssetManager.class, String.class, String.class,
                java.util.HashMap.class);
        m.setAccessible(true);
        java.util.HashMap<String, float[]> out = new java.util.HashMap<>();
        // AssetManager null: okunamaz ama çökmemeli, harita boş kalmalı
        m.invoke(null, null, "models/x.obj", "x.mtl", out);
        assertTrue("okunamayan MTL sessizce atlanmalı", out.isEmpty());
    }

    @Test
    public void eksikPaketOyunuBozmaz() {
        // exists() null AssetManager ile çökmemeli
        try {
            assertTrue("paket yoksa false dönmeli", !ObjLoader.exists(null, "models/yok.obj"));
        } catch (NullPointerException e) {
            fail("paket yokluğu çökertmemeli");
        }
    }

    @Test
    public void sayiAyristirmaBozukGirdideCokmez() throws Exception {
        Method m = ObjLoader.class.getDeclaredMethod("parse", String[].class, int.class);
        m.setAccessible(true);
        String[] t = {"v", "1.5", "abc"};
        assertEquals(1.5f, (float) m.invoke(null, t, 1), 1e-6f);
        assertEquals("bozuk sayı 0 olmalı", 0f, (float) m.invoke(null, t, 2), 1e-6f);
        assertEquals("eksik alan 0 olmalı", 0f, (float) m.invoke(null, t, 9), 1e-6f);
    }
}
