package com.karargah.survival.game;

import com.karargah.survival.engine.Audio;
import com.karargah.survival.engine.Camera;
import com.karargah.survival.engine.MathX;
import com.karargah.survival.engine.Particles;

import java.util.ArrayList;

/**
 * Oyunun simülasyon çekirdeği. Tüm varlıkları barındırır, her karede
 * günceller ve arayüzden gelen komutları işler. Yalnızca GL iş parçacığından
 * çağrılır; arayüz iş parçacığı sadece InputState üzerinden konuşur.
 */
public class GameWorld {
    public final Player player = new Player();
    public final BuildGrid grid = new BuildGrid();
    public final FlowField flow = new FlowField();
    public final WaveManager waves = new WaveManager();
    public final Camera camera = new Camera();
    public final Particles particles = new Particles(1600);
    public final Audio audio;
    public final InputState input;

    public final ArrayList<Zombie> zombies = new ArrayList<>();
    public final ArrayList<Structure> structures = new ArrayList<>();
    public final ArrayList<Projectile> projectiles = new ArrayList<>();
    public final ArrayList<Tracer> tracers = new ArrayList<>();
    public final ArrayList<FloatingText> texts = new ArrayList<>();

    private final ArrayList<Zombie> zombiePool = new ArrayList<>();
    private final ArrayList<Projectile> projPool = new ArrayList<>();
    private final ArrayList<Tracer> tracerPool = new ArrayList<>();
    private final ArrayList<FloatingText> textPool = new ArrayList<>();

    public Structure core;
    public boolean gameOver;
    public volatile boolean paused;
    public boolean started;
    public Structure selected;

    public String message = "";
    public float messageTimer;
    public String bigMessage = "";
    public float bigMessageTimer;

    public float powerGen, powerUse, powerEff = 1f;
    public int waveRecord;
    public int totalKills;
    public float playTime;
    public float nightFactor;      // 0 = gündüz (hazırlık), 1 = gece (dalga)

    private boolean flowDirty = true;
    private float flowTimer;
    private float camDistTarget = 14f;
    private float camPitchTarget = 0.66f;
    private final float[] camDelta = new float[3];
    private float statTimer;

    public int structuresBuilt;
    public int structuresLost;
    public int scrapEarned;

    public GameWorld(Audio audio, InputState input) {
        this.audio = audio;
        this.input = input;
        newGame();
    }

    // ---- kurulum --------------------------------------------------------

    public void newGame() {
        zombies.clear();
        structures.clear();
        projectiles.clear();
        tracers.clear();
        texts.clear();
        grid.clearAll();
        particles.clear();
        player.resetForNewGame();
        waves.reset();
        gameOver = false;
        paused = false;
        started = false;
        selected = null;
        totalKills = 0;
        playTime = 0f;
        structuresBuilt = 0;
        structuresLost = 0;
        scrapEarned = 0;
        nightFactor = 0f;

        createCore();
        // başlangıçta birkaç duvar hediye: oyuncu boş sahaya düşmesin
        int c = BuildGrid.N / 2;
        for (int i = -4; i <= 4; i++) {
            placeFree(Balance.S_WALL, c + i, c - 5);
            placeFree(Balance.S_WALL, c + i, c + 4);
        }
        for (int i = -4; i <= 3; i++) {
            placeFree(Balance.S_WALL, c - 5, c + i);
            placeFree(Balance.S_WALL, c + 4, c + i);
        }
        placeFree(Balance.S_MG, c - 3, c - 3);
        placeFree(Balance.S_MG, c + 2, c + 2);

        player.x = 0f;
        player.z = 7f;
        camera.targetX = player.x;
        camera.targetZ = player.z;
        camera.snapToTarget();
        flow.compute(grid);
        message("Reaktörü koru. İlk dalga yaklaşıyor.", 5f);
    }

    /** Kayıt yüklenirken çekirdeği yeniden kurar. */
    public void createCoreForLoad() {
        createCore();
    }

    private void createCore() {
        int c0 = BuildGrid.N / 2 - Balance.CORE_CELLS / 2;
        core = new Structure(Balance.S_CORE, 1, BuildGrid.N / 2, BuildGrid.N / 2, 1f);
        core.x = 0f;
        core.z = 0f;
        core.buildAnim = 0f;
        structures.add(core);
        for (int gz = c0; gz < c0 + Balance.CORE_CELLS; gz++) {
            for (int gx = c0; gx < c0 + Balance.CORE_CELLS; gx++) {
                grid.set(gx, gz, core);
            }
        }
    }

    /** Bedelsiz yerleştirme (oyun başlangıcı ve kayıttan yükleme). */
    public Structure placeFree(int type, int gx, int gz) {
        return placeFree(type, gx, gz, 1);
    }

    public Structure placeFree(int type, int gx, int gz, int level) {
        if (grid.canPlace(gx, gz) != BuildGrid.OK) return null;
        Structure s = new Structure(type, level, gx, gz, player.structHpBonus());
        structures.add(s);
        grid.set(gx, gz, s);
        flowDirty = true;
        return s;
    }

    // ---- ana döngü ------------------------------------------------------

    public volatile boolean pendingNewGame;
    public volatile boolean pendingLoaded;

