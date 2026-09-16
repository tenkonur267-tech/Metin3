package com.karargah.survival.game;

/**
 * Açık dünyanın üreteci. 100.000 x 100.000 birimlik harita hiçbir yerde
 * saklanmaz; her nokta yalnızca koordinatından hesaplanır. Aynı koordinat her
 * zaman aynı sonucu verir, dolayısıyla dünya sonsuz büyüklükte olmasına rağmen
 * bellekte yer kaplamaz ve kaydedilmesi gerekmez.
 *
 * Sağladıkları:
 *   - biyom haritası (ova, orman, bataklık, çöl, tundra, harabe, şehir)
 *   - şehirler: kafes üzerine serpilmiş merkezler, sokak ızgarası, binalar
 *   - bina çarpışması (binaların içinden geçilemez)
 *   - zombi yoğunluğu: şehir ve harabelerde çok, çölde/tundrada az
 *   - yağmalanacak sandıkların yerleri
 */
public final class WorldGen {

    private WorldGen() {
    }

    // ---- biyomlar -------------------------------------------------------

    public static final int B_PLAIN = 0;
    public static final int B_FOREST = 1;
    public static final int B_SWAMP = 2;
    public static final int B_DESERT = 3;
    public static final int B_TUNDRA = 4;
    public static final int B_RUINS = 5;
    public static final int B_CITY = 6;
    public static final int BIOME_COUNT = 7;

    private static final String[] BIOME_NAMES = {
            "Ova", "Orman", "Bataklık", "Çöl", "Tundra", "Harabe", "Şehir"
    };

    /** Zemin rengi (gündüz). */
    private static final int[] BIOME_GROUND = {
            0x6E7A4A, 0x455A34, 0x44503A, 0xBFA871, 0xC8CDD2, 0x6A655C, 0x5C5C5E
    };

    /** Biyomun kendi zombi yoğunluğu (0..1). */
    private static final float[] BIOME_DENSITY = {
            0.12f, 0.18f, 0.30f, 0.07f, 0.08f, 0.55f, 0.75f
    };

    public static String biomeName(int biome) {
        return BIOME_NAMES[clampBiome(biome)];
    }

    public static int groundColor(int biome) {
        return BIOME_GROUND[clampBiome(biome)];
    }

    private static int clampBiome(int b) {
        return b < 0 ? 0 : (b >= BIOME_COUNT ? BIOME_COUNT - 1 : b);
    }

    // ---- gürültü --------------------------------------------------------

    private static final int SEED = 0x5F3A91;

