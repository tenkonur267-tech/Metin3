package com.karargah.survival;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.karargah.survival.engine.Audio;
import com.karargah.survival.engine.MathX;
import com.karargah.survival.game.Balance;
import com.karargah.survival.game.BaseLayout;
import com.karargah.survival.game.BuildGrid;
import com.karargah.survival.game.Cmd;
import com.karargah.survival.game.GameWorld;
import com.karargah.survival.game.Harvest;
import com.karargah.survival.game.InputState;
import com.karargah.survival.game.Npc;
import com.karargah.survival.game.Pickup;
import com.karargah.survival.game.Structure;
import com.karargah.survival.game.WorldGen;
import com.karargah.survival.game.Zombie;

import org.junit.Test;

/**
 * Survival çekirdeği: kaynak toplama, çok kaynaklı inşa, gece baskınları.
 * Dalga sistemi kaldırıldı — burada dalga, evre ya da geri sayım yok.
 */
public class SurvivalTest {

    private static GameWorld world(InputState in) {
        MathX.setSeed(5150L);
        Audio audio = new Audio();
        audio.setEnabled(false);
        GameWorld w = new GameWorld(audio, in);
        w.paused = false;
        w.spawnRoamers = false;
        return w;
    }

    private static void run(GameWorld w, float seconds) {
        int frames = (int) (seconds * 60);
        for (int i = 0; i < frames; i++) w.update(1f / 60f);
    }

    /** Kampın hemen dışında toplanacak bir düğüm bulur. */
    private static float[] findNode(GameWorld w) {
        float[] node = new float[5];
        float d = Harvest.nearest(w, 0f, 0f, 160f, node);
        assertTrue("kampın çevresinde kaynak olmalı", d >= 0f);
        return node;
    }

    // ---- kaynak toplama -------------------------------------------------

