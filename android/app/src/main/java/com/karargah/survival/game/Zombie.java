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
    /** Oyuncu yerine bir yoldaşı hedefliyorsa. */
    public Npc targetNpc;
    /** Hedef, yol üstündeki engel değil de uzaktan dövülen bir yapıysa true. */
    private boolean rangedLocked;
    public float specialCd;
    public float scale = 1f;
    public float hitFlashTimer;
    public int xpValue, scrapValue;
    public float lastDamageDirX, lastDamageDirZ;

    /** Güçlendirilmiş (elit) zombiler: parlayan renk, fazladan can. */
    public boolean elite;

    /**
     * Dalga zombisi değil, dünyada kendi başına gezen zombi. Üsse yürümez;
     * doğduğu bölgede dolaşır, oyuncu ya da yoldaş yaklaşınca saldırır.
     */
    public boolean roamer;
    public float homeX, homeZ;
    private float wanderX, wanderZ, wanderTimer;

    // Sıkışma kurtarma: yürümeye çalıştığı hâlde yerinden oynamıyorsa
    // (çoğunlukla iki yapının arasındaki köşe kapanı) önündeki yapıyı döver.
    private float progressTimer;
    private float progressX, progressZ;

    public void init(int type, float x, float z, int wave, boolean elite) {
        this.type = type;
        this.def = Balance.zombie(type);
        this.x = x;
        this.z = z;
        this.y = 0f;
        this.elite = elite;
        float hpScale = Balance.dayHpScale(wave) * (elite ? 2.4f : 1f);
        this.maxHp = def.hp * hpScale;
        this.hp = maxHp;
        this.speed = def.speed * (elite ? 1.12f : 1f) * MathX.rnd(0.92f, 1.08f);
        this.damage = def.damage * Balance.dayDamageScale(wave) * (elite ? 1.5f : 1f);
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
        this.targetNpc = null;
        this.rangedLocked = false;
        this.roamer = false;
        this.homeX = x;
        this.homeZ = z;
        this.wanderX = x;
        this.wanderZ = z;
        this.wanderTimer = 0f;
        this.progressTimer = 0f;
        this.progressX = x;
        this.progressZ = z;
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
        float aggro = type == Balance.Z_RUNNER ? 14f : (isBoss() ? 12f : 8.5f);
        // Gezginler avlanır: daha uzaktan fark eder, gece daha da uyanıktır.
        if (roamer) aggro = aggro * 1.9f + w.nightFactor * 6f;

        // En yakın canlı dost: oyuncu ya da yoldaş.
        float dp = p.alive ? MathX.dist(x, z, p.x, p.z) : Float.MAX_VALUE;
        Npc bestNpc = null;
        float dn = Float.MAX_VALUE;
        for (int i = 0; i < w.npcs.size(); i++) {
            Npc n = w.npcs.get(i);
            if (!n.alive || n.downed) continue;
            float d = MathX.dist(x, z, n.x, n.z);
            if (d < dn) {
                dn = d;
                bestNpc = n;
            }
        }

        if (Math.min(dp, dn) < aggro) {
            boolean chooseNpc = dn < dp;
            targetingPlayer = !chooseNpc;
            targetNpc = chooseNpc ? bestNpc : null;
            targetStruct = null;
            float reach = radius() + Balance.PLAYER_RADIUS
                    + (def.rangedRange > 0 ? def.rangedRange : 0.55f);
            if (Math.min(dp, dn) <= reach) {
                state = ST_ATTACK;
            } else if (state == ST_ATTACK) {
                state = ST_WALK;
            }
            return;
        }
        targetingPlayer = false;
        targetNpc = null;

        if (roamer) {
            // Gezgin üsse yürümez; önündeki yapıya çarparsa döver, yoksa gezer.
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
            return;
        }

        // Menzilli zombiler yakın yapıyı uzaktan döver.
        if (def.rangedRange > 0f) {
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
        if (targetNpc != null && targetNpc.alive && !targetNpc.downed) {
            tx = targetNpc.x;
            tz = targetNpc.z;
        } else if (targetingPlayer) {
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
            } else if (targetNpc != null && targetNpc.alive && !targetNpc.downed) {
                targetNpc.hurt(damage, w);
                w.particles.blood(targetNpc.x, 1.0f, targetNpc.z,
                        (targetNpc.x - x) * 0.4f, (targetNpc.z - z) * 0.4f, 6);
                w.audio.playHit();
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

        float dirX, dirZ;
        if (roamer && !targetingPlayer && targetNpc == null) {
            wanderDir(w, dt);
            dirX = wanderX - x;
            dirZ = wanderZ - z;
            stepMove(w, dirX, dirZ, dt, 0.55f);
            return;
        }

        int gx = BuildGrid.worldToCell(x), gz = BuildGrid.worldToCell(z);
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
        if (targetingPlayer || targetNpc != null) {
            float ax = targetNpc != null ? targetNpc.x : w.player.x;
            float az = targetNpc != null ? targetNpc.z : w.player.z;
            float px = ax - x, pz = az - z;
            float pl = MathX.len(px, pz);
            if (pl > 1e-4f) {
                dirX = px / pl;
                dirZ = pz / pl;
            }
        }

        // Kanlı ay gecelerinde baskıncılar belirgin biçimde hızlıdır.
        float sp = speed * slowFactor * (w.threat.bloodMoon && !roamer ? 1.35f : 1f);
        dt0 = dt;
        float vx = dirX * sp + pushX + knockX;
        float vz = dirZ * sp + pushZ + knockZ;
        pushX = 0f;
        pushZ = 0f;

        applyStep(w, vx * dt, vz * dt, sp, dirX, dirZ, l > 1e-4f);
    }

    /** Hız vektörünü uygular: yapı ve şehir binalarıyla çarpışmayı kaydırır. */
    private void applyStep(GameWorld w, float dx, float dz, float sp,
                           float dirX, float dirZ, boolean turn) {
        float nx = x + dx;
        float nz = z + dz;

        // yapılarla çarpışma: eksen eksen kaydır
        if (!blockedAt(w, nx, z)) x = nx;
        if (!blockedAt(w, x, nz)) z = nz;

        float half = Balance.WORLD_HALF - 1f;
        x = MathX.clamp(x, -half, half);
        z = MathX.clamp(z, -half, half);

        animPhase += dt0 * (3.4f + sp * 1.5f);
        if (turn) {
            yaw = MathX.approachAngle(yaw, (float) Math.atan2(dirX, dirZ), dt0 * 6f);
        }
        checkStuck(w);
    }

    /**
     * Yürümeye çalışıp yerinden oynayamıyorsa kurtarır. Köşe kapanı gerçek bir
     * durum: iki yapının köşesi arasındaki çapraz geçit hem X hem Z ekseninde
     * kapalı görünür, zombi sonsuza kadar orada kalır ve dalga hiç bitmez.
     * Çözüm basit ve mantıklı: takıldıysan önündeki yapıyı yık.
     */
    private void checkStuck(GameWorld w) {
        progressTimer += dt0;
        if (progressTimer < 1.1f) return;
        float moved = MathX.dist(progressX, progressZ, x, z);
        progressTimer = 0f;
        progressX = x;
        progressZ = z;
        if (moved > 0.4f) return;

        if (roamer) {
            wanderTimer = 0f;          // gezgin yalnızca başka bir yön dener
            return;
        }
        Structure blocker = w.blockerNear(x, z);
        if (blocker != null) {
            targetStruct = blocker;
            rangedLocked = false;
            state = ST_ATTACK;
        }
    }

    /** Gezgin için basit adım: hedefe doğru yürü, engele çarpınca kaydır. */
    private void stepMove(GameWorld w, float dirX, float dirZ, float dt, float speedMul) {
        float l = MathX.len(dirX, dirZ);
        if (l > 1e-4f) {
            dirX /= l;
            dirZ /= l;
        }
        float sp = speed * slowFactor * speedMul;
        dt0 = dt;
        applyStep(w, (dirX * sp + pushX + knockX) * dt,
                (dirZ * sp + pushZ + knockZ) * dt, sp, dirX, dirZ, l > 1e-4f);
        pushX = 0f;
        pushZ = 0f;
    }

    /** Gezginin bir sonraki dolaşma noktası (bölgesinden çok uzaklaşmaz). */
    private void wanderDir(GameWorld w, float dt) {
        wanderTimer -= dt;
        float d = MathX.dist(x, z, wanderX, wanderZ);
        if (wanderTimer <= 0f || d < 1.6f) {
            wanderTimer = MathX.rnd(3.5f, 8f);
            float a = MathX.rnd(0f, MathX.TAU);
            float r = MathX.rnd(4f, 17f);
            wanderX = homeX + (float) Math.cos(a) * r;
            wanderZ = homeZ + (float) Math.sin(a) * r;
            // Binanın içini hedef seçme
            if (WorldGen.blocked(wanderX, wanderZ, radius())) {
                wanderX = homeX;
                wanderZ = homeZ;
            }
        }
    }

    /** applyStep/animasyon için o karenin dt'si. */
    private float dt0 = 1f / 60f;

    private boolean blockedAt(GameWorld w, float nx, float nz) {
        // Şehir binalarının içinden geçilemez (üssün kendi ızgarası ayrı).
        if (WorldGen.blocked(nx, nz, radius() * 0.6f)
                && !WorldGen.blocked(x, z, radius() * 0.6f)) {
            return true;
        }
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
