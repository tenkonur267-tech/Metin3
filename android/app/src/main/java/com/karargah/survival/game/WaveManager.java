package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

import java.util.ArrayList;

/** Dalga akışı: hazırlık süresi, zombi kadrosu, doğma noktaları ve ödüller. */
public class WaveManager {
    public static final int PHASE_PREPARE = 0;
    public static final int PHASE_WAVE = 1;
    public static final int PHASE_CLEARED = 2;

    public int wave = 0;
    public int phase = PHASE_PREPARE;
    public float timer = Balance.buildTime(1);
    public float phaseTime;

    public int totalToSpawn;
    public int spawnedCount;
    /** Dalga aşırı uzarsa kalan zombiler hızlanıp doğrudan reaktöre yürür. */
    public boolean rage;
    private float waveElapsed;
    private float spawnTimer;
    private final ArrayList<Integer> queue = new ArrayList<>();
    private int burst = 3;

    public int activeSpawnPoints = 3;
    public static final int MAX_SPAWN_POINTS = 6;
    public final float[] spawnX = new float[MAX_SPAWN_POINTS];
    public final float[] spawnZ = new float[MAX_SPAWN_POINTS];
    public final float[] spawnGlow = new float[MAX_SPAWN_POINTS];

    public WaveManager() {
        for (int i = 0; i < MAX_SPAWN_POINTS; i++) {
            float a = (MathX.TAU * i) / MAX_SPAWN_POINTS + 0.35f;
            spawnX[i] = (float) Math.sin(a) * Balance.SPAWN_RADIUS;
            spawnZ[i] = (float) Math.cos(a) * Balance.SPAWN_RADIUS;
        }
    }

    public void reset() {
        wave = 0;
        phase = PHASE_PREPARE;
        timer = Balance.buildTime(1);
        queue.clear();
        spawnedCount = 0;
        totalToSpawn = 0;
        rage = false;
        waveElapsed = 0f;
        activeSpawnPoints = 3;
    }

    public boolean isPrepare() {
        return phase == PHASE_PREPARE;
    }

    /** Hazırlık aşamasını erken bitirir. */
    public void skipPrepare() {
        if (phase == PHASE_PREPARE) timer = 0.05f;
    }

    public void update(GameWorld w, float dt) {
        phaseTime += dt;
        for (int i = 0; i < MAX_SPAWN_POINTS; i++) {
            float target = (phase == PHASE_WAVE && i < activeSpawnPoints) ? 1f : 0.15f;
            spawnGlow[i] = MathX.damp(spawnGlow[i], target, 2.5f, dt);
        }

        switch (phase) {
            case PHASE_PREPARE:
                timer -= dt;
                if (timer <= 0f) startWave(w);
                break;
            case PHASE_WAVE:
                waveElapsed += dt;
                updateSpawning(w, dt);
                boolean dragging = waveElapsed > 55f
                        || (w.zombies.size() <= 3 && waveElapsed > 26f);
                if (!rage && queue.isEmpty() && dragging && !w.zombies.isEmpty()) {
                    rage = true;
                    w.big("Kalan zombiler çıldırdı — doğrudan reaktöre geliyorlar!", 3.2f);
                }
                if (queue.isEmpty() && w.zombies.isEmpty()) {
                    finishWave(w);
                }
                break;
            case PHASE_CLEARED:
                timer -= dt;
                if (timer <= 0f) {
                    phase = PHASE_PREPARE;
                    phaseTime = 0f;
                    timer = Balance.buildTime(wave + 1);
                    w.onPrepareStarted();
                }
                break;
            default:
                break;
        }
    }

    private void startWave(GameWorld w) {
        wave++;
        phase = PHASE_WAVE;
        phaseTime = 0f;
        activeSpawnPoints = MathX.clampI(2 + (wave + 2) / 3, 3, MAX_SPAWN_POINTS);
        buildQueue(wave);
        rage = false;
        waveElapsed = 0f;
        totalToSpawn = queue.size();
        spawnedCount = 0;
        spawnTimer = 1.2f;
        burst = Math.max(2, 2 + wave / 4);
        w.onWaveStarted(wave);
    }

