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
    /** Doluysa bu yapı geliştirilecek (yeni yapı kurulmayacak). */
    public Structure upgradeTarget;

    public boolean isUpgrade() {
        return upgradeTarget != null;
    }

    /** Planın maliyeti (yeni yapı ya da geliştirme). */
    public int cost(GameWorld w) {
        return isUpgrade()
                ? w.player.buildCost(upgradeTarget.def().upgradeCost(upgradeTarget.level))
                : w.player.buildCost(def().cost);
    }

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