    public void update(float dt) {
        if (pendingNewGame) {
            pendingNewGame = false;
            newGame();
        }
        processCommands();
        updateCameraControl(dt);

        if (paused || gameOver) {
            particles.update(dt * 0.25f);
            camera.targetX = player.x;
            camera.targetZ = player.z;
            camera.targetY = 1.2f;
            camera.update(dt);
            return;
        }

        playTime += dt;
        if (messageTimer > 0f) messageTimer -= dt;
        if (bigMessageTimer > 0f) bigMessageTimer -= dt;

        float targetNight = waves.phase == WaveManager.PHASE_WAVE ? 1f : 0f;
        nightFactor = MathX.damp(nightFactor, targetNight, 0.55f, dt);

        float inX = input.moveX;
        float inZ = input.moveZ;
        // Joystick, kameranın baktığı yöne göre yorumlanır.
        float cs = (float) Math.cos(camera.yaw), sn = (float) Math.sin(camera.yaw);
        float wx = inX * cs + inZ * sn;
        float wz = -inX * sn + inZ * cs;
        boolean firing = input.firing && !input.buildMode;

        player.update(this, dt, wx, wz, firing);

        updateZombies(dt);
        separateZombies();
        updateStructures(dt);
        updateProjectiles(dt);
        updateEffects(dt);
        particles.update(dt);
        waves.update(this, dt);

        flowTimer -= dt;
        if (flowDirty && flowTimer <= 0f) {
            flow.compute(grid);
            flowDirty = false;
            flowTimer = 0.35f;
        }

        camera.targetX = player.x;
        camera.targetZ = player.z;
        camera.targetY = 1.25f;
        camera.update(dt);

        statTimer += dt;
        if (statTimer > 1f) {
            statTimer = 0f;
            if (waves.phase == WaveManager.PHASE_WAVE && !zombies.isEmpty() && MathX.chance(0.35f)) {
                Zombie z = zombies.get(MathX.rndInt(zombies.size()));
                audio.playGrowl(z.x, z.z);
            }
        }
    }

    private void updateCameraControl(float dt) {
        input.consumeCamera(camDelta);
        camera.yaw -= camDelta[0];
        camera.pitch = MathX.clamp(camera.pitch + camDelta[1], 0.25f, 1.32f);
        camDistTarget = MathX.clamp(camDistTarget * (1f - camDelta[2]), 7f, 34f);

        float wanted = input.buildMode ? Math.max(camDistTarget, 19f) : camDistTarget;
        camera.distance = MathX.damp(camera.distance, wanted, 4.5f, dt);
        if (input.buildMode) {
            camera.pitch = MathX.damp(camera.pitch, Math.max(camera.pitch, 0.95f), 3f, dt);
        }
        camPitchTarget = camera.pitch;
    }

    private void processCommands() {
        Cmd c;
        while ((c = input.commands.poll()) != null) {
            switch (c.op) {
                case Cmd.RELOAD: player.startReload(); break;
                case Cmd.DASH: player.dash(); break;
                case Cmd.SWITCH_WEAPON: player.switchWeapon(c.a); break;
                case Cmd.PLACE: tryPlace(c.a, c.b, c.c); break;
                case Cmd.SELECT: selectAt(c.b, c.c); break;
                case Cmd.DESELECT: selected = null; break;
                case Cmd.UPGRADE_SELECTED: upgradeSelected(); break;
                case Cmd.SELL_SELECTED: sellSelected(); break;
                case Cmd.REPAIR_SELECTED: repairSelected(); break;
                case Cmd.REPAIR_ALL: repairAll(); break;
                case Cmd.START_WAVE: waves.skipPrepare(); break;
                case Cmd.SKILL_UP: doSkillUp(c.a); break;
                case Cmd.WEAPON_UP: upgradeWeapon(c.a); break;
                case Cmd.BUY_WEAPON: buyWeapon(c.a); break;
                case Cmd.BUY_AMMO: buyAmmo(); break;
                case Cmd.RESTART: newGame(); break;
                default: break;
            }
        }
    }

    // ---- zombiler -------------------------------------------------------

    private void updateZombies(float dt) {
        for (int i = zombies.size() - 1; i >= 0; i--) {
            Zombie z = zombies.get(i);
            z.update(this, dt);
            if (z.removeMe) {
                zombies.remove(i);
                zombiePool.add(z);
            }
        }
    }

    /** Zombilerin üst üste binmesini engelleyen basit itme kuvveti. */
    private void separateZombies() {
        int n = zombies.size();
        for (int i = 0; i < n; i++) {
            Zombie a = zombies.get(i);
            if (!a.alive) continue;
            for (int j = i + 1; j < n; j++) {
                Zombie b = zombies.get(j);
                if (!b.alive) continue;
                float dx = b.x - a.x, dz = b.z - a.z;
                float minD = a.radius() + b.radius();
                float d2 = dx * dx + dz * dz;
                if (d2 > minD * minD || d2 < 1e-5f) continue;
                float d = (float) Math.sqrt(d2);
                float overlap = (minD - d) / d * 0.5f;
                float px = dx * overlap * 3.4f;
                float pz = dz * overlap * 3.4f;
                float wa = b.isBoss() ? 1.8f : 1f;
                float wb = a.isBoss() ? 1.8f : 1f;
                a.pushX -= px * wa;
                a.pushZ -= pz * wa;
                b.pushX += px * wb;
                b.pushZ += pz * wb;
            }
        }
    }

    public void spawnZombie(int type, float x, float z, boolean elite) {
        Zombie zb;
        if (!zombiePool.isEmpty()) {
            zb = zombiePool.remove(zombiePool.size() - 1);
        } else {
            zb = new Zombie();
        }
        zb.init(type, x, z, waves.wave, elite);
        zombies.add(zb);
        particles.dust(x, 0.1f, z, 8);
    }

