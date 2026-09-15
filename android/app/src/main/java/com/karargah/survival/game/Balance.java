package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/**
 * Oyunun tüm denge verileri tek yerde: yapılar, silahlar, zombiler, yetenekler
 * ve dalga ilerleyişi. Sayıları buradan değiştirmek oyunun tamamını etkiler.
 */
public final class Balance {

    // ---- dünya ----------------------------------------------------------
    public static final float CELL = 2f;
    public static final int GRID = 60;                 // 60x60 hücre
    public static final float WORLD_HALF = GRID * CELL * 0.5f;   // 60 birim
    public static final float BUILD_RADIUS = 40f;      // inşaat yapılabilen alan
    public static final float SPAWN_RADIUS = 54f;
    public static final int CORE_CELLS = 4;            // çekirdek 4x4 hücre kaplar

    // ---- yapı türleri ---------------------------------------------------
    public static final int KIND_CORE = 0;
    public static final int KIND_WALL = 1;
    public static final int KIND_TRAP = 2;
    public static final int KIND_TURRET = 3;
    public static final int KIND_SUPPORT = 4;

    public static final int S_CORE = 0;
    public static final int S_WALL = 1;
    public static final int S_SPIKE = 2;
    public static final int S_MG = 3;
    public static final int S_CANNON = 4;
    public static final int S_FLAME = 5;
    public static final int S_TESLA = 6;
    public static final int S_SNIPER_TOWER = 7;
    public static final int S_GENERATOR = 8;
    public static final int S_AMMO = 9;
    public static final int S_REPAIR = 10;
    public static final int S_MED = 11;
    public static final int S_COLLECTOR = 12;
    public static final int S_BARRACKS = 13;

    public static final class StructDef {
        public final int id;
        public final int kind;
        public final String name;
        public final String desc;
        public final int color;
        public final int cost;
        public final int coreCost;        // 3. seviyeden sonra gereken enerji çekirdeği
        public final float hp;
        public final float hpPerLevel;    // seviye başına çarpan artışı
        public final boolean blocks;
        public final int maxLevel;
        public final float range;
        public final float fireRate;      // saniyede atış
        public final float damage;
        public final float splash;
        public final float power;         // + üretim, - tüketim
        public final int unlockWave;
        public final String icon;

        StructDef(int id, int kind, String name, String icon, String desc, int color, int cost,
                  int coreCost, float hp, float hpPerLevel, boolean blocks, int maxLevel,
                  float range, float fireRate, float damage, float splash, float power,
                  int unlockWave) {
            this.id = id; this.kind = kind; this.name = name; this.icon = icon;
            this.desc = desc; this.color = color; this.cost = cost; this.coreCost = coreCost;
            this.hp = hp; this.hpPerLevel = hpPerLevel; this.blocks = blocks;
            this.maxLevel = maxLevel; this.range = range; this.fireRate = fireRate;
            this.damage = damage; this.splash = splash; this.power = power;
            this.unlockWave = unlockWave;
        }

        public float hpAt(int level) {
            return hp * (1f + hpPerLevel * (level - 1));
        }

        public float damageAt(int level) {
            return damage * (1f + 0.42f * (level - 1));
        }

        public float rangeAt(int level) {
            return range * (1f + 0.07f * (level - 1));
        }

        public float rateAt(int level) {
            return fireRate * (1f + 0.10f * (level - 1));
        }

        public float powerAt(int level) {
            return power * (power > 0 ? (1f + 0.35f * (level - 1)) : 1f);
        }

        /** Seviye atlama maliyeti (hurda). */
        public int upgradeCost(int level) {
            return Math.round(cost * (0.75f + 0.62f * level) * (1f + 0.22f * level));
        }

        /** Seviye atlamak için gereken enerji çekirdeği. */
        public int upgradeCores(int level) {
            return level >= 3 ? coreCost : 0;
        }

        public int sellValue(int level, float hpFraction) {
            int total = cost;
            for (int l = 1; l < level; l++) total += upgradeCost(l);
            return Math.max(1, Math.round(total * 0.55f * (0.55f + 0.45f * hpFraction)));
        }
    }

