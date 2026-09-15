package com.karargah.survival.game;

import android.opengl.Matrix;

import com.karargah.survival.engine.CharModel;
import com.karargah.survival.engine.M4;
import com.karargah.survival.engine.MathX;
import com.karargah.survival.engine.Mesh;
import com.karargah.survival.engine.Renderer3D;

/** Oyun durumunu 3B çizime dönüştürür: arazi, yapılar, karakterler, efektler. */
public class WorldRenderer {
    private final Renderer3D r = new Renderer3D();
    private final Models models = new Models();

    private final float[] model = new float[16];
    private final float[] tmp = new float[16];
    private final float[] tmp2 = new float[16];
    private final float[] bones = new float[Renderer3D.MAX_BONES * 16];
    private final float[] rgb = new float[3];

    // dekor
    private static final int DECOR = 190;
    private final float[] decorX = new float[DECOR];
    private final float[] decorZ = new float[DECOR];
    private final float[] decorYaw = new float[DECOR];
    private final float[] decorScale = new float[DECOR];
    private final int[] decorType = new int[DECOR];

    public Renderer3D renderer() {
        return r;
    }

    public Models models() {
        return models;
    }

    public void init() {
        r.init();
        models.build();
        buildDecor();
    }

    public void resize(int w, int h) {
        r.resize(w, h);
    }

    private void buildDecor() {
        for (int i = 0; i < DECOR; i++) {
            float a = MathX.rnd(0f, MathX.TAU);
            float rad = MathX.rnd(Balance.BUILD_RADIUS + 2.5f, Balance.WORLD_HALF - 2f);
            decorX[i] = (float) Math.cos(a) * rad;
            decorZ[i] = (float) Math.sin(a) * rad;
            decorYaw[i] = MathX.rnd(0f, MathX.TAU);
            decorScale[i] = MathX.rnd(0.7f, 1.5f);
            float roll = MathX.rnd();
            decorType[i] = roll < 0.34f ? 0 : (roll < 0.62f ? 1 : (roll < 0.78f ? 2 : (roll < 0.9f ? 3 : 4)));
        }
    }

    // ---- ana çizim ------------------------------------------------------

    public void render(GameWorld w, boolean buildMode) {
        applyLighting(w.nightFactor);
        r.beginFrame(w.camera);
        r.drawSky();

        drawGround();
        drawDecor(w);
        drawPortals(w);
        drawStructures(w, buildMode);
        drawZombies(w);
        drawPlayer(w);

        // saydam katman
        r.beginTransparent();
        drawShadows(w);
        if (buildMode) drawBuildOverlay(w);
        drawRangeRing(w);
        r.endTransparent();

        r.beginAdditive();
        drawTracers(w);
        drawProjectiles(w);
        drawCoreGlow(w);
        r.endTransparent();

        w.particles.render(r);
    }

    private void applyLighting(float night) {
        float n = MathX.clamp(night, 0f, 1f);
        r.lightX = MathX.lerp(0.42f, -0.28f, n);
        r.lightY = MathX.lerp(0.84f, 0.62f, n);
        r.lightZ = MathX.lerp(0.34f, -0.72f, n);
        float l = (float) Math.sqrt(r.lightX * r.lightX + r.lightY * r.lightY + r.lightZ * r.lightZ);
        r.lightX /= l; r.lightY /= l; r.lightZ /= l;

        r.sunR = MathX.lerp(1.02f, 0.30f, n);
        r.sunG = MathX.lerp(0.95f, 0.34f, n);
        r.sunB = MathX.lerp(0.80f, 0.52f, n);
        r.skyR = MathX.lerp(0.34f, 0.11f, n);
        r.skyG = MathX.lerp(0.38f, 0.14f, n);
        r.skyB = MathX.lerp(0.46f, 0.24f, n);
        r.gndR = MathX.lerp(0.20f, 0.07f, n);
        r.gndG = MathX.lerp(0.19f, 0.08f, n);
        r.gndB = MathX.lerp(0.16f, 0.10f, n);
        r.fogR = MathX.lerp(0.60f, 0.07f, n);
        r.fogG = MathX.lerp(0.64f, 0.09f, n);
        r.fogB = MathX.lerp(0.66f, 0.14f, n);
        r.fogDensity = MathX.lerp(0.0062f, 0.0135f, n);
        r.skyTopR = MathX.lerp(0.25f, 0.03f, n);
        r.skyTopG = MathX.lerp(0.48f, 0.05f, n);
        r.skyTopB = MathX.lerp(0.78f, 0.12f, n);
        r.skyHorR = MathX.lerp(0.78f, 0.16f, n);
        r.skyHorG = MathX.lerp(0.78f, 0.09f, n);
        r.skyHorB = MathX.lerp(0.74f, 0.10f, n);
    }

