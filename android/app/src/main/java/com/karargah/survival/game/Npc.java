package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/**
 * Yoldaş (yardımcı NPC).
 *
 * İki ayrı kavram var: <b>duruş</b> nerede duracağını söyler (takip et,
 * burayı tut, reaktörü koru, bölgeye saldır, geri çekil) ve <b>görevler</b>
 * ne iş yapacağını söyler. Görevler bit maskesidir: tek bir yoldaşa aynı anda
 * savaş + onar + inşa + topla + iyileştir verilebilir. Hangi işi önce
 * yapacağına her karede aciliyet ve mesafeye bakarak kendisi karar verir.
 */
public class Npc {
    public static final int TASK_NONE = 0;
    public static final int TASK_BUILD = 1;
    public static final int TASK_REPAIR = 2;
    public static final int TASK_HEAL = 3;
    public static final int TASK_GATHER = 4;

    private static final String[] NAMES = {
            "Kerem", "Selim", "Deniz", "Ayşe", "Baran", "Ece", "Tuna", "Mert",
            "Zeynep", "Onur", "Derya", "Kaya", "İlke", "Sarp", "Nil", "Emre"
    };

    public int role;
    public int level = 1;
    public String name;
    public int index;

    public float x, y, z, yaw;
    public float hp, maxHp;
    public boolean alive = true;
    public boolean downed;
    public float downedTimer;
    public float flash;
    public float animPhase;
    public boolean moving;

    /** Duruş: Balance.STANCE_*. */
    public int stance = Balance.STANCE_FOLLOW;
    /** Görev maskesi: Balance.DUTY_* bitleri. */
    public int duties = Balance.DUTY_FIGHT;
    public float orderX, orderZ;
    public boolean retreating;

    public Zombie target;
    public Structure workTarget;
    public Pickup lootTarget;
    public BuildPlan planTarget;
    public int task = TASK_NONE;
    public float fireCd;
    public float workGlow;
    public int collected;
    public int built;
    public int repaired;

    // yol takibi
    private final int[] path = new int[160];
    private int pathLen, pathIdx;
    private float repathTimer;
    private float goalX, goalZ;
    private float stuckTimer;
    private float lastX, lastZ;
    private float detourTimer;
    private float detourX, detourZ;
    private float taskTimer;
    /** Aynı işte ne kadar süredir çalışıyor (uzun sürerse diğer görevlere sıra gelir). */
    private float taskElapsed;
    /** Yeni seçilen işe en az bu kadar bağlı kalır (işler arasında titremesin). */
    private float taskLock;
    public float chatCd;

    private final float[] muzzle = new float[3];

    public void init(int role, int level, int index, float x, float z) {
        this.role = role;
        this.level = Math.max(1, level);
        this.index = index;
        this.name = NAMES[MathX.rndInt(NAMES.length)];
        this.x = x;
        this.z = z;
        this.y = 0f;
        this.maxHp = def().hpAt(this.level);
        this.hp = maxHp;
        this.alive = true;
        this.downed = false;
        this.stance = Balance.STANCE_FOLLOW;
        this.duties = Balance.defaultDuties(role);
        this.chatCd = MathX.rnd(4f, 12f);
    }

    public Balance.NpcDef def() {
        return Balance.npc(role);
    }

    public String roleName() {
        return def().name;
    }

    public boolean hasDuty(int duty) {
        return (duties & duty) != 0;
    }

    public void toggleDuty(int duty) {
        duties ^= duty;
    }

