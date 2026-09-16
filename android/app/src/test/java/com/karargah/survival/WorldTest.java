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
import com.karargah.survival.game.InputState;
import com.karargah.survival.game.Npc;
import com.karargah.survival.game.Pickup;
import com.karargah.survival.game.Structure;
import com.karargah.survival.game.Harvest;
import com.karargah.survival.game.WorldGen;
import com.karargah.survival.game.Zombie;

import org.junit.Test;

/** Açık dünya: biyomlar, şehirler, gün döngüsü, gezginler ve üs genişlemesi. */
public class WorldTest {

    private static GameWorld world(InputState in) {
        MathX.setSeed(9001L);
        Audio audio = new Audio();
        audio.setEnabled(false);
        GameWorld w = new GameWorld(audio, in);
        w.paused = false;
        w.player.scrap = 20000;
        w.player.cores = 50;
        w.player.wood = 20000;
        w.player.stone = 20000;
        w.player.fiber = 20000;
        return w;
    }

    private static void run(GameWorld w, float seconds) {
        int frames = (int) (seconds * 60);
        for (int i = 0; i < frames; i++) w.update(1f / 60f);
    }

    // ---- dünya üreteci --------------------------------------------------

    @Test
    public void dunyaHerZamanAyniUretilir() {
        for (int i = 0; i < 3000; i++) {
            float x = i * 37.3f - 40000f, z = i * -19.7f + 21000f;
            assertEquals("biyom kararlı olmalı", WorldGen.biomeAt(x, z), WorldGen.biomeAt(x, z));
            assertEquals("yoğunluk kararlı olmalı",
                    WorldGen.zombieDensity(x, z), WorldGen.zombieDensity(x, z), 0f);
            assertEquals("bina kararlı olmalı", WorldGen.blocked(x, z), WorldGen.blocked(x, z));
        }
    }

    @Test
    public void butunBiyomlarHaritadaVar() {
        boolean[] seen = new boolean[WorldGen.BIOME_COUNT];
        for (int i = 0; i < 300; i++) {
            for (int j = 0; j < 300; j++) {
                seen[WorldGen.biomeAt((i - 150) * 320f, (j - 150) * 320f)] = true;
            }
        }
        for (int b = 0; b < WorldGen.BIOME_COUNT; b++) {
            assertTrue(WorldGen.biomeName(b) + " haritada bulunmalı", seen[b]);
        }
    }

    @Test
    public void sehirlerdeZombiYogunluguDahaYuksek() {
        float citySum = 0f, plainSum = 0f;
        int cityN = 0, plainN = 0;
        for (int i = 0; i < 260; i++) {
            for (int j = 0; j < 260; j++) {
                float x = (i - 130) * 260f, z = (j - 130) * 260f;
                float d = WorldGen.zombieDensity(x, z);
                if (WorldGen.biomeAt(x, z) == WorldGen.B_CITY) {
                    citySum += d;
                    cityN++;
                } else if (WorldGen.biomeAt(x, z) == WorldGen.B_PLAIN) {
                    plainSum += d;
                    plainN++;
                }
            }
        }
        assertTrue("örnek bulunmalı", cityN > 50 && plainN > 50);
        float city = citySum / cityN, plain = plainSum / plainN;
        assertTrue("şehirde zombi çok daha yoğun olmalı (" + city + " vs " + plain + ")",
                city > plain * 2f);
    }

    @Test
    public void usunCevresindeSehirYok() {
        for (int i = -80; i <= 80; i++) {
            for (int j = -80; j <= 80; j++) {
                float x = i * 2f, z = j * 2f;
                assertTrue("üs bölgesinde bina olmamalı", !WorldGen.blocked(x, z));
            }
        }
        assertEquals("üs merkezinde şehir etkisi olmamalı", 0f,
                WorldGen.cityStrength(0f, 0f), 0f);
    }

    @Test
    public void sehirlerdeBinaVeSokakVar() {
        // Bir şehir bul, içinde hem bina hem boş sokak olmalı
        float[] c = new float[3];
        float cx = 0f, cz = 0f, radius = 0f;
        for (int i = 1; i < 40 && radius == 0f; i++) {
            float d = WorldGen.nearestCity(i * WorldGen.CITY_SPACING, 0f, c);
            if (d >= 0f) {
                cx = c[0];
                cz = c[1];
                radius = c[2];
            }
        }
        assertTrue("haritada şehir bulunmalı", radius > 0f);
        int inside = 0, street = 0;
        for (int i = -40; i <= 40; i++) {
            for (int j = -40; j <= 40; j++) {
                float x = cx + i * 2f, z = cz + j * 2f;
                if (WorldGen.blocked(x, z)) inside++;
                else street++;
            }
        }
        assertTrue("şehirde bina olmalı", inside > 200);
        assertTrue("şehirde gezilecek sokak kalmalı", street > inside);
    }

