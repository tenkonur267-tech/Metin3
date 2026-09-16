package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/**
 * 100.000 x 100.000 birimlik açık dünyanın hafif, deterministik katmanı.
 * Dünya bütünüyle belleğe alınmaz; biyom, şehir ve tehlike bilgisi koordinattan
 * hesaplanır. Böylece düşük bellekli telefonlarda da aynı dünya üretilebilir.
 */
public final class OpenWorld {
    public static final int PLAINS = 0, FOREST = 1, DESERT = 2, SNOW = 3, SWAMP = 4;
    public static final String[] BIOME_NAMES = {
            "Bozkır", "Kara Orman", "Kızıl Çöl", "Donmuş Kuzey", "Zehirli Bataklık"
    };
    /** Elle seçilmiş büyük yerleşimler; koordinatlar bütün kayıtlarda sabittir. */
    public static final float[][] CITIES = {
            {900f, 650f}, {-1450f, 820f}, {2100f, -1750f},
            {-2800f, -2300f}, {4200f, 3100f}, {-6100f, 4700f},
            {8500f, -7200f}, {-12000f, -9400f}
    };
    public static final String[] CITY_NAMES = {
            "Güvenli Liman", "Eski Sanayi", "Kızıl Vadi", "Batık Şehir",
            "Kuzey Karakolu", "Sis Kasabası", "Son İstasyon", "Kayıp Başkent"
    };

    /** Bir oyun günü 18 gerçek dakika. */
    public static final float DAY_SECONDS = 18f * 60f;
    public float timeOfDay = 0.30f;
    private float ambientTimer = 8f;

    public void reset() {
        timeOfDay = 0.30f;
        ambientTimer = 8f;
    }

    public void update(GameWorld w, float dt) {
        timeOfDay = (timeOfDay + dt / DAY_SECONDS) % 1f;
        ambientTimer -= dt;
        if (!w.started || ambientTimer > 0f || w.gameOver) return;
        ambientTimer = 2.2f;

        // Dalga zombilerine ek olarak açık dünyada oyuncu çevresinde yaşayan
        // nüfus. Şehir, bataklık ve gece daha tehlikelidir.
        int cap = 34 + Math.min(36, w.waves.wave * 2);
        if (w.zombies.size() >= cap) return;
        float density = dangerAt(w.player.x, w.player.z);
        if (MathX.rnd() > density) return;
        float a = MathX.rnd(0f, MathX.TAU);
        float d = MathX.rnd(32f, 62f);
        float x = clampWorld(w.player.x + (float) Math.cos(a) * d);
        float z = clampWorld(w.player.z + (float) Math.sin(a) * d);
        int type = MathX.rnd() < 0.68f ? Balance.Z_WALKER
                : Math.min(Balance.ZOMBIES.length - 1, MathX.rndInt(Balance.ZOMBIES.length));
        w.spawnZombie(type, x, z, density > 0.82f && MathX.chance(0.18f));
    }

    public float night() {
        // gün doğumu 0.22, gün batımı 0.72; yumuşak alacakaranlık
        float daylight = (float) Math.sin((timeOfDay - 0.22f) * MathX.TAU);
        return MathX.clamp(0.52f - daylight * 0.72f, 0f, 1f);
    }

    public int biomeAt(float x, float z) {
        float macro = MathX.smoothNoise(x * 0.00022f + 17f, z * 0.00022f - 9f);
        float latitude = z / Balance.WORLD_HALF;
        if (latitude > 0.48f || (latitude > 0.25f && macro > 0.63f)) return SNOW;
        if (x > 9000f && macro > 0.42f) return DESERT;
        if (x < -7000f && macro < 0.48f) return SWAMP;
        if (macro > 0.57f) return FOREST;
        return PLAINS;
    }

    public float dangerAt(float x, float z) {
        float danger = 0.18f;
        int b = biomeAt(x, z);
        if (b == FOREST) danger += 0.12f;
        if (b == SWAMP) danger += 0.24f;
        if (b == SNOW) danger += 0.10f;
        for (int i = 0; i < CITIES.length; i++) {
            float d = MathX.dist(x, z, CITIES[i][0], CITIES[i][1]);
            if (d < 260f) danger += (1f - d / 260f) * 0.62f;
        }
        danger += night() * 0.30f;
        return MathX.clamp(danger, 0.08f, 0.96f);
    }

    public String regionName(float x, float z) {
        for (int i = 0; i < CITIES.length; i++) {
            if (MathX.dist(x, z, CITIES[i][0], CITIES[i][1]) < 230f) return CITY_NAMES[i];
        }
        return BIOME_NAMES[biomeAt(x, z)];
    }

    public static float clampWorld(float v) {
        return MathX.clamp(v, -Balance.WORLD_HALF + 2f, Balance.WORLD_HALF - 2f);
    }
}