    public void onZombieKilled(Zombie z, Player killer) {
        totalKills++;
        player.kills++;
        particles.blood(z.x, z.centerY(), z.z, z.lastDamageDirX, z.lastDamageDirZ, z.isBoss() ? 34 : 14);
        audio.playZombieDie(z.isBoss());
        int scrap = Math.round(z.scrapValue * player.scrapBonus());
        player.scrap += scrap;
        scrapEarned += scrap;
        player.addXp(z.xpValue, this);
        if (player.lifesteal() > 0f) player.heal(player.lifesteal());
        addText(z.x, z.centerY() + 0.7f, z.z, "+" + scrap, 0xFFD54F, 1.0f, z.isBoss() ? 1.5f : 0.9f);
        if (z.isBoss()) {
            int c = 2 + waves.wave / 10;
            player.cores += c;
            addText(z.x, z.centerY() + 1.6f, z.z, "+" + c + " çekirdek", 0x4DD0E1, 2.2f, 1.4f);
            camera.addShake(0.7f);
            particles.explosion(z.x, z.centerY(), z.z, 3.5f);
        }
    }

    public Zombie zombieAt(float x, float y, float z, float radius) {
        for (int i = 0; i < zombies.size(); i++) {
            Zombie zz = zombies.get(i);
            if (!zz.alive) continue;
            float r = zz.radius() + radius;
            if (MathX.dist2(x, z, zz.x, zz.z) < r * r
                    && y > zz.y - 0.3f && y < zz.y + zz.height() + 0.4f) {
                return zz;
            }
        }
        return null;
    }

    /** Oyuncunun otomatik nişan aldığı hedef. */
    public Zombie aimTarget(Player p) {
        Balance.WeaponDef def = p.weapon();
        float best = def.range * def.range;
        Zombie bestZ = null;
        float fx = (float) Math.sin(p.aimYaw), fz = (float) Math.cos(p.aimYaw);
        for (int i = 0; i < zombies.size(); i++) {
            Zombie z = zombies.get(i);
            if (!z.alive || z.state == Zombie.ST_SPAWN) continue;
            float d2 = MathX.dist2(p.x, p.z, z.x, z.z);
            if (d2 > best) continue;
            float d = (float) Math.sqrt(d2);
            if (d > 0.01f) {
                float dot = ((z.x - p.x) * fx + (z.z - p.z) * fz) / d;
                // arkadaki hedefleri ancak çok yakınsa seç
                if (dot < -0.25f && d > 4f) continue;
                d2 *= (1.25f - dot * 0.25f);
                if (d2 > best) continue;
            }
            best = d2;
            bestZ = z;
        }
        return bestZ;
    }

    // ---- ateş etme ------------------------------------------------------