    public static final StructDef[] STRUCTS = new StructDef[]{
            new StructDef(S_CORE, KIND_CORE, "Reaktör", "◉",
                    "Üssünün kalbi. Düşerse her şey biter.",
                    0x4FC3F7, 0, 0, 4200f, 0.25f, true, 5,
                    0, 0, 0, 0, 8f, 0),
            new StructDef(S_WALL, KIND_WALL, "Duvar", "▮",
                    "Zombileri yavaşlatır, yolu kapatır. Ucuz ve vazgeçilmez.",
                    0x9E9E8A, 18, 1, 340f, 0.85f, true, 5,
                    0, 0, 0, 0, 0f, 0),
            new StructDef(S_SPIKE, KIND_TRAP, "Dikenli Tuzak", "✸",
                    "Üstünden geçen zombilere sürekli hasar verir, onları yavaşlatır.",
                    0xB0846A, 34, 1, 180f, 0.7f, false, 5,
                    1.6f, 1f, 26f, 0, 0f, 0),
            new StructDef(S_MG, KIND_TURRET, "Makineli Kule", "⌖",
                    "Hızlı ateş eden temel savunma kulesi.",
                    0x78909C, 95, 1, 260f, 0.6f, true, 5,
                    13f, 6.5f, 9f, 0, -3f, 0),
            new StructDef(S_CANNON, KIND_TURRET, "Top Kulesi", "◎",
                    "Yavaş ama alan hasarı veren ağır top.",
                    0x8D6E63, 175, 2, 300f, 0.6f, true, 5,
                    16f, 0.8f, 62f, 3f, -5f, 3),
            new StructDef(S_FLAME, KIND_TURRET, "Alev Kulesi", "▲",
                    "Kısa menzilde koni şeklinde yakar; kalabalığı eritir.",
                    0xEF6C00, 150, 2, 240f, 0.6f, true, 5,
                    7.5f, 8f, 11f, 0, -4f, 4),
            new StructDef(S_TESLA, KIND_TURRET, "Tesla Kulesi", "⚡",
                    "Zincirleme yıldırım: üç düşmana birden atlar.",
                    0x4DD0E1, 240, 3, 230f, 0.6f, true, 5,
                    11f, 1.5f, 30f, 0, -7f, 6),
            new StructDef(S_SNIPER_TOWER, KIND_TURRET, "Nişancı Kulesi", "✦",
                    "Çok uzun menzil, tek hedefe ağır hasar. Kaba zombiler için.",
                    0x546E7A, 260, 3, 220f, 0.6f, true, 5,
                    26f, 0.7f, 120f, 0, -6f, 8),
            new StructDef(S_GENERATOR, KIND_SUPPORT, "Jeneratör", "⚙",
                    "Kulelerin ihtiyaç duyduğu enerjiyi üretir.",
                    0xFDD835, 130, 1, 280f, 0.6f, true, 5,
                    0, 0, 0, 0, 10f, 2),
            new StructDef(S_AMMO, KIND_SUPPORT, "Cephanelik", "▣",
                    "Menzilindeki kuleleri hızlandırır, sen yaklaşınca şarjörünü doldurur.",
                    0x8BC34A, 120, 1, 240f, 0.6f, true, 5,
                    11f, 0, 0, 0, -1f, 3),
            new StructDef(S_REPAIR, KIND_SUPPORT, "Tamir İstasyonu", "✚",
                    "Menzildeki yapıları dalga sırasında bile onarır.",
                    0x26A69A, 165, 2, 250f, 0.6f, true, 5,
                    12f, 0, 7f, 0, -3f, 5),
            new StructDef(S_MED, KIND_SUPPORT, "Tıbbi İstasyon", "✚",
                    "Yakınındayken canını yeniler.",
                    0xE57373, 140, 1, 220f, 0.6f, true, 5,
                    9f, 0, 9f, 0, -2f, 4),
            new StructDef(S_COLLECTOR, KIND_SUPPORT, "Hurda Toplayıcı", "❖",
                    "Her dalga sonunda fazladan hurda üretir.",
                    0xFFB74D, 150, 1, 200f, 0.6f, true, 5,
                    0, 0, 22f, 0, -2f, 2),
            new StructDef(S_BARRACKS, KIND_SUPPORT, "Kışla", "⚑",
                    "Yoldaş alırsın. Her seviye bir yoldaş hakkı ve daha güçlü ekip verir.",
                    0x8D6E63, 220, 1, 340f, 0.7f, true, 5,
                    14f, 0, 0, 0, -2f, 1)
    };

    public static StructDef struct(int id) {
        return STRUCTS[id];
    }