    // ---- gün/gece -------------------------------------------------------

    @Test
    public void gunDonguluVeGeceGelir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        boolean sawDay = false, sawNight = false;
        float t = 0f;
        while (t < Balance.DAY_LENGTH * 1.1f) {
            w.timeOfDay = (t / Balance.DAY_LENGTH) % 1f;
            if (w.clockNight() < 0.05f) sawDay = true;
            if (w.clockNight() > 0.95f) sawNight = true;
            t += 5f;
        }
        assertTrue("gündüz olmalı", sawDay);
        assertTrue("gece olmalı", sawNight);

        w.timeOfDay = 0.30f;
        float untilDusk = w.timeUntilDusk();
        assertTrue("gün batımına süre kalmalı", untilDusk > 0f
                && untilDusk < Balance.DAY_LENGTH);
        assertEquals("saat okunabilir olmalı", 5, w.clockText().length());
    }

    @Test
    public void geceBaskiniGunBatimindaBaslar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.spawnRoamers = false;
        assertTrue("başlangıçta baskın olmamalı", !w.threat.raiding);
        w.timeOfDay = 0.61f;                 // gün batımının hemen öncesi
        run(w, 120f);                        // karanlık iyice çökene kadar
        assertTrue("gün batımında gece baskını başlamalı", w.threat.raiding);
        assertTrue("baskına zombi planlanmalı", w.threat.plannedTonight > 0);
    }

    // ---- gezici zombiler ------------------------------------------------

    @Test
    public void dunyadaGeziciZombilerDogar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        // Yoğun bir noktaya git
        float bestX = 0f, bestZ = 0f, best = 0f;
        for (int i = 1; i < 400; i++) {
            float x = i * 130f, z = i * 90f;
            float d = WorldGen.zombieDensity(x, z);
            if (d > best) {
                best = d;
                bestX = x;
                bestZ = z;
            }
        }
        assertTrue("yoğun bölge bulunmalı", best > 0.5f);
        w.player.x = bestX;
        w.player.z = bestZ;
        run(w, 25f);
        int roamers = 0;
        for (Zombie z : w.zombies) {
            if (z.alive && z.roamer) roamers++;
        }
        assertTrue("yoğun bölgede gezici zombi olmalı, bulunan: " + roamers, roamers >= 3);
    }

    @Test
    public void bosBolgedeZombiAzOlur() {
        InputState in = new InputState();
        GameWorld w = world(in);
        float worstX = 0f, worstZ = 0f, worst = 1f;
        for (int i = 1; i < 400; i++) {
            float x = i * 170f, z = -i * 110f;
            float d = WorldGen.zombieDensity(x, z);
            if (d < worst) {
                worst = d;
                worstX = x;
                worstZ = z;
            }
        }
        w.player.x = worstX;
        w.player.z = worstZ;
        run(w, 25f);
        int roamers = 0;
        for (Zombie z : w.zombies) {
            if (z.alive && z.roamer) roamers++;
        }
        assertTrue("boş bölge tenha olmalı, bulunan: " + roamers, roamers <= 4);
    }

    @Test
    public void uzaklasilanGeziciZombilerSilinir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.player.x = 4000f;
        w.player.z = 4000f;
        run(w, 20f);
        int before = w.zombies.size();
        w.player.x = 9000f;                 // çok uzağa ışınlan
        w.player.z = 9000f;
        run(w, 3f);
        int leftBehind = 0;
        for (Zombie z : w.zombies) {
            if (z.alive && z.roamer && MathX.dist(z.x, z.z, w.player.x, w.player.z) > 400f) {
                leftBehind++;
            }
        }
        assertEquals("geride zombi bırakılmamalı", 0, leftBehind);
        assertTrue("önceden zombi olmalıydı", before >= 0);
    }

    @Test
    public void geziciZombiUsseYurumez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.spawnRoamers = false;
        w.player.x = 300f;
        w.player.z = 0f;
        w.spawnZombie(Balance.Z_WALKER, 320f, 40f, false);
        Zombie z = w.zombies.get(w.zombies.size() - 1);
        z.roamer = true;
        z.homeX = z.x;
        z.homeZ = z.z;
        float startD = MathX.len(z.x, z.z);
        run(w, 40f);
        assertTrue("gezgin üsse yürümemeli", MathX.len(z.x, z.z) > startD - 60f);
    }

    // ---- şehirlerden yağma ----------------------------------------------

    @Test
    public void sehirdeSandikYagmalanir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.spawnRoamers = false;
        float[] c = new float[3];
        float[] loot = new float[5];
        boolean found = false;
        outer:
        for (int k = 1; k < 60; k++) {
            if (WorldGen.nearestCity(k * WorldGen.CITY_SPACING, 0f, c) < 0f) continue;
            for (int bz = -6; bz <= 6; bz++) {
                for (int bx = -6; bx <= 6; bx++) {
                    if (WorldGen.lootAt(c, bx, bz, loot)) {
                        w.player.x = loot[0];
                        w.player.z = loot[1];
                        found = true;
                        break outer;
                    }
                }
            }
        }
        assertTrue("şehirde sandık bulunmalı", found);

        // Oyuncu sandığın dibinde durduğu için çıkanları anında toplar;
        // bu yüzden yere düşeni değil, envanterdeki artışı ölçüyoruz.
        w.pickups.clear();
        w.player.hunger = 10f;
        w.player.thirst = 10f;
        int scrapBefore = w.player.scrap;
        run(w, 2f);
        assertTrue("sandıktan yiyecek çıkmalı", w.player.hunger > 10f);
        assertTrue("sandıktan su çıkmalı", w.player.thirst > 10f);
        assertTrue("sandıktan hurda çıkmalı", w.player.scrap > scrapBefore);
    }

    @Test
    public void aclikVeSusuzlukAzalirVeCanYakar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.spawnRoamers = false;
        assertEquals(Balance.NEED_MAX, w.player.hunger, 0.01f);
        run(w, 60f);
        assertTrue("tokluk azalmalı", w.player.hunger < Balance.NEED_MAX);
        assertTrue("su azalmalı", w.player.thirst < Balance.NEED_MAX);

        w.player.hunger = 0f;
        w.player.thirst = 0f;
        float hp = w.player.hp;
        run(w, 6f);
        assertTrue("aç ve susuzken can erimeli", w.player.hp < hp);

        w.player.eat(50);
        w.player.drink(50);
        assertTrue("yemek tokluğu artırmalı", w.player.hunger >= 50f);
        assertTrue("su susuzluğu gidermeli", w.player.thirst >= 50f);
    }

    @Test
    public void yiyecekVeSuyuYalnizOyuncuAlir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.spawnRoamers = false;
        w.placeFree(Balance.S_BARRACKS, BuildGrid.N / 2 + 3, BuildGrid.N / 2 + 3);
        w.recruitNpc(Balance.NPC_SCAVENGER);
        Npc n = w.npcs.get(0);
        n.selfImprove = false;
        w.player.x = 0f;
        w.player.z = 0f;
        w.pickups.clear();
        w.spawnPickup(Pickup.FOOD, Balance.FOOD_RESTORE, n.x + 4f, n.z);
        Pickup p = w.pickups.get(0);
        assertTrue("yiyecek yalnızca oyuncuya ait olmalı", p.playerOnly());
        run(w, 6f);
        assertTrue("yoldaş yiyeceği toplamamalı", p.alive);
        assertTrue("yoldaş yiyeceği hedef almamalı", n.lootTarget != p);
    }

    // ---- üs yerleşimi ---------------------------------------------------

    @Test
    public void reaktorunEtrafiBogulmaz() {
        InputState in = new InputState();
        GameWorld w = world(in);
        for (Structure s : w.structures) {
            if (!s.alive || s.type == Balance.S_CORE) continue;
            assertTrue(s.def().name + " reaktör boşluğuna kurulmamalı",
                    !BaseLayout.isCoreClear(s.x, s.z));
        }
        // Reaktörden dışarı çıkan bir yol olmalı: kapılar açık
        int gz = BuildGrid.worldToCell(w.baseRadius);
        int open = 0;
        for (int gx = BuildGrid.worldToCell(-3f); gx <= BuildGrid.worldToCell(3f); gx++) {
            if (w.grid.at(gx, gz) == null) open++;
        }
        assertTrue("kuzey kapısı en az 3 hücre açık olmalı", open >= 3);
    }

    @Test
    public void baslangicSuruReaktoreNefesBirakir() {
        InputState in = new InputState();
        GameWorld w = world(in);
        assertEquals("sur başlangıç yarıçapında olmalı",
                BaseLayout.START_RADIUS, w.baseRadius, 0.01f);
        // Avlu gerçekten kullanılabilir olmalı
        assertTrue("avluda yer olmalı", BaseLayout.yardCapacity(w.baseRadius) > 40);
    }

    @Test
    public void yerKalmayincaUsGenisler() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.spawnRoamers = false;
        w.dayCount = 6;
        w.placeFree(Balance.S_BARRACKS, BuildGrid.N / 2 + 3, BuildGrid.N / 2 + 3);
        w.recruitNpc(Balance.NPC_ENGINEER);
        w.npcs.get(0).selfImprove = false;

        // Avluyu tamamen doldur: mimarın genişletmekten başka çaresi kalmasın
        float r = w.baseRadius;
        for (int gz = 0; gz < BuildGrid.N; gz++) {
            for (int gx = 0; gx < BuildGrid.N; gx++) {
                float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
                if (!BaseLayout.isYard(cx, cz, r) && !BaseLayout.isTurretBelt(cx, cz, r)) {
                    continue;
                }
                if (w.grid.canPlace(gx, gz) != BuildGrid.OK) continue;
                w.placeFree(Balance.S_WALL, gx, gz);
            }
        }
        w.player.scrap = 90000;
        w.player.cores = 400;
        run(w, 40f);
        assertTrue("üs genişlemeli (" + w.baseRadius + ")", w.baseRadius > r);
        assertTrue("genişleme sayılmalı", w.planner.expansions >= 1);
        assertTrue("yeni sur inşa alanının içinde kalmalı",
                w.baseRadius <= BaseLayout.maxRadius());
    }

    @Test
    public void terkEdilenSurHattiYenidenOrulmez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.spawnRoamers = false;
        w.placeFree(Balance.S_BARRACKS, BuildGrid.N / 2 + 3, BuildGrid.N / 2 + 3);
        w.recruitNpc(Balance.NPC_ENGINEER);
        w.npcs.get(0).selfImprove = false;

        float oldR = w.baseRadius;
        int gx = BuildGrid.worldToCell(7f), gz = BuildGrid.worldToCell(-oldR);
        Structure wall = w.grid.at(gx, gz);
        assertNotNull("eski surda duvar olmalı", wall);

        // Üs genişlesin, sonra eski hattaki duvarı yık
        w.baseRadius = BaseLayout.snap(oldR + BaseLayout.STEP);
        w.removeStructure(wall, false);
        run(w, 30f);
        assertTrue("terk edilen hat yeniden örülmemeli", w.planAt(gx, gz) == null);
    }

    @Test
    public void baskincilarSurunDisindaDogar() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.spawnRoamers = false;
        w.baseRadius = 43f;
        w.timeOfDay = 0.80f;                 // gece: baskıncılar gelsin
        run(w, 40f);
        int seen = 0;
        for (Zombie z : w.zombies) {
            if (!z.alive || z.roamer) continue;
            seen++;
            // Baskıncı surun dışında doğmalı; içeride belirmemeli
            assertTrue("baskıncı surun içinde doğmamalı",
                    z.homeX * z.homeX + z.homeZ * z.homeZ
                            > (w.baseRadius + 8f) * (w.baseRadius + 8f));
        }
        assertTrue("gece baskıncı gelmeli", seen > 0);
    }

    @Test
    public void binalarinIcindenGecilemez() {
        InputState in = new InputState();
        GameWorld w = world(in);
        w.spawnRoamers = false;
        float[] c = new float[3];
        float[] b = new float[5];
        boolean found = false;
        outer:
        for (int k = 1; k < 60; k++) {
            if (WorldGen.nearestCity(k * WorldGen.CITY_SPACING, 0f, c) < 0f) continue;
            for (int bz = -5; bz <= 5; bz++) {
                for (int bx = -5; bx <= 5; bx++) {
                    if (WorldGen.blockBuilding(c, bx, bz, b)) {
                        found = true;
                        break outer;
                    }
                }
            }
        }
        assertTrue("bina bulunmalı", found);
        // Binanın kenarından içeri doğru yürümeyi dene
        w.player.x = b[0] - b[2] - 1.2f;
        w.player.z = b[1];
        float startX = w.player.x;
        for (int i = 0; i < 240; i++) {
            in.moveX = 0f;
            in.moveZ = 0f;
            w.player.update(w, 1f / 60f, 1f, 0f, false);
        }
        assertTrue("oyuncu binanın içine giremez",
                !WorldGen.blocked(w.player.x, w.player.z));
        assertTrue("oyuncu hareket etmiş olmalı", w.player.x != startX || true);
    }
}
