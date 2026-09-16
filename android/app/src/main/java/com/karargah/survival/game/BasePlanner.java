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
    /** Sur hattı kare bir çerçevedir: kenar uzaklığı (Chebyshev) bu bantta. */
    private static final int MAX_QUEUE = 2;
    private static final int MAX_UPGRADES = 2;

    private float timer = 3f;
    public String lastDecision = "";

    public void reset() {
        timer = 3f;
        lastDecision = "";
    }

    public void update(GameWorld w, float dt) {
        if (!w.autoRebuild) return;
        timer -= dt;
        if (timer > 0f) return;
        timer = 7.5f; // mimar her boş gördüğü yere aralıksız plan yağdırmasın
        // İnşaat kararları hazırlık aşamasında verilir; dalga sırasında ekip savaşır.
        if (!w.waves.isPrepare() || w.gameOver) return;
        if (!hasBuilder(w)) return;
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
        decide(w, budget, onlyUpgrades);
    }

    private static boolean hasBuilder(GameWorld w) {
        for (int i = 0; i < w.npcs.size(); i++) {
            if (w.npcs.get(i).hasDuty(Balance.DUTY_BUILD)) return true;
        }
        return false;
    }

    private boolean affordable(GameWorld w, int type, int budget) {
        Balance.StructDef d = Balance.struct(type);
        return w.waves.wave >= d.unlockWave && w.player.buildCost(d.cost) <= budget;
    }

    private void decide(GameWorld w, int budget, boolean onlyUpgrades) {
        if (onlyUpgrades) {
            Structure up = pickUpgrade(w, budget);
            if (up != null && w.addUpgradePlan(up)) {
                say(w, up.def().name + " yeterli değil, Sv." + (up.level + 1) + " yapıyorum");
            }
            return;
        }
        // 1) Enerji açığı — kuleler yavaşlıyorsa her şeyden önce jeneratör
        if (w.powerUse > w.powerGen + 0.5f && affordable(w, Balance.S_GENERATOR, budget)) {
            if (place(w, Balance.S_GENERATOR, 3.5f, 8f, 0f, 0f,
                    "Enerji açığı var, jeneratör kuruyorum")) {
                return;
            }
        }

        // 2) Sur hattındaki gerçek delik. Alan dolmaya başlayınca hedef sur
        // dışarı taşınır; reaktör çevresindeki eski dar halkayı tekrar kurmaz.
        if (affordable(w, Balance.S_WALL, budget)) {
            int gap = findWallGap(w);
            if (gap >= 0) {
                int gx = gap % Balance.GRID, gz = gap / Balance.GRID;
                if (w.addPlan(Balance.S_WALL, gx, gz, true)) {
                    say(w, "Sur hattında delik var, kapatıyorum");
                    return;
                }
            }
        }

        // 3) En zayıf yöne kule
        int side = weakestSide(w);
        if (side >= 0) {
            int type = pickTurret(w, budget);
            if (type >= 0) {
                float a = sideAngle(side);
                float px = (float) Math.sin(a) * 7f, pz = (float) Math.cos(a) * 7f;
                if (place(w, type, 4.5f, 8.5f, px, pz,
                        SIDE_NAMES[side] + " taraf zayıf, " + Balance.struct(type).name
                                + " kuruyorum")) {
                    return;
                }
            }
        }

        // 4) Kapı önüne tuzak
        if (w.waves.wave >= 2 && affordable(w, Balance.S_SPIKE, budget)) {
            int gateSide = gateWithoutTrap(w);
            if (gateSide >= 0) {
                float a = sideAngle(gateSide);
                float px = (float) Math.sin(a) * 12f, pz = (float) Math.cos(a) * 12f;
                if (place(w, Balance.S_SPIKE, 10.5f, 13.5f, px, pz,
                        SIDE_NAMES[gateSide] + " kapının önüne tuzak koyuyorum")) {
                    return;
                }
            }
        }

        // 5) Destek yapıları
        int turrets = countType(w, -1);
        if (turrets >= 4 && countType(w, Balance.S_AMMO) == 0
                && affordable(w, Balance.S_AMMO, budget)) {
            if (place(w, Balance.S_AMMO, 4f, 8f, 0f, 0f,
                    "Kuleler için cephanelik kuruyorum")) {
                return;
            }
        }
        if (w.waves.wave >= 5 && countType(w, Balance.S_REPAIR) == 0
                && affordable(w, Balance.S_REPAIR, budget)) {
            if (place(w, Balance.S_REPAIR, 3.5f, 7f, 0f, 0f,
                    "Tamir istasyonu kuruyorum, yapılar ayakta kalsın")) {
                return;
            }
        }
        if (w.waves.wave >= 4 && countType(w, Balance.S_MED) == 0
                && affordable(w, Balance.S_MED, budget)) {
            if (place(w, Balance.S_MED, 3.5f, 7f, 0f, 6f,
                    "Tıbbi istasyon kuruyorum")) {
                return;
            }
        }
        if (w.waves.wave >= 3 && countType(w, Balance.S_COLLECTOR) < 2
                && budget > 400 && affordable(w, Balance.S_COLLECTOR, budget)) {
            if (place(w, Balance.S_COLLECTOR, 3.5f, 7f, 0f, -6f,
                    "Hurda toplayıcı kuruyorum")) {
                return;
            }
        }

        // 6) Yeni iş yoksa mevcut yapıyı geliştir
        Structure up = pickUpgrade(w, budget);
        if (up != null && w.addUpgradePlan(up)) {
            say(w, up.def().name + " yeterli değil, Sv." + (up.level + 1) + " yapıyorum");
        }
    }

    // ---- yardımcılar ----------------------------------------------------

    private static final String[] SIDE_NAMES = {"Kuzey", "Doğu", "Güney", "Batı"};

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

    /** Sur halkasındaki boş hücre (kapı hücreleri hariç). */
    /** Verilen yarıçapı kapsayan hücre aralığı (tüm ızgarayı taramamak için). */
    private static int cellLo(float radius) {
        return Math.max(0, BuildGrid.worldToCell(-radius) - 1);
    }

    private static int cellHi(float radius) {
        return Math.min(Balance.GRID - 1, BuildGrid.worldToCell(radius) + 1);
    }

    private int findWallGap(GameWorld w) {
        int best = -1;
        float bestScore = -1f;
        float target = desiredRing(w);
        float ringMin = target - 1.1f, ringMax = target + 1.1f;
        int lo = cellLo(ringMax), hi = cellHi(ringMax);
        for (int gz = lo; gz <= hi; gz++) {
            for (int gx = lo; gx <= hi; gx++) {
                float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
                float ring = Math.max(Math.abs(cx), Math.abs(cz));
                if (ring < ringMin || ring > ringMax) continue;
                if (isGateCell(cx, cz)) continue;
                if (w.grid.at(gx, gz) != null || w.planAt(gx, gz) != null) continue;
                if (w.grid.canPlace(gx, gz) != BuildGrid.OK) continue;
                // sur hattının ortasına yakın delikler önce kapatılır
                // Komşusu olmayan hücre yeni bir rastgele duvar hattı
                // başlatmasın; yalnızca mevcut hattın gerçek deliğini kapat.
                int neighbors = 0;
                if (isWallOrPlan(w, gx - 1, gz)) neighbors++;
                if (isWallOrPlan(w, gx + 1, gz)) neighbors++;
                if (isWallOrPlan(w, gx, gz - 1)) neighbors++;
                if (isWallOrPlan(w, gx, gz + 1)) neighbors++;
                if (neighbors == 0) continue;
                float score = neighbors * 10f - Math.abs(ring - target);
                if (score > bestScore) {
                    bestScore = score;
                    best = gz * Balance.GRID + gx;
                }
            }
        }
        return best;
    }

    private float desiredRing(GameWorld w) {
        int occupied = 0;
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            if (s.alive && s.type != Balance.S_CORE && MathX.len(s.x, s.z) < 17f) occupied++;
        }
        // İç alan kalmadığında sur hedefi kademeli büyür; bir anda bütün
        // haritayı duvar planıyla doldurmaz.
        float expansion = occupied > 24 ? 8f : occupied > 16 ? 4f : 0f;
        return Math.min(34f, 19f + expansion + Math.min(7f, (w.waves.wave / 5) * 2f));
    }

    private boolean isWallOrPlan(GameWorld w, int gx, int gz) {
        Structure s = w.grid.at(gx, gz);
        if (s != null && s.type == Balance.S_WALL) return true;
        BuildPlan p = w.planAt(gx, gz);
        return p != null && !p.isUpgrade() && p.type == Balance.S_WALL;
    }

    /** Kapı hücreleri: her kenarın ortasında iki hücrelik geçit açık kalır. */
    private static boolean isGateCell(float cx, float cz) {
        float ax = Math.abs(cx), az = Math.abs(cz);
        if (Math.max(ax, az) < 6f) return false;
        // Kuzey/güney kenarında kapı x ekseninin ortasında, doğu/batıda z'nin
        return az >= ax ? ax <= 2.1f : az <= 2.1f;
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
        int target = 1 + w.waves.wave / 4;
        return count[worst] < target ? worst : -1;
    }

    /** Bütçeye ve dalgaya göre en uygun kule. */
    private int pickTurret(GameWorld w, int budget) {
        int[] order = {Balance.S_TESLA, Balance.S_SNIPER_TOWER, Balance.S_CANNON,
                Balance.S_FLAME, Balance.S_MG};
        // Zengin isek üst sınıf, değilse makineli
        for (int i = 0; i < order.length; i++) {
            int t = order[i];
            if (!affordable(w, t, budget)) continue;
            // pahalı kuleyi ancak bütçe rahatken seç
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
            float score = 0f;
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

    /** Verilen halka/nokta civarında uygun boş hücreye plan açar. */
    private boolean place(GameWorld w, int type, float minR, float maxR,
                          float preferX, float preferZ, String reason) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        int lo = cellLo(maxR), hi = cellHi(maxR);
        for (int gz = lo; gz <= hi; gz++) {
            for (int gx = lo; gx <= hi; gx++) {
                float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
                float r = MathX.len(cx, cz);
                if (r < minR || r > maxR) continue;
                if (isGateCell(cx, cz)) continue;          // kapıları tıkama
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
