package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/**
 * Dünyanın tehdidi. Dalga sistemi yok: evre, geri sayım, doğma kapısı ve
 * "dalga temizlendi" ekranı kaldırıldı. Yerine açık dünyaya yakışan tek bir
 * kural kondu:
 *
 * <ul>
 *   <li>Gündüz dünya nispeten sakindir; keşfe çıkar, kaynak toplarsın.</li>
 *   <li>Gece çöktüğünde kampın kokusunu alan zombiler üsse yürümeye başlar.
 *       Bunlar bir "dalga" değil; gece boyunca damla damla gelirler ve şafakla
 *       birlikte kesilir.</li>
 *   <li>Her yedinci gece <b>kanlı ay</b>: çok daha kalabalık ve sert.</li>
 *   <li>Baskının büyüklüğü hayatta kalınan güne göre artar.</li>
 * </ul>
 */
public class Threat {

    /** Baskıncıların doğduğu halkanın sur hattından uzaklığı. */
    private static final float SPAWN_MARGIN = 26f;
    /** Aynı anda üsse yürüyebilecek azami baskıncı. */
    private static final int LIVE_CAP = 34;

    /** Bu gece üsse gelmesi planlanan toplam zombi. */
    public int plannedTonight;
    /** Bu gece şu ana kadar gönderilen. */
    public int sentTonight;
    /** Şu an kaç baskıncı sahada (arayüz için). */
    public int liveRaiders;
    /** Bu gece kanlı ay mı? */
    public boolean bloodMoon;
    /** Gece baskını sürüyor mu? */
    public boolean raiding;
    /** Baskının kaçta kaçı geldi (0..1) — arayüzdeki gece çubuğu. */
    public float progress;

    private float spawnTimer;
    private int lastNightDay = -1;

    public void reset() {
        plannedTonight = 0;
        sentTonight = 0;
        liveRaiders = 0;
        bloodMoon = false;
        raiding = false;
        progress = 0f;
        spawnTimer = 0f;
        lastNightDay = -1;
    }

    public void update(GameWorld w, float dt) {
        liveRaiders = countRaiders(w);

        boolean night = w.isNight();
        if (night && !raiding) startNight(w);
        if (!night && raiding) endNight(w);
        if (!raiding) {
            progress = 0f;
            return;
        }

        progress = plannedTonight <= 0 ? 1f
                : MathX.clamp((float) sentTonight / plannedTonight, 0f, 1f);
        if (sentTonight >= plannedTonight || liveRaiders >= LIVE_CAP) return;

        spawnTimer -= dt;
        if (spawnTimer > 0f) return;
        // Gece boyunca eşit aralıkla damlasınlar; kanlı ayda çok daha sık.
        spawnTimer = (bloodMoon ? 1.1f : 2.6f) * MathX.rnd(0.7f, 1.3f);
        sendRaider(w);
    }

    private void startNight(GameWorld w) {
        if (w.dayCount == lastNightDay) return;      // aynı geceyi iki kez açma
        lastNightDay = w.dayCount;
        raiding = true;
        sentTonight = 0;
        bloodMoon = Balance.isBloodMoon(w.dayCount);
        plannedTonight = Balance.raidSize(w.dayCount, bloodMoon);
        spawnTimer = 3f;
        if (bloodMoon) {
            w.big("KANLI AY — kamp bu gece çok kalabalık bir baskın alacak", 3.4f);
        } else {
            w.big("Gece çöktü — zombiler kampın kokusunu aldı", 2.6f);
        }
    }

    private void endNight(GameWorld w) {
        raiding = false;
        progress = 0f;
        w.onNightSurvived();
    }

    /** Üsse yürüyecek tek bir zombi gönderir. */
    private void sendRaider(GameWorld w) {
        float radius = Math.max(Balance.SPAWN_RADIUS, w.baseRadius + SPAWN_MARGIN);
        for (int attempt = 0; attempt < 8; attempt++) {
            float a = MathX.rnd(0f, MathX.TAU);
            float r = radius * MathX.rnd(1f, 1.22f);
            float x = (float) Math.sin(a) * r, z = (float) Math.cos(a) * r;
            if (WorldGen.blocked(x, z, 1.4f)) continue;
            w.spawnRaider(pickType(w), x, z, bloodMoon && MathX.chance(0.28f));
            sentTonight++;
            return;
        }
    }

    /** Gün ilerledikçe daha sert türler karışır. */
    private int pickType(GameWorld w) {
        int day = w.dayCount;
        float roll = MathX.rnd();
        if (day >= 12 && roll < 0.12f) return Balance.Z_BRUTE;
        if (day >= 8 && roll < 0.26f) return Balance.Z_SPITTER;
        if (day >= 4 && roll < 0.44f) return Balance.Z_RUNNER;
        return Balance.Z_WALKER;
    }

    private static int countRaiders(GameWorld w) {
        int n = 0;
        for (int i = 0; i < w.zombies.size(); i++) {
            Zombie z = w.zombies.get(i);
            if (z.alive && !z.roamer) n++;
        }
        return n;
    }

    /** Arayüzde gösterilecek kısa durum metni. */
    public String statusText(GameWorld w) {
        if (raiding) {
            return (bloodMoon ? "KANLI AY " : "GECE BASKINI ")
                    + liveRaiders + " zombi";
        }
        int mins = (int) (w.timeUntilDusk() / 60f);
        int secs = (int) (w.timeUntilDusk() % 60f);
        return String.format(java.util.Locale.US, "Gün batımına %d:%02d", mins, secs);
    }
}
