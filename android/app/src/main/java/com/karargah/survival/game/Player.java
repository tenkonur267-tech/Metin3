package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/** Oyuncu karakteri: hareket, silahlar, seviye/yetenekler ve kaynaklar. */
public class Player {
    public float x, z, y;
    public float yaw = 0f;
    public float aimYaw = 0f;
    public float hp, maxHp;
    public boolean alive = true;
    public float reviveTimer;
    public int deaths;

    public float vx, vz;
    public float animPhase;
    public boolean moving;
    public float dashTimer, dashCd;
    public float dashDirX, dashDirZ;
    public float hurtFlash;
    public float recoil;
    public float muzzleTimer;

    // silahlar
    public final boolean[] unlocked = new boolean[Balance.WEAPONS.length];
    public final int[] weaponLevel = new int[Balance.WEAPONS.length];
    public final int[] magazine = new int[Balance.WEAPONS.length];
    public final int[] reserve = new int[Balance.WEAPONS.length];
    public int currentWeapon = Balance.W_PISTOL;
    public float fireCd;
    public boolean reloading;
    public float reloadTimer, reloadTotal;

    // ilerleme
    public int level = 1;
    public int xp;
    public int skillPoints;
    public final int[] skills = new int[Balance.SKILL_COUNT];
    public int scrap = 220;
    public int cores = 0;
    public int kills;

    public float regenCarry;

    public Player() {
        resetForNewGame();
    }

    public void resetForNewGame() {
        for (int i = 0; i < unlocked.length; i++) {
            unlocked[i] = (i == Balance.W_PISTOL);
            weaponLevel[i] = 1;
            magazine[i] = Balance.weapon(i).magazine;
            reserve[i] = i == Balance.W_PISTOL ? 9999 : 0;
        }
        for (int i = 0; i < skills.length; i++) skills[i] = 0;
        currentWeapon = Balance.W_PISTOL;
        level = 1;
        xp = 0;
        skillPoints = 0;
        scrap = 220;
        cores = 0;
        kills = 0;
        deaths = 0;
        maxHp = maxHp();
        hp = maxHp;
        alive = true;
        reviveTimer = 0f;
        x = 0f;
        z = 9f;
        yaw = MathX.PI;
        aimYaw = yaw;
        reloading = false;
        fireCd = 0f;
        dashCd = 0f;
    }

    // ---- yetenek türevleri ---------------------------------------------

    public float maxHp() {
        return Balance.PLAYER_BASE_HP * (1f + 0.14f * skills[Balance.SK_HP]);
    }

    public float moveSpeed() {
        return Balance.PLAYER_BASE_SPEED * (1f + 0.06f * skills[Balance.SK_SPEED]);
    }

    public float damageMul() {
        return 1f + 0.10f * skills[Balance.SK_DAMAGE];
    }

    public float armorReduction() {
        return Math.min(0.62f, 0.05f * skills[Balance.SK_ARMOR]);
    }

    public float reloadMul() {
        return 1f - 0.09f * skills[Balance.SK_RELOAD];
    }

    public float critChance() {
        return 0.06f * skills[Balance.SK_CRIT];
    }

    public float buildDiscount() {
        return 0.07f * skills[Balance.SK_ENGINEER];
    }

    public float structHpBonus() {
        return 1f + 0.12f * skills[Balance.SK_STRUCT_HP];
    }

    public float repairBonus() {
        return 1f + 0.25f * skills[Balance.SK_STRUCT_HP];
    }

    public float scrapBonus() {
        return 1f + 0.12f * skills[Balance.SK_SCRAP];
    }

    public float lifesteal() {
        return 2f * skills[Balance.SK_LIFESTEAL];
    }

    public float dashCooldown() {
        return Math.max(1.2f, Balance.DASH_COOLDOWN - 0.7f * skills[Balance.SK_DASH]);
    }

    public float regenPerSec() {
        return 0.7f * skills[Balance.SK_REGEN];
    }

    public int buildCost(int base) {
        return Math.max(1, Math.round(base * (1f - buildDiscount())));
    }

    public Balance.WeaponDef weapon() {
        return Balance.weapon(currentWeapon);
    }

    public int weaponLevel() {
        return weaponLevel[currentWeapon];
    }

