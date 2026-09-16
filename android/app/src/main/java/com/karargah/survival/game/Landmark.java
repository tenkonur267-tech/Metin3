package com.karargah.survival.game;

/**
 * Haritanın elle tasarlanmış mekânları: askeri üs, hastane, benzinlik, baraj,
 * havaalanı, radyo kulesi, terk edilmiş kamp.
 *
 * <p>{@link WorldGen}'in prosedürel şehirleri "nerede bina var"ı söyler ama
 * hepsi birbirine benzer. Landmark katmanı bunun üstüne <em>tasarlanmış</em>
 * mekânlar koyar: her türün kendi yerleşim planı, kendi ganimeti ve kendi
 * tehlikesi vardır. Açık dünyada gidilmeye değer yerler böyle doğar.
 *
 * <p>Şehirler gibi bunlar da hiçbir yerde saklanmaz — konumları, adları ve
 * içlerindeki her bina koordinattan hesaplanır. Aynı mekân her oyunda aynı
 * yerde, aynı adla ve aynı planla durur.
 */
public final class Landmark {

    private Landmark() {
    }

    // ---- türler ---------------------------------------------------------

    public static final int L_GAS = 0;        // benzinlik
    public static final int L_CAMP = 1;       // terk edilmiş kamp
    public static final int L_RADIO = 2;      // radyo kulesi
    public static final int L_HOSPITAL = 3;   // hastane
    public static final int L_MILITARY = 4;   // askeri üs
    public static final int L_DAM = 5;        // baraj
    public static final int L_AIRFIELD = 6;   // havaalanı
    public static final int TYPE_COUNT = 7;

    private static final String[] TYPE_NAMES = {
            "Benzinlik", "Terk Edilmiş Kamp", "Radyo Kulesi",
            "Hastane", "Askeri Üs", "Baraj", "Havaalanı"
    };

    /** Mekânın kapladığı yarıçap. */
    private static final float[] TYPE_RADIUS = {
            24f, 26f, 20f, 48f, 74f, 86f, 104f
    };

    /** Ne kadar sık çıkar (kafes hücresi başına ağırlık). */
    private static final float[] TYPE_WEIGHT = {
            0.30f, 0.24f, 0.14f, 0.13f, 0.09f, 0.05f, 0.05f
    };

    /** Zombi yoğunluğuna eklenen tehlike. */
    private static final float[] TYPE_DANGER = {
            0.18f, 0.10f, 0.12f, 0.42f, 0.62f, 0.30f, 0.46f
    };

    /** Sandık bolluğu (0..1): ne kadar yüksekse o kadar çok yağma. */
    private static final float[] TYPE_LOOT = {
            0.45f, 0.40f, 0.30f, 0.62f, 0.78f, 0.50f, 0.66f
    };

    public static String typeName(int type) {
        return TYPE_NAMES[clamp(type)];
    }

    public static float radiusOf(int type) {
        return TYPE_RADIUS[clamp(type)];
    }

    public static float dangerOf(int type) {
        return TYPE_DANGER[clamp(type)];
    }

    public static float lootOf(int type) {
        return TYPE_LOOT[clamp(type)];
    }

    private static int clamp(int t) {
        return t < 0 ? 0 : (t >= TYPE_COUNT ? TYPE_COUNT - 1 : t);
    }

    // ---- yerleşim -------------------------------------------------------

    /** Mekânların serpildiği kafesin adımı. Şehirlerden daha sık. */
    public static final float SPACING = 620f;
    /** Üssün etrafında bu yarıçapta mekân kurulmaz. */
    private static final float HOME_CLEAR = 200f;

    // Her kare binlerce kez çağrıldıkları için çöp üretmezler.
    private static final float[] scratchCell = new float[4];
    private static final float[] scratchNear = new float[4];
    private static final float[] scratchBlocked = new float[6];
    private static final float[] scratchLoot = new float[6];
    private static final float[] scratchDanger = new float[4];
    private static final float[] scratchName = new float[4];