    // ---- silahlar -------------------------------------------------------
    public static final int W_PISTOL = 0;
    public static final int W_SMG = 1;
    public static final int W_SHOTGUN = 2;
    public static final int W_RIFLE = 3;
    public static final int W_SNIPER = 4;
    public static final int W_LAUNCHER = 5;

    public static final class WeaponDef {
        public final int id;
        public final String name;
        public final float damage;
        public final float fireRate;
        public final int magazine;
        public final int reserveMax;
        public final float reload;
        public final float range;
        public final int pellets;
        public final float spread;
        public final float splash;
        public final float speed;      // mermi hızı (0 = anında isabet)
        public final int price;        // hurda
        public final int color;
        public final float recoil;
        public final boolean pierce;
        /** Namlu ucunun tutuş noktasına göre ileri mesafesi (mermi buradan çıkar). */
        public final float muzzleZ;
        /** Namlu ucunun tutuş noktasına göre yüksekliği. */
        public final float muzzleY;

        WeaponDef(int id, String name, float damage, float fireRate, int magazine, int reserveMax,
                  float reload, float range, int pellets, float spread, float splash,
                  float speed, int price, int color, float recoil, boolean pierce,
                  float muzzleZ, float muzzleY) {
            this.id = id; this.name = name; this.damage = damage; this.fireRate = fireRate;
            this.magazine = magazine; this.reserveMax = reserveMax; this.reload = reload;
            this.range = range; this.pellets = pellets; this.spread = spread; this.splash = splash;
            this.speed = speed; this.price = price; this.color = color; this.recoil = recoil;
            this.pierce = pierce; this.muzzleZ = muzzleZ; this.muzzleY = muzzleY;
        }

        public float damageAt(int level) {
            return damage * (1f + 0.20f * (level - 1));
        }

        public int magazineAt(int level) {
            return Math.round(magazine * (1f + 0.18f * (level - 1)));
        }

        public float reloadAt(int level) {
            return reload * (1f - 0.07f * (level - 1));
        }

        public int upgradeCost(int level) {
            return Math.round((80 + price * 0.45f) * (float) Math.pow(1.75f, level - 1));
        }
    }

    public static final int WEAPON_MAX_LEVEL = 5;

    public static final WeaponDef[] WEAPONS = new WeaponDef[]{
            //                 ad             hasar hız  şarjör yedek  şarj  menzil sac  yay   alan  hız  fiyat renk     geri  delici
            new WeaponDef(0, "Tabanca",        17f, 4.5f, 12,  9999, 1.05f, 24f, 1, 0.012f, 0f,   0f,    0, 0xB0BEC5, 0.25f, false, 0.42f, 0.07f),
            new WeaponDef(1, "Hafif Makineli", 12f, 11f,  34,  420,  1.70f, 22f, 1, 0.045f, 0f,   0f,  420, 0xFFA726, 0.14f, false, 0.72f, 0.07f),
            new WeaponDef(2, "Pompalı",        11f, 1.3f,  6,  110,  2.30f, 12f, 9, 0.115f, 0f,   0f,  560, 0xA1887F, 0.85f, false, 0.98f, 0.06f),
            new WeaponDef(3, "Saldırı Tüfeği", 24f, 7.2f, 30,  360,  1.95f, 30f, 1, 0.026f, 0f,   0f,  900, 0x7E9E6A, 0.22f, false, 1.02f, 0.07f),
            new WeaponDef(4, "Keskin Nişancı",130f, 0.9f,  5,   70,  2.60f, 55f, 1, 0.002f, 0f,   0f, 1400, 0x5D8AA8, 1.10f, true,  1.28f, 0.08f),
            new WeaponDef(5, "Roketatar",      95f, 0.65f, 4,   40,  3.00f, 40f, 1, 0.010f, 4.2f, 34f, 2100, 0xD84315, 1.30f, false, 0.92f, 0.10f)
    };

    public static WeaponDef weapon(int id) {
        return WEAPONS[id];
    }

    // ---- zombiler -------------------------------------------------------
    public static final int Z_WALKER = 0;
    public static final int Z_RUNNER = 1;
    public static final int Z_BRUTE = 2;
    public static final int Z_SPITTER = 3;
    public static final int Z_BOSS = 4;
    public static final int Z_CRAWLER = 5;

    public static final class ZombieDef {
        public final int id;
        public final String name;
        public final float hp;
        public final float speed;
        public final float damage;       // saldırı başına
        public final float attackRate;   // saniyede saldırı
        public final float structDamageMul;
        public final float radius;
        public final float height;
        public final int skin;
        public final int cloth;
        public final int xp;
        public final int scrap;
        public final float rangedRange;  // 0 = yakın dövüş
        public final int budget;         // dalga bütçesinde maliyeti

