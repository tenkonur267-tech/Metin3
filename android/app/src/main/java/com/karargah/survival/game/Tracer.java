package com.karargah.survival.game;

/** Kurşun izi: kısa ömürlü, ışıldayan bir çizgi. */
public class Tracer {
    public float x0, y0, z0, x1, y1, z1;
    public float life, maxLife;
    public int color;
    public float width = 0.045f;
    public boolean alive;

    public void set(float x0, float y0, float z0, float x1, float y1, float z1,
                    int color, float life, float width) {
        this.x0 = x0; this.y0 = y0; this.z0 = z0;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.color = color;
        this.life = life;
        this.maxLife = life;
        this.width = width;
        this.alive = true;
    }

    public void update(float dt) {
        if (!alive) return;
        life -= dt;
        if (life <= 0f) alive = false;
    }

    public float alpha() {
        return maxLife <= 0f ? 0f : Math.max(0f, life / maxLife);
    }
}