    /**
     * Kafes hücresinde mekân var mı? Varsa out = {x, z, yarıçap, tür}.
     *
     * <p>Şehirlerle çakışmaz: bir şehrin içine denk gelen mekân elenir, çünkü
     * şehrin kendi bina ızgarası zaten oraya yerleşmiştir.
     */
    public static boolean cellAt(int ci, int cj, float[] out) {
        if (WorldGen.rand01(ci, cj, 601) > 0.42f) return false;

        int type = pickType(ci, cj);
        float radius = TYPE_RADIUS[type];
        float jx = (WorldGen.rand01(ci, cj, 602) - 0.5f) * SPACING * 0.66f;
        float jz = (WorldGen.rand01(ci, cj, 603) - 0.5f) * SPACING * 0.66f;
        float x = (ci + 0.5f) * SPACING + jx;
        float z = (cj + 0.5f) * SPACING + jz;

        if (Math.abs(x) > Balance.WORLD_HALF - radius) return false;
        if (Math.abs(z) > Balance.WORLD_HALF - radius) return false;
        // Üssün dibinde mekân olmasın
        if (x * x + z * z < (HOME_CLEAR + radius) * (HOME_CLEAR + radius)) return false;
        // Şehrin içine düşen mekân elenir
        if (WorldGen.cityStrength(x, z) > 0.15f) return false;

        out[0] = x;
        out[1] = z;
        out[2] = radius;
        out[3] = type;
        return true;
    }

    /** Ağırlıklara göre tür seçer. */
    private static int pickType(int ci, int cj) {
        float roll = WorldGen.rand01(ci, cj, 604);
        float acc = 0f;
        for (int t = 0; t < TYPE_COUNT; t++) {
            acc += TYPE_WEIGHT[t];
            if (roll < acc) return t;
        }
        return L_GAS;
    }

    /**
     * Noktaya en yakın mekânı bulur. Bulursa out = {x, z, yarıçap, tür} ve
     * dönüş değeri merkeze uzaklık; yoksa -1.
     */
    public static float nearest(float x, float z, float[] out) {
        int ci = (int) Math.floor(x / SPACING);
        int cj = (int) Math.floor(z / SPACING);
        float bestD = -1f;
        float[] tmp = scratchCell;
        for (int j = cj - 1; j <= cj + 1; j++) {
            for (int i = ci - 1; i <= ci + 1; i++) {
                if (!cellAt(i, j, tmp)) continue;
                float dx = x - tmp[0], dz = z - tmp[1];
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (bestD < 0f || d < bestD) {
                    bestD = d;
                    out[0] = tmp[0];
                    out[1] = tmp[1];
                    out[2] = tmp[2];
                    out[3] = tmp[3];
                }
            }
        }
        return bestD;
    }

    /** Mekânın adı: "Kuzey Askeri Üssü" gibi, koordinattan türetilir. */
    private static final String[] PREFIX = {
            "Kuzey", "Güney", "Doğu", "Batı", "Eski", "Yeni", "Kayıp", "Sessiz",
            "Kızıl", "Kara", "Yüksek", "Alçak", "Çorak", "Sisli", "Uzak", "Terkedilmiş"
    };

    public static String nameAt(float x, float z, int type) {
        int ci = (int) Math.floor(x / SPACING), cj = (int) Math.floor(z / SPACING);
        String prefix = PREFIX[Math.floorMod(WorldGen.hash(ci, cj, 605), PREFIX.length)];
        return prefix + " " + TYPE_NAMES[clamp(type)];
    }

    /** Menzildeki en yakın mekânın adı; yoksa null. */
    public static String nearestName(float x, float z, float range) {
        float[] c = scratchName;
        float d = nearest(x, z, c);
        if (d < 0f || d > range) return null;
        return nameAt(c[0], c[1], (int) c[3]);
    }

    /** Mekân etkisi: merkezde 1, kenarda 0. */
    public static float strength(float x, float z) {
        float[] c = scratchDanger;
        float d = nearest(x, z, c);
        if (d < 0f || d >= c[2]) return 0f;
        float t = 1f - d / c[2];
        return t * t * (3f - 2f * t);
    }

    /** Mekânların zombi yoğunluğuna katkısı. */
    public static float dangerAt(float x, float z) {
        float[] c = scratchDanger;
        float d = nearest(x, z, c);
        if (d < 0f || d >= c[2]) return 0f;
        float t = 1f - d / c[2];
        return TYPE_DANGER[clamp((int) c[3])] * (t * t * (3f - 2f * t));
    }

    // ---- yerleşim planları ----------------------------------------------

    /**
     * Mekânın kaç parçadan (bina, hangar, kule, çit) oluştuğu. Her tür kendi
     * elle tasarlanmış planına sahiptir.
     */
    public static int partCount(int type) {
        switch (clamp(type)) {
            case L_GAS: return 4;
            case L_CAMP: return 7;
            case L_RADIO: return 3;
            case L_HOSPITAL: return 6;
            case L_MILITARY: return 14;
            case L_DAM: return 6;
            case L_AIRFIELD: return 9;
            default: return 1;
        }
    }

