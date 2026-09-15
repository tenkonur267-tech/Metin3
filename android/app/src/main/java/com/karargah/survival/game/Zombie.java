package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/** Tek bir zombi: hedef seçimi, yol takibi, saldırı ve ölüm animasyonu. */
public class Zombie {
    public static final int ST_WALK = 0;
    public static final int ST_ATTACK = 1;
    public static final int ST_DEAD = 2;
    public static final int ST_SPAWN = 3;

    public int type;
    public Balance.ZombieDef def;
    public float x, z, y;
    public float yaw;
    public float hp, maxHp;
    public float speed;
    public float damage;
    public int state = ST_SPAWN;
    public float stateTime;
    public float attackCd;
    public float animPhase;
    public float flash;
    public float slowTimer, slowFactor = 1f;
    public float burnTimer, burnDps;
    public float stunTimer;
    public float pushX, pushZ;
    public float knockX, knockZ;
    public float deadTime;
    public boolean alive = true;
    public boolean removeMe;
    public Structure targetStruct;
    public boolean targetingPlayer;
    /** Hedef, yol üstündeki engel değil de uzaktan dövülen bir yapıysa true. */
    private boolean rangedLocked;
    public float specialCd;
    public float scale = 1f;
    public float hitFlashTimer;
    public int xpValue, scrapValue;
    public float lastDamageDirX, lastDamageDirZ;

    /** Güçlendirilmiş (elit) zombiler: parlayan renk, fazladan can. */
    public boolean elite;

    public void init(int type, float x, float z, int wave, boolean elite) {
        this.type = type;
        this.def = Balance.zombie(type);
        this.x = x;
        this.z = z;
        this.y = 0f;
        this.elite = elite;
        float hpScale = Balance.waveHpScale(wave) * (elite ? 2.4f : 1f);
        this.maxHp = def.hp * hpScale;
        this.hp = maxHp;
        this.speed = def.speed * (elite ? 1.12f : 1f) * MathX.rnd(0.92f, 1.08f);
        this.damage = def.damage * Balance.waveDamageScale(wave) * (elite ? 1.5f : 1f);
        this.xpValue = Math.round(def.xp * (elite ? 2.2f : 1f));
        this.scrapValue = Math.round(def.scrap * (elite ? 2.4f : 1f));
        this.scale = (elite ? 1.18f : 1f) * MathX.rnd(0.94f, 1.06f);
        this.state = ST_SPAWN;
        this.stateTime = 0f;
        this.alive = true;
        this.removeMe = false;
        this.deadTime = 0f;
        this.animPhase = MathX.rnd(0f, MathX.TAU);
        this.targetStruct = null;
        this.rangedLocked = false;
        this.slowFactor = 1f;
        this.slowTimer = 0f;
        this.burnTimer = 0f;
        this.stunTimer = 0f;
        this.knockX = 0f;
        this.knockZ = 0f;
        this.specialCd = 6f;
        this.yaw = (float) Math.atan2(-x, -z);
    }

    public float radius() {
        return def.radius * scale;
    }

    public float height() {
        return def.height * scale;
    }

    public float centerY() {
        return height() * 0.55f;
    }

    public boolean isBoss() {
        return type == Balance.Z_BOSS;
    }

    public void applySlow(float factor, float duration) {
        slowFactor = Math.min(slowFactor, factor);
        slowTimer = Math.max(slowTimer, duration);
    }

    public void applyBurn(float dps, float duration) {
        burnDps = Math.max(burnDps, dps);
        burnTimer = Math.max(burnTimer, duration);
    }

    public void knockback(float dx, float dz, float force) {
        if (isBoss()) force *= 0.12f;
        float l = MathX.len(dx, dz);
        if (l < 1e-4f) return;
        knockX += dx / l * force;
        knockZ += dz / l * force;
    }

    /** @return öldürücü vuruşsa true */
    public boolean hurt(float amount, float dirX, float dirZ) {
        if (!alive) return false;
        hp -= amount;
        flash = 0.16f;
        lastDamageDirX = dirX;
        lastDamageDirZ = dirZ;
        if (hp <= 0f) {
            hp = 0f;
            alive = false;
            state = ST_DEAD;
            stateTime = 0f;
            return true;
        }
        return false;
    }

