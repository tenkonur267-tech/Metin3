package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/** Yere düşen ganimet: hurda yığını ya da enerji çekirdeği. */
public class Pickup {
    public static final int SCRAP = 0;
    public static final int CORE = 1;
    /** Şehirlerden yağmalanan konserve. */
    public static final int FOOD = 2;
    /** Şehirlerden yağmalanan su. */
    public static final int WATER = 3;

    public int kind;
    public int amount;
    public float x, y, z;
    public float vx, vy, vz;
    public float spin;
    public float life;
    public float bob;
    public boolean alive;
    public boolean resting;
    /** 0 = yerde, 1 = toplayıcıya doğru çekiliyor. */
    public float magnet;
    /** Bu yığına giden yoldaş (başkası aynı yığına koşmasın). */
    public Npc claimedBy;
    /**
     * Ulaşılamadığı anlaşıldığında bir süre kimse denemesin (ör. duvarın
     * üstüne düşmüş yığın). Sıfıra inince yeniden denenir.
     */
    public float unreachable;

    /** Yalnızca oyuncunun toplayabileceği tür mü (yiyecek, su)? */
    public boolean playerOnly() {
        return kind == FOOD || kind == WATER;
    }

    public void init(int kind, int amount, float x, float z) {
        this.kind = kind;
        this.amount = amount;
        this.x = x;
        this.z = z;
        this.y = 0.9f;
        float a = MathX.rnd(0f, MathX.TAU);
        float sp = MathX.rnd(0.8f, 3.2f);
        this.vx = (float) Math.cos(a) * sp;
        this.vz = (float) Math.sin(a) * sp;
        this.vy = MathX.rnd(2.2f, 4.6f);
        this.spin = MathX.rnd(0f, MathX.TAU);
        this.life = Balance.PICKUP_LIFE;
        this.alive = true;
        this.resting = false;
        this.magnet = 0f;
        this.claimedBy = null;
        this.unreachable = 0f;
        this.bob = MathX.rnd(0f, MathX.TAU);
    }

    public void update(GameWorld w, float dt) {
        if (!alive) return;
        if (unreachable > 0f) unreachable -= dt;
        life -= dt;
        if (life <= 0f) {
            alive = false;
            return;
        }
        spin += dt * (resting ? 1.6f : 6f);
        bob += dt * 2.6f;

        if (!resting) {
            vy -= 14f * dt;
            x += vx * dt;
            y += vy * dt;
            z += vz * dt;
            vx *= Math.max(0f, 1f - dt * 2.2f);
            vz *= Math.max(0f, 1f - dt * 2.2f);
            if (y <= 0.22f) {
                y = 0.22f;
                if (vy < -1.2f) {
                    vy = -vy * 0.32f;       // sek
                } else {
                    vy = 0f;
                    resting = true;
                    // Duvarın ya da kışlanın üstüne düşen yığına kimse
                    // ulaşamaz; yere konarken en yakın açık hücreye kayar.
                    w.slideOutOfStructures(this);
                }
            }
            float half = Balance.WORLD_HALF - 1f;
            x = MathX.clamp(x, -half, half);
            z = MathX.clamp(z, -half, half);
            return;
        }

        // Oyuncu yaklaşınca kendiliğinden sürüklenir.
        Player p = w.player;
        if (p.alive) {
            float d = MathX.dist(x, z, p.x, p.z);
            if (d < Balance.PICKUP_GRAB) {
                w.collectPickup(this, true);
                return;
            }
            if (d < Balance.PICKUP_MAGNET) {
                magnet = MathX.clamp(1f - d / Balance.PICKUP_MAGNET, 0f, 1f);
                float pull = (3f + magnet * 12f) * dt;
                x += (p.x - x) / d * pull;
                z += (p.z - z) / d * pull;
                y = MathX.damp(y, 0.75f, 5f, dt);
                return;
            }
        }
        magnet = 0f;
        y = 0.22f + (float) Math.sin(bob) * 0.06f;
    }

    /** Toplayıcı yoldaşın hedefe yaklaşması için zemindeki konum. */
    public boolean grabbable() {
        return alive && resting;
    }
}