    public void hitscan(float x, float y, float z, float dx, float dz, float range,
                        float damage, boolean pierce, boolean crit, Player shooter) {
        float step = 0.45f;
        float travelled = 0f;
        Zombie last = null;
        float hitX = x + dx * range, hitZ = z + dz * range;
        boolean hitAny = false;
        while (travelled < range) {
            travelled += step;
            float px = x + dx * travelled;
            float pz = z + dz * travelled;
            Zombie t = null;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < zombies.size(); i++) {
                Zombie zz = zombies.get(i);
                if (!zz.alive || zz == last || zz.state == Zombie.ST_SPAWN) continue;
                float r = zz.radius() + 0.18f;
                float d2 = MathX.dist2(px, pz, zz.x, zz.z);
                if (d2 < r * r && d2 < bestD) {
                    bestD = d2;
                    t = zz;
                }
            }
            if (t != null) {
                hitAny = true;
                hitX = px;
                hitZ = pz;
                applyBulletHit(t, damage, dx, dz, crit);
                if (!pierce) break;
                last = t;
            }
        }
        tracer(x, y, z, hitX, y * 0.92f, hitZ,
                crit ? 0xFFF176 : 0xFFE0A3, 0.07f, crit ? 0.06f : 0.04f);
        if (!hitAny) {
            particles.dust(hitX, 0.1f, hitZ, 3);
        }
    }

    private void applyBulletHit(Zombie t, float damage, float dx, float dz, boolean crit) {
        boolean killed = t.hurt(damage, dx, dz);
        t.knockback(dx, dz, crit ? 2.6f : 1.3f);
        particles.blood(t.x, t.centerY(), t.z, dx, dz, crit ? 12 : 6);
        addText(t.x, t.centerY() + 0.6f, t.z, String.valueOf(Math.round(damage)),
                crit ? 0xFFF176 : 0xFFFFFF, 0.65f, crit ? 1.25f : 0.8f);
        if (killed) onZombieKilled(t, player);
    }

    public void spawnRocket(float x, float y, float z, float dx, float dz,
                            float damage, float splash) {
        Projectile p = obtainProjectile();
        p.initRocket(x, y, z, dx, dz, damage, splash);
        projectiles.add(p);
    }

    public void spawnAcid(Zombie from, float tx, float tz) {
        Projectile p = obtainProjectile();
        float dx = tx - from.x, dz = tz - from.z;
        float d = MathX.len(dx, dz);
        if (d < 0.01f) d = 0.01f;
        float speed = 17f;
        float t = d / speed;
        float vy = (0.9f + 0.5f * 13f * t * t) / Math.max(0.15f, t);
        p.initAcid(from.x, from.centerY() + 0.4f, from.z, dx / d, vy / speed, dz / d,
                speed, from.damage);
        projectiles.add(p);
        audio.playSpit();
    }

    public void explosionDamage(float x, float z, float radius, float damage, Projectile src) {
        for (int i = 0; i < zombies.size(); i++) {
            Zombie zz = zombies.get(i);
            if (!zz.alive) continue;
            float d = MathX.dist(x, z, zz.x, zz.z);
            if (d > radius + zz.radius()) continue;
            float f = MathX.clamp(1f - d / (radius + zz.radius()), 0.25f, 1f);
            float dmg = damage * f;
            boolean killed = zz.hurt(dmg, zz.x - x, zz.z - z);
            zz.knockback(zz.x - x, zz.z - z, 5.5f * f);
            addText(zz.x, zz.centerY() + 0.6f, zz.z, String.valueOf(Math.round(dmg)), 0xFFAB40, 0.7f, 1f);
            if (killed) onZombieKilled(zz, player);
        }
        camera.addShake(0.3f);
    }

    public void acidSplash(float x, float z, float radius, float damage) {
        if (player.alive && MathX.dist(x, z, player.x, player.z) < radius) {
            player.hurt(damage, this);
        }
        Structure s = grid.atWorld(x, z);
        if (s != null && s.alive && s != core) {
            s.damage(damage * 0.8f);
            if (!s.alive) onStructureDestroyed(s);
        }
        for (int i = 0; i < 10; i++) {
            particles.spawn(x, 0.1f, z, MathX.rnd(-2f, 2f), MathX.rnd(0.5f, 2.5f), MathX.rnd(-2f, 2f),
                    0x9CCC65, 0.7f, 0.12f, 0.03f, 0.7f, -8f, 1.4f, Particles.BLEND_ALPHA);
        }
    }

    public void bossSlam(Zombie boss) {
        camera.addShake(0.8f);
        particles.explosion(boss.x, 0.3f, boss.z, 3.4f);
        particles.dust(boss.x, 0.1f, boss.z, 26);
        audio.playExplosion();
        float radius = 6.5f;
        if (player.alive && MathX.dist(boss.x, boss.z, player.x, player.z) < radius) {
            player.hurt(boss.damage * 1.4f, this);
        }
        for (int i = structures.size() - 1; i >= 0; i--) {
            Structure s = structures.get(i);
            if (!s.alive) continue;
            if (MathX.dist(boss.x, boss.z, s.x, s.z) < radius) {
                s.damage(boss.damage * 2.2f);
                if (!s.alive) onStructureDestroyed(s);
            }
        }
    }

    // ---- yapılar --------------------------------------------------------

    private final ArrayList<Structure> supports = new ArrayList<>();

    private void updateStructures(float dt) {
        powerGen = 0f;
        powerUse = 0f;
        supports.clear();
        for (int i = 0; i < structures.size(); i++) {
            Structure s = structures.get(i);
            if (!s.alive) continue;
            float p = s.def().powerAt(s.level);
            if (p > 0) powerGen += p;
            else powerUse -= p;
            if (s.kind() == Balance.KIND_SUPPORT) supports.add(s);
        }
        powerEff = powerUse <= 0.01f ? 1f
                : MathX.clamp(powerGen / powerUse, Balance.MIN_POWER_EFFICIENCY, 1f);

        for (int i = structures.size() - 1; i >= 0; i--) {
            Structure s = structures.get(i);
            if (!s.alive) {
                structures.remove(i);
                continue;
            }
            s.updateVisual(dt);
            switch (s.kind()) {
                case Balance.KIND_TURRET: updateTurret(s, dt); break;
                case Balance.KIND_TRAP: updateTrap(s, dt); break;
                case Balance.KIND_SUPPORT: updateSupport(s, dt); break;
                case Balance.KIND_CORE: updateCore(s, dt); break;
                default: break;
            }
        }
        if (core != null && !core.alive && !gameOver) {
            triggerGameOver();
        }
    }

    private void updateCore(Structure s, float dt) {
        // Reaktör yavaşça kendini onarır (hazırlık aşamasında daha hızlı).
        float rate = waves.isPrepare() ? 26f : 4f;
        s.repair(rate * dt);
        if (MathX.chance(dt * 6f)) {
            particles.spawn(s.x + MathX.rnd(-1.4f, 1.4f), MathX.rnd(1.2f, 3.6f),
                    s.z + MathX.rnd(-1.4f, 1.4f), 0f, MathX.rnd(0.4f, 1.1f), 0f,
                    0x4FC3F7, 0.6f, 0.1f, 0.01f, 1.1f, 0.2f, 1f, Particles.BLEND_ADD);
        }
    }

    private void updateTurret(Structure s, float dt) {
        Balance.StructDef d = s.def();
        float range = d.rangeAt(s.level);
        float rate = d.rateAt(s.level) * powerEff * ammoBoost(s);
        float damage = d.damageAt(s.level);

        if (s.target != null && (!s.target.alive
                || MathX.dist(s.x, s.z, s.target.x, s.target.z) > range * 1.05f)) {
            s.target = null;
        }
        if (s.target == null) {
            s.target = findTurretTarget(s, range);
        }
        if (s.target == null) {
            s.yaw += dt * 0.35f;
            return;
        }

        Zombie t = s.target;
        float want = (float) Math.atan2(t.x - s.x, t.z - s.z);
        s.yaw = MathX.approachAngle(s.yaw, want, dt * 5.5f);

        if (s.type == Balance.S_FLAME) {
            // koni şeklinde sürekli hasar
            s.cooldown -= dt;
            float dps = damage * rate;
            boolean burned = false;
            for (int i = 0; i < zombies.size(); i++) {
                Zombie z = zombies.get(i);
                if (!z.alive) continue;
                float dist = MathX.dist(s.x, s.z, z.x, z.z);
                if (dist > range) continue;
                float ang = (float) Math.atan2(z.x - s.x, z.z - s.z);
                if (Math.abs(MathX.angleDiff(s.yaw, ang)) > 0.55f) continue;
                burned = true;
                if (z.hurt(dps * dt, z.x - s.x, z.z - s.z)) {
                    onZombieKilled(z, null);
                } else {
                    z.applyBurn(dps * 0.35f, 2.4f);
                }
            }
            if (burned || s.target != null) {
                float fx = (float) Math.sin(s.yaw), fz = (float) Math.cos(s.yaw);
                for (int i = 0; i < 2; i++) {
                    particles.spawn(s.x + fx * 0.8f, 1.5f, s.z + fz * 0.8f,
                            fx * MathX.rnd(5f, 10f) + MathX.rnd(-1.5f, 1.5f), MathX.rnd(-0.3f, 1.2f),
                            fz * MathX.rnd(5f, 10f) + MathX.rnd(-1.5f, 1.5f),
                            MathX.chance(0.4f) ? 0xFFD54F : 0xFF6E28, 0.55f,
                            0.18f, 0.5f, 0.32f, 1.4f, 1.8f, Particles.BLEND_ADD);
                }
            }
            s.chargeGlow = 1f;
            return;
        }

        s.cooldown -= dt;
        if (s.cooldown > 0f) return;
        s.cooldown = 1f / Math.max(0.05f, rate);
        s.chargeGlow = 1f;

        float muzzleY = turretMuzzleHeight(s);
        float fx = (float) Math.sin(s.yaw), fz = (float) Math.cos(s.yaw);
        float mx = s.x + fx * 0.85f, mz = s.z + fz * 0.85f;
        particles.muzzleFlash(mx, muzzleY, mz, fx, fz);

        switch (s.type) {
            case Balance.S_CANNON: {
                float dx = t.x - s.x, dz = t.z - s.z;
                float dist = MathX.len(dx, dz);
                float speed = 30f;
                float tt = dist / speed;
                float vy = (t.centerY() - muzzleY + 0.5f * 13f * tt * tt) / Math.max(0.12f, tt);
                Projectile p = obtainProjectile();
                p.initShell(mx, muzzleY, mz, dx / Math.max(0.01f, dist), vy / speed,
                        dz / Math.max(0.01f, dist), speed, damage, d.splash);
                projectiles.add(p);
                audio.playCannon();
                break;
            }
            case Balance.S_TESLA: {
                int chains = 2 + s.level / 2;
                Zombie cur = t;
                float px = s.x, py = muzzleY, pz = s.z;
                float dmg = damage;
                for (int i = 0; i < chains && cur != null; i++) {
                    tracer(px, py, pz, cur.x, cur.centerY(), cur.z, 0x8CE9FF, 0.14f, 0.07f);
                    particles.sparks(cur.x, cur.centerY(), cur.z, 5, 0x8CE9FF);
                    boolean killed = cur.hurt(dmg, cur.x - px, cur.z - pz);
                    cur.applySlow(0.55f, 1.1f);
                    addText(cur.x, cur.centerY() + 0.6f, cur.z, String.valueOf(Math.round(dmg)),
                            0x8CE9FF, 0.6f, 0.85f);
                    px = cur.x; py = cur.centerY(); pz = cur.z;
                    Zombie prev = cur;
                    if (killed) onZombieKilled(cur, null);
                    cur = findChainTarget(prev, px, pz, 6.5f);
                    dmg *= 0.72f;
                }
                audio.playTesla();
                break;
            }
            case Balance.S_SNIPER_TOWER: {
                tracer(mx, muzzleY, mz, t.x, t.centerY(), t.z, 0xFFF0B0, 0.16f, 0.05f);
                boolean killed = t.hurt(damage, t.x - s.x, t.z - s.z);
                t.knockback(t.x - s.x, t.z - s.z, 3.2f);
                particles.blood(t.x, t.centerY(), t.z, t.x - s.x, t.z - s.z, 10);
                addText(t.x, t.centerY() + 0.7f, t.z, String.valueOf(Math.round(damage)), 0xFFE082, 0.7f, 1.05f);
                if (killed) onZombieKilled(t, null);
                audio.playSniper();
                break;
            }
            default: {
                tracer(mx, muzzleY, mz, t.x, t.centerY(), t.z, 0xFFE0A3, 0.06f, 0.035f);
                boolean killed = t.hurt(damage, t.x - s.x, t.z - s.z);
                t.knockback(t.x - s.x, t.z - s.z, 0.8f);
                particles.blood(t.x, t.centerY(), t.z, t.x - s.x, t.z - s.z, 4);
                if (killed) onZombieKilled(t, null);
                audio.playTurret();
                break;
            }
        }
    }

    public static float turretMuzzleHeight(Structure s) {
        switch (s.type) {
            case Balance.S_SNIPER_TOWER: return 3.4f;
            case Balance.S_CANNON: return 1.9f;
            case Balance.S_TESLA: return 3.0f;
            default: return 1.75f;
        }
    }

    private float ammoBoost(Structure turret) {
        float boost = 1f;
        for (int i = 0; i < supports.size(); i++) {
            Structure a = supports.get(i);
            if (a.type != Balance.S_AMMO || !a.alive) continue;
            if (MathX.dist(a.x, a.z, turret.x, turret.z) <= a.def().rangeAt(a.level)) {
                boost = Math.max(boost, 1.12f + 0.06f * a.level);
            }
        }
        return boost;
    }

    private Zombie findTurretTarget(Structure s, float range) {
        Zombie best = null;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < zombies.size(); i++) {
            Zombie z = zombies.get(i);
            if (!z.alive || z.state == Zombie.ST_SPAWN) continue;
            float d2 = MathX.dist2(s.x, s.z, z.x, z.z);
            if (d2 > range * range) continue;
            // çekirdeğe en yakın olan önceliklidir
            float score = MathX.dist2(0f, 0f, z.x, z.z);
            if (z.isBoss()) score *= 0.45f;
            if (score < bestScore) {
                bestScore = score;
                best = z;
            }
        }
        return best;
    }

    private Zombie findChainTarget(Zombie from, float fromX, float fromZ, float range) {
        Zombie best = null;
        float bestD = range * range;
        for (int i = 0; i < zombies.size(); i++) {
            Zombie z = zombies.get(i);
            if (!z.alive || z == from) continue;
            float d2 = MathX.dist2(fromX, fromZ, z.x, z.z);
            if (d2 < bestD) {
                bestD = d2;
                best = z;
            }
        }
        return best;
    }

    private void updateTrap(Structure s, float dt) {
        Balance.StructDef d = s.def();
        float r = d.rangeAt(s.level);
        float dps = d.damageAt(s.level);
        for (int i = 0; i < zombies.size(); i++) {
            Zombie z = zombies.get(i);
            if (!z.alive) continue;
            if (MathX.dist(s.x, s.z, z.x, z.z) > r + z.radius()) continue;
            z.applySlow(0.55f, 0.4f);
            if (z.hurt(dps * dt, 0f, 0f)) {
                onZombieKilled(z, null);
            } else if (MathX.chance(dt * 4f)) {
                particles.blood(z.x, 0.25f, z.z, 0f, 0f, 2);
            }
            s.damage(dt * 1.6f);   // tuzaklar kullanıldıkça yıpranır
        }
        if (!s.alive) onStructureDestroyed(s);
    }

    private void updateSupport(Structure s, float dt) {
        Balance.StructDef d = s.def();
        switch (s.type) {
            case Balance.S_REPAIR: {
                float amount = d.damageAt(s.level) * player.repairBonus() * dt;
                float r = d.rangeAt(s.level);
                for (int i = 0; i < structures.size(); i++) {
                    Structure o = structures.get(i);
                    if (!o.alive || o == s || o.hp >= o.maxHp) continue;
                    if (MathX.dist(s.x, s.z, o.x, o.z) > r) continue;
                    o.repair(amount);
                    if (MathX.chance(dt * 2.2f)) {
                        particles.spawn(o.x, 1.2f, o.z, 0f, 1.2f, 0f, 0x26A69A, 0.7f,
                                0.1f, 0.02f, 0.5f, 0.3f, 1f, Particles.BLEND_ADD);
                    }
                }
                break;
            }
            case Balance.S_MED: {
                if (player.alive && player.hp < player.maxHp
                        && MathX.dist(s.x, s.z, player.x, player.z) < d.rangeAt(s.level)) {
                    player.heal(d.damageAt(s.level) * dt);
                    if (MathX.chance(dt * 4f)) {
                        particles.spawn(player.x, 1.3f, player.z, 0f, 1.4f, 0f, 0xE57373, 0.8f,
                                0.1f, 0.02f, 0.6f, 0.2f, 1f, Particles.BLEND_ADD);
                    }
                }
                break;
            }
            case Balance.S_AMMO: {
                if (player.alive && MathX.dist(s.x, s.z, player.x, player.z) < 3.2f) {
                    s.repairCarry += dt;
                    if (s.repairCarry > 1.2f) {
                        s.repairCarry = 0f;
                        player.refillAmmo(0.16f + 0.03f * s.level);
                        addText(player.x, 2.2f, player.z, "cephane +", 0x8BC34A, 1f, 0.9f);
                    }
                }
                break;
            }
            default:
                break;
        }
        if (s.type == Balance.S_GENERATOR && MathX.chance(dt * 3f)) {
            particles.spawn(s.x + MathX.rnd(-0.5f, 0.5f), 1.8f, s.z + MathX.rnd(-0.5f, 0.5f),
                    0f, MathX.rnd(0.3f, 0.9f), 0f, 0xFDD835, 0.5f, 0.08f, 0.01f, 0.6f, 0.4f, 1f,
                    Particles.BLEND_ADD);
        }
    }

    public Structure nearestStructure(float x, float z, float range) {
        Structure best = null;
        float bestD = range * range;
        for (int i = 0; i < structures.size(); i++) {
            Structure s = structures.get(i);
            if (!s.alive) continue;
            float d2 = MathX.dist2(x, z, s.x, s.z);
            if (d2 < bestD) {
                bestD = d2;
                best = s;
            }
        }
        return best;
    }

    public void onStructureDestroyed(Structure s) {
        if (s == core) {
            triggerGameOver();
            return;
        }
        structuresLost++;
        particles.explosion(s.x, 0.8f, s.z, 1.6f);
        particles.smoke(s.x, 0.6f, s.z, 8, 0x555048, 0.9f);
        audio.playStructDown();
        grid.clear(s.gx, s.gz);
        if (selected == s) selected = null;
        flowDirty = true;
    }

    // ---- inşa / yükseltme ----------------------------------------------

    public void tryPlace(int type, int gx, int gz) {
        if (gameOver) return;
        Balance.StructDef d = Balance.struct(type);
        if (waves.wave < d.unlockWave) {
            message(d.name + " " + d.unlockWave + ". dalgada açılır", 2f);
            return;
        }
        int code = grid.canPlace(gx, gz);
        if (code != BuildGrid.OK) {
            message(BuildGrid.placeError(code), 1.4f);
            return;
        }
        int cost = player.buildCost(d.cost);
        if (player.scrap < cost) {
            message("Yetersiz hurda (" + cost + ")", 1.6f);
            audio.playError();
            return;
        }
        // oyuncunun üstüne inşa etme
        float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
        if (d.blocks && MathX.dist(cx, cz, player.x, player.z) < 1.1f) {
            message("Burada duruyorsun", 1.2f);
            return;
        }
        player.scrap -= cost;
        Structure s = new Structure(type, 1, gx, gz, player.structHpBonus());
        structures.add(s);
        grid.set(gx, gz, s);
        flowDirty = true;
        structuresBuilt++;
        selected = s;
        particles.dust(cx, 0.1f, cz, 12);
        audio.playBuild();
        addText(cx, 1.6f, cz, "-" + cost, 0xFFAB91, 0.8f, 0.85f);
    }

    public void selectAt(int gx, int gz) {
        Structure s = grid.at(gx, gz);
        selected = s;
        if (s != null) audio.playClick();
    }

    public void upgradeSelected() {
        Structure s = selected;
        if (s == null || !s.alive) return;
        Balance.StructDef d = s.def();
        if (s.level >= d.maxLevel) {
            message("Azami seviye", 1.2f);
            return;
        }
        int cost = player.buildCost(d.upgradeCost(s.level));
        int cores = d.upgradeCores(s.level);
        if (player.scrap < cost) {
            message("Yetersiz hurda (" + cost + ")", 1.6f);
            audio.playError();
            return;
        }
        if (player.cores < cores) {
            message("Yetersiz enerji çekirdeği (" + cores + ")", 1.8f);
            audio.playError();
            return;
        }
        player.scrap -= cost;
        player.cores -= cores;
        s.level++;
        float frac = s.hpFraction();
        s.maxHp = d.hpAt(s.level) * player.structHpBonus();
        s.hp = Math.max(s.maxHp * frac, s.maxHp * 0.6f);
        s.buildAnim = 0.7f;
        audio.playUpgrade();
        particles.sparks(s.x, 1.4f, s.z, 18, 0xFFD54F);
        addText(s.x, 2.2f, s.z, "Sv." + s.level, 0xFFD54F, 1.2f, 1.1f);
    }

    public void sellSelected() {
        Structure s = selected;
        if (s == null || s == core || !s.alive) return;
        int value = s.def().sellValue(s.level, s.hpFraction());
        player.scrap += value;
        s.alive = false;
        grid.clear(s.gx, s.gz);
        structures.remove(s);
        selected = null;
        flowDirty = true;
        particles.dust(s.x, 0.2f, s.z, 10);
        audio.playSell();
        addText(s.x, 1.6f, s.z, "+" + value, 0xFFD54F, 1f, 1f);
    }

    public void repairSelected() {
        Structure s = selected;
        if (s == null || !s.alive) return;
        if (s.hp >= s.maxHp) {
            message("Yapı zaten sağlam", 1.2f);
            return;
        }
        int cost = repairCost(s);
        if (player.scrap < cost) {
            message("Yetersiz hurda (" + cost + ")", 1.6f);
            audio.playError();
            return;
        }
        player.scrap -= cost;
        s.hp = s.maxHp;
        audio.playUpgrade();
        particles.sparks(s.x, 1.2f, s.z, 10, 0x80CBC4);
        addText(s.x, 1.8f, s.z, "Onarıldı", 0x80CBC4, 1f, 0.9f);
    }

    public int repairCost(Structure s) {
        float missing = 1f - s.hpFraction();
        return Math.max(1, Math.round(s.def().cost * missing * 0.55f * (1f + 0.25f * s.level)));
    }

    public int repairAllCost() {
        int total = 0;
        for (int i = 0; i < structures.size(); i++) {
            Structure s = structures.get(i);
            if (s.alive && s.hp < s.maxHp) total += repairCost(s);
        }
        return total;
    }

    public void repairAll() {
        int total = repairAllCost();
        if (total <= 0) {
            message("Onarılacak yapı yok", 1.2f);
            return;
        }
        if (player.scrap < total) {
            message("Tümünü onarmak için " + total + " hurda gerekli", 2f);
            audio.playError();
            return;
        }
        player.scrap -= total;
        for (int i = 0; i < structures.size(); i++) {
            Structure s = structures.get(i);
            if (s.alive) s.hp = s.maxHp;
        }
        audio.playUpgrade();
        message("Tüm yapılar onarıldı (-" + total + ")", 1.8f);
    }

    private void doSkillUp(int skill) {
        if (player.spendSkillPoint(skill)) {
            audio.playUpgrade();
            message(Balance.SKILLS[skill].name + " geliştirildi", 1.4f);
            // yapı canı yeteneği tüm yapıları etkiler
            if (skill == Balance.SK_STRUCT_HP) {
                for (int i = 0; i < structures.size(); i++) {
                    structures.get(i).refreshMaxHp(player.structHpBonus());
                }
            }
        } else {
            audio.playError();
        }
    }

    private void upgradeWeapon(int id) {
        if (id < 0 || id >= Balance.WEAPONS.length || !player.unlocked[id]) return;
        int lvl = player.weaponLevel[id];
        if (lvl >= Balance.WEAPON_MAX_LEVEL) {
            message("Azami seviye", 1.2f);
            return;
        }
        int cost = Balance.weapon(id).upgradeCost(lvl);
        if (player.scrap < cost) {
            message("Yetersiz hurda (" + cost + ")", 1.6f);
            audio.playError();
            return;
        }
        player.scrap -= cost;
        player.weaponLevel[id]++;
        audio.playUpgrade();
        message(Balance.weapon(id).name + " Sv." + player.weaponLevel[id], 1.6f);
    }

    private void buyWeapon(int id) {
        if (id < 0 || id >= Balance.WEAPONS.length || player.unlocked[id]) return;
        int cost = Balance.weapon(id).price;
        if (player.scrap < cost) {
            message("Yetersiz hurda (" + cost + ")", 1.6f);
            audio.playError();
            return;
        }
        player.scrap -= cost;
        player.unlocked[id] = true;
        Balance.WeaponDef d = Balance.weapon(id);
        player.magazine[id] = d.magazineAt(1);
        player.reserve[id] = d.reserveMax / 2;
        player.switchWeapon(id);
        audio.playUpgrade();
        message(d.name + " satın alındı", 1.8f);
    }

    private void buyAmmo() {
        int cost = 60;
        if (player.scrap < cost) {
            message("Yetersiz hurda (" + cost + ")", 1.6f);
            audio.playError();
            return;
        }
        player.scrap -= cost;
        player.refillAmmo(0.45f);
        audio.playBuild();
        message("Cephane dolduruldu", 1.4f);
    }

    // ---- olaylar --------------------------------------------------------

    public void onWaveStarted(int wave) {
        started = true;
        selected = null;
        input.buildMode = false;   // dalga başlayınca savaş moduna dön
        flow.compute(grid);
        audio.playWaveStart();
        big(wave + ". DALGA" + (Balance.isBossWave(wave) ? " — MUTANT DEV!" : ""), 2.6f);
        if (wave > waveRecord) waveRecord = wave;
    }

    public void onWaveCleared(int wave) {
        int reward = Math.round(Balance.waveScrapReward(wave) * player.scrapBonus());
        for (int i = 0; i < structures.size(); i++) {
            Structure s = structures.get(i);
            if (s.alive && s.type == Balance.S_COLLECTOR) {
                reward += Math.round(s.def().damageAt(s.level) * player.scrapBonus());
            }
        }
        player.scrap += reward;
        scrapEarned += reward;
        player.addXp(Balance.waveXpReward(wave), this);
        player.refillAmmo(0.3f);
        if (Balance.isBossWave(wave)) player.cores += 1;
        audio.playWaveCleared();
        big(wave + ". dalga temizlendi  +" + reward + " hurda", 2.8f);
    }

    public void onPrepareStarted() {
        input.buildMode = true;    // hazırlıkta doğrudan inşa moduna geç
        message("Hazırlık: inşa et, geliştir, mevzilen", 3f);
    }

    public void onLevelUp() {
        audio.playLevelUp();
        big("Seviye " + player.level + "! Yetenek puanı kazandın", 2.4f);
        particles.explosion(player.x, 1f, player.z, 1.4f);
    }

    public void onPlayerDown() {
        audio.playPlayerDown();
        big("Yere düştün! " + Math.round(Balance.REVIVE_TIME) + " sn içinde ayağa kalkacaksın", 3f);
        int loss = Math.min(player.scrap, 40);
        player.scrap -= loss;
    }

    public void revivePlayer() {
        player.alive = true;
        player.hp = player.maxHp() * 0.55f;
        player.x = 0f;
        player.z = 6f;
        player.dashCd = 0f;
        message("Ayağa kalktın", 1.6f);
    }

    private void triggerGameOver() {
        if (gameOver) return;
        gameOver = true;
        core.alive = false;
        particles.explosion(0f, 2f, 0f, 9f);
        camera.addShake(1.2f);
        audio.playGameOver();
        big("REAKTÖR YOK EDİLDİ", 6f);
    }

    // ---- efekt yardımcıları --------------------------------------------

    private Projectile obtainProjectile() {
        if (!projPool.isEmpty()) return projPool.remove(projPool.size() - 1);
        return new Projectile();
    }

    private void updateProjectiles(float dt) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            p.update(this, dt);
            if (!p.alive) {
                projectiles.remove(i);
                projPool.add(p);
            }
        }
    }

    public void tracer(float x0, float y0, float z0, float x1, float y1, float z1,
                       int color, float life, float width) {
        Tracer t = tracerPool.isEmpty() ? new Tracer() : tracerPool.remove(tracerPool.size() - 1);
        t.set(x0, y0, z0, x1, y1, z1, color, life, width);
        tracers.add(t);
    }

    public void addText(float x, float y, float z, String s, int color, float life, float size) {
        if (texts.size() > 60) return;
        FloatingText t = textPool.isEmpty() ? new FloatingText() : textPool.remove(textPool.size() - 1);
        t.set(x, y, z, s, color, life, size);
        texts.add(t);
    }

    private void updateEffects(float dt) {
        for (int i = tracers.size() - 1; i >= 0; i--) {
            Tracer t = tracers.get(i);
            t.update(dt);
            if (!t.alive) {
                tracers.remove(i);
                tracerPool.add(t);
            }
        }
        for (int i = texts.size() - 1; i >= 0; i--) {
            FloatingText t = texts.get(i);
            t.update(dt);
            if (!t.alive) {
                texts.remove(i);
                textPool.add(t);
            }
        }
    }

    public void message(String s, float time) {
        message = s;
        messageTimer = time;
    }

    public void big(String s, float time) {
        bigMessage = s;
        bigMessageTimer = time;
    }

    public void markFlowDirty() {
        flowDirty = true;
    }
}