        ZombieDef(int id, String name, float hp, float speed, float damage, float attackRate,
                  float structDamageMul, float radius, float height, int skin, int cloth,
                  int xp, int scrap, float rangedRange, int budget) {
            this.id = id; this.name = name; this.hp = hp; this.speed = speed;
            this.damage = damage; this.attackRate = attackRate;
            this.structDamageMul = structDamageMul; this.radius = radius; this.height = height;
            this.skin = skin; this.cloth = cloth; this.xp = xp; this.scrap = scrap;
            this.rangedRange = rangedRange; this.budget = budget;
        }
    }

    public static final ZombieDef[] ZOMBIES = new ZombieDef[]{
            new ZombieDef(0, "Yürüyen",  70f, 1.85f, 11f, 0.9f, 1.0f, 0.40f, 1.75f, 0x7E9E72, 0x4A5A63, 12, 6, 0f, 1),
            new ZombieDef(1, "Koşucu",   48f, 4.20f,  8f, 1.5f, 0.7f, 0.34f, 1.65f, 0x9CB07A, 0x6B3F3F, 16, 8, 0f, 2),
            new ZombieDef(2, "Kaba",    460f, 1.35f, 34f, 0.55f, 2.6f, 0.78f, 2.55f, 0x6E8A5E, 0x38414A, 55, 34, 0f, 6),
            new ZombieDef(3, "Tüküren", 110f, 1.60f, 16f, 0.75f, 1.0f, 0.42f, 1.80f, 0x8FA35C, 0x5C4A2E, 28, 16, 12f, 4),
            new ZombieDef(4, "Mutant Dev", 2600f, 1.55f, 42f, 0.6f, 2.4f, 1.35f, 4.2f, 0x86603F, 0x2F3B2A, 420, 260, 0f, 30),
            new ZombieDef(5, "Sürüngen", 38f, 2.70f,  7f, 1.4f, 0.5f, 0.34f, 0.95f, 0x89A06B, 0x53412F, 10, 5, 0f, 1)
    };

    public static ZombieDef zombie(int id) {
        return ZOMBIES[id];
    }

    /** Dalga numarasına göre zombi güç çarpanı. */
    public static float waveHpScale(int wave) {
        return 1f + 0.15f * (wave - 1) + 0.009f * (float) Math.pow(wave, 1.85);
    }

    public static float waveDamageScale(int wave) {
        return 1f + 0.10f * (wave - 1) + 0.004f * (float) Math.pow(wave, 1.7);
    }

    public static int waveBudget(int wave) {
        return Math.round(7 + wave * 4.2f + (float) Math.pow(wave, 1.9) * 0.55f);
    }

    public static int waveScrapReward(int wave) {
        return 45 + wave * 14;
    }

    public static int waveXpReward(int wave) {
        return 40 + wave * 18;
    }

    public static boolean isBossWave(int wave) {
        return wave % 5 == 0;
    }

    public static float buildTime(int wave) {
        return wave <= 1 ? 60f : 38f;
    }

    // ---- oyuncu ---------------------------------------------------------
    public static final float PLAYER_BASE_HP = 130f;
    public static final float PLAYER_BASE_SPEED = 6.4f;
    public static final float PLAYER_RADIUS = 0.42f;
    public static final float DASH_SPEED = 19f;
    public static final float DASH_TIME = 0.20f;
    public static final float DASH_COOLDOWN = 4.2f;
    public static final float REVIVE_TIME = 9f;

    public static int xpForLevel(int level) {
        return Math.round(70 + level * 52 + (float) Math.pow(level, 1.75) * 11f);
    }

    // ---- yetenekler -----------------------------------------------------
    public static final int SK_HP = 0;
    public static final int SK_ARMOR = 1;
    public static final int SK_SPEED = 2;
    public static final int SK_DAMAGE = 3;
    public static final int SK_RELOAD = 4;
    public static final int SK_CRIT = 5;
    public static final int SK_ENGINEER = 6;
    public static final int SK_STRUCT_HP = 7;
    public static final int SK_SCRAP = 8;
    public static final int SK_LIFESTEAL = 9;
    public static final int SK_DASH = 10;
    public static final int SK_REGEN = 11;
    public static final int SKILL_COUNT = 12;