    /** Görev harflerinin kısa gösterimi (S O İ T +). */
    public String dutyLetters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Balance.DUTY_BITS.length; i++) {
            if (hasDuty(Balance.DUTY_BITS[i])) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Balance.DUTY_LETTER[i]);
            }
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    public String statusText() {
        if (downed) return "yerde (" + Math.max(1, Math.round(downedTimer)) + " sn)";
        if (retreating) return "geri çekiliyor";
        switch (task) {
            case TASK_BUILD: return "inşa ediyor";
            case TASK_REPAIR: return "onarıyor";
            case TASK_HEAL: return "iyileştiriyor";
            case TASK_GATHER: return "ganimet topluyor";
            default:
                return target != null ? "çatışmada"
                        : Balance.STANCE_NAMES[MathX.clampI(stance, 0, Balance.STANCE_COUNT - 1)];
        }
    }

    public float radius() {
        return 0.42f;
    }

    /** Görev için iş gücü (saniyede). */
    public float workRate(int duty) {
        return def().workAt(level) * def().mulFor(duty);
    }

    public void hurt(float amount, GameWorld w) {
        if (downed || !alive) return;
        hp -= amount;
        flash = 0.2f;
        if (hp <= 0f) {
            hp = 0f;
            downed = true;
            downedTimer = Balance.NPC_REVIVE_TIME;
            target = null;
            task = TASK_NONE;
            w.onNpcDowned(this);
        }
    }

    public void heal(float amount) {
        if (downed) return;
        hp = Math.min(maxHp, hp + amount);
    }

    public void setStance(int newStance, float ox, float oz) {
        stance = MathX.clampI(newStance, 0, Balance.STANCE_COUNT - 1);
        orderX = ox;
        orderZ = oz;
        pathLen = 0;
        repathTimer = 0f;
        retreating = false;
    }

    // ---- kare güncellemesi ---------------------------------------------

    public void update(GameWorld w, float dt) {
        if (!alive) return;
        if (flash > 0f) flash = Math.max(0f, flash - dt * 4f);
        if (workGlow > 0f) workGlow = Math.max(0f, workGlow - dt * 3f);
        if (fireCd > 0f) fireCd -= dt;
        if (chatCd > 0f) chatCd -= dt;
        if (taskTimer > 0f) taskTimer -= dt;
        if (taskLock > 0f) taskLock -= dt;
        taskElapsed += dt;

        if (downed) {
            downedTimer -= dt;
            if (downedTimer <= 0f) w.reviveNpc(this);
            return;
        }

        float frac = hp / Math.max(1f, maxHp);
        if (!retreating && frac < 0.28f && stance != Balance.STANCE_RETREAT) {
            retreating = true;
            w.npcSays(this, "Vuruldum, geri çekiliyorum!");
        } else if (retreating && frac > 0.72f) {
            retreating = false;
            w.npcSays(this, "İyileştim, göreve dönüyorum.");
        }
        boolean withdrawing = retreating || stance == Balance.STANCE_RETREAT;
        if (withdrawing && w.nearBase(x, z, 12f)) {
            hp = Math.min(maxHp, hp + 3.5f * dt);
        }

        acquireTarget(w);
        if (withdrawing) {
            task = TASK_NONE;
        } else if (taskTimer <= 0f) {
            chooseTask(w);
            taskTimer = 0.4f;     // her karede hedef değiştirip titremesin
        }
        validateTask(w);

        float destX = x, destZ = z;
        float arrive = 1.2f;
        boolean holdStill = false;

        if (withdrawing) {
            destX = 0f;
            destZ = 0f;
            arrive = 7f;
        } else if (task != TASK_NONE) {
            float[] tp = taskPoint(w);
            destX = tp[0];
            destZ = tp[1];
            arrive = taskRange(w) * 0.8f;
        } else {
            switch (stance) {
                case Balance.STANCE_HOLD:
                    destX = orderX;
                    destZ = orderZ;
                    arrive = 0.9f;
                    break;
                case Balance.STANCE_DEFEND: {
                    float a = MathX.TAU * (index % 6) / 6f;
                    destX = (float) Math.cos(a) * 6.5f;
                    destZ = (float) Math.sin(a) * 6.5f;
                    arrive = 1.1f;
                    break;
                }
                case Balance.STANCE_ATTACK:
                    destX = orderX;
                    destZ = orderZ;
                    arrive = 2.4f;
                    break;
                default: {
                    float a = MathX.TAU * (index % 6) / 6f + 0.6f;
                    destX = w.player.x + (float) Math.cos(a) * 2.4f;
                    destZ = w.player.z + (float) Math.sin(a) * 2.4f;
                    arrive = 1.3f;
                    break;
                }
            }
        }

        // Dövüş görevi varsa ve düşman menzildeyse durup ateş eder.
        if (!withdrawing && target != null && hasDuty(Balance.DUTY_FIGHT)) {
            float td = MathX.dist(x, z, target.x, target.z);
            boolean threat = td < 6.5f;
            if (threat || (task == TASK_NONE && td < def().range * 0.85f)) {
                holdStill = true;
            }
        }

        if (!holdStill) {
            moveTo(w, destX, destZ, dt, arrive);
        } else {
            moving = false;
        }

        doTask(w, dt);
        shoot(w, dt);

        if (target != null) {
            yaw = MathX.approachAngle(yaw, (float) Math.atan2(target.x - x, target.z - z), dt * 9f);
        }
    }

    // ---- görev seçimi ---------------------------------------------------

    /** Duruşa göre işlerin yapılabileceği yarıçap (bölgeden kopmasın). */
    private float leash(GameWorld w) {
        switch (stance) {
            case Balance.STANCE_HOLD: return 14f;
            case Balance.STANCE_DEFEND: return 16f;
            case Balance.STANCE_ATTACK: return 8f;
            default: return 13f;
        }
    }

    /**
     * Uzun süredir aynı işi yapıyorsa o işin puanı düşer; böylece birden çok
     * görevi olan yoldaş işler arasında sırayla dolaşır, tek işe saplanmaz.
     */
    private int rotatingFrom;

    private float rotationPenalty(int candidate) {
        if (candidate != rotatingFrom || taskElapsed < 8f) return 0f;
        return Math.min(70f, (taskElapsed - 8f) * 10f);
    }

    private void chooseTask(GameWorld w) {
        // Seçilen işe kısa süre bağlı kal: yoksa iki iş arasında gidip gelir.
        if (task != TASK_NONE && taskLock > 0f) return;
        int previous = task;
        rotatingFrom = previous;
        task = TASK_NONE;
        planTarget = null;
        workTarget = null;
        lootTarget = null;
        float range = leash(w);
        boolean prepare = w.waves.isPrepare();
        float best = 0f;

        if (hasDuty(Balance.DUTY_BUILD)) {
            BuildPlan p = w.nearestPlan(x, z, range * 2.5f);
            if (p != null) {
                float score = 90f + (prepare ? 45f : 0f) - MathX.dist(x, z, p.x, p.z) * 0.6f
                        - rotationPenalty(TASK_BUILD);
                if (p.waiting && !w.canAfford(w.player.buildCost(p.def().cost))) score -= 80f;
                if (score > best) {
                    best = score;
                    task = TASK_BUILD;
                    planTarget = p;
                }
            }
        }
        if (hasDuty(Balance.DUTY_REPAIR)) {
            Structure s = w.mostDamagedStructure(x, z, range);
            if (s != null) {
                float score = 40f + 70f * (1f - s.hpFraction()) - MathX.dist(x, z, s.x, s.z) * 0.5f
                        - rotationPenalty(TASK_REPAIR);
                if (s.type == Balance.S_CORE) score += 30f;
                if (score > best) {
                    best = score;
                    task = TASK_REPAIR;
                    workTarget = s;
                    planTarget = null;
                }
            }
        }
        if (hasDuty(Balance.DUTY_HEAL)) {
            float[] h = w.nearestHurtAllyNeed(x, z, range);
            if (h != null) {
                float score = 45f + 90f * h[2] - MathX.dist(x, z, h[0], h[1]) * 0.5f
                        - rotationPenalty(TASK_HEAL);
                if (score > best) {
                    best = score;
                    task = TASK_HEAL;
                    healX = h[0];
                    healZ = h[1];
                    workTarget = null;
                    planTarget = null;
                }
            }
        }
        if (hasDuty(Balance.DUTY_GATHER)) {
            Pickup p = w.nearestPickup(x, z, range * 2.2f);
            if (p != null) {
                float score = 26f + Math.min(24f, w.pickups.size() * 1.5f)
                        - MathX.dist(x, z, p.x, p.z) * 0.45f - rotationPenalty(TASK_GATHER);
                if (score > best) {
                    best = score;
                    task = TASK_GATHER;
                    lootTarget = p;
                    workTarget = null;
                    planTarget = null;
                }
            }
        }
        if (best <= 0f) task = TASK_NONE;
        if (task != previous) {
            taskElapsed = 0f;
            taskLock = task == TASK_NONE ? 0f : 4.5f;
        }
    }

    private float healX, healZ;

    private void validateTask(GameWorld w) {
        switch (task) {
            case TASK_BUILD:
                if (planTarget == null || !planTarget.alive) task = TASK_NONE;
                break;
            case TASK_REPAIR:
                if (workTarget == null || !workTarget.alive || workTarget.hp >= workTarget.maxHp) {
                    task = TASK_NONE;
                }
                break;
            case TASK_GATHER:
                if (lootTarget == null || !lootTarget.alive) task = TASK_NONE;
                break;
            default:
                break;
        }
    }

    private final float[] taskPointTmp = new float[2];

    private float[] taskPoint(GameWorld w) {
        switch (task) {
            case TASK_BUILD:
                taskPointTmp[0] = planTarget.x;
                taskPointTmp[1] = planTarget.z;
                break;
            case TASK_REPAIR:
                taskPointTmp[0] = workTarget.x;
                taskPointTmp[1] = workTarget.z;
                break;
            case TASK_HEAL:
                taskPointTmp[0] = healX;
                taskPointTmp[1] = healZ;
                break;
            case TASK_GATHER:
                taskPointTmp[0] = lootTarget.x;
                taskPointTmp[1] = lootTarget.z;
                break;
            default:
                taskPointTmp[0] = x;
                taskPointTmp[1] = z;
                break;
        }
        return taskPointTmp;
    }

    private float taskRange(GameWorld w) {
        switch (task) {
            case TASK_BUILD: return 2.4f;
            case TASK_REPAIR: return 2.6f + (workTarget != null ? workTarget.footprintRadius() : 0f);
            case TASK_HEAL: return 3.0f;
            case TASK_GATHER: return 1.0f;
            default: return 1.5f;
        }
    }

    private void doTask(GameWorld w, float dt) {
        if (task == TASK_NONE) return;
        float[] tp = taskPoint(w);
        float d = MathX.dist(x, z, tp[0], tp[1]);
        if (d > taskRange(w)) return;

        switch (task) {
            case TASK_BUILD: {
                float rate = Balance.BUILD_WORK_PER_SEC * workRate(Balance.DUTY_BUILD);
                if (w.workOnPlan(this, planTarget, dt, rate)) workGlow = 1f;
                break;
            }
            case TASK_REPAIR: {
                float amount = workRate(Balance.DUTY_REPAIR) * w.player.repairBonus() * dt;
                workTarget.repair(amount);
                repaired += Math.round(amount);
                workGlow = 1f;
                if (MathX.chance(dt * 3f)) {
                    w.particles.sparks(workTarget.x + MathX.rnd(-0.5f, 0.5f), 1f,
                            workTarget.z + MathX.rnd(-0.5f, 0.5f), 3, 0xFFB74D);
                }
                break;
            }
            case TASK_HEAL: {
                float healed = workRate(Balance.DUTY_HEAL) * dt;
                if (w.player.alive && MathX.dist(x, z, w.player.x, w.player.z) < 3.2f) {
                    w.player.heal(healed);
                    workGlow = 1f;
                }
                for (int i = 0; i < w.npcs.size(); i++) {
                    Npc o = w.npcs.get(i);
                    if (o == this) continue;
                    if (MathX.dist(x, z, o.x, o.z) > 3.2f) continue;
                    if (o.downed) {
                        o.downedTimer -= dt * (1.2f + def().healMul);
                        workGlow = 1f;
                    } else if (o.hp < o.maxHp) {
                        o.heal(healed);
                        workGlow = 1f;
                    }
                }
                if (workGlow > 0f && MathX.chance(dt * 4f)) {
                    w.particles.spawn(x, 1.4f, z, 0f, 1.2f, 0f, 0xE57373, 0.7f,
                            0.1f, 0.02f, 0.5f, 0.3f, 1f, 1);
                }
                break;
            }
            case TASK_GATHER: {
                collected += lootTarget.amount;
                w.collectPickup(lootTarget, false);
                lootTarget = null;
                task = TASK_NONE;
                workGlow = 1f;
                break;
            }
            default:
                break;
        }
    }

    // ---- hedefleme ve ateş ---------------------------------------------

    private void acquireTarget(GameWorld w) {
        Balance.NpcDef d = def();
        if (target != null && (!target.alive
                || MathX.dist(x, z, target.x, target.z) > d.range * 1.25f)) {
            target = null;
        }
        if (target == null) {
            // Dövüş görevi kapalı olsa bile 7 birim içindeki tehdide karşılık verir.
            float range = hasDuty(Balance.DUTY_FIGHT) ? d.range * 1.15f : 7f;
            target = w.bestZombieFor(x, z, range);
        }
    }

    private void shoot(GameWorld w, float dt) {
        if (target == null || downed) return;
        if (retreating || stance == Balance.STANCE_RETREAT) return;
        Balance.NpcDef d = def();
        float dist = MathX.dist(x, z, target.x, target.z);
        if (dist > d.range || fireCd > 0f) return;
        if (!PathFinder.clearLine(w.grid, x, z, target.x, target.z)) return;
        float rate = d.fireRate * (hasDuty(Balance.DUTY_FIGHT) ? 1f : 0.6f);
        fireCd = 1f / Math.max(0.1f, rate);
        w.npcShoot(this, target);
    }

    public void muzzleWorld(float[] out) {
        Balance.WeaponDef wd = Balance.weapon(def().weapon);
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float wx = x + (Models.HAND_X * c + Models.HAND_Z * s);
        float wz = z + (-Models.HAND_X * s + Models.HAND_Z * c);
        out[0] = wx + (float) Math.sin(yaw) * wd.muzzleZ;
        out[1] = Models.HAND_Y + wd.muzzleY;
        out[2] = wz + (float) Math.cos(yaw) * wd.muzzleZ;
    }

    public float[] muzzle() {
        muzzleWorld(muzzle);
        return muzzle;
    }

    // ---- hareket --------------------------------------------------------

    private void moveTo(GameWorld w, float tx, float tz, float dt, float arrive) {
        float dist = MathX.dist(x, z, tx, tz);
        if (dist <= arrive) {
            moving = false;
            pathLen = 0;
            return;
        }

        float dirX, dirZ;
        if (detourTimer > 0f) {
            detourTimer -= dt;
            dirX = detourX - x;
            dirZ = detourZ - z;
            if (MathX.len(dirX, dirZ) < 0.5f) detourTimer = 0f;
        } else if (PathFinder.clearLine(w.grid, x, z, tx, tz)) {
            pathLen = 0;
            dirX = tx - x;
            dirZ = tz - z;
        } else {
            repathTimer -= dt;
            boolean goalMoved = MathX.dist(goalX, goalZ, tx, tz) > 2.5f;
            if (pathLen == 0 || repathTimer <= 0f || goalMoved) {
                requestPath(w, tx, tz);
            }
            float[] wp = nextWaypoint(w);
            if (wp == null) {
                dirX = tx - x;
                dirZ = tz - z;
            } else {
                dirX = wp[0] - x;
                dirZ = wp[1] - z;
            }
        }

        float l = MathX.len(dirX, dirZ);
        if (l < 1e-4f) {
            moving = false;
            return;
        }
        dirX /= l;
        dirZ /= l;

        float sp = def().speed * (retreating ? 1.15f : 1f);
        float nx = x + dirX * sp * dt;
        float nz = z + dirZ * sp * dt;
        boolean movedX = false, movedZ = false;
        if (!blocked(w, nx, z)) {
            x = nx;
            movedX = true;
        }
        if (!blocked(w, x, nz)) {
            z = nz;
            movedZ = true;
        }
        float half = Balance.WORLD_HALF - 1.5f;
        x = MathX.clamp(x, -half, half);
        z = MathX.clamp(z, -half, half);

        moving = true;
        animPhase += dt * (5f + sp * 0.6f);
        if (target == null) {
            yaw = MathX.approachAngle(yaw, (float) Math.atan2(dirX, dirZ), dt * 8f);
        }

        stuckTimer += dt;
        if (stuckTimer > 0.6f) {
            float moved = MathX.dist(lastX, lastZ, x, z);
            if (moved < 0.12f && (!movedX || !movedZ)) {
                if (pathLen > 0) {
                    requestPath(w, tx, tz);
                } else {
                    pickDetour(w, tx, tz);
                }
            }
            lastX = x;
            lastZ = z;
            stuckTimer = 0f;
        }
    }

    private void requestPath(GameWorld w, float tx, float tz) {
        repathTimer = MathX.rnd(0.7f, 1.1f);
        goalX = tx;
        goalZ = tz;
        pathLen = 0;
        pathIdx = 0;
        int sx = BuildGrid.worldToCell(x), sz = BuildGrid.worldToCell(z);
        int gx = BuildGrid.worldToCell(tx), gz = BuildGrid.worldToCell(tz);
        if (w.pathFinder.find(w.grid, sx, sz, gx, gz, 2200)) {
            int n = Math.min(w.pathFinder.pathLength, path.length);
            System.arraycopy(w.pathFinder.path, 0, path, 0, n);
            pathLen = n;
            pathIdx = Math.min(1, n - 1);
        }
    }

    private final float[] wpTmp = new float[2];

    private float[] nextWaypoint(GameWorld w) {
        while (pathIdx < pathLen) {
            int cell = path[pathIdx];
            float cx = BuildGrid.cellToWorld(cell % Balance.GRID);
            float cz = BuildGrid.cellToWorld(cell / Balance.GRID);
            if (MathX.dist(x, z, cx, cz) < 0.75f) {
                pathIdx++;
                continue;
            }
            wpTmp[0] = cx;
            wpTmp[1] = cz;
            return wpTmp;
        }
        pathLen = 0;
        return null;
    }

    private void pickDetour(GameWorld w, float tx, float tz) {
        float dx = tx - x, dz = tz - z;
        float l = MathX.len(dx, dz);
        if (l < 1e-3f) return;
        dx /= l;
        dz /= l;
        float side = MathX.chance(0.5f) ? 1f : -1f;
        detourX = x + (-dz) * side * 3.2f + dx * 1.2f;
        detourZ = z + (dx) * side * 3.2f + dz * 1.2f;
        detourTimer = 1.4f;
    }

    private boolean blocked(GameWorld w, float nx, float nz) {
        int gx = BuildGrid.worldToCell(nx), gz = BuildGrid.worldToCell(nz);
        Structure s = w.grid.at(gx, gz);
        if (s == null || !s.blocks()) return false;
        if (w.grid.atWorld(x, z) == s) return false;
        float half = Balance.CELL * 0.5f;
        float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
        return Math.abs(nx - cx) < half + radius() * 0.6f
                && Math.abs(nz - cz) < half + radius() * 0.6f;
    }
}