    /**
     * Mekânın i. parçası. out = {merkezX, merkezZ, yarımEn, yarımBoy,
     * yükseklik, tonIpucu}. Ton 0..1: 0 koyu beton, 1 açık metal.
     *
     * @param cx mekânın merkezi
     * @param cz mekânın merkezi
     */
    public static void partAt(int type, float cx, float cz, int i, float[] out) {
        switch (clamp(type)) {
            case L_GAS: gasPart(cx, cz, i, out); break;
            case L_CAMP: campPart(cx, cz, i, out); break;
            case L_RADIO: radioPart(cx, cz, i, out); break;
            case L_HOSPITAL: hospitalPart(cx, cz, i, out); break;
            case L_MILITARY: militaryPart(cx, cz, i, out); break;
            case L_DAM: damPart(cx, cz, i, out); break;
            default: airfieldPart(cx, cz, i, out); break;
        }
    }

    private static void box(float[] out, float x, float z, float hw, float hd,
                            float h, float tone) {
        out[0] = x;
        out[1] = z;
        out[2] = hw;
        out[3] = hd;
        out[4] = h;
        out[5] = tone;
    }

    /** Benzinlik: kanopi, iki pompa adası, küçük dükkân. */
    private static void gasPart(float cx, float cz, int i, float[] out) {
        switch (i) {
            case 0: box(out, cx, cz - 9f, 8f, 5f, 4.2f, 0.75f); break;   // dükkân
            case 1: box(out, cx, cz + 3f, 10f, 6f, 0.4f, 0.55f); break;  // kanopi tabanı
            case 2: box(out, cx - 4f, cz + 3f, 1.1f, 1.6f, 1.9f, 0.9f); break;
            default: box(out, cx + 4f, cz + 3f, 1.1f, 1.6f, 1.9f, 0.9f); break;
        }
    }

    /** Terk edilmiş kamp: çadırlar, sandıklar, sönmüş ateş. */
    private static void campPart(float cx, float cz, int i, float[] out) {
        if (i == 0) {
            box(out, cx, cz, 1.6f, 1.6f, 0.5f, 0.3f);      // ateş çukuru
            return;
        }
        float a = (float) (Math.PI * 2.0 * (i - 1) / 6.0);
        float r = 9f;
        float x = cx + (float) Math.cos(a) * r;
        float z = cz + (float) Math.sin(a) * r;
        boolean tent = (i % 2) == 1;
        if (tent) box(out, x, z, 2.6f, 2.2f, 2.3f, 0.45f);
        else box(out, x, z, 1.2f, 1.2f, 1.1f, 0.65f);
    }

    /** Radyo kulesi: uzaktan görünen yüksek direk ve kulübe. */
    private static void radioPart(float cx, float cz, int i, float[] out) {
        switch (i) {
            case 0: box(out, cx, cz, 1.5f, 1.5f, 34f, 0.95f); break;     // direk
            case 1: box(out, cx, cz, 4.5f, 4.5f, 1.2f, 0.6f); break;     // beton taban
            default: box(out, cx + 8f, cz + 5f, 3.2f, 2.6f, 3f, 0.7f); break;
        }
    }

    /** Hastane: ana blok, iki kanat, ambulans girişi. */
    private static void hospitalPart(float cx, float cz, int i, float[] out) {
        switch (i) {
            case 0: box(out, cx, cz, 16f, 10f, 13f, 0.85f); break;       // ana blok
            case 1: box(out, cx - 22f, cz, 8f, 7f, 8f, 0.8f); break;     // batı kanat
            case 2: box(out, cx + 22f, cz, 8f, 7f, 8f, 0.8f); break;     // doğu kanat
            case 3: box(out, cx, cz + 16f, 9f, 5f, 4f, 0.7f); break;     // ambulans girişi
            case 4: box(out, cx - 10f, cz - 18f, 3f, 3f, 2.4f, 0.5f); break;
            default: box(out, cx + 12f, cz - 18f, 3f, 3f, 2.4f, 0.5f); break;
        }
    }