    /** Tamsayı karıştırıcı: aynı girdi her zaman aynı 32 bit çıktıyı verir. */
    public static int hash(int x, int y, int salt) {
        int h = x * 0x27D4EB2D ^ y * 0x165667B1 ^ (SEED + salt) * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    /** 0..1 arası deterministik sayı. */
    public static float rand01(int x, int y, int salt) {
        return (hash(x, y, salt) >>> 8) * (1f / 16777216f);
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    /** Değer gürültüsü: verilen ölçekte yumuşak 0..1 alanı. */
    public static float noise(float x, float z, float scale, int salt) {
        float fx = x / scale, fz = z / scale;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
        float tx = smooth(fx - x0), tz = smooth(fz - z0);
        float a = rand01(x0, z0, salt);
        float b = rand01(x0 + 1, z0, salt);
        float c = rand01(x0, z0 + 1, salt);
        float d = rand01(x0 + 1, z0 + 1, salt);
        float top = a + (b - a) * tx;
        float bot = c + (d - c) * tx;
        return top + (bot - top) * tz;
    }

    /** Üç katmanlı gürültü: büyük yapıyı koruyup ayrıntı ekler. */
    public static float fbm(float x, float z, float scale, int salt) {
        float v = noise(x, z, scale, salt) * 0.6f;
        v += noise(x, z, scale * 0.42f, salt + 11) * 0.27f;
        v += noise(x, z, scale * 0.17f, salt + 23) * 0.13f;
        return v;
    }

    // ---- şehirler -------------------------------------------------------

    /** Şehir merkezlerinin serpildiği kafesin adımı. */
    public static final float CITY_SPACING = 1400f;
    /** Üssün etrafındaki bu yarıçapta şehir kurulmaz (başlangıç alanı açık kalsın). */
    public static final float HOME_CLEAR = 300f;

    private static final float BLOCK = 30f;    // ada boyu
    private static final float STREET = 11f;   // sokak genişliği

    // Her kare binlerce kez çağrıldıkları için bu yardımcılar çöp üretmez;
    // her fonksiyonun kendi tamponu var ki iç içe çağrılar birbirini bozmasın.
    private static final float[] scratchCity = new float[3];
    private static final float[] scratchStrength = new float[3];
    private static final float[] scratchBuilding = new float[3];
    private static final float[] scratchBlocked = new float[5];
    private static final float[] scratchLoot = new float[5];

    /** Kafes hücresinde şehir var mı? Varsa merkezi out[0],out[1]; yarıçapı out[2]. */
    private static boolean cityCell(int ci, int cj, float[] out) {
        if (rand01(ci, cj, 101) > 0.45f) return false;
        float jx = (rand01(ci, cj, 102) - 0.5f) * CITY_SPACING * 0.62f;
        float jz = (rand01(ci, cj, 103) - 0.5f) * CITY_SPACING * 0.62f;
        float cx = (ci + 0.5f) * CITY_SPACING + jx;
        float cz = (cj + 0.5f) * CITY_SPACING + jz;
        float radius = 150f + rand01(ci, cj, 104) * 330f;
        if (Math.abs(cx) > Balance.WORLD_HALF || Math.abs(cz) > Balance.WORLD_HALF) return false;
        // Üssün dibinde şehir olmasın
        if (cx * cx + cz * cz < (HOME_CLEAR + radius) * (HOME_CLEAR + radius)) return false;
        out[0] = cx;
        out[1] = cz;
        out[2] = radius;
        return true;
    }

    /**
     * Noktaya en yakın şehri bulur. Bulursa out[0..2] = merkez x, z, yarıçap ve
     * dönüş değeri merkeze uzaklık; yoksa -1.
     */
    public static float nearestCity(float x, float z, float[] out) {
        int ci = (int) Math.floor(x / CITY_SPACING);
        int cj = (int) Math.floor(z / CITY_SPACING);
        float bestD = -1f;
        float[] tmp = scratchCity;
        for (int j = cj - 1; j <= cj + 1; j++) {
            for (int i = ci - 1; i <= ci + 1; i++) {
                if (!cityCell(i, j, tmp)) continue;
                float dx = x - tmp[0], dz = z - tmp[1];
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (bestD < 0f || d < bestD) {
                    bestD = d;
                    out[0] = tmp[0];
                    out[1] = tmp[1];
                    out[2] = tmp[2];
                }
            }
        }
        return bestD;
    }

    /** Şehir etkisi: merkezde 1, kenarda 0. Şehir yoksa 0. */
    public static float cityStrength(float x, float z) {
        float[] c = scratchStrength;
        float d = nearestCity(x, z, c);
        if (d < 0f) return 0f;
        float r = c[2];
        if (d >= r) return 0f;
        // Kenarda yumuşak geçiş (varoşlar)
        float t = 1f - d / r;
        return smooth(Math.min(1f, t * 1.35f));
    }

    // ---- binalar --------------------------------------------------------

    /**
     * Noktanın hangi ada (blok) içinde kaldığını ve o adada bina olup
     * olmadığını çözer. Bina varsa out = {merkezX, merkezZ, yarımEn, yarımBoy,
     * yükseklik}; yoksa false.
     */
    public static boolean buildingAt(float x, float z, float[] out) {
        float[] c = scratchBuilding;
        float d = nearestCity(x, z, c);
        if (d < 0f || d >= c[2]) return false;

        int bx = (int) Math.floor((x - c[0]) / BLOCK);
        int bz = (int) Math.floor((z - c[1]) / BLOCK);
        return blockBuilding(c, bx, bz, out);
    }

    /** Şehir c'nin (bx,bz) adasındaki bina. Yoksa false. */
    public static boolean blockBuilding(float[] c, int bx, int bz, float[] out) {
        int sx = (int) c[0], sz = (int) c[1];
        float roll = rand01(bx * 73 + sx, bz * 91 + sz, 205);
        if (roll < 0.22f) return false;                 // boş arsa / meydan

        float ox = c[0] + (bx + 0.5f) * BLOCK;
        float oz = c[1] + (bz + 0.5f) * BLOCK;
        // Merkeze uzaklık binanın boyunu belirler: merkezde gökdelen, kenarda ev
        float dx = ox - c[0], dz = oz - c[1];
        float rel = (float) Math.sqrt(dx * dx + dz * dz) / Math.max(1f, c[2]);
        float half = (BLOCK - STREET) * 0.5f - 1.2f;
        float hw = half * (0.62f + rand01(bx + sx, bz - sz, 206) * 0.38f);
        float hd = half * (0.62f + rand01(bx - sx, bz + sz, 207) * 0.38f);
        float h = (4f + rand01(bx + 7, bz + 13, 208) * 9f) * (1.9f - rel);
        out[0] = ox;
        out[1] = oz;
        out[2] = hw;
        out[3] = hd;
        out[4] = Math.max(3.5f, h);
        return true;
    }

    /** Bu nokta bir binanın içinde mi? (çarpışma için) */
    public static boolean blocked(float x, float z) {
        float[] b = new float[5];
        if (!buildingAt(x, z, b)) return false;
        return Math.abs(x - b[0]) < b[2] && Math.abs(z - b[1]) < b[3];
    }

    /** Verilen yarıçaptaki bir gövde bu noktada binaya giriyor mu? */
    public static boolean blocked(float x, float z, float radius) {
        float[] b = scratchBlocked;
        if (!buildingAt(x, z, b)) return false;
        return Math.abs(x - b[0]) < b[2] + radius && Math.abs(z - b[1]) < b[3] + radius;
    }

    // ---- biyom seçimi ---------------------------------------------------

    /** Harabe alanları: seyrek ama zombi kaynayan bölgeler. */
    public static float ruinStrength(float x, float z) {
        float v = fbm(x, z, 620f, 41);
        if (v < 0.70f) return 0f;
        return Math.min(1f, (v - 0.70f) / 0.16f);
    }

    public static int biomeAt(float x, float z) {
        if (cityStrength(x, z) > 0.42f) return B_CITY;
        if (ruinStrength(x, z) > 0.45f) return B_RUINS;

        float temp = fbm(x, z, 1500f, 7);
        float moist = fbm(x + 4200f, z - 3100f, 1100f, 19);
        if (temp < 0.34f) return B_TUNDRA;
        if (temp > 0.63f && moist < 0.46f) return B_DESERT;
        if (moist > 0.70f) return B_SWAMP;
        if (moist > 0.47f) return B_FOREST;
        return B_PLAIN;
    }

    // ---- zombi yoğunluğu ------------------------------------------------

    /**
     * Bu noktada ne kadar zombi olmalı (0..1). Şehirler ve harabeler sıcak
     * nokta; çöl ve tundra neredeyse boş. Gece her yerde artar.
     */
    public static float zombieDensity(float x, float z) {
        int b = biomeAt(x, z);
        float d = BIOME_DENSITY[b];
        float city = cityStrength(x, z);
        if (city > 0f) d = Math.max(d, 0.30f + city * 0.60f);
        float ruin = ruinStrength(x, z);
        if (ruin > 0f) d = Math.max(d, 0.26f + ruin * 0.48f);
        // Yerel dalgalanma: aynı biyomda bile boş ve kalabalık cepler var
        d *= 0.55f + fbm(x, z, 190f, 67) * 0.9f;
        return d < 0f ? 0f : (d > 1f ? 1f : d);
    }

    /** Sıcak nokta mı (arayüzde uyarı vermek için)? */
    public static boolean isHotspot(float x, float z) {
        return zombieDensity(x, z) > 0.55f;
    }

    // ---- yağma ----------------------------------------------------------

    /**
     * Bu adada yağmalanacak bir sandık var mı? Varsa out = {x, z}. Sandıklar
     * yalnızca binaların olduğu adalarda, binanın kapısının önünde durur.
     */
    public static boolean lootAt(float[] city, int bx, int bz, float[] out) {
        float[] b = scratchLoot;
        if (!blockBuilding(city, bx, bz, b)) return false;
        int sx = (int) city[0], sz = (int) city[1];
        if (rand01(bx * 31 + sx, bz * 17 - sz, 311) > 0.42f) return false;
        out[0] = b[0] + b[2] + 1.6f;
        out[1] = b[1];
        return true;
    }

    // ---- bitki örtüsü ---------------------------------------------------

    public static final int PROP_NONE = -1;
    public static final int PROP_ROCK = 0;
    public static final int PROP_TREE = 1;
    public static final int PROP_GRASS = 2;
    public static final int PROP_BARREL = 3;
    public static final int PROP_CRATE = 4;

    /**
     * (px,pz) serpme noktasında hangi süs nesnesi var? Biyoma göre değişir;
     * hiç yoksa PROP_NONE.
     */
    public static int propAt(int px, int pz, float wx, float wz) {
        int biome = biomeAt(wx, wz);
        float roll = rand01(px, pz, 401);
        float chance;
        switch (biome) {
            case B_FOREST: chance = 0.62f; break;
            case B_SWAMP: chance = 0.46f; break;
            case B_PLAIN: chance = 0.34f; break;
            case B_TUNDRA: chance = 0.22f; break;
            case B_DESERT: chance = 0.16f; break;
            case B_RUINS: chance = 0.44f; break;
            default: chance = 0.14f; break;       // şehirde sokaklar boş
        }
        if (roll > chance) return PROP_NONE;
        float kind = rand01(px, pz, 402);
        switch (biome) {
            case B_FOREST:
                return kind < 0.74f ? PROP_TREE : (kind < 0.92f ? PROP_GRASS : PROP_ROCK);
            case B_SWAMP:
                return kind < 0.48f ? PROP_TREE : PROP_GRASS;
            case B_DESERT:
                return kind < 0.72f ? PROP_ROCK : PROP_GRASS;
            case B_TUNDRA:
                return kind < 0.62f ? PROP_ROCK : PROP_TREE;
            case B_RUINS:
                return kind < 0.42f ? PROP_ROCK : (kind < 0.72f ? PROP_BARREL : PROP_CRATE);
            case B_CITY:
                return kind < 0.5f ? PROP_BARREL : PROP_CRATE;
            default:
                return kind < 0.33f ? PROP_GRASS : (kind < 0.68f ? PROP_ROCK : PROP_TREE);
        }
    }
}
