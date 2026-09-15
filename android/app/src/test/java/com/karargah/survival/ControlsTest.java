package com.karargah.survival;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.karargah.survival.engine.Audio;
import com.karargah.survival.engine.M4;
import com.karargah.survival.engine.MathX;
import com.karargah.survival.game.Balance;
import com.karargah.survival.game.BuildGrid;
import com.karargah.survival.game.Cmd;
import com.karargah.survival.game.GameWorld;
import com.karargah.survival.game.InputState;
import com.karargah.survival.game.Structure;

import org.junit.Test;

/** Kontrol yönü, duvar hizalama ve döndürme davranışının regresyon testleri. */
public class ControlsTest {

    private static GameWorld world(InputState in) {
        MathX.setSeed(99L);
        Audio audio = new Audio();
        audio.setEnabled(false);
        GameWorld w = new GameWorld(audio, in);
        w.paused = false;
        in.buildMode = false;
        w.camera.setAspect(1600, 900);
        return w;
    }

    /**
     * Sanal çubuğu sağa ittiğinde karakter EKRANDA sağa gitmeli. Kamera
     * karakteri takip ettiği için ölçüm, hareketten önceki kamera matrisiyle
     * yapılır. (Bu daha önce tersti: sağa basınca sola gidiyordu.)
     */
    @Test
    public void yonTusuEkranYonuyleAyni() {
        float[] yaws = {0f, 0.7f, 2.4f, -1.9f, 3.9f};
        for (float yaw : yaws) {
            InputState in = new InputState();
            GameWorld w = world(in);
            w.camera.yaw = yaw;
            w.camera.snapToTarget();
            w.camera.update(0.016f);

            float[] vp = new float[16];
            System.arraycopy(w.camera.viewProj, 0, vp, 0, 16);
            float[] tmp = new float[4];
            float[] before = new float[2];
            float[] after = new float[2];
            assertTrue(M4.project(vp, w.player.x, 1f, w.player.z, 1600, 900, tmp, before));

            in.moveX = 1f;      // çubuk sağa
            in.moveZ = 0f;
            for (int i = 0; i < 20; i++) w.update(1f / 60f);
            assertTrue(M4.project(vp, w.player.x, 1f, w.player.z, 1600, 900, tmp, after));

            float dx = after[0] - before[0];
            float dy = after[1] - before[1];
            assertTrue("yaw=" + yaw + " için sağa basınca ekranda sağa gitmeli (dx=" + dx + ")",
                    dx > 40f);
            assertTrue("yan hareket ileri/geri hareketine dönüşmemeli (dy=" + dy + ")",
                    Math.abs(dy) < Math.abs(dx) * 0.8f);
        }
    }

    /** Çubuğu ileri ittiğinde karakter kameradan uzaklaşmalı. */
    @Test
    public void ileriTusuKameradanUzaklastirir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.camera.yaw = 0.9f;
        w.camera.snapToTarget();
        w.camera.update(0.016f);
        float camX = w.camera.eyeX, camZ = w.camera.eyeZ;
        float d0 = MathX.dist(camX, camZ, w.player.x, w.player.z);

