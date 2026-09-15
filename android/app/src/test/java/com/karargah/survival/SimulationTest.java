package com.karargah.survival;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.karargah.survival.engine.Audio;
import com.karargah.survival.engine.MathX;
import com.karargah.survival.game.Balance;
import com.karargah.survival.game.BuildGrid;
import com.karargah.survival.game.Cmd;
import com.karargah.survival.game.GameWorld;
import com.karargah.survival.game.InputState;
import com.karargah.survival.game.Structure;
import com.karargah.survival.game.WaveManager;
import com.karargah.survival.game.Zombie;

import org.junit.Test;

/**
 * Oyun simülasyonunu çizim olmadan koşturan regresyon testleri.
 * (Android çağrıları için unitTests.returnDefaultValues=true yeterli; bu
 * katman zaten saf Java.)
 */
public class SimulationTest {

    private static GameWorld newWorld(InputState in) {
        MathX.setSeed(1337L);
        Audio audio = new Audio();
        audio.setEnabled(false);
        GameWorld w = new GameWorld(audio, in);
        w.paused = false;
        return w;
    }

    /** Yapay oyuncu: rastgele gezer, ateş eder, hazırlıkta inşa eder. */
    private static void step(GameWorld w, InputState in, int frame, float dt, boolean rich) {
        float t = frame * dt;
        in.moveX = (float) Math.sin(t * 0.7f);
        in.moveZ = (float) Math.cos(t * 0.5f);
        in.firing = true;
        if (w.waves.isPrepare()) {
            if (frame % 12 == 0) {
                int type = 1 + MathX.rndInt(Balance.STRUCTS.length - 1);
                if (w.waves.wave < Balance.struct(type).unlockWave) type = Balance.S_WALL;
                int gx = BuildGrid.N / 2 - 10 + MathX.rndInt(20);
                int gz = BuildGrid.N / 2 - 10 + MathX.rndInt(20);
                in.push(new Cmd(Cmd.PLACE, type, gx, gz));
            }
            if (w.waves.timer < 18f && frame % 30 == 0) in.push(new Cmd(Cmd.START_WAVE));
        }
        if (frame % 240 == 0 && w.player.skillPoints > 0) {
            in.push(new Cmd(Cmd.SKILL_UP, MathX.rndInt(Balance.SKILL_COUNT)));
        }
        if (rich && frame % 300 == 0) {
            w.player.scrap += 400;
            w.player.cores += 1;
            w.player.hp = w.player.maxHp;
        }
        w.update(dt);
    }

