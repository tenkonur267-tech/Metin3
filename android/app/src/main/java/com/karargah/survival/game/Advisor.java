package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/**
 * Yoldaşların durum değerlendirmesi. Üssü ve oyuncunun durumunu düzenli
 * olarak tarar, en öncelikli sorunu bulur ve uygun roldeki yoldaşın ağzından
 * öneri olarak söyler. Aynı öneriyi arka arkaya tekrarlamaz.
 */
public class Advisor {
    private static final float CHECK_PERIOD = 7f;
    private static final float REPEAT_COOLDOWN = 45f;

    public static final int TIP_NONE = -1;
    public static final int TIP_POWER = 0;
    public static final int TIP_REPAIR = 1;
    public static final int TIP_WEAK_SIDE = 2;
    public static final int TIP_AMMO = 3;
    public static final int TIP_SKILL = 4;
    public static final int TIP_SPEND = 5;
    public static final int TIP_LOOT = 6;
    public static final int TIP_CORE = 7;
    public static final int TIP_BOSS = 8;
    public static final int TIP_WALLS = 9;
    public static final int TIP_MEDIC = 10;
    public static final int TIP_NO_BUILDER = 11;
    public static final int TIP_PLAN_WAITING = 12;
    public static final int TIP_COUNT = 13;

    private final float[] lastSaid = new float[TIP_COUNT];
    private float timer = 4f;
    /** Arayüzde gösterilen güncel öneri. */
    public String currentTip = "";
    public float tipTimer;

    public void reset() {
        for (int i = 0; i < lastSaid.length; i++) lastSaid[i] = -999f;
        currentTip = "";
        tipTimer = 0f;
        timer = 4f;
    }

    public void update(GameWorld w, float dt) {
        if (tipTimer > 0f) tipTimer -= dt;
        timer -= dt;
        if (timer > 0f || w.npcs.isEmpty()) return;
        timer = CHECK_PERIOD;

        int tip = evaluate(w);
        if (tip == TIP_NONE) return;
        if (w.playTime - lastSaid[tip] < REPEAT_COOLDOWN) return;
        lastSaid[tip] = w.playTime;

        Npc speaker = pickSpeaker(w, tip);
        if (speaker == null) return;
        String text = textFor(w, tip);
        currentTip = text;
        tipTimer = 8f;
        w.npcSays(speaker, text);
    }

    /** En öncelikli sorunu seçer. */
    private int evaluate(GameWorld w) {
        Player p = w.player;
        boolean prepare = !w.isNight();

        if (w.core != null && w.core.hpFraction() < 0.55f) return TIP_CORE;

        // Bekleyen şantiyeler
        if (!w.plans.isEmpty()) {
            boolean builder = false;
            boolean waiting = false;
            for (int i = 0; i < w.npcs.size(); i++) {
                if (w.npcs.get(i).hasDuty(Balance.DUTY_BUILD)) builder = true;
            }
            for (int i = 0; i < w.plans.size(); i++) {
                if (w.plans.get(i).waiting) waiting = true;
            }
            if (!builder) return TIP_NO_BUILDER;
            if (waiting) return TIP_PLAN_WAITING;
        }
        if (w.powerUse > 0.5f && w.powerEff < 0.999f) return TIP_POWER;

        int damaged = 0;
        int walls = 0;
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            if (!s.alive) continue;
            if (s.hpFraction() < 0.6f) damaged++;
            if (s.type == Balance.S_WALL) walls++;
        }
        if (damaged >= 3 && prepare) return TIP_REPAIR;
        if (walls < 12 && w.dayCount >= 2 && prepare) return TIP_WALLS;

        int weak = weakestSide(w);
        if (weak >= 0 && prepare && w.dayCount >= 3) return TIP_WEAK_SIDE;

        if (!prepare && w.threat.bloodMoon) return TIP_BOSS;