        in.moveZ = 1f;
        for (int i = 0; i < 20; i++) w.update(1f / 60f);
        float d1 = MathX.dist(camX, camZ, w.player.x, w.player.z);
        assertTrue("ileri basınca kameradan uzaklaşmalı (" + d0 + " -> " + d1 + ")", d1 > d0 + 0.8f);
    }

    /** Duvarlar komşularına göre birleşir: maske bitleri doğru kurulmalı. */
    @Test
    public void duvarlarKomsulariylaBirlesir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.scrap = 5000;
        int gz = BuildGrid.N / 2 + 12;
        int gx = BuildGrid.N / 2 - 1;
        for (int i = 0; i < 3; i++) {
            in.push(new Cmd(Cmd.PLACE, Balance.S_WALL, gx + i, gz));
            w.update(0.016f);
        }
        Structure left = w.grid.at(gx, gz);
        Structure mid = w.grid.at(gx + 1, gz);
        Structure right = w.grid.at(gx + 2, gz);
        assertTrue(left != null && mid != null && right != null);
        assertEquals("ortadaki duvar iki yana bağlanmalı", 1 | 2, mid.wallMask);
        assertEquals("soldaki yalnız sağa bağlanmalı", 1, left.wallMask);
        assertEquals("sağdaki yalnız sola bağlanmalı", 2, right.wallMask);

        // dik komşu eklenince maske güncellenmeli
        in.push(new Cmd(Cmd.PLACE, Balance.S_WALL, gx + 1, gz + 1));
        w.update(0.016f);
        assertEquals("ortadaki duvar artık T şeklinde olmalı", 1 | 2 | 4, mid.wallMask);

        // yıkılınca komşular tekrar güncellenmeli
        in.push(new Cmd(Cmd.SELECT, 0, gx + 2, gz));
        in.push(new Cmd(Cmd.SELL_SELECTED));
        w.update(0.016f);
        assertEquals("satılan komşunun biti düşmeli", 2 | 4, mid.wallMask);
    }

    /** Döndürme: yerleştirme yönü ve seçili yapının yönü değişmeli. */
    @Test
    public void dondurmeCalisir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.scrap = 5000;
        w.waves.wave = 5;   // jeneratör açılmış olsun
        assertEquals(0, in.buildRotation);

        in.push(new Cmd(Cmd.ROTATE));
        w.update(0.016f);
        assertEquals("seçim yokken yerleştirme yönü dönmeli", 1, in.buildRotation);

        int gx = BuildGrid.N / 2 + 8, gz = BuildGrid.N / 2 + 8;
        in.push(new Cmd(Cmd.PLACE, Balance.S_GENERATOR, gx, gz));
        w.update(0.016f);
        Structure s = w.grid.at(gx, gz);
        assertTrue("jeneratör kurulmalı", s != null);
        assertEquals("kurulan yapı seçili yönü almalı", 1, s.rotation);

        in.push(new Cmd(Cmd.SELECT, 0, gx, gz));
        in.push(new Cmd(Cmd.ROTATE));
        w.update(0.016f);
        assertEquals("seçili yapı dönmeli", 2, s.rotation);
        assertEquals("seçim varken yerleştirme yönü değişmemeli", 1, in.buildRotation);

        assertTrue("dönüş açısı çeyrek turun katı olmalı",
                Math.abs(s.placementYaw() - MathX.PI) < 1e-4f);
    }

    /** Geliştirilen yapılar farklı görünmeli: seviye ölçeği artmalı. */
    @Test
    public void gelistirmeGorseliDegisir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.scrap = 50000;
        w.player.cores = 50;
        int gx = BuildGrid.N / 2 + 6, gz = BuildGrid.N / 2 + 6;
        in.push(new Cmd(Cmd.PLACE, Balance.S_MG, gx, gz));
        w.update(0.016f);
        Structure s = w.grid.at(gx, gz);
        assertTrue(s != null);
        float scale1 = s.levelScale();
        in.push(new Cmd(Cmd.SELECT, 0, gx, gz));
        for (int i = 0; i < 4; i++) {
            in.push(new Cmd(Cmd.UPGRADE_SELECTED));
            w.update(0.016f);
        }
        assertEquals(5, s.level);
        assertTrue("azami seviyede yapı daha iri olmalı", s.levelScale() > scale1 + 0.1f);
        // duvarlar hücreye sığmalı, büyümemeli
        in.push(new Cmd(Cmd.PLACE, Balance.S_WALL, gx + 3, gz));
        w.update(0.016f);
        Structure wall = w.grid.at(gx + 3, gz);
        assertTrue(wall != null);
        assertEquals("duvarlar seviyeyle büyümemeli", 1f, wall.levelScale(), 1e-5f);
    }
}