    public void update(GameWorld w, float dt) {
        if (state == ST_DEAD) {
            deadTime += dt;
            if (deadTime > 2.4f) removeMe = true;
            return;
        }

        if (flash > 0f) flash = Math.max(0f, flash - dt * 6f);
        if (slowTimer > 0f) {
            slowTimer -= dt;
            if (slowTimer <= 0f) slowFactor = 1f;
        }
        if (burnTimer > 0f) {
            burnTimer -= dt;
            if (hurt(burnDps * dt, 0f, 0f)) {
                w.onZombieKilled(this, null);
                return;
            }
            if (MathX.chance(dt * 9f)) {
                w.particles.spawn(x + MathX.rnd(-0.3f, 0.3f), centerY() + MathX.rnd(-0.4f, 0.6f),
                        z + MathX.rnd(-0.3f, 0.3f), 0f, MathX.rnd(0.6f, 1.6f), 0f,
                        0xFF8A2A, 0.8f, 0.14f, 0.02f, 0.4f, 0.4f, 1.6f, 1);
            }
        }
        if (stunTimer > 0f) {
            stunTimer -= dt;
        }

        stateTime += dt;
        if (state == ST_SPAWN) {
            y = MathX.lerp(-height(), 0f, MathX.clamp(stateTime / 1.1f, 0f, 1f));
            if (stateTime >= 1.1f) {
                state = ST_WALK;
                y = 0f;
                stateTime = 0f;
            }
            return;
        }

        // geri tepme sönümü
        knockX *= Math.max(0f, 1f - dt * 7f);
        knockZ *= Math.max(0f, 1f - dt * 7f);

        chooseTarget(w, dt);

        if (attackCd > 0f) attackCd -= dt;

        if (state == ST_ATTACK) {
            handleAttack(w, dt);
        } else {
            move(w, dt);
        }

        if (isBoss()) {
            specialCd -= dt;
            if (specialCd <= 0f) {
                specialCd = 9.5f;
                w.bossSlam(this);
            }
        }
    }

    private void chooseTarget(GameWorld w, float dt) {
        Player p = w.player;
        float dp = MathX.dist(x, z, p.x, p.z);
        float aggro = type == Balance.Z_RUNNER ? 14f : (isBoss() ? 12f : 8.5f);
        boolean playerAlive = p.alive;

        // Oyuncu yakınsa ve görüş alanındaysa ona yönel.
        if (playerAlive && dp < aggro) {
            targetingPlayer = true;
            targetStruct = null;
            float reach = radius() + Balance.PLAYER_RADIUS + (def.rangedRange > 0 ? def.rangedRange : 0.55f);
            if (dp <= reach) {
                state = ST_ATTACK;
            } else if (state == ST_ATTACK) {
                state = ST_WALK;
            }
            return;
        }
        targetingPlayer = false;

        // Öfke modunda uzaktan yapı dövmek bırakılır; herkes reaktöre yürür.
        boolean rage = w.waves.rage;
        if (rage && rangedLocked) {
            rangedLocked = false;
            targetStruct = null;
        }

        // Menzilli zombiler yakın yapıyı uzaktan döver.
        if (def.rangedRange > 0f && !rage) {
            Structure s = w.nearestStructure(x, z, def.rangedRange);
            if (s != null) {
                targetStruct = s;
                rangedLocked = true;
                state = ST_ATTACK;
                return;
            }
        }

        if (targetStruct != null && !targetStruct.alive) targetStruct = null;
        if (targetStruct != null) {
            float d = MathX.dist(x, z, targetStruct.x, targetStruct.z);
            if (d > targetStruct.footprintRadius() + radius() + 1.4f) {
                targetStruct = null;
            } else {
                state = ST_ATTACK;
                return;
            }
        }

        state = ST_WALK;
    }

