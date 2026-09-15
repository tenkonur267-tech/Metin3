package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/**
 * Yoldaş (yardımcı NPC). Emir alır, ama emrin içinde kendi başına akıllı
 * davranır: hedef seçer, ateş hattı kapalıysa ateş etmez, duvarlara
 * takılmamak için A* yolu izler, canı azalınca geri çekilir, boştayken
 * rolünün işini yapar.
 */
public class Npc {
    public static final int ORDER_FOLLOW = 0;
    public static final int ORDER_HOLD = 1;
    public static final int ORDER_DEFEND_CORE = 2;
    public static final int ORDER_GATHER = 3;
    public static final int ORDER_REPAIR = 4;
    public static final int ORDER_ATTACK = 5;
    public static final int ORDER_RETREAT = 6;
    public static final int ORDER_COUNT = 7;

    public static final String[] ORDER_NAMES = {
            "Takip et", "Burayı tut", "Reaktörü koru", "Ganimet topla",
            "Yapıları onar", "Bölgeye saldır", "Geri çekil"
    };
    public static final String[] ORDER_SHORT = {
            "TAKİP", "TUT", "KORU", "TOPLA", "ONAR", "SALDIR", "ÇEKİL"
    };

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

    public int order = ORDER_FOLLOW;
    public float orderX, orderZ;
    /** Canı azalınca geçici geri çekilme (emir değişmez). */
    public boolean retreating;

    public Zombie target;
    public Structure workTarget;
    public Pickup lootTarget;
    public float fireCd;
    public float workGlow;
    public int collected;