    private void finishWave(GameWorld w) {
        phase = PHASE_CLEARED;
        phaseTime = 0f;
        timer = 3.2f;
        w.onWaveCleared(wave);
    }

    private void updateSpawning(GameWorld w, float dt) {
        if (queue.isEmpty()) return;
        spawnTimer -= dt;
        if (spawnTimer > 0f) return;
        // Ekranda çok fazla zombi birikmesin (performans için üst sınır).
        if (w.zombies.size() > 90) {
            spawnTimer = 0.6f;
            return;
        }
        int n = Math.min(burst, queue.size());
        for (int i = 0; i < n; i++) {
            int type = queue.remove(queue.size() - 1);
            int sp = MathX.rndInt(activeSpawnPoints);
            float ang = MathX.rnd(0f, MathX.TAU);
            float rad = MathX.rnd(0f, type == Balance.Z_BOSS ? 1.5f : 4.5f);
            float px = spawnX[sp] + (float) Math.cos(ang) * rad;
            float pz = spawnZ[sp] + (float) Math.sin(ang) * rad;
            boolean elite = type != Balance.Z_BOSS && MathX.chance(eliteChance(wave));
            w.spawnZombie(type, px, pz, elite);
            spawnedCount++;
        }
        spawnTimer = MathX.rnd(1.1f, 2.2f) * (wave > 12 ? 0.75f : 1f);
    }

    private static float eliteChance(int wave) {
        return MathX.clamp((wave - 4) * 0.022f, 0f, 0.3f);
    }

    /** Dalganın zombi kadrosunu bütçeye göre kurar. */
    private void buildQueue(int wave) {
        queue.clear();
        int budget = Balance.waveBudget(wave);

        if (Balance.isBossWave(wave)) {
            int bosses = 1 + (wave >= 20 ? 1 : 0) + (wave >= 35 ? 1 : 0);
            for (int i = 0; i < bosses; i++) {
                queue.add(Balance.Z_BOSS);
                budget -= Balance.zombie(Balance.Z_BOSS).budget;
            }
        }

        int guard = 0;
        while (budget > 0 && guard++ < 400) {
            int type = pickType(wave);
            int cost = Balance.zombie(type).budget;
            if (cost > budget && queue.size() > 2) break;
            queue.add(type);
            budget -= cost;
        }
        // karıştır
        for (int i = queue.size() - 1; i > 0; i--) {
            int j = MathX.rndInt(i + 1);
            int t = queue.get(i);
            queue.set(i, queue.get(j));
            queue.set(j, t);
        }
    }

    private int pickType(int wave) {
        float r = MathX.rnd();
        float walker = 0.55f;
        float crawler = wave >= 2 ? 0.16f : 0.30f;
        float runner = wave >= 3 ? MathX.clamp(0.10f + wave * 0.012f, 0f, 0.30f) : 0f;
        float spitter = wave >= 4 ? MathX.clamp(0.05f + wave * 0.008f, 0f, 0.18f) : 0f;
        float brute = wave >= 5 ? MathX.clamp(0.04f + wave * 0.010f, 0f, 0.22f) : 0f;
        float total = walker + crawler + runner + spitter + brute;
        r *= total;
        if ((r -= walker) < 0) return Balance.Z_WALKER;
        if ((r -= crawler) < 0) return Balance.Z_CRAWLER;
        if ((r -= runner) < 0) return Balance.Z_RUNNER;
        if ((r -= spitter) < 0) return Balance.Z_SPITTER;
        return Balance.Z_BRUTE;
    }

    public int remaining(GameWorld w) {
        return queue.size() + w.zombies.size();
    }

    /** Kaydetme/yükleme için sıradaki zombiler. */
    public ArrayList<Integer> queueRef() {
        return queue;
    }

    public void restoreQueue(ArrayList<Integer> saved) {
        queue.clear();
        queue.addAll(saved);
    }
}
