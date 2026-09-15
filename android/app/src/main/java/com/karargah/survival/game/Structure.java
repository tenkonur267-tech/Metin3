package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/** Sahadaki bir yapı örneği (duvar, kule, destek birimi ya da reaktör). */
public class Structure {
    public int type;
    public int level = 1;
    public int gx, gz;
    public float x, z;
    public float hp, maxHp;
    public boolean alive = true;
    /** Çeyrek tur cinsinden yerleştirme dönüşü (0..3). Duvarlarda kullanılmaz. */
    public int rotation;
    /** Duvarın komşu maskesi: bit0 +X, bit1 -X, bit2 +Z, bit3 -Z. */
    public int wallMask;

    /** Kule namlusunun bakış açısı ve atış sayacı. */
    public float yaw;
    public float cooldown;
    public float flash;        // hasar aldığında beyaz parlama
    public float buildAnim;    // yeni kurulduğunda yükselme animasyonu
    public float spin;         // görsel dönüş (jeneratör vs.)
    public float chargeGlow;   // ateş ederken parlama
    public Zombie target;
    public float repairCarry;  // küsuratlı tamir birikimi

    public Structure(int type, int level, int gx, int gz, float structHpBonus) {
        this(type, level, gx, gz, structHpBonus, 0);
    }

    public Structure(int type, int level, int gx, int gz, float structHpBonus, int rotation) {
        this.type = type;
        this.level = level;
        this.gx = gx;
        this.gz = gz;
        this.x = BuildGrid.cellToWorld(gx);
        this.z = BuildGrid.cellToWorld(gz);
        this.maxHp = def().hpAt(level) * structHpBonus;
        this.hp = maxHp;
        this.buildAnim = 1f;
        this.rotation = rotation & 3;
    }

    /** Görsel dönüş açısı (radyan). */
    public float placementYaw() {
        return rotation * (MathX.PI * 0.5f);
    }

    /** Seviye büyüdükçe yapı biraz irileşir (duvar/tuzak hariç). */
    public float levelScale() {
        if (type == Balance.S_WALL || type == Balance.S_SPIKE) return 1f;
        return 1f + 0.045f * (level - 1);
    }

    public Balance.StructDef def() {
        return Balance.struct(type);
    }

    public int kind() {
        return def().kind;
    }

    public boolean blocks() {
        return def().blocks;
    }

    public float hpFraction() {
        return maxHp <= 0 ? 0f : MathX.clamp(hp / maxHp, 0f, 1f);
    }

    /** Yapının kapladığı alanın yarıçapı (reaktör 4x4 hücre kaplar). */
    public float footprintRadius() {
        return type == Balance.S_CORE
                ? Balance.CELL * Balance.CORE_CELLS * 0.5f
                : Balance.CELL * 0.5f;
    }

    public boolean isTurret() {
        return def().kind == Balance.KIND_TURRET;
    }

    public void refreshMaxHp(float structHpBonus) {
        float frac = hpFraction();
        maxHp = def().hpAt(level) * structHpBonus;
        hp = maxHp * frac;
    }

    public void damage(float amount) {
        if (!alive) return;
        hp -= amount;
        flash = 0.22f;
        if (hp <= 0f) {
            hp = 0f;
            alive = false;
        }
    }

    public void repair(float amount) {
        if (!alive) return;
        hp = Math.min(maxHp, hp + amount);
    }

    /** Görsel yükseklik (inşa animasyonu için 0..1). */
    public float riseFactor() {
        return 1f - buildAnim * buildAnim;
    }

    public void updateVisual(float dt) {
        if (buildAnim > 0f) buildAnim = Math.max(0f, buildAnim - dt * 2.6f);
        if (flash > 0f) flash = Math.max(0f, flash - dt * 4f);
        if (chargeGlow > 0f) chargeGlow = Math.max(0f, chargeGlow - dt * 5.5f);
        spin += dt;
    }
}