    @Test
    public void kampinDibindeKaynakVar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        float[] node = new float[5];
        float d = Harvest.nearest(w, 0f, 0f, 160f, node);
        assertTrue("kaynak bulunmalı", d >= 0f);
        assertTrue("kaynak surun dışında olmalı", d >= Harvest.campClear(w) - 0.01f);
        assertTrue("kaynak yürüme mesafesinde olmalı", d < 90f);
    }

    @Test
    public void agacKesilinceOdunGelir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        // Odun veren bir düğüm bul
        float[] node = new float[5];
        boolean found = false;
        for (int r = 0; r < 400 && !found; r++) {
            float a = r * 0.7f;
            float px = (float) Math.cos(a) * (30f + r), pz = (float) Math.sin(a) * (30f + r);
            if (Harvest.nearest(w, px, pz, 12f, node) >= 0f
                    && (int) node[2] == WorldGen.PROP_TREE) {
                found = true;
            }
        }
        assertTrue("haritada ağaç olmalı", found);

        int before = w.player.wood;
        w.player.x = node[0] - 1f;
        w.player.z = node[1];
        in.push(new Cmd(Cmd.HARVEST, 1));
        run(w, 10f);
        assertTrue("ağaç kesilince odun artmalı (" + before + " -> " + w.player.wood + ")",
                w.player.wood > before);
        assertTrue("kesilen ağaç tüketilmiş sayılmalı",
                w.isDepleted(Harvest.key((int) node[3], (int) node[4])));
    }

    @Test
    public void kayaKirilincaTasGelir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        float[] node = new float[5];
        boolean found = false;
        for (int r = 0; r < 500 && !found; r++) {
            float a = r * 1.3f;
            float px = (float) Math.cos(a) * (30f + r * 1.5f);
            float pz = (float) Math.sin(a) * (30f + r * 1.5f);
            if (Harvest.nearest(w, px, pz, 12f, node) >= 0f
                    && (int) node[2] == WorldGen.PROP_ROCK) {
                found = true;
            }
        }
        assertTrue("haritada kaya olmalı", found);
        int before = w.player.stone;
        w.player.x = node[0] - 1f;
        w.player.z = node[1];
        in.push(new Cmd(Cmd.HARVEST, 1));
        run(w, 12f);
        assertTrue("kaya kırılınca taş artmalı", w.player.stone > before);
    }

    @Test
    public void toplamaMenzilDisindaIlerlemez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        float[] node = findNode(w);
        w.player.x = node[0] + 30f;       // çok uzakta
        w.player.z = node[1];
        in.push(new Cmd(Cmd.HARVEST, 1));
        run(w, 4f);
        assertEquals("menzil dışında toplama düğmesi çalışmamalı", 0, w.depletedCount());
    }

    @Test
    public void kesilenDugumGeriBuyur() {
        InputState in = new InputState();
        GameWorld w = world(in);
        float[] node = findNode(w);
        long key = Harvest.key((int) node[3], (int) node[4]);
        w.player.x = node[0] - 1f;
        w.player.z = node[1];
        in.push(new Cmd(Cmd.HARVEST, 1));
        run(w, 12f);
        assertTrue("düğüm tüketilmiş olmalı", w.isDepleted(key));

        in.push(new Cmd(Cmd.HARVEST, 0));
        // Yeniden büyüme süresinin tamamını beklemeden önce hâlâ tüketilmiş
        run(w, 30f);
        assertTrue("hemen geri gelmemeli", w.isDepleted(key));
        run(w, Harvest.REGROW_ROCK + 5f);
        assertTrue("zamanı gelince geri büyümeli", !w.isDepleted(key));
    }

    @Test
    public void aletGelistirmeToplamayiHizlandirir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        float base = w.player.harvestSpeed(Balance.R_WOOD);
        w.player.wood = 500;
        w.player.scrap = 500;
        w.player.fiber = 500;
        in.push(new Cmd(Cmd.UPGRADE_TOOL, 0));
        w.update(0.016f);
        assertEquals("balta seviye atlamalı", 2, w.player.axeLevel);
        assertTrue("balta odun toplamayı hızlandırmalı",
                w.player.harvestSpeed(Balance.R_WOOD) > base);
        assertTrue("balta taşa yaramamalı",
                w.player.harvestSpeed(Balance.R_STONE) == base);
    }

    @Test
    public void malzemeYoksaAletGelistirilemez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.wood = 0;
        w.player.scrap = 0;
        w.player.fiber = 0;
        in.push(new Cmd(Cmd.UPGRADE_TOOL, 0));
        w.update(0.016f);
        assertEquals("malzeme yokken seviye atlamamalı", 1, w.player.axeLevel);
    }

    // ---- çok kaynaklı inşa ----------------------------------------------

    @Test
    public void duvarOdunlaOrulur() {
        InputState in = new InputState();
        GameWorld w = world(in);
        Balance.StructDef wall = Balance.struct(Balance.S_WALL);
        assertTrue("duvar odun istemeli", wall.woodCost > 0);
        assertEquals("duvar hurda istememeli", 0, wall.cost);

        w.player.wood = 0;
        w.player.scrap = 9999;
        assertTrue("odun yokken duvar kurulamaz", !w.canBuild(wall));
        assertEquals("eksik olan odun olmalı", "odun", w.missingFor(wall));

        w.player.wood = 500;
        assertTrue("odun varken duvar kurulabilir", w.canBuild(wall));
        int gx = BuildGrid.worldToCell(9f), gz = BuildGrid.worldToCell(9f);
        int woodBefore = w.player.wood;
        in.push(new Cmd(Cmd.PLACE, Balance.S_WALL, gx, gz));
        w.update(0.016f);
        assertNotNull("duvar kurulmalı", w.grid.at(gx, gz));
        assertTrue("odun harcanmalı", w.player.wood < woodBefore);
    }

    @Test
    public void sifirMaliyetliKaynakIstenmez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        // Duvar taş istemiyor; taşı olmayan oyuncu duvar örebilmeli
        w.player.stone = 0;
        w.player.wood = 500;
        w.player.scrap = 500;
        assertTrue("taş istemeyen yapı taş olmadan kurulabilmeli",
                w.canBuild(Balance.struct(Balance.S_WALL)));
        assertEquals("sıfır maliyet sıfır kalmalı", 0, w.player.buildCost(0));
    }

    @Test
    public void yoldasInsaatiButunKaynaklariOder() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.scrap = 20000;
        w.player.wood = 20000;
        w.player.stone = 20000;
        w.player.cores = 50;
        w.dayCount = 3;
        w.placeFree(Balance.S_BARRACKS, BuildGrid.N / 2 + 3, BuildGrid.N / 2 + 3);
        w.recruitNpc(Balance.NPC_ENGINEER);
        Npc n = w.npcs.get(0);
        n.selfImprove = false;

        int gx = BuildGrid.worldToCell(n.x + 4f), gz = BuildGrid.worldToCell(n.z + 2f);
        int woodBefore = w.player.wood;
        in.push(new Cmd(Cmd.PLAN, Balance.S_WALL, gx, gz));
        w.update(0.016f);
        run(w, 25f);
        assertTrue("yoldaşın kurduğu duvar da odun harcamalı",
                w.player.wood < woodBefore);
    }

    // ---- gece baskınları (dalga yok) ------------------------------------

    @Test
    public void dalgaSistemiKalkti() {
        InputState in = new InputState();
        GameWorld w = world(in);
        // Tehdit artık güne ve geceye bağlı; evre/geri sayım yok
        assertTrue("gün sayacı olmalı", w.dayCount >= 1);
        assertTrue("başlangıçta baskın olmamalı", !w.threat.raiding);
        assertEquals("başlangıçta planlanmış baskın olmamalı", 0, w.threat.plannedTonight);
    }

    @Test
    public void geceCokunceBaskinBaslar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.timeOfDay = 0.54f;
        assertTrue("akşamdan önce gündüz olmalı", !w.isNight());
        run(w, 100f);
        assertTrue("gece olmalı", w.isNight());
        assertTrue("gece baskını başlamalı", w.threat.raiding);
        assertTrue("baskına zombi planlanmalı", w.threat.plannedTonight > 0);

        run(w, 60f);
        // Gelenler kuleler tarafından öldürülmüş olabilir; ölçüt gönderilmiş
        // olmaları, o an hayatta olmaları değil.
        assertTrue("üsse zombi gönderilmeli", w.threat.sentTonight > 0);
    }

    @Test
    public void safakSokunceBaskinBiter() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.timeOfDay = 0.75f;
        run(w, 30f);
        assertTrue("gece baskını sürmeli", w.threat.raiding);
        int day = w.dayCount;

        w.timeOfDay = 0.97f;                 // şafak sökmüş
        run(w, 20f);
        assertTrue("şafakla baskın bitmeli", !w.threat.raiding);
        assertTrue("gün ilerlemeli", w.dayCount >= day);
    }

    @Test
    public void baskinGunlerGectikceBuyur() {
        assertTrue("ilerleyen günlerde baskın büyümeli",
                Balance.raidSize(10, false) > Balance.raidSize(2, false));
        assertTrue("kanlı ay yedinci gecelerde", Balance.isBloodMoon(7));
        assertTrue("kanlı ay her gece değil", !Balance.isBloodMoon(8));
        assertTrue("baskın tavanı olmalı", Balance.raidSize(200, true) <= 60);
    }

    @Test
    public void baskincilarReaktoreYurur() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.x = 400f;                   // oyuncu uzakta, dikkat çekmesin
        w.player.z = 400f;
        w.spawnRaider(Balance.Z_WALKER, 60f, 0f, false);
        Zombie z = w.zombies.get(w.zombies.size() - 1);
        assertTrue("baskıncı gezgin olmamalı", !z.roamer);
        float before = MathX.len(z.x, z.z);
        run(w, 25f);
        assertTrue("baskıncı reaktöre yaklaşmalı (" + before + " -> "
                + MathX.len(z.x, z.z) + ")", MathX.len(z.x, z.z) < before - 4f);
    }

    // ---- ganimet yerleşimi ----------------------------------------------

    @Test
    public void ganimetYapininUstundeKalmaz() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.wood = 5000;
        int gx = BuildGrid.worldToCell(9f), gz = BuildGrid.worldToCell(9f);
        in.push(new Cmd(Cmd.PLACE, Balance.S_WALL, gx, gz));
        w.update(0.016f);
        Structure wall = w.grid.at(gx, gz);
        assertNotNull(wall);

        w.pickups.clear();
        w.player.x = -60f;                   // oyuncu uzakta, toplamasın
        w.player.z = -60f;
        w.spawnPickup(Pickup.SCRAP, 20, wall.x, wall.z);
        run(w, 4f);
        assertEquals(1, w.pickups.size());
        Pickup p = w.pickups.get(0);
        Structure under = w.grid.atWorld(p.x, p.z);
        assertTrue("ganimet duvarın üstünde kalmamalı",
                under == null || !under.blocks());
    }

    // ---- hayatta kalma döngüsü ------------------------------------------

    @Test
    public void toplamaHayattaKalmayiSurdurur() {
        InputState in = new InputState();
        GameWorld w = world(in);
        float[] node = new float[5];
        int harvests = 0;

        // Oyuncu düğümden düğüme gider ve toplar; 6 dakika sonunda hâlâ
        // ayakta olmalı ve kasası dolmuş olmalı.
        for (int f = 0; f < 60 * 60 * 6; f++) {
            float d = Harvest.nearest(w, w.player.x, w.player.z, 70f, node);
            if (d >= 0f) {
                if (d > Harvest.REACH * 0.7f) {
                    float dx = node[0] - w.player.x, dz = node[1] - w.player.z;
                    float l = MathX.len(dx, dz);
                    float cs = (float) Math.cos(w.camera.yaw);
                    float sn = (float) Math.sin(w.camera.yaw);
                    float wx = dx / l, wz = dz / l;
                    in.moveX = -wx * cs + wz * sn;
                    in.moveZ = wx * sn + wz * cs;
                    in.push(new Cmd(Cmd.HARVEST, 0));
                } else {
                    in.moveX = 0f;
                    in.moveZ = 0f;
                    in.push(new Cmd(Cmd.HARVEST, 1));
                }
            }
            int before = w.depletedCount();
            w.update(1f / 60f);
            if (w.depletedCount() > before) harvests++;
        }
        assertTrue("oyuncu toplayarak hayatta kalmalı", w.player.alive);
        assertTrue("çok sayıda kaynak toplanmalı (" + harvests + ")", harvests > 15);
        assertTrue("odun birikmeli", w.harvested[Balance.R_WOOD] > 0);
        assertTrue("taş birikmeli", w.harvested[Balance.R_STONE] > 0);
        assertTrue("gün ilerlemeli", w.dayCount >= 1);
    }

    @Test
    public void yoldasKaynakToplar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.scrap = 20000;
        w.player.wood = 20000;
        w.player.stone = 20000;
        w.player.cores = 50;
        w.dayCount = 3;
        w.placeFree(Balance.S_BARRACKS, BuildGrid.N / 2 + 3, BuildGrid.N / 2 + 3);
        w.recruitNpc(Balance.NPC_SCAVENGER);
        Npc n = w.npcs.get(0);
        n.selfImprove = false;
        w.pickups.clear();
        w.autoRebuild = false;               // mimar kaynak harcamasın

        int before = w.depletedCount();
        run(w, 60f);
        assertTrue("yoldaş dünyadan kaynak toplamalı", w.depletedCount() > before);
        assertTrue("yoldaşın topladığı sayılmalı", n.harvested > 0);
    }

    @Test
    public void ulasilamayanHedefBirakilir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.scrap = 20000;
        w.player.wood = 20000;
        w.player.stone = 20000;
        w.player.cores = 50;
        w.dayCount = 3;
        w.placeFree(Balance.S_BARRACKS, BuildGrid.N / 2 + 3, BuildGrid.N / 2 + 3);
        w.recruitNpc(Balance.NPC_SCAVENGER);
        Npc n = w.npcs.get(0);
        n.selfImprove = false;
        w.pickups.clear();

        // Duvarın tam ortasına, ulaşılamayacak bir yığın koy
        float r = w.baseRadius;
        w.spawnPickup(Pickup.SCRAP, 40, 9f, r);
        Pickup p = w.pickups.get(0);
        p.x = 9f;
        p.z = r;
        p.resting = true;
        boolean blacklisted = false;
        for (int i = 0; i < 60 * 25 && !blacklisted; i++) {
            w.update(1f / 60f);
            if (p.unreachable > 0f || !p.alive) blacklisted = true;
        }
        assertTrue("ulaşılamayan yığın bir süre kara listeye alınmalı", blacklisted);
    }

    // ---- üs düzeni ------------------------------------------------------

    @Test
    public void insaAlaniKamptanBuyukAmaMakul() {
        assertTrue("inşa alanı sur için yeterli olmalı",
                Balance.BUILD_RADIUS > BaseLayout.START_RADIUS + BaseLayout.STEP);
        assertTrue("inşa alanı dünyayı yutmamalı", Balance.BUILD_RADIUS < 120f);
        assertTrue("sur büyüyebilmeli", BaseLayout.maxRadius() > BaseLayout.START_RADIUS);
    }
}
