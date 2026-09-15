package com.karargah.survival.game;

/**
 * İnşa planı (şantiye). Oyuncu plan modunda yerleştirir ya da yıkılan bir
 * yapı için kendiliğinden oluşur; inşa görevi olan yoldaşlar gelip kurar.
 * Hurda, inşaat fiilen başladığında ortak kasadan düşülür.
 */
public class BuildPlan {
    public int type;
    public int gx, gz;
    public int rotation;
    public float x, z;
    /** 0..1 arası tamamlanma oranı. */
    public float progress;
    /** Hurda kasadan düşüldü mü? */
    public boolean paid;
    /** Yıkılan yapının yerine kendiliğinden açılan plan mı? */
    public boolean auto;
    public boolean alive = true;
    /** Kasa yetmediği için bekliyor. */
    public boolean waiting;

    public BuildPlan(int type, int gx, int gz, int rotation, boolean auto) {
        this.type = type;
        this.gx = gx;
        this.gz = gz;
        this.rotation = rotation & 3;
        this.auto = auto;
        this.x = BuildGrid.cellToWorld(gx);
        this.z = BuildGrid.cellToWorld(gz);
    }

    public Balance.StructDef def() {
        return Balance.struct(type);
    }
}