    public int magCapacity() {
        return weapon().magazineAt(weaponLevel());
    }

    // ---- ilerleme -------------------------------------------------------

    public void addXp(int amount, GameWorld w) {
        xp += amount;
        while (xp >= Balance.xpForLevel(level)) {
            xp -= Balance.xpForLevel(level);
            level++;
            skillPoints++;
            float before = maxHp;
            maxHp = maxHp();
            hp += maxHp - before;
            hp = Math.min(hp, maxHp);
            if (w != null) w.onLevelUp();
        }
    }

    public boolean spendSkillPoint(int skill) {
        if (skill < 0 || skill >= skills.length) return false;
        if (skillPoints <= 0) return false;
        if (skills[skill] >= Balance.SKILLS[skill].maxLevel) return false;
        skillPoints--;
        skills[skill]++;
        float frac = maxHp > 0 ? hp / maxHp : 1f;
        maxHp = maxHp();
        hp = maxHp * frac;
        return true;
    }

    public void addScrap(int amount) {
        scrap += Math.max(0, Math.round(amount * scrapBonus()));
    }

    // ---- can ------------------------------------------------------------

    public void hurt(float amount, GameWorld w) {
        if (!alive || dashTimer > 0f) return;
        float dmg = amount * (1f - armorReduction());
        hp -= dmg;
        hurtFlash = 0.45f;
        if (w != null) w.camera.addShake(0.25f);
        if (hp <= 0f) {
            hp = 0f;
            alive = false;
            deaths++;
            reviveTimer = Balance.REVIVE_TIME;
            if (w != null) w.onPlayerDown();
        }
    }

    public void heal(float amount) {
        if (!alive) return;
        hp = Math.min(maxHp, hp + amount);
    }

    // ---- kare güncellemesi ---------------------------------------------

    public void update(GameWorld w, float dt, float inX, float inZ, boolean firing) {
        maxHp = maxHp();
        if (hurtFlash > 0f) hurtFlash = Math.max(0f, hurtFlash - dt * 2.2f);
        if (recoil > 0f) recoil = Math.max(0f, recoil - dt * 7f);
        if (muzzleTimer > 0f) muzzleTimer = Math.max(0f, muzzleTimer - dt);
        if (dashCd > 0f) dashCd = Math.max(0f, dashCd - dt);

        if (!alive) {
            reviveTimer -= dt;
            if (reviveTimer <= 0f) w.revivePlayer();
            return;
        }

        if (regenPerSec() > 0f && hp < maxHp) {
            heal(regenPerSec() * dt);
        }

        float speed = moveSpeed();
        if (dashTimer > 0f) {
            dashTimer -= dt;
            vx = dashDirX * Balance.DASH_SPEED;
            vz = dashDirZ * Balance.DASH_SPEED;
            if (MathX.chance(dt * 30f)) {
                w.particles.dust(x, 0.1f, z, 2);
            }
        } else {
            vx = inX * speed;
            vz = inZ * speed;
        }

        moving = MathX.len(inX, inZ) > 0.05f || dashTimer > 0f;
        if (moving) animPhase += dt * (5.2f + MathX.len(vx, vz) * 0.65f);

        float nx = x + vx * dt;
        float nz = z + vz * dt;
        if (!blocked(w, nx, z)) x = nx;
        if (!blocked(w, x, nz)) z = nz;
        float half = Balance.WORLD_HALF - 1.2f;
        x = MathX.clamp(x, -half, half);
        z = MathX.clamp(z, -half, half);

        // nişan: menzildeki en yakın zombiye otomatik kilitlen
        Zombie t = w.aimTarget(this);
        float desired;
        if (t != null) {
            desired = (float) Math.atan2(t.x - x, t.z - z);
        } else if (moving) {
            desired = (float) Math.atan2(vx, vz);
        } else {
            desired = aimYaw;
        }
        aimYaw = MathX.approachAngle(aimYaw, desired, dt * 12f);
        yaw = MathX.approachAngle(yaw, moving && t == null ? (float) Math.atan2(vx, vz) : aimYaw, dt * 12f);

        if (reloading) {
            reloadTimer -= dt;
            if (reloadTimer <= 0f) finishReload();
        }
        if (fireCd > 0f) fireCd -= dt;

        if (firing && !reloading && fireCd <= 0f) {
            tryFire(w, t);
        }
    }

