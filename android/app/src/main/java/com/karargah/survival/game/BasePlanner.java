package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/**
 * Üssün "mimarı". Oyuncuya sormadan, üssün o anki durumuna bakarak nereye ne
 * kurulacağına kendisi karar verir ve şantiye açar; inşa görevli yoldaşlar da
 * gidip kurar.
 *
 * Karar sırası (en kritikten en lükse):
 *   1. Enerji açığı varsa jeneratör
 *   2. Sur hattındaki delikler (kapılar bilerek açık bırakılır)
 *   3. Savunmasız yöne kule
 *   4. Kapı önlerine tuzak
 *   5. Destek yapıları (cephanelik, tamir, tıbbi, toplayıcı)
 *   6. Yapacak yeni iş yoksa mevcut yapıları geliştir
 */
public class BasePlanner {
    /** Oyuncuya bırakılan asgari hurda. */
    public static final int RESERVE = 130;
    private static final int MAX_QUEUE = 3;
    private static final int MAX_UPGRADES = 2;
    /** İki karar arasındaki en kısa süre — sürekli her yere bir şey dikmesin. */
    private static final float DECIDE_PERIOD = 3.5f;
    /** Yapacak iş bulamadığında bu kadar bekler (boşuna tarama yapmaz). */
    private static final float IDLE_PERIOD = 8f;

    private float timer = 3f;
    public String lastDecision = "";
    /** Kaç kez üs genişletildi (arayüzde ve testlerde okunur). */
    public int expansions;

    public void reset() {
        timer = 3f;
        lastDecision = "";
        expansions = 0;
    }

    public void update(GameWorld w, float dt) {
        if (!w.autoRebuild) return;
        timer -= dt;
        if (timer > 0f) return;
        timer = DECIDE_PERIOD;
        // İnşaat kararları hazırlık aşamasında verilir; dalga sırasında ekip savaşır.
        // İnşa kararları gündüz verilir; gece ekip kampı savunur.
        if (w.isNight() || w.gameOver) return;
        if (!hasBuilder(w)) return;

        // Yer darlığı kararı kuyruktan önce gelir: kuyruk dolu diye üs
        // büyüyemez kalırsa mimar sonsuza kadar aynı avluyu tıka basa doldurur.
        if (needsMoreRoom(w, w.baseRadius) && tryExpand(w)) return;

        int queuedBuilds = 0, queuedUpgrades = 0;
        for (int i = 0; i < w.plans.size(); i++) {
            BuildPlan p = w.plans.get(i);
            if (!p.auto) continue;
            if (p.isUpgrade()) queuedUpgrades++;
            else queuedBuilds++;
        }
        // Yeni inşaat kuyruğu dolsa bile geliştirme kararı verilebilir.
        boolean onlyUpgrades = queuedBuilds >= MAX_QUEUE;
        if (onlyUpgrades && queuedUpgrades >= MAX_UPGRADES) return;
        int budget = w.scrap() - RESERVE;
        if (budget < 20) return;
        if (!decide(w, budget, onlyUpgrades)) timer = IDLE_PERIOD;
    }

    private static boolean hasBuilder(GameWorld w) {
        for (int i = 0; i < w.npcs.size(); i++) {
            if (w.npcs.get(i).hasDuty(Balance.DUTY_BUILD)) return true;
        }
        return false;
    }

    private boolean affordable(GameWorld w, int type, int budget) {
        Balance.StructDef d = Balance.struct(type);
        return w.dayCount >= d.unlockDay && w.canBuild(d);
    }

