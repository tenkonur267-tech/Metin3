package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/** Roket, top mermisi ve zombi asidi için ortak mermi sınıfı. */
public class Projectile {
    public static final int ROCKET = 0;
    public static final int ACID = 1;
    public static final int SHELL = 2;   // top kulesi mermisi

    public int kind;
    public float x, y, z;
    public float vx, vy, vz;
    public float damage, splash;
    public float life;
    public boolean alive;
    public int color;
    public float size = 0.22f;
    public float trailTimer;
    public Structure owner;

    public void initRocket(float x, float y, float z, float dx, float dz, float damage, float splash) {
        set(ROCKET, x, y, z, dx * 34f, 0f, dz * 34f, damage, splash, 2.6f, 0xD84315, 0.26f);
    }

    public void initShell(float x, float y, float z, float dx, float dy, float dz,
                          float speed, float damage, float splash) {
        set(SHELL, x, y, z, dx * speed, dy * speed, dz * speed, damage, splash, 3.2f, 0xFFCA28, 0.20f);
    }

    public void initAcid(float x, float y, float z, float dx, float dy, float dz,
                         float speed, float damage) {
        set(ACID, x, y, z, dx * speed, dy * speed, dz * speed, damage, 1.6f, 3.5f, 0x9CCC65, 0.24f);
    }

    private void set(int kind, float x, float y, float z, float vx, float vy, float vz,
                     float damage, float splash, float life, int color, float size) {
        this.kind = kind;
        this.x = x; this.y = y; this.z = z;
        this.vx = vx; this.vy = vy; this.vz = vz;
        this.damage = damage; this.splash = splash;
        this.life = life; this.color = color; this.size = size;
        this.alive = true;
        this.trailTimer = 0f;
    }

    public void update(GameWorld w, float dt) {
        if (!alive) return;
        life -= dt;
        if (life <= 0f) {
            explode(w);
            return;
        }
        if (kind != ROCKET) {
            vy -= 13f * dt;   // balistik
        }
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;

        trailTimer -= dt;
        if (trailTimer <= 0f) {
            trailTimer = 0.02f;
            if (kind == ACID) {
                w.particles.spawn(x, y, z, 0f, 0f, 0f, 0x9CCC65, 0.7f, 0.12f, 0.02f, 0.3f, 0f, 2f, 0);
            } else {
                w.particles.spawn(x, y, z, MathX.rnd(-0.4f, 0.4f), MathX.rnd(0.1f, 0.8f),
                        MathX.rnd(-0.4f, 0.4f), 0x6B6255, 0.4f, 0.1f, 0.3f, 0.5f, 0.3f, 1.5f, 0);
            }
        }

        if (y <= 0.08f) {
            y = 0.08f;
            explode(w);
            return;
        }

        if (kind == ACID) {
            if (w.player.alive && MathX.dist3(x, y, z, w.player.x, 1f, w.player.z) < 0.9f) {
                explode(w);
            }
        } else {
            Zombie hit = w.zombieAt(x, y, z, size + 0.35f);
            if (hit != null) {
                explode(w);
            }
        }
    }

    private void explode(GameWorld w) {
        alive = false;
        if (kind == ACID) {
            w.particles.explosion(x, y, z, 1.2f);
            w.acidSplash(x, z, splash, damage);
            w.audio.playAcid();
        } else {
            w.particles.explosion(x, y, z, splash);
            w.explosionDamage(x, z, splash, damage, this);
            w.camera.addShake(0.4f);
            w.audio.playExplosion();
        }
    }
}