    private boolean blocked(GameWorld w, float nx, float nz) {
        Structure s = w.grid.atWorld(nx, nz);
        if (s == null || !s.blocks()) return false;
        float half = Balance.CELL * 0.5f;
        return Math.abs(nx - s.x) < half + Balance.PLAYER_RADIUS * 0.6f
                && Math.abs(nz - s.z) < half + Balance.PLAYER_RADIUS * 0.6f;
    }

    public void dash() {
        if (!alive || dashCd > 0f || dashTimer > 0f) return;
        float l = MathX.len(vx, vz);
        if (l > 0.3f) {
            dashDirX = vx / l;
            dashDirZ = vz / l;
        } else {
            dashDirX = (float) Math.sin(yaw);
            dashDirZ = (float) Math.cos(yaw);
        }
        dashTimer = Balance.DASH_TIME;
        dashCd = dashCooldown();
    }

    public void startReload() {
        if (reloading || !alive) return;
        int cap = magCapacity();
        if (magazine[currentWeapon] >= cap) return;
        if (reserve[currentWeapon] <= 0) return;
        reloading = true;
        reloadTotal = weapon().reloadAt(weaponLevel()) * reloadMul();
        reloadTimer = reloadTotal;
    }

    private void finishReload() {
        reloading = false;
        int cap = magCapacity();
        int need = cap - magazine[currentWeapon];
        int take = Math.min(need, reserve[currentWeapon]);
        magazine[currentWeapon] += take;
        if (reserve[currentWeapon] < 9000) reserve[currentWeapon] -= take;
    }

    public void switchWeapon(int id) {
        if (id < 0 || id >= Balance.WEAPONS.length) return;
        if (!unlocked[id]) return;
        if (currentWeapon == id) return;
        currentWeapon = id;
        reloading = false;
        fireCd = Math.max(fireCd, 0.22f);
    }

    private void tryFire(GameWorld w, Zombie lockTarget) {
        Balance.WeaponDef def = weapon();
        if (magazine[currentWeapon] <= 0) {
            if (reserve[currentWeapon] > 0) {
                startReload();
            } else {
                fireCd = 0.4f;
                w.audio.playEmpty();
            }
            return;
        }
        magazine[currentWeapon]--;
        fireCd = 1f / def.fireRate;
        recoil = Math.min(1.1f, recoil + def.recoil);
        muzzleTimer = 0.06f;
        w.camera.addShake(def.recoil * 0.16f);
        w.audio.playShot(currentWeapon);

        float dirX = (float) Math.sin(aimYaw);
        float dirZ = (float) Math.cos(aimYaw);
        float mx = x + dirX * 0.75f;
        float mz = z + dirZ * 0.75f;
        float my = 1.12f;
        w.particles.muzzleFlash(mx, my, mz, dirX, dirZ);

        float dmg = def.damageAt(weaponLevel()) * damageMul();
        for (int p = 0; p < def.pellets; p++) {
            float spread = MathX.rnd(-def.spread, def.spread) * MathX.PI;
            float a = aimYaw + spread;
            float dx = (float) Math.sin(a), dz = (float) Math.cos(a);
            boolean crit = MathX.chance(critChance());
            float shotDmg = crit ? dmg * 2.2f : dmg;
            if (def.speed > 0f) {
                w.spawnRocket(mx, my, mz, dx, dz, shotDmg, def.splash);
            } else {
                w.hitscan(mx, my, mz, dx, dz, def.range, shotDmg, def.pierce, crit, this);
            }
        }
        if (magazine[currentWeapon] == 0 && reserve[currentWeapon] > 0) {
            startReload();
        }
    }

    /** Cephanelik / dalga arası dolumu. */
    public void refillAmmo(float fraction) {
        for (int i = 0; i < Balance.WEAPONS.length; i++) {
            if (!unlocked[i] || i == Balance.W_PISTOL) continue;
            Balance.WeaponDef d = Balance.weapon(i);
            int add = Math.max(1, Math.round(d.reserveMax * fraction));
            reserve[i] = Math.min(d.reserveMax, reserve[i] + add);
        }
    }
}
