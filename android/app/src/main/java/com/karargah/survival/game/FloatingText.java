package com.karargah.survival.game;

/** Dünyada süzülen yazı (hasar sayıları, "+hurda" gibi bildirimler). */
public class FloatingText {
    public float x, y, z;
    public float vy = 1.5f;
    public String text;
    public int color;
    public float life, maxLife;
    public float size = 1f;
    public boolean alive;

    public void set(float x, float y, float z, String text, int color, float life, float size) {
        this.x = x; this.y = y; this.z = z;
        this.text = text;
        this.color = color;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.vy = 1.6f;
        this.alive = true;
    }

    public void update(float dt) {
        if (!alive) return;
        y += vy * dt;
        vy *= Math.max(0f, 1f - dt * 1.4f);
        life -= dt;
        if (life <= 0f) alive = false;
    }

    public float alpha() {
        return maxLife <= 0f ? 0f : Math.min(1f, life / (maxLife * 0.45f));
    }
}
