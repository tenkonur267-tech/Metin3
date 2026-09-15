package com.karargah.survival;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.karargah.survival.engine.Audio;
import com.karargah.survival.engine.MathX;
import com.karargah.survival.game.Balance;
import com.karargah.survival.game.BuildGrid;
import com.karargah.survival.game.Cmd;
import com.karargah.survival.game.GameWorld;
import com.karargah.survival.game.InputState;
import com.karargah.survival.game.Npc;
import com.karargah.survival.game.PathFinder;
import com.karargah.survival.game.Pickup;
import com.karargah.survival.game.Structure;

import org.junit.Test;

/** Yoldaşlar, emirler, yol bulma ve yere düşen ganimet testleri. */
public class SquadTest {

    private static GameWorld world(InputState in) {
        MathX.setSeed(2024L);
        Audio audio = new Audio();
        audio.setEnabled(false);
        GameWorld w = new GameWorld(audio, in);
        w.paused = false;
        w.player.scrap = 20000;
        w.player.cores = 50;
        return w;
    }

    private static Npc hire(GameWorld w, InputState in, int role) {
        int gx = BuildGrid.N / 2 + 5, gz = BuildGrid.N / 2 + 5;
        if (w.barracks() == null) {
            w.waves.wave = 3;
            in.push(new Cmd(Cmd.PLACE, Balance.S_BARRACKS, gx, gz));
            w.update(0.016f);
        }
        int before = w.npcs.size();
        in.push(new Cmd(Cmd.RECRUIT, role));
        w.update(0.016f);
        assertEquals("yoldaş alınmalı", before + 1, w.npcs.size());
        return w.npcs.get(w.npcs.size() - 1);
    }

    private static void run(GameWorld w, float seconds) {
        int frames = (int) (seconds * 60);
        for (int i = 0; i < frames; i++) w.update(1f / 60f);
    }

    @Test
    public void kislaOlmadanYoldasAlinmaz() {
        InputState in = new InputState();
        GameWorld w = world(in);
        in.push(new Cmd(Cmd.RECRUIT, Balance.NPC_GUARD));
        w.update(0.016f);
        assertTrue("kışla yokken yoldaş alınmamalı", w.npcs.isEmpty());
    }