    private void drawGround() {
        M4.setIdentity(model);
        r.draw(models.ground, model, 1f, 1f, 1f, 1f);
        r.draw(models.basePlatform, model, 1f, 1f, 1f, 1f);
    }

    private void drawDecor(GameWorld w) {
        for (int i = 0; i < DECOR; i++) {
            if (!visible(w, decorX[i], decorZ[i], 3f)) continue;
            Mesh m;
            switch (decorType[i]) {
                case 0: m = models.rock; break;
                case 1: m = models.deadTree; break;
                case 2: m = models.grassTuft; break;
                case 3: m = models.barrel; break;
                default: m = models.crate; break;
            }
            M4.trs(model, decorX[i], 0f, decorZ[i], decorYaw[i], decorScale[i]);
            r.draw(m, model, 1f, 1f, 1f, 1f);
        }
    }

    private void drawPortals(GameWorld w) {
        for (int i = 0; i < WaveManager.MAX_SPAWN_POINTS; i++) {
            float glow = w.waves.spawnGlow[i];
            if (!visible(w, w.waves.spawnX[i], w.waves.spawnZ[i], 4f)) continue;
            M4.trs(model, w.waves.spawnX[i], 0f, w.waves.spawnZ[i], 0f, 1f);
            r.draw(models.portal, model, 1f, 0.75f + glow * 0.25f, 0.75f, 1f, glow * 0.55f);
        }
    }

    // ---- yapılar --------------------------------------------------------