    public static final class SkillDef {
        public final int id;
        public final String name;
        public final String desc;
        public final int maxLevel;
        public final String icon;

        SkillDef(int id, String name, String icon, String desc, int maxLevel) {
            this.id = id; this.name = name; this.icon = icon; this.desc = desc;
            this.maxLevel = maxLevel;
        }
    }

    public static final SkillDef[] SKILLS = new SkillDef[]{
            new SkillDef(SK_HP, "Sağlamlık", "♥", "Her seviyede +%14 azami can.", 5),
            new SkillDef(SK_ARMOR, "Zırh", "⛨", "Her seviyede gelen hasar %5 azalır.", 5),
            new SkillDef(SK_SPEED, "Atiklik", "»", "Her seviyede +%6 hareket hızı.", 5),
            new SkillDef(SK_DAMAGE, "Silah Ustası", "⌖", "Her seviyede +%10 silah hasarı.", 5),
            new SkillDef(SK_RELOAD, "Hızlı Şarjör", "↻", "Her seviyede %9 daha hızlı şarjör.", 5),
            new SkillDef(SK_CRIT, "Nişancı", "✷", "Her seviyede +%6 kritik şansı (x2.2 hasar).", 5),
            new SkillDef(SK_ENGINEER, "Mühendis", "⚒", "Her seviyede inşa/geliştirme %7 ucuz.", 5),
            new SkillDef(SK_STRUCT_HP, "Usta Tamirci", "✚", "Yapı canı +%12, tamir gücü +%25.", 5),
            new SkillDef(SK_SCRAP, "Hurdacı", "❖", "Her seviyede +%12 hurda kazancı.", 5),
            new SkillDef(SK_LIFESTEAL, "Kan Emici", "☣", "Her öldürmede +2 can geri kazan.", 3),
            new SkillDef(SK_DASH, "Kaçış Ustası", "⇉", "Atılma bekleme süresi -0.7 sn.", 3),
            new SkillDef(SK_REGEN, "Yenilenme", "✜", "Saniyede +0.7 can yenilenmesi.", 5)
    };

    // ---- yoldaşlar ------------------------------------------------------
    public static final int NPC_GUARD = 0;
    public static final int NPC_ENGINEER = 1;
    public static final int NPC_SCAVENGER = 2;
    public static final int NPC_MEDIC = 3;
    public static final int NPC_COUNT = 4;

    public static final class NpcDef {
        public final int id;
        public final String name;
        public final String desc;
        public final int hire;          // hurda maliyeti
        public final float hp;
        public final float speed;
        public final float damage;
        public final float fireRate;
        public final float range;
        public final float work;        // temel iş gücü (saniyede)
        public final int suit;
        public final int accent;
        public final int weapon;        // taşıdığı silah modeli
        /** Görev verimleri: onarım, inşa, iyileştirme, toplama, dövüş. */
        public final float repairMul, buildMul, healMul, gatherMul, fightMul;

        NpcDef(int id, String name, String desc, int hire, float hp, float speed, float damage,
               float fireRate, float range, float work, int suit, int accent, int weapon,
               float repairMul, float buildMul, float healMul, float gatherMul, float fightMul) {
            this.id = id; this.name = name; this.desc = desc; this.hire = hire; this.hp = hp;
            this.speed = speed; this.damage = damage; this.fireRate = fireRate; this.range = range;
            this.work = work; this.suit = suit; this.accent = accent; this.weapon = weapon;
            this.repairMul = repairMul; this.buildMul = buildMul; this.healMul = healMul;
            this.gatherMul = gatherMul; this.fightMul = fightMul;
        }

        /** Belirli bir görevdeki verim çarpanı. */
        public float mulFor(int duty) {
            switch (duty) {
                case DUTY_REPAIR: return repairMul;
                case DUTY_BUILD: return buildMul;
                case DUTY_HEAL: return healMul;
                case DUTY_GATHER: return gatherMul;
                default: return fightMul;
            }
        }

        public float hpAt(int level) {
            return hp * (1f + 0.22f * (level - 1));
        }

        public float damageAt(int level) {
            return damage * (1f + 0.28f * (level - 1));
        }

        public float workAt(int level) {
            return work * (1f + 0.30f * (level - 1));
        }
    }