    /**
     * Askeri üs: dört köşede kule, sıra sıra kışla, iki hangar, araç parkı.
     * Haritanın en zengin ve en tehlikeli yeri.
     */
    private static void militaryPart(float cx, float cz, int i, float[] out) {
        if (i < 4) {                                    // köşe kuleleri
            float sx = (i == 0 || i == 3) ? -1f : 1f;
            float sz = (i < 2) ? -1f : 1f;
            box(out, cx + sx * 52f, cz + sz * 52f, 3f, 3f, 11f, 0.6f);
            return;
        }
        if (i < 9) {                                    // kışla sırası
            float k = (i - 4) - 2f;
            box(out, cx - 20f, cz + k * 14f, 7f, 5f, 5.5f, 0.55f);
            return;
        }
        switch (i) {
            case 9: box(out, cx + 26f, cz - 20f, 14f, 10f, 9f, 0.7f); break;   // hangar 1
            case 10: box(out, cx + 26f, cz + 14f, 14f, 10f, 9f, 0.7f); break;  // hangar 2
            case 11: box(out, cx, cz - 44f, 24f, 3f, 3.2f, 0.4f); break;       // ana kapı seddi
            case 12: box(out, cx + 4f, cz + 40f, 10f, 6f, 1.6f, 0.45f); break; // araç parkı
            default: box(out, cx - 44f, cz + 8f, 5f, 12f, 6.5f, 0.65f); break; // cephanelik
        }
    }

    /** Baraj: uzun beton gövde ve santral binası. */
    private static void damPart(float cx, float cz, int i, float[] out) {
        switch (i) {
            case 0: box(out, cx, cz, 60f, 7f, 22f, 0.8f); break;          // gövde
            case 1: box(out, cx - 34f, cz + 18f, 10f, 8f, 9f, 0.7f); break;
            case 2: box(out, cx + 34f, cz + 18f, 10f, 8f, 9f, 0.7f); break;
            case 3: box(out, cx, cz + 24f, 8f, 6f, 6f, 0.6f); break;      // santral
            case 4: box(out, cx - 58f, cz, 5f, 10f, 14f, 0.75f); break;
            default: box(out, cx + 58f, cz, 5f, 10f, 14f, 0.75f); break;
        }
    }

    /** Havaalanı: pist, iki hangar, kule, terminal. */
    private static void airfieldPart(float cx, float cz, int i, float[] out) {
        switch (i) {
            case 0: box(out, cx, cz, 78f, 9f, 0.3f, 0.35f); break;        // pist
            case 1: box(out, cx - 40f, cz + 30f, 18f, 13f, 11f, 0.7f); break;
            case 2: box(out, cx + 4f, cz + 30f, 18f, 13f, 11f, 0.7f); break;
            case 3: box(out, cx + 44f, cz + 28f, 6f, 6f, 20f, 0.85f); break;  // kule
            case 4: box(out, cx - 10f, cz - 30f, 22f, 9f, 7f, 0.75f); break;  // terminal
            case 5: box(out, cx + 30f, cz - 28f, 7f, 5f, 3f, 0.5f); break;
            case 6: box(out, cx - 60f, cz - 20f, 4f, 4f, 2.5f, 0.45f); break;
            case 7: box(out, cx + 62f, cz + 8f, 4f, 4f, 2.5f, 0.45f); break;
            default: box(out, cx - 44f, cz - 6f, 9f, 4f, 1.4f, 0.4f); break;  // uçak enkazı
        }
    }

    // ---- çarpışma ve yağma ----------------------------------------------

    /**
     * Bu nokta bir mekânın binasının içinde mi? Pist ve kanopi tabanı gibi
     * alçak parçalar engel sayılmaz — üstünden yürünür.
     */
    public static boolean blocked(float x, float z, float radius) {
        float[] c = scratchNear;
        float d = nearest(x, z, c);
        if (d < 0f || d > c[2] + 8f) return false;

        int type = (int) c[3];
        float[] part = scratchBlocked;
        int n = partCount(type);
        for (int i = 0; i < n; i++) {
            partAt(type, c[0], c[1], i, part);
            if (part[4] < 1.2f) continue;              // alçak zemin, engel değil
            if (Math.abs(x - part[0]) < part[2] + radius
                    && Math.abs(z - part[1]) < part[3] + radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mekândaki i. sandığın yeri. Sandıklar binaların önünde durur; sayıları
     * mekânın zenginliğine göre değişir. Bulunursa out = {x, z} ve true.
     */
    public static boolean lootAt(int type, float cx, float cz, int i, float[] out) {
        int n = partCount(type);
        if (i < 0 || i >= n) return false;
        int key = (int) cx * 31 + (int) cz * 17 + i;
        if (WorldGen.rand01(key, i, 606) > TYPE_LOOT[clamp(type)]) return false;
        float[] part = scratchLoot;
        partAt(type, cx, cz, i, part);
        if (part[4] < 1.2f) return false;             // zeminde sandık olmaz
        out[0] = part[0] + part[2] + 1.8f;
        out[1] = part[1];
        return true;
    }

    /** Mekân türüne göre sandıktan çıkacak hurda miktarının çarpanı. */
    public static float lootRichness(int type) {
        return 0.6f + TYPE_LOOT[clamp(type)] * 1.6f;
    }
}
