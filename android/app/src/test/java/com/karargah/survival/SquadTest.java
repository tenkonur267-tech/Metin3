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
        w.spawnRoamers = false;      // testlerde yalnızca kurulan senaryo koşsun
        w.player.scrap = 20000;
        w.player.wood = 20000;
        w.player.stone = 20000;
        w.player.fiber = 20000;
        w.player.cores = 50;
        return w;
    }

    private static Npc hire(GameWorld w, InputState in, int role) {
        int gx = BuildGrid.N / 2 + 3, gz = BuildGrid.N / 2 + 3;
        if (w.barracks() == null) {
            w.dayCount = 3;
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

    /** Ganimet dalga bitince kaybolmamalı, toplanana kadar yerde durmalı. */
    @Test
    public void ganimetDalgaSonundaKaybolmaz() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.x = -40f;
        w.player.z = -40f;          // oyuncu uzakta, kendiliğinden toplamasın
        w.dropLoot(40f, 40f, 75, 0, 3);
        run(w, 2f);
        int piles = w.pickups.size();
        assertTrue("ganimet düşmeli", piles > 0);
        assertEquals("yerdeki hurda sayılmalı", 75, w.looseScrap());

        w.onNightSurvived();
        assertEquals("gece bitince ganimet silinmemeli", piles, w.pickups.size());

        Pickup first = w.pickups.get(0);
        run(w, 120f);               // uzun süre beklese de durmalı
        assertTrue("ganimetin ömrü dolmamalı", first.alive);
        assertTrue("yerdeki hurda kaybolmamalı", w.looseScrap() >= 75);
    }

    /** Asıl şikâyet: zombiler ortalıktayken de yoldaş ganimeti toplayabilmeli. */
    @Test
    public void yoldasCatismaSirasindaDaGanimetToplar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_SCAVENGER);
        n.selfImprove = false;      // ölçümü kasa harcaması bulandırmasın
        w.player.x = n.x;
        w.player.z = n.z;
        // uzakta birkaç zombi olsun (yakın tehdit değil)
        w.spawnZombie(Balance.Z_WALKER, n.x + 24f, n.z + 18f, false);
        w.spawnZombie(Balance.Z_WALKER, n.x - 26f, n.z - 20f, false);
        int before = w.scrap();
        w.dropLoot(n.x + 7f, n.z + 3f, 50, 0, 2);
        run(w, 16f);
        assertTrue("çatışma ortamında da toplamalı (kasa " + before + " -> " + w.scrap() + ")",
                w.scrap() > before);
        assertTrue(n.collected > 0);
    }

    @Test
    public void yoldasKendiniGelistirir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        assertEquals(1, n.weaponLevel);
        assertTrue(n.selfImprove);
        w.player.scrap = 4000;
        int before = w.scrap();
        run(w, 40f);              // hazırlık aşamasındayız
        assertTrue("silahını ya da kendini geliştirmeli",
                n.weaponLevel > 1 || n.level > 1);
        assertTrue("kasadan harcamalı", w.scrap() < before);
        assertTrue("kasada asgari yedek kalmalı", w.scrap() >= Balance.NPC_TREASURY_RESERVE);
        assertTrue("ne yaptığını söylemeli", n.bubble != null && !n.bubble.isEmpty());
    }

    @Test
    public void kasaAzkenKendiniGelistirmez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        w.player.scrap = 200;     // yedeğin altında
        run(w, 30f);
        assertEquals("kasa azken harcamamalı", 1, n.weaponLevel);
        assertEquals(1, n.level);
        assertEquals(200, w.scrap());
    }

    @Test
    public void serbestDurusReaktoruSavunur() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        assertEquals("yeni yoldaş serbest karar verir", Balance.STANCE_AUTO, n.stance);
        n.selfImprove = false;
        n.x = 30f;
        n.z = 30f;
        w.player.x = 34f;
        w.player.z = 34f;
        // Reaktörün dibine dayanıklı bir zombi: kuleler hemen temizlemesin
        w.spawnZombie(Balance.Z_BRUTE, 5f, 5f, true);

        float startD = MathX.len(n.x, n.z);
        float minD = startD;
        boolean sawCoreMode = false;
        for (int i = 0; i < 16 * 60; i++) {
            w.update(1f / 60f);
            minD = Math.min(minD, MathX.len(n.x, n.z));
            if (n.autoMode == GameWorld.AUTO_CORE) sawCoreMode = true;
        }
        assertTrue("reaktör tehdidini fark etmeli", sawCoreMode);
        assertTrue("reaktöre doğru yaklaşmalı (" + startD + " -> " + minD + ")",
                minD < startD - 15f);
    }

    @Test
    public void gorevDegisinceBaloncukGosterir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        w.player.x = n.x;
        w.player.z = n.z;
        Structure s = w.placeFree(Balance.S_WALL, BuildGrid.worldToCell(n.x + 4f),
                BuildGrid.worldToCell(n.z));
        assertNotNull(s);
        s.hp = s.maxHp * 0.3f;
        run(w, 3f);
        assertTrue("ne yaptığını baloncukta söylemeli",
                n.bubble != null && !n.bubble.isEmpty() && n.bubbleTimer > 0f);
    }

    @Test
    public void toplayiciGanimetiToplar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_SCAVENGER);
        n.selfImprove = false;
        int before = w.player.scrap;
        w.player.x = n.x;
        w.player.z = n.z;
        // Avlunun içine bırak: sur hattına düşen yığına zaten ulaşılamaz.
        w.dropLoot(n.x - 4f, n.z - 3f, 60, 0, 2);
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
        n.selfImprove = false;
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
        assertTrue("o plan tamamlanmış olmalı", w.planAt(gx, gz) == null);
        assertTrue("hurda ortak kasadan düşmeli", w.scrap() < before);
        assertTrue("inşa sayacı işlemeli", n.built > 0);
    }

    @Test
    public void kasaYetmezseInsaBekler() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        // Kasa tamamen boş: duvar artık odunla örülüyor, hepsini sıfırla.
        w.player.scrap = 0;
        w.player.wood = 0;
        w.player.stone = 0;
        int gx = BuildGrid.worldToCell(n.x + 4f), gz = BuildGrid.worldToCell(n.z + 2f);
        in.push(new Cmd(Cmd.PLAN, Balance.S_WALL, gx, gz));
        w.update(0.016f);
        run(w, 10f);
        assertTrue("kasa boşken kurulmamalı", w.grid.at(gx, gz) == null);
        assertEquals("plan beklemede kalmalı", 1, w.plans.size());

        w.addToTreasury(500, 0, true);
        w.addResource(Balance.R_WOOD, 500, true);
        w.addResource(Balance.R_STONE, 500, true);
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

    // ---- kendi kendine inşaat kararları ----

    /** Ekip, oyuncu hiçbir şey yapmadan üssü planlayıp kurmalı. */
    @Test
    public void mimarKendiKendineInsaEder() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        n.selfImprove = false;
        w.player.scrap = 6000;
        w.dayCount = 4;
        int before = w.structures.size();
        run(w, 60f);
        assertTrue("mimar plan açmalı ya da yapı kurulmalı",
                w.structures.size() > before || !w.plans.isEmpty());
        assertTrue("kararını bildirmeli", !w.planner.lastDecision.isEmpty());
        assertTrue("hepsi otomatik plan olmalı",
                w.plans.isEmpty() || w.plans.get(0).auto);
    }

    /** Enerji açığı varsa önce jeneratör kurar. */
    @Test
    public void enerjiAciginaJeneratorKurar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        n.selfImprove = false;
        w.dayCount = 5;
        w.player.scrap = 4000;
        // enerji tüketen kuleler ekle
        for (int i = 0; i < 5; i++) {
            w.placeFree(Balance.S_MG, BuildGrid.N / 2 - 3 + i, BuildGrid.N / 2 - 6);
        }
        run(w, 8f);
        assertTrue("enerji açığı oluşmalı", w.powerUse > w.powerGen);
        run(w, 50f);
        boolean generator = false;
        for (int i = 0; i < w.structures.size(); i++) {
            if (w.structures.get(i).type == Balance.S_GENERATOR) generator = true;
        }
        for (int i = 0; i < w.plans.size(); i++) {
            if (w.plans.get(i).type == Balance.S_GENERATOR) generator = true;
        }
        assertTrue("enerji açığına jeneratör ile cevap vermeli", generator);
    }

    /** Sur hattındaki delik kapatılmalı ama kapılar açık kalmalı. */
    @Test
    public void surDeligiKapatilirKapiAcikKalir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        n.selfImprove = false;
        w.player.scrap = 6000;
        w.dayCount = 3;

        // Sur hattından bir duvarı sök (kapı olmayan bir yerden). Hücreleri
        // dünya koordinatından çözüyoruz ki sur yarıçapı değişse de test
        // doğru yeri bulsun.
        float r = w.baseRadius;
        int wallGx = BuildGrid.worldToCell(7f);
        int wallGz = BuildGrid.worldToCell(-r);
        Structure gapWall = w.grid.at(wallGx, wallGz);
        assertNotNull("sur duvarı olmalı", gapWall);
        int gx = gapWall.gx, gz = gapWall.gz;
        w.grid.clear(gx, gz);
        w.structures.remove(gapWall);
        gapWall.alive = false;

        run(w, 70f);
        boolean filled = w.grid.at(gx, gz) != null || w.planAt(gx, gz) != null;
        assertTrue("sur deliği kapatılmalı", filled);
        // kapı hücreleri (kenar ortası) kapatılmamalı
        int gateGx = BuildGrid.worldToCell(1f), gateGz = BuildGrid.worldToCell(r);
        assertTrue("kuzey kapısı açık kalmalı",
                w.grid.at(gateGx, gateGz) == null && w.planAt(gateGx, gateGz) == null);
    }

    /** Dalga sırasında yeni şantiye açmaz; ekip savaşır. */
    @Test
    public void dalgaSirasindaInsaatPlanlamaz() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        n.selfImprove = false;
        w.player.scrap = 6000;
        w.dayCount = 4;
        w.timeOfDay = 0.80f;                 // gecenin ortası
        run(w, 4f);
        assertTrue("gece olmalı", w.isNight());
        w.plans.clear();
        run(w, 30f);
        assertTrue("gece otomatik şantiye açmamalı", w.plans.isEmpty());
    }

    /** Yapacak yeni iş kalmayınca mevcut yapıları geliştirir. */
    @Test
    public void isBitinceYapilariGelistirir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_ENGINEER);
        n.selfImprove = false;
        w.player.scrap = 30000;
        w.player.cores = 50;
        w.dayCount = 6;
        boolean upgraded = false;
        for (int step = 0; step < 40 && !upgraded; step++) {
            run(w, 5f);
            for (int i = 0; i < w.plans.size(); i++) {
                if (w.plans.get(i).isUpgrade()) upgraded = true;
            }
            for (int i = 0; i < w.structures.size(); i++) {
                if (w.structures.get(i).level > 1) upgraded = true;
            }
        }
        assertTrue("bir noktada geliştirme kararı vermeli", upgraded);
    }

    /** Duvar labirentinin içinde takılıp kalmamalı. */
    @Test
    public void yoldasDuvarLabirentindeTakilmaz() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        n.selfImprove = false;
        w.autoRebuild = false;

        // NPC ile hedef arasına iki kademeli duvar (aralarında kaydırılmış geçit)
        int c = BuildGrid.N / 2;
        for (int i = -8; i <= 8; i++) {
            if (i != 6 && i != 7) w.placeFree(Balance.S_WALL, c + 8, c + i);
            if (i != -6 && i != -7) w.placeFree(Balance.S_WALL, c + 12, c + i);
        }
        w.markFlowDirty();
        n.x = BuildGrid.cellToWorld(c + 16);
        n.z = 0f;

        int tgx = c - 2, tgz = c;
        in.push(new Cmd(Cmd.ORDER_AT, 0, tgz * BuildGrid.N + tgx, Balance.STANCE_HOLD));
        w.update(0.016f);
        float targetX = BuildGrid.cellToWorld(tgx), targetZ = BuildGrid.cellToWorld(tgz);
        float minD = MathX.dist(n.x, n.z, targetX, targetZ);
        for (int i = 0; i < 40 * 60; i++) {
            w.update(1f / 60f);
            minD = Math.min(minD, MathX.dist(n.x, n.z, targetX, targetZ));
        }
        assertTrue("iki duvar hattını da geçip hedefe varmalı (en yakın " + minD + ")",
                minD < 3.5f);
        assertTrue("duvarın içinde kalmamalı",
                PathFinder.passable(w.grid, BuildGrid.worldToCell(n.x),
                        BuildGrid.worldToCell(n.z)));
    }

    @Test
    public void yoldasZombiyleArasindaMesafeKorur() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_SCAVENGER);
        n.selfImprove = false;
        n.x = 10f;
        n.z = 0f;
        float comfort = n.comfortDistance();
        // Zombiyi tam dibine bırak: yoldaş geri çekilip mesafeyi açmalı.
        w.spawnZombie(0, n.x + 1.2f, n.z, false);
        float hp0 = n.hp;

        float minD = Float.MAX_VALUE;
        int tooClose = 0, samples = 0;
        for (int i = 0; i < 12 * 60; i++) {
            w.update(1f / 60f);
            if (!n.alive || n.downed) break;
            com.karargah.survival.game.Zombie zb = w.nearestZombie(n.x, n.z, 80f);
            if (zb == null) break;
            float d = MathX.dist(n.x, n.z, zb.x, zb.z);
            minD = Math.min(minD, d);
            samples++;
            if (i > 120 && d < comfort * 0.6f) tooClose++;
        }
        assertTrue("dibinden çıkmalı (en yakın " + minD + ")", samples == 0 || tooClose * 4 < samples);
        assertTrue("zombinin dibinde kalıp can eritmemeli", n.hp > hp0 * 0.55f);
    }

    @Test
    public void zombiDibindekiGanimeteGitmez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_SCAVENGER);
        n.selfImprove = false;
        n.x = 4f;
        n.z = 0f;
        // Tek ganimet yığını, üstünde de bir zombi.
        w.pickups.clear();
        w.dropLoot(18f, 0f, 30, 0, 1);
        assertEquals(1, w.pickups.size());
        Pickup p = w.pickups.get(0);
        w.spawnZombie(0, p.x, p.z + 0.4f, false);

        // Zombi yığının üstünde durduğu sürece yaklaşmamalı.
        for (int i = 0; i < 4 * 60; i++) {
            com.karargah.survival.game.Zombie zb = w.zombies.get(0);
            zb.x = p.x;
            zb.z = p.z + 0.4f;
            w.update(1f / 60f);
            assertTrue("zombi dibindeki yığını hedeflememeli", n.lootTarget != p);
        }
        assertTrue("yığın hâlâ yerde durmalı", p.alive);

        // Tehdit çekilince aynı yığını rahatça toplamalı.
        w.zombies.get(0).alive = false;
        run(w, 14f);
        assertTrue("tehdit gidince toplamalı", !p.alive);
    }

    @Test
    public void ikiYoldasAyniYiginiKapismaz() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc a = hire(w, in, Balance.NPC_SCAVENGER);
        while (w.npcCapacity() < 2) {
            w.selected = w.barracks();
            w.upgradeSelected();
        }
        w.selected = null;
        Npc b = hire(w, in, Balance.NPC_SCAVENGER);
        a.selfImprove = false;
        b.selfImprove = false;
        w.pickups.clear();
        w.dropLoot(7f, 7f, 20, 0, 1);
        w.dropLoot(-7f, 7f, 20, 0, 1);
        assertEquals(2, w.pickups.size());
        a.x = 0f; a.z = 4f;
        b.x = 0f; b.z = 4.5f;

        run(w, 1.5f);
        Pickup pa = a.lootTarget, pb = b.lootTarget;
        if (pa != null && pb != null) {
            assertTrue("iki yoldaş aynı yığını hedeflememeli", pa != pb);
        }
        run(w, 12f);
        assertEquals("iki yığın da toplanmalı", 0, w.pickups.size());
    }

    @Test
    public void ganimetTopladiktanSonraSahiplenmeBirakilir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_SCAVENGER);
        n.selfImprove = false;
        w.pickups.clear();
        w.dropLoot(n.x + 3f, n.z, 15, 0, 1);
        Pickup p = w.pickups.get(0);
        run(w, 10f);
        assertTrue("yığın toplanmalı", !p.alive);
        assertTrue("sahiplenme bırakılmalı", p.claimedBy == null);
    }

    @Test
    public void yoldasBirIleriBirGeriTitremez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        n.selfImprove = false;
        n.setStance(Balance.STANCE_AUTO, 0f, 0f);
        w.timeOfDay = 0.80f;                 // gece baskını sürsün
        w.update(0.016f);
        run(w, 3f);

        float px = n.x, pz = n.z, pdx = 0f, pdz = 0f;
        int flips = 0, steps = 0;
        for (int i = 0; i < 60 * 60; i++) {
            w.update(1f / 60f);
            if (!n.alive) break;
            if (i % 6 != 0 || n.downed) continue;
            float dx = n.x - px, dz = n.z - pz;
            if (MathX.len(dx, dz) > 0.05f && MathX.len(pdx, pdz) > 0.05f) {
                steps++;
                float dot = (dx * pdx + dz * pdz)
                        / (MathX.len(dx, dz) * MathX.len(pdx, pdz));
                if (dot < -0.7f) flips++;
            }
            pdx = dx; pdz = dz; px = n.x; pz = n.z;
        }
        assertTrue("sürekli yön değiştirip titrememeli (" + flips + "/" + steps + ")",
                steps < 20 || flips * 4 < steps);
    }

    @Test
    public void serbestDurusZombininUstuneBinmez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Npc n = hire(w, in, Balance.NPC_GUARD);
        n.selfImprove = false;
        n.setStance(Balance.STANCE_AUTO, 0f, 0f);
        w.update(0.016f);
        // Üs alanına sızmış tek zombi: yoldaş yanaşır ama dibine girmez.
        w.spawnZombie(0, 6f, 6f, false);
        float minD = Float.MAX_VALUE;
        for (int i = 0; i < 10 * 60; i++) {
            w.update(1f / 60f);
            com.karargah.survival.game.Zombie zb = w.nearestZombie(n.x, n.z, 80f);
            if (zb == null || !n.alive || n.downed) break;
            if (i > 60) minD = Math.min(minD, MathX.dist(n.x, n.z, zb.x, zb.z));
        }
        assertTrue("zombinin üstüne binmemeli (en yakın " + minD + ")",
                minD == Float.MAX_VALUE || minD > n.comfortDistance() * 0.55f);
    }
}