        if (p.unlocked[Balance.W_PISTOL] && lowAmmo(p)) return TIP_AMMO;
        if (p.skillPoints > 0) return TIP_SKILL;
        if (prepare && p.scrap > 600) return TIP_SPEND;
        if (w.pickups.size() > 8) return TIP_LOOT;
        if (p.hp < p.maxHp * 0.5f && !hasRole(w, Balance.NPC_MEDIC)) return TIP_MEDIC;
        return TIP_NONE;
    }

    private static boolean lowAmmo(Player p) {
        for (int i = 0; i < Balance.WEAPONS.length; i++) {
            if (!p.unlocked[i] || i == Balance.W_PISTOL) continue;
            if (p.reserve[i] < Balance.weapon(i).reserveMax * 0.2f) return true;
        }
        return false;
    }

    private static boolean hasRole(GameWorld w, int role) {
        for (int i = 0; i < w.npcs.size(); i++) {
            if (w.npcs.get(i).role == role) return true;
        }
        return false;
    }

    /**
     * Kulelerin kapsamadığı yönü bulur: her yön için o yöndeki kule sayısına
     * bakar, en zayıf olanı döndürür (0=kuzey, 1=doğu, 2=güney, 3=batı).
     */
    private int weakestSide(GameWorld w) {
        int[] count = new int[4];
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            if (!s.alive || !s.isTurret()) continue;
            count[sideOf(s.x, s.z)]++;
        }
        int total = count[0] + count[1] + count[2] + count[3];
        if (total < 3) return -1;
        int worst = 0;
        for (int i = 1; i < 4; i++) {
            if (count[i] < count[worst]) worst = i;
        }
        return count[worst] * 4 <= total ? worst : -1;
    }

    private static int sideOf(float x, float z) {
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? 1 : 3;
        return z > 0 ? 0 : 2;
    }

    private static final String[] SIDE_NAMES = {"kuzey", "doğu", "güney", "batı"};

    private Npc pickSpeaker(GameWorld w, int tip) {
        int preferred;
        switch (tip) {
            case TIP_REPAIR:
            case TIP_WALLS:
            case TIP_POWER:
            case TIP_CORE:
            case TIP_NO_BUILDER:
            case TIP_PLAN_WAITING:
                preferred = Balance.NPC_ENGINEER;
                break;
            case TIP_LOOT:
            case TIP_SPEND:
                preferred = Balance.NPC_SCAVENGER;
                break;
            case TIP_MEDIC:
                preferred = Balance.NPC_MEDIC;
                break;
            default:
                preferred = Balance.NPC_GUARD;
                break;
        }
        Npc fallback = null;
        for (int i = 0; i < w.npcs.size(); i++) {
            Npc n = w.npcs.get(i);
            if (n.downed) continue;
            if (n.role == preferred) return n;
            if (fallback == null) fallback = n;
        }
        return fallback;
    }

    private String textFor(GameWorld w, int tip) {
        switch (tip) {
            case TIP_POWER:
                return "Enerji açığımız var, kuleler yavaş atıyor — jeneratör kur.";
            case TIP_REPAIR:
                return "Birkaç yapı ağır hasarlı, dalga başlamadan onaralım.";
            case TIP_WEAK_SIDE: {
                int side = weakestSide(w);
                return (side >= 0 ? SIDE_NAMES[side] : "bir") + " taraf savunmasız, oraya kule lazım.";
            }
            case TIP_AMMO:
                return "Cephanen azaldı, cephanelikten doldur.";
            case TIP_SKILL:
                return "Harcanmamış yetenek puanın var, kendini geliştir.";
            case TIP_SPEND:
                return "Hurda birikti, geliştirme yapmanın tam sırası.";
            case TIP_LOOT:
                return "Sahada toplanmamış ganimet var, toplamamı ister misin?";
            case TIP_CORE:
                return "Reaktör ağır hasarlı! Önceliğimiz onu korumak.";
            case TIP_BOSS:
                return "Dev geliyor — dağılmayalım, ağır silahları ona yoğunlaştır.";
            case TIP_WALLS:
                return "Duvar hattımız ince, zombiler doğrudan içeri giriyor.";
            case TIP_MEDIC:
                return "Canın düşük; bir sağlıkçı yoldaş işimizi kolaylaştırır.";
            case TIP_NO_BUILDER:
                return "Şantiyeler bekliyor ama kimsede İNŞA görevi yok, birine ver.";
            case TIP_PLAN_WAITING:
                return "Kasada hurda yetmiyor, şantiyeler bekliyor.";
            default:
                return "";
        }
    }
}