    /** Bir karar verildiyse true; yapacak iş kalmadıysa false. */
    private boolean decide(GameWorld w, int budget, boolean onlyUpgrades) {
        if (onlyUpgrades) return upgradeSomething(w, budget);

        float radius = w.baseRadius;

        // 1) Enerji açığı — kuleler yavaşlıyorsa her şeyden önce jeneratör
        if (w.powerUse > w.powerGen + 0.5f && affordable(w, Balance.S_GENERATOR, budget)
                && place(w, Balance.S_GENERATOR, ZONE_YARD, radius, 0f, 0f,
                        "Enerji açığı var, jeneratör kuruyorum")) {
            return true;
        }

        // 2) Sur hattındaki delik (kapılar bilerek açık kalır). Aynı anda en
        // fazla iki duvar şantiyesi açılır; yoksa mimar bütün bütçeyi ve tüm
        // inşaatçıları sur hattına gömüp kule dikmeye hiç sıra gelmiyor.
        if (affordable(w, Balance.S_WALL, budget) && pendingWalls(w) < 2) {
            int gap = findWallGap(w, radius);
            if (gap >= 0 && w.addPlan(Balance.S_WALL, gap % Balance.GRID,
                    gap / Balance.GRID, true)) {
                say(w, "Sur hattında delik var, kapatıyorum");
                return true;
            }
        }

        // 3) En zayıf yöne kule (sur hattının hemen gerisindeki kuşağa)
        int side = weakestSide(w);
        if (side >= 0) {
            int type = pickTurret(w, budget);
            if (type >= 0) {
                float a = sideAngle(side);
                float belt = radius - BaseLayout.TURRET_BELT * 0.5f;
                float px = (float) Math.sin(a) * belt, pz = (float) Math.cos(a) * belt;
                if (place(w, type, ZONE_BELT, radius, px, pz,
                        SIDE_NAMES[side] + " taraf zayıf, " + Balance.struct(type).name
                                + " kuruyorum")) {
                    return true;
                }
            }
        }

        // 4) Kapı önüne tuzak
        if (w.dayCount >= 2 && affordable(w, Balance.S_SPIKE, budget)) {
            int gateSide = gateWithoutTrap(w);
            if (gateSide >= 0) {
                float a = sideAngle(gateSide);
                float out = radius + Balance.CELL * 1.8f;
                float px = (float) Math.sin(a) * out, pz = (float) Math.cos(a) * out;
                if (place(w, Balance.S_SPIKE, ZONE_TRAP, radius, px, pz,
                        SIDE_NAMES[gateSide] + " kapının önüne tuzak koyuyorum")) {
                    return true;
                }
            }
        }

        // 5) Destek yapıları — hepsi avluya, reaktörün boşluğuna dokunmadan
        if (countType(w, -1) >= 4 && countType(w, Balance.S_AMMO) == 0
                && affordable(w, Balance.S_AMMO, budget)
                && place(w, Balance.S_AMMO, ZONE_YARD, radius, 0f, 0f,
                        "Kuleler için cephanelik kuruyorum")) {
            return true;
        }
        if (w.dayCount >= 5 && countType(w, Balance.S_REPAIR) == 0
                && affordable(w, Balance.S_REPAIR, budget)
                && place(w, Balance.S_REPAIR, ZONE_YARD, radius, 0f, 0f,
                        "Tamir istasyonu kuruyorum, yapılar ayakta kalsın")) {
            return true;
        }
        if (w.dayCount >= 4 && countType(w, Balance.S_MED) == 0
                && affordable(w, Balance.S_MED, budget)
                && place(w, Balance.S_MED, ZONE_YARD, radius, 0f, 9f,
                        "Tıbbi istasyon kuruyorum")) {
            return true;
        }
        if (w.dayCount >= 3 && countType(w, Balance.S_COLLECTOR) < 2 && budget > 400
                && affordable(w, Balance.S_COLLECTOR, budget)
                && place(w, Balance.S_COLLECTOR, ZONE_YARD, radius, 0f, -9f,
                        "Hurda toplayıcı kuruyorum")) {
            return true;
        }

        // 6) Yeni iş yoksa mevcut yapıyı geliştir
        return upgradeSomething(w, budget);
    }

    /** Kuyrukta bekleyen duvar şantiyesi sayısı. */
    private int pendingWalls(GameWorld w) {
        int n = 0;
        for (int i = 0; i < w.plans.size(); i++) {
            BuildPlan p = w.plans.get(i);
            if (p.alive && !p.isUpgrade() && p.type == Balance.S_WALL) n++;
        }
        return n;
    }

    private boolean upgradeSomething(GameWorld w, int budget) {
        Structure up = pickUpgrade(w, budget);
        if (up == null || !w.addUpgradePlan(up)) return false;
        say(w, up.def().name + " yeterli değil, Sv." + (up.level + 1) + " yapıyorum");
        return true;
    }

    // ---- genişleme ------------------------------------------------------

    /** Avlu ya da kule kuşağı doldu mu? */
    private boolean needsMoreRoom(GameWorld w, float radius) {
        if (radius >= BaseLayout.maxRadius()) return false;
        return freeCells(w, ZONE_YARD, radius, 6) < 6
                || freeCells(w, ZONE_BELT, radius, 4) < 4;
    }

    /** Bölgede en fazla {@code cap} taneye kadar boş hücre sayar. */
    private int freeCells(GameWorld w, int zone, float radius, int cap) {
        int n = 0;
        float reach = radius + Balance.CELL * 3f;
        int lo = cellLo(reach), hi = cellHi(reach);
        for (int gz = lo; gz <= hi; gz++) {
            for (int gx = lo; gx <= hi; gx++) {
                float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
                if (!inZone(zone, cx, cz, radius)) continue;
                if (w.grid.canPlace(gx, gz) != BuildGrid.OK) continue;
                if (w.planAt(gx, gz) != null) continue;
                if (++n >= cap) return n;
            }
        }
        return n;
    }