    private void handleAttack(GameWorld w, float dt) {
        float tx, tz;
        if (targetingPlayer) {
            tx = w.player.x;
            tz = w.player.z;
        } else if (targetStruct != null && targetStruct.alive) {
            tx = targetStruct.x;
            tz = targetStruct.z;
        } else {
            state = ST_WALK;
            return;
        }
        yaw = MathX.approachAngle(yaw, (float) Math.atan2(tx - x, tz - z), dt * 7f);

        if (attackCd <= 0f && stunTimer <= 0f) {
            attackCd = 1f / def.attackRate;
            if (def.rangedRange > 0f) {
                w.spawnAcid(this, tx, 1.0f, tz);   // gövde/yapı orta yüksekliği
            } else if (targetingPlayer) {
                w.player.hurt(damage, w);
                w.particles.blood(w.player.x, 1.0f, w.player.z,
                        (w.player.x - x) * 0.4f, (w.player.z - z) * 0.4f, 6);
                w.audio.playHit();
            } else if (targetStruct != null) {
                float dmg = damage * def.structDamageMul;
                targetStruct.damage(dmg);
                w.particles.sparks(targetStruct.x + MathX.rnd(-0.4f, 0.4f), 1f + MathX.rnd(0f, 0.8f),
                        targetStruct.z + MathX.rnd(-0.4f, 0.4f), 4, 0xC8B18A);
                w.audio.playStructHit();
                if (!targetStruct.alive) {
                    w.onStructureDestroyed(targetStruct);
                    targetStruct = null;
                    state = ST_WALK;
                }
            }
        }
    }

    private void move(GameWorld w, float dt) {
        if (stunTimer > 0f) return;

        int gx = BuildGrid.worldToCell(x), gz = BuildGrid.worldToCell(z);
        float dirX, dirZ;

        int best = w.flow.bestNeighbor(gx, gz);
        if (best >= 0) {
            float bx = BuildGrid.cellToWorld(FlowField.cellX(best));
            float bz = BuildGrid.cellToWorld(FlowField.cellZ(best));
            dirX = bx - x;
            dirZ = bz - z;
            Structure blocker = w.grid.at(FlowField.cellX(best), FlowField.cellZ(best));
            if (blocker != null && blocker.blocks()) {
                float d = MathX.dist(x, z, blocker.x, blocker.z);
                if (d < blocker.footprintRadius() + radius() + 1.3f) {
                    targetStruct = blocker;
                    rangedLocked = false;
                    state = ST_ATTACK;
                    return;
                }
            }
        } else {
            // Daha iyi komşu yok: ya reaktörün dibindeyiz ya da yol kapalı.
            Structure near = w.blockerNear(x, z);
            if (near != null) {
                targetStruct = near;
                rangedLocked = false;
                state = ST_ATTACK;
                return;
            }
            dirX = -x;
            dirZ = -z;
        }

        float l = MathX.len(dirX, dirZ);
        if (l > 1e-4f) {
            dirX /= l;
            dirZ /= l;
        }
        if (targetingPlayer) {
            float px = w.player.x - x, pz = w.player.z - z;
            float pl = MathX.len(px, pz);
            if (pl > 1e-4f) {
                dirX = px / pl;
                dirZ = pz / pl;
            }
        }

        float sp = speed * slowFactor * (w.waves.rage ? 1.55f : 1f);
        float vx = dirX * sp + pushX + knockX;
        float vz = dirZ * sp + pushZ + knockZ;
        pushX = 0f;
        pushZ = 0f;

        float nx = x + vx * dt;
        float nz = z + vz * dt;

        // yapılarla çarpışma: eksen eksen kaydır
        if (!blockedAt(w, nx, z)) x = nx;
        if (!blockedAt(w, x, nz)) z = nz;

        float half = Balance.WORLD_HALF - 1f;
        x = MathX.clamp(x, -half, half);
        z = MathX.clamp(z, -half, half);

        animPhase += dt * (3.4f + sp * 1.5f);
        if (l > 1e-4f) {
            yaw = MathX.approachAngle(yaw, (float) Math.atan2(dirX, dirZ), dt * 6f);
        }
    }

    private boolean blockedAt(GameWorld w, float nx, float nz) {
        int gx = BuildGrid.worldToCell(nx), gz = BuildGrid.worldToCell(nz);
        Structure s = w.grid.at(gx, gz);
        if (s == null || !s.blocks()) return false;
        // Aynı yapının içinde sıkıştıysak dışarı çıkabilmeliyiz.
        if (w.grid.at(BuildGrid.worldToCell(x), BuildGrid.worldToCell(z)) == s) return false;
        // Çok hücreli yapılarda (reaktör) yapının merkezi değil, hücrenin
        // merkezi esas alınmalı; yoksa zombiler yapının üstüne yürüyebiliyor.
        float half = Balance.CELL * 0.5f;
        float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
        return Math.abs(nx - cx) < half + radius() * 0.65f
                && Math.abs(nz - cz) < half + radius() * 0.65f;
    }
}