    private static void assertSane(GameWorld w, int frame) {
        assertTrue("oyuncu konumu bozuk, kare " + frame,
                finite(w.player.x) && finite(w.player.z) && finite(w.player.hp));
        assertTrue("negatif kaynak, kare " + frame, w.player.scrap >= 0 && w.player.cores >= 0);
        for (int i = 0; i < w.zombies.size(); i++) {
            Zombie z = w.zombies.get(i);
            assertTrue("zombi değeri bozuk, kare " + frame,
                    finite(z.x) && finite(z.z) && finite(z.hp));
        }
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            assertTrue("yapı canı bozuk, kare " + frame, finite(s.hp) && s.hp <= s.maxHp + 1f);
        }
    }

    private static boolean finite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v) && Math.abs(v) < 1e6f;
    }

    @Test
    public void oyunIlerlerVeSayilarBozulmaz() {
        InputState in = new InputState();
        GameWorld w = newWorld(in);
        float dt = 1f / 60f;
        for (int f = 0; f < 60000 && !w.gameOver; f++) {
            step(w, in, f, dt, false);
            if (f % 60 == 0) assertSane(w, f);
        }
        assertTrue("16 dakikada en az 3 dalga tamamlanmalı, ulaşılan: " + w.waves.wave,
                w.waves.wave >= 3);
        assertTrue("zombi öldürülmüş olmalı", w.totalKills > 0);
    }

    /**
     * Dalgaların tıkanmaması: hiçbir dalga makul süreden uzun sürmemeli
     * (öfke modu kalan zombileri reaktöre yürütür).
     */
    @Test
    public void dalgalarTikanmaz() {
        InputState in = new InputState();
        GameWorld w = newWorld(in);
        float dt = 1f / 60f;
        int waveStartFrame = 0;
        int lastWave = 0;
        int longest = 0;
        for (int f = 0; f < 120000 && !w.gameOver; f++) {
            step(w, in, f, dt, true);
            if (w.waves.wave != lastWave) {
                lastWave = w.waves.wave;
                waveStartFrame = f;
            }
            if (w.waves.phase == WaveManager.PHASE_WAVE) {
                int elapsed = f - waveStartFrame;
                longest = Math.max(longest, elapsed);
                assertTrue("dalga " + w.waves.wave + " " + (elapsed / 60) + " saniyedir bitmiyor",
                        elapsed < 60 * 220);
            }
        }
        assertTrue("uzun koşuda ilerleme olmalı", w.waves.wave >= 8);
        assertTrue("en uzun dalga makul olmalı: " + (longest / 60) + " sn", longest < 60 * 220);
    }

    @Test
    public void insaVeGelistirmeCalisir() {
        InputState in = new InputState();
        GameWorld w = newWorld(in);
        w.player.scrap = 5000;
        w.player.cores = 20;
        int before = w.structures.size();
        int gx = BuildGrid.N / 2 + 6, gz = BuildGrid.N / 2 + 6;
        in.push(new Cmd(Cmd.PLACE, Balance.S_MG, gx, gz));
        w.update(0.016f);
        assertEquals("kule kurulmalı", before + 1, w.structures.size());
        Structure s = w.grid.at(gx, gz);
        assertNotNull(s);
        assertEquals(Balance.S_MG, s.type);

        in.push(new Cmd(Cmd.SELECT, 0, gx, gz));
        for (int i = 0; i < 4; i++) {
            in.push(new Cmd(Cmd.UPGRADE_SELECTED));
            w.update(0.016f);
        }
        assertEquals("kule 5. seviyeye çıkmalı", 5, s.level);
        assertTrue("geliştirme canı artırmalı", s.maxHp > Balance.struct(Balance.S_MG).hp);

        float scrapBefore = w.player.scrap;
        in.push(new Cmd(Cmd.SELL_SELECTED));
        w.update(0.016f);
        assertTrue("satış hurda kazandırmalı", w.player.scrap > scrapBefore);
        assertFalse("satılan yapı ızgaradan silinmeli", w.grid.at(gx, gz) != null);
    }

    @Test
    public void reaktorUstune_insa_edilemez() {
        InputState in = new InputState();
        GameWorld w = newWorld(in);
        w.player.scrap = 5000;
        int c = BuildGrid.N / 2;
        assertEquals(BuildGrid.ERR_CORE, w.grid.canPlace(c, c));
        assertEquals(BuildGrid.ERR_AREA, w.grid.canPlace(1, 1));
    }

    @Test
    public void dengeTablolariTutarli() {
        for (Balance.StructDef d : Balance.STRUCTS) {
            assertNotNull(d.name);
            assertTrue(d.name + ": can pozitif olmalı", d.hp > 0);
            assertTrue(d.name + ": azami seviye 5", d.maxLevel == 5);
            assertTrue(d.name + ": geliştirme maliyeti artmalı",
                    d.id == Balance.S_CORE || d.upgradeCost(2) > d.upgradeCost(1));
            for (int lv = 1; lv <= 5; lv++) {
                assertTrue(d.name + ": seviye canı artmalı", d.hpAt(lv) >= d.hp);
            }
        }
        for (Balance.WeaponDef d : Balance.WEAPONS) {
            assertTrue(d.name + ": hasar pozitif", d.damage > 0);
            assertTrue(d.name + ": şarjör pozitif", d.magazine > 0);
            assertTrue(d.name + ": geliştirme hasarı artırmalı", d.damageAt(2) > d.damageAt(1));
        }
        for (int w = 1; w <= 40; w++) {
            assertTrue("dalga bütçesi artmalı", Balance.waveBudget(w + 1) > Balance.waveBudget(w));
            assertTrue("zombi gücü artmalı", Balance.waveHpScale(w + 1) > Balance.waveHpScale(w));
        }
    }
}