    public static final NpcDef[] NPCS = new NpcDef[]{
            //        rol            ad          açıklama (kısaltıldı)
            new NpcDef(NPC_GUARD, "Muhafız",
                    "Dövüşte en iyisi. Onarım ve inşaatı da yapar ama yavaş.",
                    180, 150f, 5.9f, 15f, 3.4f, 18f, 7f, 0x3E4A33, 0x8BC34A, W_RIFLE,
                    0.55f, 0.5f, 0.3f, 0.8f, 1.0f),
            new NpcDef(NPC_ENGINEER, "Mühendis",
                    "İnşaat ve onarımda en iyisi; dalga sırasında bile çalışır.",
                    200, 130f, 5.6f, 8f, 2.2f, 12f, 16f, 0x4E5B66, 0xFFB74D, W_PISTOL,
                    1.0f, 1.0f, 0.35f, 0.9f, 0.55f),
            new NpcDef(NPC_SCAVENGER, "Toplayıcı",
                    "Ganimet toplamada en hızlısı; hafif iş ve dövüş de yapar.",
                    150, 110f, 7.2f, 9f, 3.0f, 13f, 9f, 0x5D4037, 0xFFD54F, W_SMG,
                    0.5f, 0.6f, 0.3f, 1.5f, 0.7f),
            new NpcDef(NPC_MEDIC, "Sağlıkçı",
                    "İyileştirmede en iyisi, düşen yoldaşı hızlı kaldırır.",
                    220, 125f, 6.2f, 7f, 2.4f, 12f, 12f, 0x37474F, 0xE57373, W_PISTOL,
                    0.45f, 0.45f, 1.0f, 0.9f, 0.6f)
    };

    public static NpcDef npc(int id) {
        return NPCS[MathX.clampI(id, 0, NPCS.length - 1)];
    }

    // Duruş: yoldaşın nerede duracağını belirler (tek seçim).
    public static final int STANCE_FOLLOW = 0;
    public static final int STANCE_HOLD = 1;
    public static final int STANCE_DEFEND = 2;
    public static final int STANCE_ATTACK = 3;
    public static final int STANCE_RETREAT = 4;
    public static final int STANCE_COUNT = 5;
    public static final String[] STANCE_NAMES = {
            "Takip et", "Burayı tut", "Reaktörü koru", "Bölgeye saldır", "Geri çekil"};
    public static final String[] STANCE_SHORT = {"TAKİP", "TUT", "KORU", "SALDIR", "ÇEKİL"};

    // Görevler: bir yoldaşa aynı anda birden fazlası verilebilir (bit maskesi).
    public static final int DUTY_FIGHT = 1;
    public static final int DUTY_REPAIR = 2;
    public static final int DUTY_BUILD = 4;
    public static final int DUTY_GATHER = 8;
    public static final int DUTY_HEAL = 16;
    public static final int[] DUTY_BITS = {DUTY_FIGHT, DUTY_REPAIR, DUTY_BUILD, DUTY_GATHER, DUTY_HEAL};
    public static final String[] DUTY_NAMES = {"Savaş", "Onar", "İnşa et", "Ganimet topla", "İyileştir"};
    public static final String[] DUTY_SHORT = {"SAVAŞ", "ONAR", "İNŞA", "TOPLA", "İYİLEŞ"};
    public static final String[] DUTY_LETTER = {"S", "O", "İ", "T", "+"};

    /** Rolün açılışta gelen görevleri. */
    public static int defaultDuties(int role) {
        switch (role) {
            case NPC_ENGINEER: return DUTY_REPAIR | DUTY_BUILD | DUTY_FIGHT;
            case NPC_SCAVENGER: return DUTY_GATHER | DUTY_FIGHT;
            case NPC_MEDIC: return DUTY_HEAL | DUTY_FIGHT;
            default: return DUTY_FIGHT;
        }
    }

    /** İnşaat hızı: saniyede tamamlanan oran (iş gücüne göre). */
    public static final float BUILD_WORK_PER_SEC = 0.055f;

    /** Kışla seviyesi başına yoldaş hakkı. */
    public static final int NPC_PER_BARRACKS_LEVEL = 1;
    public static final int NPC_MAX = 6;
    public static final float NPC_REVIVE_TIME = 22f;

    // ---- yere düşen ganimet ---------------------------------------------
    /** Oyuncunun ganimeti kendine çekme yarıçapı. */
    public static final float PICKUP_MAGNET = 4.2f;
    public static final float PICKUP_GRAB = 1.25f;
    public static final float PICKUP_LIFE = 90f;

    // ---- güç ------------------------------------------------------------
    public static final float MIN_POWER_EFFICIENCY = 0.42f;

    private Balance() {}
}