    /**
     * Sur hattını bir kademe dışarı taşır. Eski hat yıkılmaz — içeride ikinci
     * bir savunma çizgisi olarak kalır ama artık tamir/tamamlama listesinde
     * değildir, üstelik dört kapısı da sonuna kadar açılır ki ekip sıkışmasın.
     */
    private boolean tryExpand(GameWorld w) {
        float old = w.baseRadius;
        float next = BaseLayout.snap(old + BaseLayout.STEP);
        if (next <= old + 0.5f) return false;
        w.baseRadius = next;
        expansions++;
        openOldGates(w, old);
        say(w, "Üsse yer kalmadı, sur hattını " + Math.round(next)
                + " birime genişletiyorum");
        return true;
    }

    /** Eski sur hattındaki kapıları iyice açar (içeride kalmayalım). */
    private void openOldGates(GameWorld w, float oldRadius) {
        float wide = BaseLayout.GATE_HALF * 2.2f;
        int lo = cellLo(oldRadius + 2f), hi = cellHi(oldRadius + 2f);
        for (int gz = lo; gz <= hi; gz++) {
            for (int gx = lo; gx <= hi; gx++) {
                float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
                if (!BaseLayout.isWallRing(cx, cz, oldRadius)) continue;
                float ax = Math.abs(cx), az = Math.abs(cz);
                boolean nearGate = az >= ax ? ax <= wide : az <= wide;
                if (!nearGate) continue;
                Structure st = w.grid.at(gx, gz);
                if (st != null && st.type == Balance.S_WALL) w.removeStructure(st, true);
                BuildPlan pl = w.planAt(gx, gz);
                if (pl != null) w.cancelPlanAt(gx, gz);
            }
        }
    }

    // ---- yardımcılar ----------------------------------------------------

    private static final String[] SIDE_NAMES = {"Kuzey", "Doğu", "Güney", "Batı"};

    static final int ZONE_YARD = 0;
    static final int ZONE_BELT = 1;
    static final int ZONE_TRAP = 2;

    private static boolean inZone(int zone, float cx, float cz, float radius) {
        if (BaseLayout.isCoreClear(cx, cz)) return false;
        switch (zone) {
            case ZONE_BELT: return BaseLayout.isTurretBelt(cx, cz, radius);
            case ZONE_TRAP: return BaseLayout.isTrapBand(cx, cz, radius);
            default: return BaseLayout.isYard(cx, cz, radius);
        }
    }

    private static float sideAngle(int side) {
        switch (side) {
            case 0: return 0f;                 // +Z
            case 1: return MathX.PI * 0.5f;    // +X
            case 2: return MathX.PI;           // -Z
            default: return MathX.PI * 1.5f;   // -X
        }
    }

    private static int sideOf(float x, float z) {
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? 1 : 3;
        return z > 0 ? 0 : 2;
    }

    /** Verilen yarıçapı kapsayan hücre aralığı (tüm ızgarayı taramamak için). */
    private static int cellLo(float radius) {
        return Math.max(0, BuildGrid.worldToCell(-radius) - 1);
    }

    private static int cellHi(float radius) {
        return Math.min(Balance.GRID - 1, BuildGrid.worldToCell(radius) + 1);
    }

    /** Güncel sur hattındaki ilk boş hücre (kapılar hariç). */
    private int findWallGap(GameWorld w, float radius) {
        int best = -1;
        float bestScore = -1f;
        float reach = radius + Balance.CELL;
        int lo = cellLo(reach), hi = cellHi(reach);
        for (int gz = lo; gz <= hi; gz++) {
            for (int gx = lo; gx <= hi; gx++) {
                float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
                if (!BaseLayout.isWallRing(cx, cz, radius)) continue;
                if (BaseLayout.isGate(cx, cz)) continue;
                if (w.grid.at(gx, gz) != null || w.planAt(gx, gz) != null) continue;
                if (w.grid.canPlace(gx, gz) != BuildGrid.OK) continue;
                // Kapıya en yakın delikler önce kapatılır (en çok oradan girerler)
                float ax = Math.abs(cx), az = Math.abs(cz);
                float alongGate = az >= ax ? ax : az;
                float score = 40f - alongGate;
                if (score > bestScore) {
                    bestScore = score;
                    best = gz * Balance.GRID + gx;
                }
            }
        }
        return best;
    }