    @Test
    public void yoldasTakipEder() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        w.player.x = 18f;
        w.player.z = -14f;
        run(w, 8f);
        float d = MathX.dist(n.x, n.z, w.player.x, w.player.z);
        assertTrue("yoldaş oyuncuya yaklaşmalı (uzaklık " + d + ")", d < 5f);
    }

    /** Aradaki duvar hattını dolaşıp hedefe ulaşmalı — takılıp kalmamalı. */
    @Test
    public void duvariDolasipHedefeUlasir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        n.x = 22f;
        n.z = 0f;

        // NPC ile hedef arasına uzun bir duvar dik
        int cx = BuildGrid.N / 2;
        int wallX = BuildGrid.worldToCell(16f);
        for (int i = -6; i <= 6; i++) {
            w.placeFree(Balance.S_WALL, wallX, cx + i);
        }
        w.markFlowDirty();

        int tgx = BuildGrid.worldToCell(6f), tgz = BuildGrid.worldToCell(0f);
        in.push(new Cmd(Cmd.ORDER_AT, 0, tgz * BuildGrid.N + tgx, Balance.STANCE_HOLD));
        w.update(0.016f);
        run(w, 22f);

        float d = MathX.dist(n.x, n.z, 6f, 0f);
        assertTrue("duvarı dolaşıp emir noktasına varmalı (uzaklık " + d + ")", d < 3.5f);
        assertTrue("duvarın içinde sıkışmamalı",
                PathFinder.passable(w.grid, BuildGrid.worldToCell(n.x), BuildGrid.worldToCell(n.z)));
    }

    @Test
    public void zombiOlunceYereHurdaDuser() {
        InputState in = new InputState();
        GameWorld w = world(in);
        int scrapBefore = w.player.scrap;
        w.spawnZombie(Balance.Z_WALKER, 30f, 0f, false);
        run(w, 1.5f);
        assertEquals(1, w.zombies.size());
        w.zombies.get(0).hurt(99999f, 0f, 0f);
        w.onZombieKilled(w.zombies.get(0), w.player);
        assertTrue("yere ganimet düşmeli", !w.pickups.isEmpty());
        assertEquals("hurda anında eklenmemeli", scrapBefore, w.player.scrap);

        // oyuncu yanına gidince toplanmalı
        Pickup p = w.pickups.get(0);
        run(w, 1.2f);            // yere insin
        w.player.x = p.x;
        w.player.z = p.z;
        run(w, 1.5f);
        assertTrue("üstüne gidince toplanmalı", w.player.scrap > scrapBefore);
    }

    @Test
    public void dalgaSonundaKalanGanimetToplanir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        int before = w.player.scrap;
        w.dropLoot(40f, 40f, 75, 0, 3);
        run(w, 2f);
        assertTrue(!w.pickups.isEmpty());
        w.onWaveCleared(1);
        assertTrue("dalga bitince sahadaki ganimet toplanmalı", w.player.scrap >= before + 75);
    }

    @Test
    public void toplayiciGanimetiToplar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_SCAVENGER);
        int before = w.player.scrap;
        w.player.x = n.x;
        w.player.z = n.z;
        w.dropLoot(n.x + 9f, n.z + 4f, 60, 0, 2);
        in.push(new Cmd(Cmd.ORDER, 0, Balance.STANCE_FOLLOW, 0));
        run(w, 14f);
        assertTrue("toplayıcı ganimeti toplamalı", w.player.scrap > before);
        assertTrue("topladığı sayılmalı", n.collected > 0);
    }

    @Test
    public void muhendisYapiOnarir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        Structure s = w.placeFree(Balance.S_WALL, BuildGrid.N / 2 + 8, BuildGrid.N / 2);
        assertNotNull(s);
        s.hp = s.maxHp * 0.3f;
        w.player.x = s.x;
        w.player.z = s.z;
        float hpBefore = s.hp;
        in.push(new Cmd(Cmd.ORDER, 0, Balance.STANCE_FOLLOW, 0));
        run(w, 14f);
        assertTrue("mühendis hasarlı yapıyı onarmalı (" + hpBefore + " -> " + s.hp + ")",
                s.hp > hpBefore + 10f);
    }

    @Test
    public void yoldasCaniAzalincaCekilir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        n.x = 30f;
        n.z = 30f;
        n.hp = n.maxHp * 0.2f;
        run(w, 1f);
        assertTrue("canı azalan yoldaş geri çekilmeli", n.retreating);
        float d0 = MathX.len(n.x, n.z);
        run(w, 6f);
        assertTrue("üsse doğru gitmeli", MathX.len(n.x, n.z) < d0 - 3f);
    }

    @Test
    public void yoldaslarZombiOldurur() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        n.x = 0f;
        n.z = 14f;
        w.player.x = 0f;
        w.player.z = 14f;
        w.spawnZombie(Balance.Z_WALKER, 4f, 16f, false);
        run(w, 20f);
        assertTrue("yoldaş zombiyi temizlemeli", w.totalKills > 0);
    }

    @Test
    public void namluOyuncununOnunde() {
        InputState in = new InputState();
        GameWorld w = world(in);
        float[] m = new float[3];
        w.player.x = 0f;
        w.player.z = 0f;
        w.player.yaw = 0f;
        w.player.aimYaw = 0f;
        w.player.muzzleWorld(m);
        assertTrue("namlu ileride olmalı", m[2] > 0.35f);
        assertTrue("namlu silah yüksekliğinde olmalı", m[1] > 0.9f && m[1] < 1.3f);
        assertTrue("namlu gövde merkezinde olmamalı", MathX.len(m[0] - 0f, m[2] - 0f) > 0.4f);

        w.player.yaw = MathX.PI * 0.5f;
        w.player.aimYaw = MathX.PI * 0.5f;
        w.player.muzzleWorld(m);
        assertTrue("dönünce namlu da dönmeli", m[0] > 0.35f && Math.abs(m[2]) < 0.6f);
    }

    // ---- çok görevlilik, inşaatçı ve ortak kasa ----

    @Test
    public void tekYoldasaBirdenCokGorevVerilebilir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        assertTrue("muhafız varsayılan olarak savaşır", n.hasDuty(Balance.DUTY_FIGHT));

        in.push(new Cmd(Cmd.DUTY, 0, Balance.DUTY_REPAIR, 0));
        in.push(new Cmd(Cmd.DUTY, 0, Balance.DUTY_GATHER, 0));
        w.update(0.016f);
        assertTrue(n.hasDuty(Balance.DUTY_FIGHT));
        assertTrue(n.hasDuty(Balance.DUTY_REPAIR));
        assertTrue(n.hasDuty(Balance.DUTY_GATHER));
        assertTrue("üç görev birden görünmeli", n.dutyLetters().length() >= 5);

        // hem onarım hem toplama işini sırayla yapmalı
        w.player.x = n.x;
        w.player.z = n.z;
        Structure s = w.placeFree(Balance.S_WALL, BuildGrid.worldToCell(n.x + 4f),
                BuildGrid.worldToCell(n.z));
        assertNotNull(s);
        s.hp = s.maxHp * 0.25f;
        float hpBefore = s.hp;
        w.dropLoot(n.x - 4f, n.z + 2f, 40, 0, 1);
        run(w, 18f);
        assertTrue("onarım yapılmalı", s.hp > hpBefore + 5f);
        assertTrue("ganimet de toplanmalı", n.collected > 0);

        in.push(new Cmd(Cmd.DUTY, 0, Balance.DUTY_REPAIR, 0));
        w.update(0.016f);
        assertTrue("görev kapatılabilmeli", !n.hasDuty(Balance.DUTY_REPAIR));
    }

    @Test
    public void insaatciPlaniKurarVeKasadanOder() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        assertTrue("mühendis inşa görevini taşır", n.hasDuty(Balance.DUTY_BUILD));

        int gx = BuildGrid.worldToCell(n.x + 5f), gz = BuildGrid.worldToCell(n.z + 2f);
        w.player.scrap = 400;
        int before = w.scrap();
        in.push(new Cmd(Cmd.PLAN, Balance.S_WALL, gx, gz));
        w.update(0.016f);
        assertEquals("plan açılmalı", 1, w.plans.size());
        assertEquals("plan açarken hurda düşmemeli", before, w.scrap());

        run(w, 30f);
        assertNotNull("yoldaş planı kurmalı", w.grid.at(gx, gz));
        assertTrue("plan listesi boşalmalı", w.plans.isEmpty());
        assertTrue("hurda ortak kasadan düşmeli", w.scrap() < before);
        assertTrue("inşa sayacı işlemeli", n.built > 0);
    }

    @Test
    public void kasaYetmezseInsaBekler() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        w.player.scrap = 0;
        int gx = BuildGrid.worldToCell(n.x + 4f), gz = BuildGrid.worldToCell(n.z + 2f);
        in.push(new Cmd(Cmd.PLAN, Balance.S_WALL, gx, gz));
        w.update(0.016f);
        run(w, 10f);
        assertTrue("kasa boşken kurulmamalı", w.grid.at(gx, gz) == null);
        assertEquals("plan beklemede kalmalı", 1, w.plans.size());

        w.addToTreasury(500, 0, true);
        run(w, 25f);
        assertNotNull("kasa dolunca kurulmalı", w.grid.at(gx, gz));
    }

    @Test
    public void yikilanYapiIcinOtomatikPlanAcilir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        hire(w, in, Balance.NPC_ENGINEER);
        Structure s = w.placeFree(Balance.S_MG, BuildGrid.N / 2 + 7, BuildGrid.N / 2 + 1);
        assertNotNull(s);
        int gx = s.gx, gz = s.gz;
        s.damage(999999f);
        w.onStructureDestroyed(s);
        assertTrue("yıkılan yapı için plan açılmalı", w.planAt(gx, gz) != null);
        assertTrue("plan otomatik işaretlenmeli", w.planAt(gx, gz).auto);

        w.player.scrap = 3000;
        run(w, 40f);
        assertNotNull("ekip yıkılan yapıyı yeniden dikmeli", w.grid.at(gx, gz));
    }

    @Test
    public void ortakKasaPaylasilir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.scrap = 100;
        w.addToTreasury(250, 2, true);
        assertEquals(350, w.scrap());
        assertEquals(52, w.cores());
        assertTrue("ekibin katkısı sayılmalı", w.scrapFromNpcs >= 250);
        assertTrue(w.spendScrap(300));
        assertEquals(50, w.scrap());
        assertTrue("kasada olmayan para harcanamaz", !w.spendScrap(999));
    }

    @Test
    public void planaDokununcaIptalEdilir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        int gx = BuildGrid.N / 2 + 9, gz = BuildGrid.N / 2 + 2;
        in.push(new Cmd(Cmd.PLAN, Balance.S_WALL, gx, gz));
        w.update(0.016f);
        assertEquals(1, w.plans.size());
        in.push(new Cmd(Cmd.CANCEL_PLAN, 0, gx, gz));
        w.update(0.016f);
        assertTrue("plan iptal edilmeli", w.plans.isEmpty());
    }
}