    private void drawStructures(GameWorld w, boolean buildMode) {
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            if (!s.alive) continue;
            if (!visible(w, s.x, s.z, 4f)) continue;

            float rise = s.riseFactor();
            float hpf = s.hpFraction();
            float dr = MathX.lerp(1.15f, 1f, hpf);
            float dg = MathX.lerp(0.55f, 1f, hpf);
            float db = MathX.lerp(0.5f, 1f, hpf);
            float emis = s.flash * 2.2f + s.chargeGlow * 0.25f;

            M4.trs(model, s.x, (rise - 1f) * 1.6f, s.z, 0f, 1f, rise, 1f);
            Mesh base = models.structBase[s.type][Math.min(5, Math.max(1, s.level))];
            if (w.selected == s) {
                r.drawHighlighted(base, model, dr, dg, db, 1f, emis, 0.16f, 0.30f, 0.16f, 1f);
            } else {
                r.draw(base, model, dr, dg, db, 1f, emis);
            }

            Mesh head = models.structHead[s.type];
            if (head != null) {
                M4.trs(model, s.x, (rise - 1f) * 1.6f, s.z, s.yaw, 1f, rise, 1f);
                r.draw(head, model, dr, dg, db, 1f, emis);
            }
        }
    }

    private void drawCoreGlow(GameWorld w) {
        Structure c = w.core;
        if (c == null || !c.alive) return;
        float t = (float) (System.nanoTime() % 4000000000L) / 4.0e9f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * MathX.TAU * 2f);
        M4.trs(model, 0f, 2.55f, 0f, t * MathX.TAU, 0.62f * pulse);
        r.draw(models.coreOrb, model, 0.45f, 0.85f, 1f, 0.75f, 1.6f);
    }

    // ---- karakterler ----------------------------------------------------

    private void drawZombies(GameWorld w) {
        for (int i = 0; i < w.zombies.size(); i++) {
            Zombie z = w.zombies.get(i);
            if (!visible(w, z.x, z.z, 3f)) continue;
            CharModel cm = models.zombies[z.type];
            if (cm == null) continue;

            float sink = 0f;
            float tilt = 0f;
            if (z.state == Zombie.ST_DEAD) {
                float t = MathX.clamp(z.deadTime / 1.2f, 0f, 1f);
                tilt = t * 88f;
                sink = Math.max(0f, z.deadTime - 1.4f) * 0.7f;
            }
            animateZombie(cm, z, tilt);

            M4.trsFull(model, z.x, z.y - sink, z.z, 0f, z.yaw, 0f, z.scale, z.scale, z.scale);
            float flash = z.flash * 3.2f;
            float eliteGlow = z.elite ? 0.25f : 0f;
            float rr = 1f + flash, gg = 1f + flash * 0.4f, bb = 1f + flash * 0.4f;
            if (z.burnTimer > 0f) {
                rr += 0.3f;
                gg *= 0.8f;
                bb *= 0.6f;
            }
            if (z.elite) {
                rr *= 1.1f;
                bb *= 1.25f;
            }
            float alpha = z.state == Zombie.ST_DEAD
                    ? MathX.clamp(1f - (z.deadTime - 1.6f) / 0.8f, 0f, 1f) : 1f;
            r.drawSkinned(cm.mesh, model, bones, Renderer3D.MAX_BONES, rr, gg, bb, alpha,
                    flash * 0.5f + eliteGlow);
        }
    }

    private void drawPlayer(GameWorld w) {
        Player p = w.player;
        if (!p.alive && p.reviveTimer <= 0f) return;
        CharModel cm = models.player;
        animatePlayer(cm, p);
        float lean = p.alive ? 0f : 80f;
        M4.trsFull(model, p.x, p.y, p.z, lean, p.yaw, 0f, 1f, 1f, 1f);
        float flash = p.hurtFlash * 1.6f;
        r.drawSkinned(cm.mesh, model, bones, Renderer3D.MAX_BONES,
                1f + flash, 1f - flash * 0.35f, 1f - flash * 0.35f, 1f, flash * 0.5f);

        if (p.alive) {
            // silah: sağ el kemiğine bağlı
            System.arraycopy(bones, Models.BONE_ARM_R * 16, tmp2, 0, 16);
            M4.mul(tmp, model, tmp2);
            Matrix.translateM(tmp, 0, 0.38f, 1.06f, 0.16f);
            Mesh gun = models.weapons[p.currentWeapon];
            if (gun != null) {
                float rec = p.recoil * 0.12f;
                Matrix.translateM(tmp, 0, 0f, 0f, -rec);
                r.draw(gun, tmp, 1f, 1f, 1f, 1f, p.muzzleTimer > 0f ? 1.4f : 0f);
            }
        }
    }

    /** Kemik matrisi: pivot etrafında döndür, sonra ötele. */
    private void setBone(int index, float[] pivot, float rx, float ry, float rz,
                         float ox, float oy, float oz) {
        int o = index * 16;
        Matrix.setIdentityM(bones, o);
        Matrix.translateM(bones, o, pivot[0] + ox, pivot[1] + oy, pivot[2] + oz);
        if (ry != 0f) Matrix.rotateM(bones, o, ry, 0f, 1f, 0f);
        if (rx != 0f) Matrix.rotateM(bones, o, rx, 1f, 0f, 0f);
        if (rz != 0f) Matrix.rotateM(bones, o, rz, 0f, 0f, 1f);
        Matrix.translateM(bones, o, -pivot[0], -pivot[1], -pivot[2]);
    }

    private void identityBones() {
        for (int i = 0; i < Renderer3D.MAX_BONES; i++) {
            Matrix.setIdentityM(bones, i * 16);
        }
    }

    private void animatePlayer(CharModel cm, Player p) {
        identityBones();
        float[][] pv = cm.pivots;
        float phase = p.animPhase;
        float amp = p.moving ? 34f : 0f;
        float swing = (float) Math.sin(phase) * amp;
        float bob = p.moving ? Math.abs((float) Math.sin(phase)) * 0.045f : 0f;
        float breathe = (float) Math.sin(System.nanoTime() * 1e-9f * 1.7f) * 0.012f;

        setBone(Models.BONE_HIPS, pv[0], 0f, 0f, 0f, 0f, bob + breathe, 0f);
        setBone(Models.BONE_TORSO, pv[1], p.moving ? -7f : -2f, swing * 0.12f, 0f, 0f, bob + breathe, 0f);
        setBone(Models.BONE_HEAD, pv[2], -2f, 0f, 0f, 0f, bob + breathe, 0f);
        setBone(Models.BONE_LEG_L, pv[5], swing, 0f, 0f, 0f, 0f, 0f);
        setBone(Models.BONE_LEG_R, pv[6], -swing, 0f, 0f, 0f, 0f, 0f);

        // nişan alırken kollar ileride
        float aimDiff = MathX.angleDiff(p.yaw, p.aimYaw) / MathX.DEG;
        float recoilKick = p.recoil * 18f;
        setBone(Models.BONE_ARM_R, pv[4], -78f + recoilKick, aimDiff * 0.6f, -12f, 0f, bob, 0f);
        setBone(Models.BONE_ARM_L, pv[3], -68f + recoilKick * 0.6f, aimDiff * 0.6f, 20f, 0f, bob, 0f);
    }

    private void animateZombie(CharModel cm, Zombie z, float deathTilt) {
        identityBones();
        float[][] pv = cm.pivots;
        float phase = z.animPhase;
        boolean walking = z.state == Zombie.ST_WALK;
        float amp = walking ? (z.type == Balance.Z_RUNNER ? 52f : 30f) : 6f;
        float swing = (float) Math.sin(phase) * amp;
        float bob = walking ? Math.abs((float) Math.sin(phase)) * 0.05f : 0f;

        if (deathTilt > 0f) {
            setBone(Models.BONE_HIPS, pv[0], deathTilt, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_TORSO, pv[1], deathTilt, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_HEAD, pv[2], deathTilt * 0.6f, 0f, 20f, 0f, 0f, 0f);
            setBone(Models.BONE_ARM_L, pv[3], deathTilt * 0.4f, 0f, 35f, 0f, 0f, 0f);
            setBone(Models.BONE_ARM_R, pv[4], deathTilt * 0.4f, 0f, -35f, 0f, 0f, 0f);
            setBone(Models.BONE_LEG_L, pv[5], deathTilt * 0.5f, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_LEG_R, pv[6], deathTilt * 0.5f, 0f, 0f, 0f, 0f, 0f);
            return;
        }

        if (z.type == Balance.Z_CRAWLER) {
            float crawl = (float) Math.sin(phase * 1.6f);
            setBone(Models.BONE_TORSO, pv[1], crawl * 5f, 0f, crawl * 4f, 0f, bob * 0.4f, 0f);
            setBone(Models.BONE_HEAD, pv[2], 10f + crawl * 6f, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_ARM_L, pv[3], -40f + crawl * 40f, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_ARM_R, pv[4], -40f - crawl * 40f, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_LEG_L, pv[5], crawl * 18f, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_LEG_R, pv[6], -crawl * 18f, 0f, 0f, 0f, 0f, 0f);
            return;
        }

        float attackSwing = 0f;
        if (z.state == Zombie.ST_ATTACK) {
            float cd = 1f / z.def.attackRate;
            float t = 1f - MathX.clamp(z.attackCd / cd, 0f, 1f);
            attackSwing = (float) Math.sin(t * MathX.PI) * 75f;
        }
        float spawnDip = z.state == Zombie.ST_SPAWN ? 30f : 0f;

        setBone(Models.BONE_HIPS, pv[0], 0f, 0f, 0f, 0f, bob, 0f);
        setBone(Models.BONE_TORSO, pv[1], 12f + spawnDip, swing * 0.2f, (float) Math.sin(phase * 0.5f) * 5f,
                0f, bob, 0f);
        setBone(Models.BONE_HEAD, pv[2], -8f, (float) Math.sin(phase * 0.7f) * 8f, 6f, 0f, bob, 0f);
        setBone(Models.BONE_ARM_L, pv[3], -62f - attackSwing + swing * 0.25f, 0f, 14f, 0f, bob, 0f);
        setBone(Models.BONE_ARM_R, pv[4], -62f - attackSwing - swing * 0.25f, 0f, -14f, 0f, bob, 0f);
        setBone(Models.BONE_LEG_L, pv[5], swing, 0f, 0f, 0f, 0f, 0f);
        setBone(Models.BONE_LEG_R, pv[6], -swing, 0f, 0f, 0f, 0f, 0f);
    }

    // ---- gölgeler, efektler --------------------------------------------

    private void drawShadows(GameWorld w) {
        for (int i = 0; i < w.zombies.size(); i++) {
            Zombie z = w.zombies.get(i);
            if (z.state == Zombie.ST_DEAD && z.deadTime > 1.5f) continue;
            if (!visible(w, z.x, z.z, 2f)) continue;
            r.drawBlobShadow(z.x, z.z, z.radius() * 1.9f, 0.32f);
        }
        if (w.player.alive) {
            r.drawBlobShadow(w.player.x, w.player.z, 0.62f, 0.38f);
        }
        for (int i = 0; i < w.structures.size(); i++) {
            Structure s = w.structures.get(i);
            if (!s.alive || !visible(w, s.x, s.z, 3f)) continue;
            float rad = s.type == Balance.S_CORE ? 4.6f : Balance.CELL * 0.62f;
            r.drawBlobShadow(s.x, s.z, rad, 0.26f);
        }
    }

    private void drawTracers(GameWorld w) {
        for (int i = 0; i < w.tracers.size(); i++) {
            Tracer t = w.tracers.get(i);
            float dx = t.x1 - t.x0, dy = t.y1 - t.y0, dz = t.z1 - t.z0;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.01f) continue;
            float yaw = (float) Math.atan2(dx, dz);
            float pitch = (float) Math.asin(MathX.clamp(dy / len, -1f, 1f));
            M4.trsFull(model, (t.x0 + t.x1) * 0.5f, (t.y0 + t.y1) * 0.5f, (t.z0 + t.z1) * 0.5f,
                    -pitch / MathX.DEG, yaw / MathX.DEG, 0f,
                    t.width, t.width, len);
            MathX.colorToRgb(t.color, rgb);
            float a = t.alpha();
            r.draw(models.unitBox, model, rgb[0], rgb[1], rgb[2], a, 1.8f * a);
        }
    }

    private void drawProjectiles(GameWorld w) {
        for (int i = 0; i < w.projectiles.size(); i++) {
            Projectile p = w.projectiles.get(i);
            if (!p.alive) continue;
            MathX.colorToRgb(p.color, rgb);
            float yaw = (float) Math.atan2(p.vx, p.vz);
            M4.trsFull(model, p.x, p.y, p.z, 0f, yaw / MathX.DEG, 0f,
                    p.size, p.size, p.size * 2.4f);
            r.draw(models.unitBox, model, rgb[0], rgb[1], rgb[2], 1f, 1.5f);
        }
    }

    // ---- inşa arayüzü ---------------------------------------------------

    private void drawBuildOverlay(GameWorld w) {
        M4.setIdentity(model);
        r.draw(models.gridOverlay, model, 0.55f, 0.75f, 1f, 0.16f, 0.5f);

        int gx = w.input.hoverGx, gz = w.input.hoverGz;
        if (gx < 0 || gz < 0) return;
        int code = w.grid.canPlace(gx, gz);
        int type = w.input.buildType;
        Balance.StructDef d = Balance.struct(type);
        boolean affordable = w.player.scrap >= w.player.buildCost(d.cost)
                && w.waves.wave >= d.unlockWave;
        boolean ok = code == BuildGrid.OK && affordable;
        float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);

        M4.trs(model, cx, 0.07f, cz, 0f, 1f);
        r.draw(models.unitCell, model, ok ? 0.3f : 1f, ok ? 1f : 0.3f, 0.35f, 0.45f, 0.6f);

        if (code == BuildGrid.OK) {
            Mesh ghost = models.structBase[type][1];
            M4.trs(model, cx, 0f, cz, 0f, 1f);
            r.draw(ghost, model, ok ? 0.6f : 1.2f, ok ? 1.2f : 0.5f, ok ? 0.8f : 0.5f, 0.45f, 0.35f);
            Mesh head = models.structHead[type];
            if (head != null) {
                r.draw(head, model, ok ? 0.6f : 1.2f, ok ? 1.2f : 0.5f, ok ? 0.8f : 0.5f, 0.45f, 0.35f);
            }
            if (d.range > 0f) {
                M4.trs(model, cx, 0.08f, cz, 0f, d.range, 1f, d.range);
                r.draw(models.ringFlat, model, 0.5f, 0.9f, 1f, 0.5f, 0.6f);
            }
        }
    }

    private void drawRangeRing(GameWorld w) {
        Structure s = w.selected;
        if (s == null || !s.alive) return;
        float range = s.def().rangeAt(s.level);
        if (range <= 0.5f) return;
        M4.trs(model, s.x, 0.09f, s.z, 0f, range, 1f, range);
        r.draw(models.ringFlat, model, 0.45f, 1f, 0.6f, 0.65f, 0.8f);
    }

    // ---- yardımcı -------------------------------------------------------

    private boolean visible(GameWorld w, float x, float z, float radius) {
        float dx = x - r.camX, dz = z - r.camZ;
        float d2 = dx * dx + dz * dz;
        if (d2 > 115f * 115f) return false;
        float d = (float) Math.sqrt(d2);
        if (d < radius + 6f) return true;
        float fx = w.camera.focusX - r.camX, fz = w.camera.focusZ - r.camZ;
        float fl = MathX.len(fx, fz);
        if (fl < 0.01f) return true;
        float dot = (dx * fx + dz * fz) / (d * fl);
        return dot > 0.05f - radius / d;
    }

    public void dispose() {
        r.dispose();
    }
}