    // yol takibi
    private final int[] path = new int[160];
    private int pathLen, pathIdx;
    private float repathTimer;
    private float goalX, goalZ;
    private float stuckTimer;
    private float lastX, lastZ;
    private float detourTimer;
    private float detourX, detourZ;
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
        this.order = ORDER_FOLLOW;
        this.chatCd = MathX.rnd(4f, 12f);
    }

    public Balance.NpcDef def() {
        return Balance.npc(role);
    }

    public String roleName() {
        return def().name;
    }

    public String statusText() {
        if (downed) return "yerde (" + Math.max(1, Math.round(downedTimer)) + " sn)";
        if (retreating) return "geri çekiliyor";
        return ORDER_NAMES[MathX.clampI(order, 0, ORDER_COUNT - 1)];
    }

    public float radius() {
        return 0.42f;
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
            w.onNpcDowned(this);
        }
    }

    public void heal(float amount) {
        if (downed) return;
        hp = Math.min(maxHp, hp + amount);
    }

    public void setOrder(int newOrder, float ox, float oz) {
        order = MathX.clampI(newOrder, 0, ORDER_COUNT - 1);
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

        if (downed) {
            downedTimer -= dt;
            if (downedTimer <= 0f) w.reviveNpc(this);
            return;
        }

        // Durum değerlendirmesi: canı azaldıysa kendi kararıyla çekilir.
        float frac = hp / Math.max(1f, maxHp);
        if (!retreating && frac < 0.28f && order != ORDER_RETREAT) {
            retreating = true;
            w.npcSays(this, "Vuruldum, geri çekiliyorum!");
        } else if (retreating && frac > 0.72f) {
            retreating = false;
            w.npcSays(this, "İyileştim, göreve dönüyorum.");
        }
        if (retreating || order == ORDER_RETREAT) {
            if (w.nearBase(x, z, 12f)) hp = Math.min(maxHp, hp + 3.5f * dt);
        }

        acquireTarget(w);
        float destX = x, destZ = z;
        float arrive = 1.2f;
        boolean holdStill = false;

        if (retreating || order == ORDER_RETREAT) {
            destX = 0f;
            destZ = 0f;
            arrive = 7f;
        } else {
            switch (order) {
                case ORDER_HOLD:
                    destX = orderX;
                    destZ = orderZ;
                    arrive = 0.9f;
                    break;
                case ORDER_DEFEND_CORE: {
                    float a = MathX.TAU * (index % 6) / 6f;
                    destX = (float) Math.cos(a) * 6.5f;
                    destZ = (float) Math.sin(a) * 6.5f;
                    arrive = 1.1f;
                    break;
                }
                case ORDER_GATHER: {
                    Pickup p = pickLoot(w, 200f);
                    if (p != null) {
                        destX = p.x;
                        destZ = p.z;
                        arrive = 0.7f;
                    } else {
                        destX = w.player.x;
                        destZ = w.player.z;
                        arrive = 2.2f;
                    }
                    break;
                }
                case ORDER_REPAIR: {
                    Structure s = pickRepair(w, 200f);
                    if (s != null) {
                        destX = s.x;
                        destZ = s.z;
                        arrive = 1.8f;
                    } else {
                        destX = w.player.x;
                        destZ = w.player.z;
                        arrive = 2.2f;
                    }
                    break;
                }
                case ORDER_ATTACK:
                    destX = orderX;
                    destZ = orderZ;
                    arrive = 2.4f;
                    if (target != null && MathX.dist(x, z, target.x, target.z) < def().range) {
                        holdStill = true;
                    }
                    break;
                default: {   // takip
                    float a = MathX.TAU * (index % 6) / 6f + 0.6f;
                    destX = w.player.x + (float) Math.cos(a) * 2.4f;
                    destZ = w.player.z + (float) Math.sin(a) * 2.4f;
                    arrive = 1.3f;
                    break;
                }
            }
            // Boştayken rolünün işini kendiliğinden yapar.
            if (order == ORDER_FOLLOW || order == ORDER_HOLD || order == ORDER_DEFEND_CORE) {
                float leash = order == ORDER_FOLLOW ? 9f : 11f;
                if (role == Balance.NPC_SCAVENGER) {
                    Pickup p = pickLoot(w, leash);
                    if (p != null) {
                        destX = p.x;
                        destZ = p.z;
                        arrive = 0.7f;
                    }
                } else if (role == Balance.NPC_ENGINEER) {
                    Structure s = pickRepair(w, leash);
                    if (s != null) {
                        destX = s.x;
                        destZ = s.z;
                        arrive = 1.8f;
                    }
                } else if (role == Balance.NPC_MEDIC) {
                    float[] h = w.nearestHurtAlly(x, z, leash);
                    if (h != null) {
                        destX = h[0];
                        destZ = h[1];
                        arrive = 1.6f;
                    }
                }
            }
        }

        // Hedefe ateş ederken duran roller
        if (!holdStill && target != null && role == Balance.NPC_GUARD
                && MathX.dist(x, z, target.x, target.z) < def().range * 0.8f
                && order != ORDER_GATHER && order != ORDER_REPAIR) {
            holdStill = true;
        }

        if (!holdStill) {
            moveTo(w, destX, destZ, dt, arrive);
        } else {
            moving = false;
        }

        doRoleWork(w, dt);
        shoot(w, dt);

        if (target != null) {
            yaw = MathX.approachAngle(yaw, (float) Math.atan2(target.x - x, target.z - z), dt * 9f);
        }
    }

    // ---- hedefleme ve ateş ---------------------------------------------

    private void acquireTarget(GameWorld w) {
        if (retreating || order == ORDER_RETREAT || role == Balance.NPC_SCAVENGER
                && order == ORDER_GATHER) {
            if (role != Balance.NPC_SCAVENGER) target = null;
        }
        Balance.NpcDef d = def();
        if (target != null && (!target.alive
                || MathX.dist(x, z, target.x, target.z) > d.range * 1.25f)) {
            target = null;
        }
        if (target == null) {
            target = w.bestZombieFor(x, z, d.range * 1.15f);
        }
    }

    private void shoot(GameWorld w, float dt) {
        if (target == null || downed) return;
        if (retreating || order == ORDER_RETREAT) return;
        Balance.NpcDef d = def();
        float dist = MathX.dist(x, z, target.x, target.z);
        if (dist > d.range || fireCd > 0f) return;
        if (!PathFinder.clearLine(w.grid, x, z, target.x, target.z)) return;   // duvara ateş etmez
        fireCd = 1f / Math.max(0.1f, d.fireRate);
        w.npcShoot(this, target);
    }

    /** Silah namlusunun dünya konumu. */
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

    // ---- rol işleri -----------------------------------------------------

    private void doRoleWork(GameWorld w, float dt) {
        Balance.NpcDef d = def();
        switch (role) {
            case Balance.NPC_ENGINEER: {
                Structure s = workTarget;
                if (s != null && s.alive && s.hp < s.maxHp
                        && MathX.dist(x, z, s.x, s.z) < 2.6f + s.footprintRadius()) {
                    s.repair(d.workAt(level) * w.player.repairBonus() * dt);
                    workGlow = 1f;
                    if (MathX.chance(dt * 3f)) {
                        w.particles.sparks(s.x + MathX.rnd(-0.5f, 0.5f), 1f,
                                s.z + MathX.rnd(-0.5f, 0.5f), 3, 0xFFB74D);
                    }
                }
                break;
            }
            case Balance.NPC_MEDIC: {
                float healed = d.workAt(level) * dt;
                if (w.player.alive && MathX.dist(x, z, w.player.x, w.player.z) < 3.2f
                        && w.player.hp < w.player.maxHp) {
                    w.player.heal(healed);
                    workGlow = 1f;
                }
                for (int i = 0; i < w.npcs.size(); i++) {
                    Npc o = w.npcs.get(i);
                    if (o == this || !o.alive) continue;
                    float dd = MathX.dist(x, z, o.x, o.z);
                    if (dd > 3.2f) continue;
                    if (o.downed) {
                        o.downedTimer -= dt * 2.2f;   // düşeni kaldırmayı hızlandırır
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
            case Balance.NPC_SCAVENGER: {
                Pickup p = lootTarget;
                if (p != null && p.alive && MathX.dist(x, z, p.x, p.z) < 1.1f) {
                    collected += p.amount;
                    w.collectPickup(p, false);
                    lootTarget = null;
                    workGlow = 1f;
                }
                break;
            }
            default:
                break;
        }
    }

    private Pickup pickLoot(GameWorld w, float range) {
        if (lootTarget != null && lootTarget.alive
                && MathX.dist(x, z, lootTarget.x, lootTarget.z) < range) {
            return lootTarget;
        }
        lootTarget = w.nearestPickup(x, z, range);
        return lootTarget;
    }

    private Structure pickRepair(GameWorld w, float range) {
        if (workTarget != null && workTarget.alive && workTarget.hp < workTarget.maxHp
                && MathX.dist(x, z, workTarget.x, workTarget.z) < range) {
            return workTarget;
        }
        workTarget = w.mostDamagedStructure(x, z, range);
        return workTarget;
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
            pathLen = 0;               // yol açık, doğrudan git
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

        // Takılma kontrolü: yerinde sayıyorsa yolu yenile, olmazsa kısa bir
        // sapma noktası seçip duvarı dolaşmayı dener.
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

    /** Yol bulunamadıysa duvarı yandan dolaşmayı dene. */
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