    private int countType(GameWorld w, int type) {
        int n = 0;
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            if (!s.alive) continue;
            if (type < 0 ? s.isTurret() : s.type == type) n++;
        }
        for (int i = 0; i < w.plans.size(); i++) {
            BuildPlan p = w.plans.get(i);
            if (!p.alive || p.isUpgrade()) continue;
            if (type < 0 ? Balance.struct(p.type).kind == Balance.KIND_TURRET : p.type == type) n++;
        }
        return n;
    }

    /** Kule sayısı en az olan yön (planlar da sayılır). */
    private int weakestSide(GameWorld w) {
        int[] count = new int[4];
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            if (s.alive && s.isTurret()) count[sideOf(s.x, s.z)]++;
        }
        for (int i = 0; i < w.plans.size(); i++) {
            BuildPlan p = w.plans.get(i);
            if (p.alive && !p.isUpgrade() && Balance.struct(p.type).kind == Balance.KIND_TURRET) {
                count[sideOf(p.x, p.z)]++;
            }
        }
        int worst = 0;
        for (int i = 1; i < 4; i++) {
            if (count[i] < count[worst]) worst = i;
        }
        // Dalga ilerledikçe her yönde daha çok kule hedeflenir
        int target = 1 + w.dayCount / 4;
        return count[worst] < target ? worst : -1;
    }

    /** Bütçeye ve dalgaya göre en uygun kule. */
    private int pickTurret(GameWorld w, int budget) {
        int[] order = {Balance.S_TESLA, Balance.S_SNIPER_TOWER, Balance.S_CANNON,
                Balance.S_FLAME, Balance.S_MG};
        for (int i = 0; i < order.length; i++) {
            int t = order[i];
            if (!affordable(w, t, budget)) continue;
            int cost = w.player.buildCost(Balance.struct(t).cost);
            if (cost * 2 > budget && t != Balance.S_MG) continue;
            return t;
        }
        return affordable(w, Balance.S_MG, budget) ? Balance.S_MG : -1;
    }

    private int gateWithoutTrap(GameWorld w) {
        boolean[] hasTrap = new boolean[4];
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            if (s.alive && s.type == Balance.S_SPIKE) hasTrap[sideOf(s.x, s.z)] = true;
        }
        for (int i = 0; i < w.plans.size(); i++) {
            BuildPlan p = w.plans.get(i);
            if (p.alive && !p.isUpgrade() && p.type == Balance.S_SPIKE) {
                hasTrap[sideOf(p.x, p.z)] = true;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (!hasTrap[i]) return i;
        }
        return -1;
    }

    /** Geliştirilecek en değerli yapı: önce düşük seviyeli kuleler. */
    private Structure pickUpgrade(GameWorld w, int budget) {
        Structure best = null;
        float bestScore = 0f;
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            if (!s.alive || s.level >= s.def().maxLevel) continue;
            if (w.planAt(s.gx, s.gz) != null) continue;
            int cost = w.player.buildCost(s.def().upgradeCost(s.level));
            if (cost > budget) continue;
            if (s.def().upgradeCores(s.level) > w.cores()) continue;
            float score;
            if (s.isTurret()) score = 60f - s.level * 8f;
            else if (s.type == Balance.S_WALL) score = 22f - s.level * 5f;
            else if (s.type == Balance.S_CORE) score = 30f - s.level * 6f;
            else score = 34f - s.level * 6f;
            score -= cost * 0.02f;
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /** Bölge içinde tercih edilen noktaya en yakın boş hücreye plan açar. */
    private boolean place(GameWorld w, int type, int zone, float radius,
                          float preferX, float preferZ, String reason) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        float reach = radius + Balance.CELL * 3f;
        int lo = cellLo(reach), hi = cellHi(reach);
        for (int gz = lo; gz <= hi; gz++) {
            for (int gx = lo; gx <= hi; gx++) {
                float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
                if (!inZone(zone, cx, cz, radius)) continue;
                if (BaseLayout.isGate(cx, cz) && zone != ZONE_TRAP) continue;
                if (w.grid.canPlace(gx, gz) != BuildGrid.OK) continue;
                if (w.planAt(gx, gz) != null) continue;
                float d = MathX.dist2(cx, cz, preferX, preferZ);
                if (d < bestD) {
                    bestD = d;
                    best = gz * Balance.GRID + gx;
                }
            }
        }
        if (best < 0) return false;
        if (!w.addPlan(type, best % Balance.GRID, best / Balance.GRID, true)) return false;
        say(w, reason);
        return true;
    }

    private void say(GameWorld w, String reason) {
        lastDecision = reason;
        w.squadReact(reason + ".", Balance.NPC_ENGINEER);
    }
}
