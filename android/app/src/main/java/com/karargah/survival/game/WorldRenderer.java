package com.karargah.survival.game;

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

    /** Zemin parçasının kenar uzunluğu (birim). */
    public static final float CHUNK = 32f;
    /** Oyuncunun çevresinde çizilen parça yarıçapı (parça sayısı). */
    private static final int CHUNK_VIEW = 5;
    /** Bitki/kaya serpme ızgarasının adımı. */
    private static final float PROP_STEP = Harvest.STEP;
    /** Süs nesnelerinin çizildiği azami uzaklık. */
    private static final float PROP_RANGE = 78f;
    /** Şehir binalarının çizildiği azami uzaklık. */
    private static final float CITY_RANGE = 150f;

    private final float[] cityTmp = new float[3];
    private final float[] buildTmp = new float[5];
    private final float[] markTmp = new float[4];
    private final float[] partTmp = new float[6];

    public Renderer3D renderer() {
        return r;
    }

    public Models models() {
        return models;
    }

    public void init() {
        init(null);
    }

    /**
     * @param assets varsa {@code assets/models/} altındaki hazır model paketi
     *               kullanılır; yoksa her şey kodla üretilir.
     */
    public void init(android.content.res.AssetManager assets) {
        r.init();
        models.build(assets);
    }

    public void resize(int w, int h) {
        r.resize(w, h);
    }

    // ---- ana çizim ------------------------------------------------------

    public void render(GameWorld w, boolean buildMode) {
        applyLighting(w.nightFactor);
        r.beginFrame(w.camera);
        r.drawSky();

        drawGround(w);
        drawCity(w);
        drawLandmark(w);
        drawDecor(w);
        drawStructures(w, buildMode);
        drawZombies(w);
        drawNpcs(w);
        drawPlayer(w);
        drawPickups(w);

        // saydam katman (zemine yapışan katmanlar derinlik kaydırmasıyla çizilir)
        r.beginTransparent();
        r.setDepthOffset(-2f, -4f);
        drawShadows(w);
        r.setDepthOffset(-3f, -6f);
        drawPlans(w);
        if (buildMode) drawBuildOverlay(w);
        r.setDepthOffset(-4f, -8f);
        drawRangeRing(w);
        r.setDepthOffset(0f, 0f);
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

    /**
     * Zemin, oyuncunun çevresindeki parçalar hâlinde çizilir. Her parça biyom
     * rengiyle boyanır; böylece 100.000 birimlik dünya için tek bir dev ağ
     * tutmaya gerek kalmaz.
     */
    private void drawGround(GameWorld w) {
        int c0x = (int) Math.floor(r.camX / CHUNK);
        int c0z = (int) Math.floor(r.camZ / CHUNK);
        for (int cz = c0z - CHUNK_VIEW; cz <= c0z + CHUNK_VIEW; cz++) {
            for (int cx = c0x - CHUNK_VIEW; cx <= c0x + CHUNK_VIEW; cx++) {
                float wx = (cx + 0.5f) * CHUNK, wz = (cz + 0.5f) * CHUNK;
                if (!visible(w, wx, wz, CHUNK * 0.75f)) continue;
                int col = WorldGen.groundColor(WorldGen.biomeAt(wx, wz));
                // Aynı biyomda bile parçadan parçaya hafif ton farkı
                float shade = 0.86f + WorldGen.rand01(cx, cz, 909) * 0.26f;
                MathX.colorToRgb(col, rgb);
                M4.trs(model, wx, 0f, wz, 0f, 1f);
                r.draw(models.groundTile, model,
                        rgb[0] * shade, rgb[1] * shade, rgb[2] * shade, 1f);
            }
        }
        // Kamp zemini sur hattıyla birlikte büyür.
        float pad = (w.baseRadius + 3f) / Balance.BUILD_RADIUS;
        M4.trs(model, 0f, 0f, 0f, 0f, pad, 1f, pad);
        r.setDepthOffset(-1f, -2f);
        r.draw(models.basePlatform, model, 1f, 1f, 1f, 1f);
        r.setDepthOffset(0f, 0f);
    }

    /**
     * Tasarlanmış mekânların yapıları: askeri üssün kışlaları ve kuleleri,
     * hastanenin kanatları, barajın gövdesi. Hepsi koordinattan hesaplanır.
     */
    private void drawLandmark(GameWorld w) {
        float d = Landmark.nearest(r.camX, r.camZ, markTmp);
        if (d < 0f || d > markTmp[2] + CITY_RANGE) return;
        int type = (int) markTmp[3];
        int n = Landmark.partCount(type);
        for (int i = 0; i < n; i++) {
            Landmark.partAt(type, markTmp[0], markTmp[1], i, partTmp);
            float span = Math.max(partTmp[2], partTmp[3]);
            if (!visible(w, partTmp[0], partTmp[1], span)) continue;
            // Ton: 0 koyu beton, 1 açık metal; her parça hafifçe farklı
            float tone = partTmp[5] * (0.88f + WorldGen.rand01(i, type, 911) * 0.24f);
            M4.trs(model, partTmp[0], 0f, partTmp[1], 0f,
                    partTmp[2] * 2f, partTmp[4], partTmp[3] * 2f);
            r.draw(models.cityBuilding, model, tone * 0.88f, tone * 0.9f, tone * 0.86f, 1f);
        }
    }

    /** Şehirlerin binaları: adalara göre deterministik, hiç saklanmaz. */
    private void drawCity(GameWorld w) {
        float d = WorldGen.nearestCity(r.camX, r.camZ, cityTmp);
        if (d < 0f || d > cityTmp[2] + CITY_RANGE) return;
        float cx = cityTmp[0], cz = cityTmp[1];
        int b0x = (int) Math.floor((r.camX - CITY_RANGE - cx) / 30f);
        int b1x = (int) Math.floor((r.camX + CITY_RANGE - cx) / 30f);
        int b0z = (int) Math.floor((r.camZ - CITY_RANGE - cz) / 30f);
        int b1z = (int) Math.floor((r.camZ + CITY_RANGE - cz) / 30f);
        for (int bz = b0z; bz <= b1z; bz++) {
            for (int bx = b0x; bx <= b1x; bx++) {
                // Ada şehrin sınırları içinde mi?
                float ox = cx + (bx + 0.5f) * 30f, oz = cz + (bz + 0.5f) * 30f;
                if (MathX.dist(ox, oz, cx, cz) > cityTmp[2]) continue;
                if (!WorldGen.blockBuilding(cityTmp, bx, bz, buildTmp)) continue;
                if (!visible(w, buildTmp[0], buildTmp[1], Math.max(buildTmp[2], buildTmp[3]))) {
                    continue;
                }
                float tone = 0.55f + WorldGen.rand01(bx, bz, 910) * 0.5f;
                M4.trs(model, buildTmp[0], 0f, buildTmp[1], 0f,
                        buildTmp[2] * 2f, buildTmp[4], buildTmp[3] * 2f);
                r.draw(models.cityBuilding, model, tone * 0.82f, tone * 0.8f, tone * 0.76f, 1f);
            }
        }
    }

    /**
     * Kaya, ağaç, ot ve varil gibi süsler dünyada saklanmaz: oyuncunun
     * çevresindeki serpme noktaları koordinatlarından hesaplanır, dolayısıyla
     * aynı yere dönüldüğünde aynı manzara karşılar.
     */
    private void drawDecor(GameWorld w) {
        int p0x = (int) Math.floor((r.camX - PROP_RANGE) / PROP_STEP);
        int p1x = (int) Math.floor((r.camX + PROP_RANGE) / PROP_STEP);
        int p0z = (int) Math.floor((r.camZ - PROP_RANGE) / PROP_STEP);
        int p1z = (int) Math.floor((r.camZ + PROP_RANGE) / PROP_STEP);
        for (int pz = p0z; pz <= p1z; pz++) {
            for (int px = p0x; px <= p1x; px++) {
                float jx = (WorldGen.rand01(px, pz, 501) - 0.5f) * PROP_STEP * 0.85f;
                float jz = (WorldGen.rand01(px, pz, 502) - 0.5f) * PROP_STEP * 0.85f;
                float x = (px + 0.5f) * PROP_STEP + jx;
                float z = (pz + 0.5f) * PROP_STEP + jz;
                // Kampın içi boş kalır; dışarısı ormandır.
                if (MathX.len(x, z) < Harvest.campClear(w)) continue;
                if (WorldGen.blocked(x, z, 1f)) continue;
                if (!visible(w, x, z, 3f)) continue;
                int type = WorldGen.propAt(px, pz, x, z);
                if (type == WorldGen.PROP_NONE) continue;
                // Kesilmiş/kırılmış düğüm: yerinde kütük kalır, zamanla geri gelir
                boolean gone = w.isDepleted(Harvest.key(px, pz));
                float yaw = WorldGen.rand01(px, pz, 503) * MathX.TAU;
                float scale = 0.7f + WorldGen.rand01(px, pz, 504) * 0.8f;
                if (gone) {
                    if (type == WorldGen.PROP_GRASS) continue;    // çalı iz bırakmaz
                    M4.trs(model, x, 0f, z, yaw, scale * 0.42f, scale * 0.2f, scale * 0.42f);
                    r.draw(type == WorldGen.PROP_TREE ? models.deadTree : models.rock,
                            model, 0.55f, 0.48f, 0.4f, 1f);
                    continue;
                }
                Mesh m;
                switch (type) {
                    case WorldGen.PROP_ROCK: m = models.rock; break;
                    case WorldGen.PROP_TREE: m = models.deadTree; break;
                    case WorldGen.PROP_GRASS: m = models.grassTuft; break;
                    case WorldGen.PROP_BARREL: m = models.barrel; break;
                    default: m = models.crate; break;
                }
                M4.trs(model, x, 0f, z, yaw, scale);
                r.draw(m, model, 1f, 1f, 1f, 1f);
            }
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

            int lv = Math.min(5, Math.max(1, s.level));
            float ls = s.levelScale();
            // Duvarlar komşularına göre birleşen parçalarla çizilir, diğer
            // yapılar oyuncunun seçtiği dönüşle.
            Mesh base = s.type == Balance.S_WALL
                    ? models.wallMesh[lv][s.wallMask & 15]
                    : models.structBase[s.type][lv];
            float yaw = s.type == Balance.S_WALL ? 0f : s.placementYaw();
            M4.trs(model, s.x, (rise - 1f) * 1.6f, s.z, yaw, ls, rise * ls, ls);
            if (w.selected == s) {
                r.drawHighlighted(base, model, dr, dg, db, 1f, emis, 0.16f, 0.30f, 0.16f, 1f);
            } else {
                r.draw(base, model, dr, dg, db, 1f, emis);
            }

            Mesh head = models.structHead[s.type][lv];
            if (head != null) {
                M4.trs(model, s.x, (rise - 1f) * 1.6f, s.z, s.yaw, ls, rise * ls, ls);
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
        float lean = p.alive ? 0f : 80f * MathX.DEG;   // trsFull radyan bekler
        M4.trsFull(model, p.x, p.y, p.z, lean, p.yaw, 0f, 1f, 1f, 1f);
        float flash = p.hurtFlash * 1.6f;
        r.drawSkinned(cm.mesh, model, bones, Renderer3D.MAX_BONES,
                1f + flash, 1f - flash * 0.35f, 1f - flash * 0.35f, 1f, flash * 0.5f);

        if (p.alive) {
            Mesh gun = models.weapons[p.currentWeapon];
            if (gun != null) {
                // Silah sağ el kemiğine bağlanır. Kolun öne savrulma açısı
                // (armRx/armRz) burada geri alınmazsa namlu yukarı bakar.
                System.arraycopy(bones, Models.BONE_ARM_R * 16, tmp2, 0, 16);
                M4.mul(tmp, model, tmp2);
                M4.translateM(tmp, 0, Models.HAND_X, Models.HAND_Y, Models.HAND_Z);
                M4.rotateM(tmp, 0, -armRz, 0f, 0f, 1f);
                M4.rotateM(tmp, 0, -armRx, 1f, 0f, 0f);
                M4.rotateM(tmp, 0, -4f, 1f, 0f, 0f);          // hafif aşağı eğim
                M4.translateM(tmp, 0, 0f, 0f, -p.recoil * 0.12f);
                int lvl = p.weaponLevel[p.currentWeapon];
                float up = (lvl - 1) * 0.06f;                 // geliştirildikçe parlar
                r.draw(gun, tmp, 1f + up, 1f + up * 0.7f, 1f - up * 0.3f, 1f,
                        p.muzzleTimer > 0f ? 1.4f : up * 0.5f);
            }
        }
    }

    /** Kemik matrisi: pivot etrafında döndür, sonra ötele. */
    private void setBone(int index, float[] pivot, float rx, float ry, float rz,
                         float ox, float oy, float oz) {
        int o = index * 16;
        M4.setIdentity(bones, o);
        M4.translateM(bones, o, pivot[0] + ox, pivot[1] + oy, pivot[2] + oz);
        if (ry != 0f) M4.rotateM(bones, o, ry, 0f, 1f, 0f);
        if (rx != 0f) M4.rotateM(bones, o, rx, 1f, 0f, 0f);
        if (rz != 0f) M4.rotateM(bones, o, rz, 0f, 0f, 1f);
        M4.translateM(bones, o, -pivot[0], -pivot[1], -pivot[2]);
    }

    private void identityBones() {
        for (int i = 0; i < Renderer3D.MAX_BONES; i++) {
            M4.setIdentity(bones, i * 16);
        }
    }

    private void animatePlayer(CharModel cm, Player p) {
        animateHuman(cm, p.animPhase, p.moving, MathX.angleDiff(p.yaw, p.aimYaw) / MathX.DEG,
                p.recoil, false);
    }

    /** Yoldaşlar ve oyuncu aynı insan animasyonunu paylaşır. */
    private void animateHuman(CharModel cm, float animPhase, boolean isMoving,
                              float aimDiffDeg, float recoil, boolean downed) {
        identityBones();
        float[][] pv = cm.pivots;
        if (downed) {
            setBone(Models.BONE_HIPS, pv[0], 84f, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_TORSO, pv[1], 84f, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_HEAD, pv[2], 40f, 0f, 22f, 0f, 0f, 0f);
            setBone(Models.BONE_ARM_L, pv[3], 20f, 0f, 46f, 0f, 0f, 0f);
            setBone(Models.BONE_ARM_R, pv[4], 20f, 0f, -46f, 0f, 0f, 0f);
            setBone(Models.BONE_LEG_L, pv[5], 24f, 0f, 0f, 0f, 0f, 0f);
            setBone(Models.BONE_LEG_R, pv[6], 16f, 0f, 0f, 0f, 0f, 0f);
            armRx = 0f;
            armRy = 0f;
            armRz = 0f;
            return;
        }
        float phase = animPhase;
        float amp = isMoving ? 34f : 0f;
        float swing = (float) Math.sin(phase) * amp;
        float bob = isMoving ? Math.abs((float) Math.sin(phase)) * 0.045f : 0f;
        float breathe = (float) Math.sin(System.nanoTime() * 1e-9f * 1.7f) * 0.012f;

        setBone(Models.BONE_HIPS, pv[0], 0f, 0f, 0f, 0f, bob + breathe, 0f);
        setBone(Models.BONE_TORSO, pv[1], isMoving ? -7f : -2f, swing * 0.12f, 0f, 0f, bob + breathe, 0f);
        setBone(Models.BONE_HEAD, pv[2], -2f, 0f, 0f, 0f, bob + breathe, 0f);
        setBone(Models.BONE_LEG_L, pv[5], swing, 0f, 0f, 0f, 0f, 0f);
        setBone(Models.BONE_LEG_R, pv[6], -swing, 0f, 0f, 0f, 0f, 0f);

        // nişan alırken kollar ileride
        float aimDiff = aimDiffDeg;
        float recoilKick = recoil * 18f;
        armRx = -78f + recoilKick;
        armRz = -12f;
        armRy = aimDiff * 0.6f;
        setBone(Models.BONE_ARM_R, pv[4], armRx, armRy, armRz, 0f, bob, 0f);
        setBone(Models.BONE_ARM_L, pv[3], -68f + recoilKick * 0.6f, armRy, 20f, 0f, bob, 0f);
    }

    /** Sağ kolun o karedeki açıları; silahı ele oturturken geri alınır. */
    private float armRx, armRy, armRz;

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

    private void drawNpcs(GameWorld w) {
        for (int i = 0; i < w.npcs.size(); i++) {
            Npc n = w.npcs.get(i);
            if (!n.alive || !visible(w, n.x, n.z, 3f)) continue;
            CharModel cm = models.npcModels[n.role];
            if (cm == null) continue;
            animateHuman(cm, n.animPhase, n.moving, 0f, 0f, n.downed);
            M4.trsFull(model, n.x, n.y, n.z, 0f, n.yaw, 0f, 1f, 1f, 1f);
            float flash = n.flash * 3f;
            float glow = n.workGlow * 0.35f;
            r.drawSkinned(cm.mesh, model, bones, Renderer3D.MAX_BONES,
                    1f + flash, 1f - flash * 0.3f + glow, 1f - flash * 0.3f, 1f,
                    flash * 0.5f + glow);

            if (!n.downed) {
                Mesh gun = models.weapons[n.def().weapon];
                if (gun != null) {
                    System.arraycopy(bones, Models.BONE_ARM_R * 16, tmp2, 0, 16);
                    M4.mul(tmp, model, tmp2);
                    M4.translateM(tmp, 0, Models.HAND_X, Models.HAND_Y, Models.HAND_Z);
                    M4.rotateM(tmp, 0, -armRz, 0f, 0f, 1f);
                    M4.rotateM(tmp, 0, -armRx, 1f, 0f, 0f);
                    M4.rotateM(tmp, 0, -4f, 1f, 0f, 0f);
                    r.draw(gun, tmp, 1f, 1f, 1f, 1f, n.fireCd > 0.85f / n.def().fireRate ? 1.2f : 0f);
                }
            }
        }
    }

    /** İnşa planları: saydam hayalet + zeminde ilerleme çubuğu. */
    private void drawPlans(GameWorld w) {
        for (int i = 0; i < w.plans.size(); i++) {
            BuildPlan p = w.plans.get(i);
            if (!p.alive || !visible(w, p.x, p.z, 3f)) continue;
            float warn = p.waiting ? 1f : 0f;
            if (!p.isUpgrade()) {
                Mesh ghost = p.type == Balance.S_WALL
                        ? models.wallMesh[1][w.wallMaskAt(p.gx, p.gz)]
                        : models.structBase[p.type][1];
                float grow = 0.35f + 0.65f * MathX.clamp(p.progress, 0f, 1f);
                M4.trs(model, p.x, 0f, p.z,
                        p.type == Balance.S_WALL ? 0f : p.rotation * MathX.PI * 0.5f,
                        1f, grow, 1f);
                r.draw(ghost, model, 0.5f + warn * 0.8f, 1.1f - warn * 0.6f, 1.4f - warn * 0.8f,
                        0.42f, 0.4f);
            } else {
                // Geliştirme şantiyesi: yapının çevresinde parlayan halka
                M4.trs(model, p.x, 0.12f, p.z, 0f, 1.25f, 1f, 1.25f);
                r.draw(models.ringFlat, model, 1f, 0.85f, 0.35f, 0.75f, 0.9f);
            }

            // zeminde ilerleme çubuğu
            float prog = MathX.clamp(p.progress, 0.02f, 1f);
            M4.trs(model, p.x - (1f - prog) * (Balance.CELL - 0.3f) * 0.5f, 0.06f, p.z, 0f,
                    prog * 0.92f, 1f, 0.16f);
            r.draw(models.unitCell, model, 0.35f, 1f, 0.7f, 0.85f, 0.7f);
        }
    }

    private void drawPickups(GameWorld w) {
        for (int i = 0; i < w.pickups.size(); i++) {
            Pickup p = w.pickups.get(i);
            if (!p.alive || !visible(w, p.x, p.z, 1.5f)) continue;
            boolean core = p.kind == Pickup.CORE;
            float bobY = p.y + (core ? 0.12f : 0f);
            float ps = core ? 1.1f : 0.85f + Math.min(0.75f, p.amount / 70f);
            M4.trs(model, p.x, bobY, p.z, p.spin, ps);
            if (core) {
                r.draw(models.corePickup, model, 1f, 1f, 1f, 1f, 1.1f + p.magnet);
            } else if (p.kind == Pickup.FOOD) {
                r.draw(models.crate, model, 0.55f, 1.05f, 0.45f, 1f, 0.3f + p.magnet * 0.6f);
            } else if (p.kind == Pickup.WATER) {
                r.draw(models.barrel, model, 0.4f, 0.85f, 1.15f, 1f, 0.35f + p.magnet * 0.6f);
            } else {
                r.draw(models.scrapPickup, model, 1f, 1f, 1f, 1f, 0.25f + p.magnet * 0.8f);
            }
        }
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
        for (int i = 0; i < w.npcs.size(); i++) {
            Npc n = w.npcs.get(i);
            if (!n.alive || !visible(w, n.x, n.z, 2f)) continue;
            r.drawBlobShadow(n.x, n.z, n.downed ? 0.9f : 0.6f, 0.34f);
        }
        for (int i = 0; i < w.pickups.size(); i++) {
            Pickup p = w.pickups.get(i);
            if (!p.alive || !visible(w, p.x, p.z, 1f)) continue;
            r.drawBlobShadow(p.x, p.z, 0.3f, 0.25f);
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
                    -pitch, yaw, 0f, t.width, t.width, len);
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
            M4.trsFull(model, p.x, p.y, p.z, 0f, yaw, 0f,
                    p.size, p.size, p.size * 2.4f);
            r.draw(models.unitBox, model, rgb[0], rgb[1], rgb[2], 1f, 1.5f);
        }
    }

    // ---- inşa arayüzü ---------------------------------------------------

    private void drawBuildOverlay(GameWorld w) {
        // Izgara artık tek dev ağ değil: yalnızca oyuncunun çevresindeki
        // hücreler için çerçeve çiziliyor (inşa alanı 110 birim yarıçapında).
        int pgx = BuildGrid.worldToCell(r.camX), pgz = BuildGrid.worldToCell(r.camZ);
        int span = 16;
        for (int gz = pgz - span; gz <= pgz + span; gz++) {
            for (int gx = pgx - span; gx <= pgx + span; gx++) {
                if (!BuildGrid.inBounds(gx, gz) || !BuildGrid.inBuildArea(gx, gz)) continue;
                float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
                if (!visible(w, cx, cz, 1.5f)) continue;
                M4.trs(model, cx, 0f, cz, 0f, 1f);
                r.draw(models.gridCell, model, 0.55f, 0.75f, 1f, 0.16f, 0.5f);
            }
        }

        int gx = w.input.hoverGx, gz = w.input.hoverGz;
        if (gx < 0 || gz < 0) return;
        int code = w.grid.canPlace(gx, gz);
        int type = w.input.buildType;
        Balance.StructDef d = Balance.struct(type);
        boolean affordable = w.player.scrap >= w.player.buildCost(d.cost)
                && w.dayCount >= d.unlockDay;
        boolean ok = code == BuildGrid.OK && affordable;
        float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);

        M4.trs(model, cx, 0.07f, cz, 0f, 1f);
        r.draw(models.unitCell, model, ok ? 0.3f : 1f, ok ? 1f : 0.3f, 0.35f, 0.45f, 0.6f);

        if (code == BuildGrid.OK) {
            // Hayalet önizleme: duvarsa komşularına göre birleşmiş hâli görünür
            Mesh ghost = type == Balance.S_WALL
                    ? models.wallMesh[1][w.wallMaskAt(gx, gz)]
                    : models.structBase[type][1];
            float gyaw = type == Balance.S_WALL
                    ? 0f : (w.input.buildRotation & 3) * MathX.PI * 0.5f;
            M4.trs(model, cx, 0f, cz, gyaw, 1f);
            r.draw(ghost, model, ok ? 0.6f : 1.2f, ok ? 1.2f : 0.5f, ok ? 0.8f : 0.5f, 0.45f, 0.35f);
            Mesh head = models.structHead[type][1];
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
        if (d2 > 170f * 170f) return false;
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
