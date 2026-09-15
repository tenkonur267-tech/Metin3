package com.karargah.survival.game;

import com.karargah.survival.engine.CharModel;
import com.karargah.survival.engine.MathX;
import com.karargah.survival.engine.Mesh;
import com.karargah.survival.engine.MeshBuilder;

/**
 * Oyundaki tüm 3B modeller burada kodla üretilir: arazi, yapıların her
 * seviyesi, karakterler, silahlar ve dekor. Hiç model dosyası yoktur.
 */
public class Models {
    public static final int BONE_HIPS = 0;
    public static final int BONE_TORSO = 1;
    public static final int BONE_HEAD = 2;
    public static final int BONE_ARM_L = 3;
    public static final int BONE_ARM_R = 4;
    public static final int BONE_LEG_L = 5;
    public static final int BONE_LEG_R = 6;
    public static final int BONE_EXTRA = 7;

    public Mesh ground, basePlatform, gridOverlay, unitBox, unitCell, ringFlat, coreOrb, portal;
    public Mesh rock, deadTree, barrel, crate, grassTuft;
    public final Mesh[][] structBase = new Mesh[Balance.STRUCTS.length][6];
    public final Mesh[] structHead = new Mesh[Balance.STRUCTS.length];
    public final Mesh[] weapons = new Mesh[Balance.WEAPONS.length];
    public CharModel player;
    public final CharModel[] zombies = new CharModel[Balance.ZOMBIES.length];

    public void build() {
        buildGround();
        buildProps();
        buildStructures();
        buildCharacters();
        buildWeapons();
        buildHelpers();
    }

    // ---- arazi ----------------------------------------------------------

    private void buildGround() {
        MeshBuilder b = new MeshBuilder();
        int n = 60;
        float cell = Balance.WORLD_HALF * 2f / n;
        for (int gz = 0; gz < n; gz++) {
            for (int gx = 0; gx < n; gx++) {
                float x0 = -Balance.WORLD_HALF + gx * cell;
                float z0 = -Balance.WORLD_HALF + gz * cell;
                float cx = x0 + cell * 0.5f, cz = z0 + cell * 0.5f;
                float r = MathX.len(cx, cz);
                float noise = MathX.smoothNoise(gx * 0.32f, gz * 0.32f);
                int col;
                if (r < Balance.BUILD_RADIUS) {
                    col = MathX.mixColor(0x4A4E44, 0x565A4E, noise);
                } else {
                    col = MathX.mixColor(0x333B2C, 0x46452F, noise);
                }
                if (noise > 0.78f) col = MathX.mixColor(col, 0x6B6A52, 0.5f);
                b.color(col);
                b.addQuad(x0, 0, z0 + cell, x0 + cell, 0, z0 + cell,
                        x0 + cell, 0, z0, x0, 0, z0, 0, 1, 0);
            }
        }
        ground = b.build();

        // üs zemini: beton daire + kenar şeridi
        MeshBuilder p = new MeshBuilder();
        p.color(0x56564C).translate(0f, 0.02f, 0f).disc(Balance.BUILD_RADIUS, 48);
        p.resetTransform();
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            float a0 = MathX.TAU * i / seg, a1 = MathX.TAU * (i + 1) / seg;
            float r0 = Balance.BUILD_RADIUS - 1.4f, r1 = Balance.BUILD_RADIUS;
            p.color(i % 2 == 0 ? 0xC9A227 : 0x3B3B34);
            p.addQuad((float) Math.cos(a0) * r0, 0.03f, (float) Math.sin(a0) * r0,
                    (float) Math.cos(a1) * r0, 0.03f, (float) Math.sin(a1) * r0,
                    (float) Math.cos(a1) * r1, 0.03f, (float) Math.sin(a1) * r1,
                    (float) Math.cos(a0) * r1, 0.03f, (float) Math.sin(a0) * r1, 0, 1, 0);
        }
        basePlatform = p.build();

        // inşa ızgarası (yalnızca inşa modunda çizilir)
        MeshBuilder g = new MeshBuilder();
        g.color(0xBBD7FF);
        float pad = 0.09f;
        for (int gz = 0; gz < BuildGrid.N; gz++) {
            for (int gx = 0; gx < BuildGrid.N; gx++) {
                if (!BuildGrid.inBuildArea(gx, gz)) continue;
                float cx = BuildGrid.cellToWorld(gx), cz = BuildGrid.cellToWorld(gz);
                float h = Balance.CELL * 0.5f - pad;
                float t = 0.055f;
                // dört ince kenar çizgisi
                g.addQuad(cx - h, 0.05f, cz + h, cx + h, 0.05f, cz + h,
                        cx + h, 0.05f, cz + h - t, cx - h, 0.05f, cz + h - t, 0, 1, 0);
                g.addQuad(cx - h, 0.05f, cz - h + t, cx + h, 0.05f, cz - h + t,
                        cx + h, 0.05f, cz - h, cx - h, 0.05f, cz - h, 0, 1, 0);
                g.addQuad(cx - h, 0.05f, cz + h, cx - h + t, 0.05f, cz + h,
                        cx - h + t, 0.05f, cz - h, cx - h, 0.05f, cz - h, 0, 1, 0);
                g.addQuad(cx + h - t, 0.05f, cz + h, cx + h, 0.05f, cz + h,
                        cx + h, 0.05f, cz - h, cx + h - t, 0.05f, cz - h, 0, 1, 0);
            }
        }
        gridOverlay = g.build();
    }

    private void buildProps() {
        MeshBuilder b = new MeshBuilder();
        b.shade(0x6B6A62, 0.1f).push().scale(1f, 0.8f, 1f).sphere(0.9f, 7, 4).pop();
        b.shade(0x5C5B54, 0.1f).push().translate(0.5f, 0.15f, 0.4f).scale(0.6f).sphere(0.7f, 6, 4).pop();
        rock = b.build();

        b = new MeshBuilder();
        b.shade(0x4A3B2C, 0.08f).cylinder(0.22f, 0.15f, 3.2f, 7);
        b.push().translate(0f, 2.4f, 0f).rotateZ(38f).color(0x453728).cylinder(0.11f, 0.05f, 1.6f, 5).pop();
        b.push().translate(0f, 1.9f, 0f).rotateZ(-46f).color(0x453728).cylinder(0.1f, 0.04f, 1.4f, 5).pop();
        b.push().translate(0f, 2.7f, 0f).rotateX(52f).color(0x453728).cylinder(0.09f, 0.04f, 1.2f, 5).pop();
        deadTree = b.build();

        b = new MeshBuilder();
        b.shade(0x7A3B2C, 0.08f).cylinder(0.42f, 0.42f, 1.1f, 10);
        b.color(0x8E8E85).push().translate(0f, 0.32f, 0f).cylinder(0.44f, 0.44f, 0.1f, 10).pop();
        b.color(0x8E8E85).push().translate(0f, 0.72f, 0f).cylinder(0.44f, 0.44f, 0.1f, 10).pop();
        barrel = b.build();

        b = new MeshBuilder();
        b.shade(0x7C6242, 0.08f).boxGround(1f, 0.9f, 1f);
        b.color(0x5E4A33).push().translate(0f, 0.45f, 0.51f).boxAt(0, 0, 0, 1.02f, 0.12f, 0.02f).pop();
        crate = b.build();

        b = new MeshBuilder();
        for (int i = 0; i < 5; i++) {
            float a = MathX.rnd(0f, MathX.TAU);
            b.shade(0x5E6B43, 0.18f).push()
                    .translate(MathX.rnd(-0.3f, 0.3f), 0f, MathX.rnd(-0.3f, 0.3f))
                    .rotateY(a / MathX.DEG)
                    .addQuad(-0.06f, 0f, 0f, 0.06f, 0f, 0f, 0.05f, 0.45f, 0.06f, -0.05f, 0.45f, 0.06f,
                            0, 0.4f, 1f).pop();
        }
        grassTuft = b.build();
    }

    // ---- yapılar --------------------------------------------------------

    private void buildStructures() {
        for (int t = 0; t < Balance.STRUCTS.length; t++) {
            for (int lv = 1; lv <= 5; lv++) {
                structBase[t][lv] = buildStructure(t, lv);
            }
        }
        structHead[Balance.S_MG] = buildMgHead();
        structHead[Balance.S_CANNON] = buildCannonHead();
        structHead[Balance.S_FLAME] = buildFlameHead();
        structHead[Balance.S_TESLA] = buildTeslaHead();
        structHead[Balance.S_SNIPER_TOWER] = buildSniperHead();
    }

    private static int levelTint(int base, int level) {
        switch (level) {
            case 1: return base;
            case 2: return MathX.mixColor(base, 0xFFFFFF, 0.12f);
            case 3: return MathX.mixColor(base, 0x90A4AE, 0.30f);
            case 4: return MathX.mixColor(base, 0xFFB74D, 0.26f);
            default: return MathX.mixColor(base, 0x4DD0E1, 0.34f);
        }
    }

    private Mesh buildStructure(int type, int level) {
        MeshBuilder b = new MeshBuilder();
        switch (type) {
            case Balance.S_CORE: return buildCore(b, level);
            case Balance.S_WALL: return buildWall(b, level);
            case Balance.S_SPIKE: return buildSpike(b, level);
            case Balance.S_GENERATOR: return buildGenerator(b, level);
            case Balance.S_AMMO: return buildCrateStation(b, level, 0x8BC34A);
            case Balance.S_REPAIR: return buildStation(b, level, 0x26A69A, true);
            case Balance.S_MED: return buildStation(b, level, 0xE57373, false);
            case Balance.S_COLLECTOR: return buildCollector(b, level);
            default: return buildTurretBase(b, type, level);
        }
    }

    private Mesh buildCore(MeshBuilder b, int level) {
        float s = Balance.CELL * Balance.CORE_CELLS * 0.5f;
        b.shade(0x4E5259, 0.05f).boxGround(s * 2f - 0.4f, 0.55f, s * 2f - 0.4f);
        b.color(0x37474F).push().translate(0f, 0.55f, 0f).boxGround(s * 1.5f, 0.35f, s * 1.5f).pop();
        for (int i = 0; i < 4; i++) {
            float a = MathX.PI * 0.5f * i + MathX.PI * 0.25f;
            b.shade(0x455A64, 0.07f).push()
                    .translate((float) Math.cos(a) * (s - 0.6f), 0.55f, (float) Math.sin(a) * (s - 0.6f))
                    .cylinder(0.3f, 0.22f, 2.6f + level * 0.16f, 6).pop();
        }
        b.color(0x263238).push().translate(0f, 0.9f, 0f).cylinder(1.5f, 1.15f, 1.5f, 10).pop();
        b.color(levelTint(0x4FC3F7, level)).push().translate(0f, 2.3f, 0f).cylinder(0.75f, 0.75f, 0.35f, 10).pop();
        b.color(0x37474F).push().translate(0f, 2.65f, 0f).cylinder(0.9f, 0.6f, 0.5f, 10).pop();
        // üst antenler
        for (int i = 0; i < 3; i++) {
            float a = MathX.TAU * i / 3f;
            b.color(0x90A4AE).push()
                    .translate((float) Math.cos(a) * 0.55f, 3.1f, (float) Math.sin(a) * 0.55f)
                    .rotateZ(MathX.rnd(-9f, 9f)).cylinder(0.07f, 0.03f, 1.1f, 5).pop();
        }
        return b.build();
    }

    private Mesh buildWall(MeshBuilder b, int level) {
        float w = Balance.CELL - 0.12f;
        switch (level) {
            case 1: {   // ahşap palisad
                b.color(0x8A6B45);
                for (int i = 0; i < 5; i++) {
                    float x = -w * 0.5f + w * (i + 0.5f) / 5f;
                    b.shade(0x8A6B45, 0.12f).push().translate(x, 0f, 0f)
                            .boxGround(w / 5f - 0.05f, 1.55f + MathX.rnd(-0.12f, 0.12f), 0.5f).pop();
                }
                b.color(0x6B5436).push().translate(0f, 1.05f, 0f).boxAt(0, 0, 0, w, 0.13f, 0.56f).pop();
                break;
            }
            case 2: {   // taş duvar
                b.shade(0x8E8E85, 0.06f).boxGround(w, 1.7f, 0.72f);
                for (int i = 0; i < 4; i++) {
                    b.shade(0x77776E, 0.08f).push()
                            .translate(MathX.rnd(-w * 0.35f, w * 0.35f), MathX.rnd(0.2f, 1.4f), 0.37f)
                            .boxAt(0, 0, 0, MathX.rnd(0.3f, 0.6f), 0.22f, 0.06f).pop();
                }
                break;
            }
            case 3: {   // çelik panel
                b.shade(0x9AA3A8, 0.05f).boxGround(w, 1.85f, 0.66f);
                b.color(0x62707A).push().translate(0f, 0.95f, 0.35f).boxAt(0, 0, 0, w, 0.16f, 0.06f).pop();
                b.color(0x62707A).push().translate(0f, 1.6f, 0.35f).boxAt(0, 0, 0, w, 0.16f, 0.06f).pop();
                for (int i = -1; i <= 1; i += 2) {
                    b.color(0x455A64).push().translate(i * (w * 0.5f - 0.14f), 0f, 0f)
                            .boxGround(0.22f, 1.95f, 0.76f).pop();
                }
                break;
            }
            case 4: {   // takviyeli + dikenli tel
                b.shade(0x77808A, 0.05f).boxGround(w, 2.0f, 0.8f);
                b.color(0xFFB74D).push().translate(0f, 1.05f, 0.42f).boxAt(0, 0, 0, w, 0.18f, 0.05f).pop();
                for (int i = -1; i <= 1; i += 2) {
                    b.color(0x546E7A).push().translate(i * (w * 0.5f - 0.15f), 0f, 0f)
                            .boxGround(0.26f, 2.15f, 0.9f).pop();
                }
                for (int i = 0; i < 6; i++) {
                    b.color(0xB0BEC5).push()
                            .translate(-w * 0.45f + w * 0.9f * i / 5f, 2.08f, 0f)
                            .rotateY(MathX.rnd(0f, 180f)).rotateZ(MathX.rnd(-25f, 25f))
                            .boxAt(0, 0.1f, 0, 0.06f, 0.3f, 0.06f).pop();
                }
                break;
            }
            default: {  // enerji kalkanı
                b.shade(0x62707A, 0.05f).boxGround(w, 1.2f, 0.85f);
                for (int i = -1; i <= 1; i += 2) {
                    b.color(0x455A64).push().translate(i * (w * 0.5f - 0.16f), 0f, 0f)
                            .boxGround(0.3f, 2.5f, 0.95f).pop();
                }
                b.color(0x4DD0E1).push().translate(0f, 1.85f, 0f).boxAt(0, 0, 0, w - 0.5f, 1.3f, 0.2f).pop();
                b.color(0x80DEEA).push().translate(0f, 1.2f, 0f).boxAt(0, 0, 0, w, 0.1f, 0.3f).pop();
                break;
            }
        }
        return b.build();
    }

    private Mesh buildSpike(MeshBuilder b, int level) {
        b.shade(0x5A4A3A, 0.06f).boxGround(Balance.CELL - 0.2f, 0.12f, Balance.CELL - 0.2f);
        int rows = 3;
        int color = level >= 4 ? 0xCFD8DC : (level >= 2 ? 0xB0BEC5 : 0x9E8E7A);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < rows; j++) {
                float x = -0.6f + 0.6f * i;
                float z = -0.6f + 0.6f * j;
                b.color(color).push().translate(x, 0.1f, z)
                        .pyramid(0.30f, 0.55f + 0.06f * level).pop();
            }
        }
        if (level >= 3) {
            b.color(0x8BC34A).push().translate(0f, 0.13f, 0f)
                    .quadXZ(Balance.CELL - 0.4f, Balance.CELL - 0.4f).pop();
        }
        return b.build();
    }

    private Mesh buildTurretBase(MeshBuilder b, int type, int level) {
        Balance.StructDef d = Balance.struct(type);
        int col = levelTint(d.color, level);
        b.shade(0x4A4F52, 0.05f).cylinder(0.85f, 0.75f, 0.28f, 10);
        b.color(col).push().translate(0f, 0.28f, 0f).cylinder(0.62f, 0.5f, 0.55f, 9).pop();
        if (type == Balance.S_SNIPER_TOWER) {
            // uzun kule gövdesi
            for (int i = 0; i < 4; i++) {
                float a = MathX.PI * 0.5f * i + MathX.PI * 0.25f;
                b.color(MathX.mixColor(col, 0x000000, 0.25f)).push()
                        .translate((float) Math.cos(a) * 0.45f, 0.3f, (float) Math.sin(a) * 0.45f)
                        .cylinder(0.11f, 0.09f, 2.5f, 5).pop();
            }
            b.color(col).push().translate(0f, 2.8f, 0f).cylinder(0.62f, 0.55f, 0.3f, 8).pop();
        } else if (type == Balance.S_TESLA) {
            b.color(MathX.mixColor(col, 0x000000, 0.3f)).push().translate(0f, 0.8f, 0f)
                    .cylinder(0.3f, 0.22f, 1.8f, 8).pop();
            b.color(0x80DEEA).push().translate(0f, 2.6f, 0f).sphere(0.34f, 9, 6).pop();
        } else {
            b.color(MathX.mixColor(col, 0x000000, 0.2f)).push().translate(0f, 0.8f, 0f)
                    .cylinder(0.28f, 0.26f, 0.5f, 8).pop();
        }
        // seviye göstergesi: tabandaki ışıklar
        for (int i = 0; i < level; i++) {
            float a = MathX.TAU * i / 5f;
            b.color(0xFFF176).push()
                    .translate((float) Math.cos(a) * 0.72f, 0.3f, (float) Math.sin(a) * 0.72f)
                    .boxAt(0, 0, 0, 0.12f, 0.06f, 0.12f).pop();
        }
        return b.build();
    }

    private Mesh buildMgHead() {
        MeshBuilder b = new MeshBuilder();
        b.color(0x546E7A).boxAt(0f, 1.3f, 0f, 0.7f, 0.42f, 0.8f);
        b.color(0x37474F).push().translate(0f, 1.3f, 0.45f).rotateX(90f)
                .cylinder(0.1f, 0.09f, 0.85f, 7).pop();
        b.color(0x37474F).push().translate(0.16f, 1.22f, 0.45f).rotateX(90f)
                .cylinder(0.07f, 0.06f, 0.75f, 6).pop();
        b.color(0x263238).push().translate(0f, 1.58f, -0.15f).boxAt(0, 0, 0, 0.45f, 0.2f, 0.4f).pop();
        return b.build();
    }

    private Mesh buildCannonHead() {
        MeshBuilder b = new MeshBuilder();
        b.color(0x6D4C41).boxAt(0f, 1.35f, -0.1f, 0.9f, 0.55f, 0.95f);
        b.color(0x4E342E).push().translate(0f, 1.42f, 0.4f).rotateX(83f)
                .cylinder(0.19f, 0.17f, 1.35f, 9).pop();
        b.color(0x8D6E63).push().translate(0f, 1.42f, 0.42f).rotateX(83f)
                .cylinder(0.24f, 0.24f, 0.25f, 9).pop();
        b.color(0x3E2723).push().translate(0f, 1.72f, -0.35f).boxAt(0, 0, 0, 0.5f, 0.22f, 0.3f).pop();
        return b.build();
    }

    private Mesh buildFlameHead() {
        MeshBuilder b = new MeshBuilder();
        b.color(0xEF6C00).boxAt(0f, 1.25f, 0f, 0.66f, 0.4f, 0.7f);
        b.color(0x424242).push().translate(0f, 1.28f, 0.4f).rotateX(90f)
                .cylinder(0.12f, 0.16f, 0.7f, 8).pop();
        b.color(0xB71C1C).push().translate(-0.28f, 1.35f, -0.3f).cylinder(0.16f, 0.16f, 0.62f, 7).pop();
        b.color(0xB71C1C).push().translate(0.28f, 1.35f, -0.3f).cylinder(0.16f, 0.16f, 0.62f, 7).pop();
        return b.build();
    }

    private Mesh buildTeslaHead() {
        MeshBuilder b = new MeshBuilder();
        b.color(0x4DD0E1).push().translate(0f, 2.6f, 0f).sphere(0.42f, 10, 7).pop();
        for (int i = 0; i < 4; i++) {
            float a = MathX.TAU * i / 4f;
            b.color(0xB2EBF2).push()
                    .translate((float) Math.cos(a) * 0.42f, 2.6f, (float) Math.sin(a) * 0.42f)
                    .rotateY(-a / MathX.DEG).rotateZ(-60f)
                    .cylinder(0.06f, 0.02f, 0.5f, 5).pop();
        }
        return b.build();
    }

    private Mesh buildSniperHead() {
        MeshBuilder b = new MeshBuilder();
        b.color(0x455A64).boxAt(0f, 3.2f, -0.1f, 0.6f, 0.36f, 0.75f);
        b.color(0x263238).push().translate(0f, 3.24f, 0.5f).rotateX(90f)
                .cylinder(0.08f, 0.07f, 1.6f, 7).pop();
        b.color(0x78909C).push().translate(0f, 3.46f, 0f).boxAt(0, 0, 0, 0.2f, 0.16f, 0.5f).pop();
        return b.build();
    }

    private Mesh buildGenerator(MeshBuilder b, int level) {
        b.shade(0x5D4037, 0.05f).boxGround(1.6f, 0.3f, 1.6f);
        b.color(levelTint(0xFDD835, level)).push().translate(0f, 0.3f, 0f)
                .boxGround(1.25f, 1.1f, 1.25f).pop();
        b.color(0x424242).push().translate(0f, 1.4f, 0f).cylinder(0.32f, 0.3f, 0.5f, 8).pop();
        b.color(0x616161).push().translate(0.45f, 1.4f, 0.0f).cylinder(0.14f, 0.12f, 0.9f, 6).pop();
        b.color(0x263238).push().translate(0f, 0.75f, 0.64f).boxAt(0, 0, 0, 0.8f, 0.5f, 0.05f).pop();
        for (int i = 0; i < level; i++) {
            b.color(0xFFF59D).push().translate(-0.3f + i * 0.16f, 1.18f, 0.64f)
                    .boxAt(0, 0, 0, 0.1f, 0.1f, 0.04f).pop();
        }
        return b.build();
    }

    private Mesh buildCrateStation(MeshBuilder b, int level, int color) {
        b.shade(0x4E342E, 0.05f).boxGround(1.7f, 0.22f, 1.7f);
        b.color(levelTint(color, level)).push().translate(0f, 0.22f, 0f).boxGround(1.3f, 0.7f, 1.0f).pop();
        b.color(MathX.mixColor(color, 0x000000, 0.35f)).push().translate(0f, 0.92f, 0f)
                .boxGround(1.35f, 0.14f, 1.05f).pop();
        b.color(0x455A64).push().translate(0.42f, 1.06f, 0f).boxGround(0.35f, 0.6f, 0.35f).pop();
        b.color(0xFFF176).push().translate(-0.3f, 1.12f, 0f).boxGround(0.22f, 0.5f, 0.22f).pop();
        return b.build();
    }

    private Mesh buildStation(MeshBuilder b, int level, int color, boolean arm) {
        b.shade(0x455A64, 0.05f).cylinder(0.85f, 0.8f, 0.24f, 10);
        b.color(levelTint(color, level)).push().translate(0f, 0.24f, 0f)
                .boxGround(1.1f, 0.85f, 1.1f).pop();
        b.color(0xECEFF1).push().translate(0f, 1.12f, 0f).boxGround(0.75f, 0.28f, 0.75f).pop();
        if (arm) {
            b.color(0x90A4AE).push().translate(0f, 1.4f, 0f).rotateZ(28f)
                    .cylinder(0.08f, 0.06f, 1.2f, 6).pop();
            b.color(color).push().translate(0.55f, 2.35f, 0f).sphere(0.18f, 8, 5).pop();
        } else {
            b.color(color).push().translate(0f, 1.42f, 0f).boxAt(0, 0, 0, 0.5f, 0.14f, 0.14f).pop();
            b.color(color).push().translate(0f, 1.42f, 0f).boxAt(0, 0, 0, 0.14f, 0.5f, 0.14f).pop();
        }
        return b.build();
    }

    private Mesh buildCollector(MeshBuilder b, int level) {
        b.shade(0x4E4A42, 0.05f).boxGround(1.7f, 0.25f, 1.7f);
        b.color(levelTint(0xFFB74D, level)).push().translate(0f, 0.25f, 0f)
                .cylinder(0.7f, 0.55f, 0.9f, 9).pop();
        b.color(0x6D4C41).push().translate(0f, 1.15f, 0f).cylinder(0.75f, 0.2f, 0.6f, 9).pop();
        for (int i = 0; i < 3; i++) {
            float a = MathX.TAU * i / 3f;
            b.shade(0x8D6E63, 0.15f).push()
                    .translate((float) Math.cos(a) * 0.55f, 1.4f, (float) Math.sin(a) * 0.55f)
                    .rotateY(a / MathX.DEG).boxGround(0.3f, 0.25f, 0.3f).pop();
        }
        return b.build();
    }

    // ---- karakterler ----------------------------------------------------

    private void buildCharacters() {
        player = buildHuman();
        zombies[Balance.Z_WALKER] = buildZombie(Balance.Z_WALKER, 1f, false);
        zombies[Balance.Z_RUNNER] = buildZombie(Balance.Z_RUNNER, 0.94f, false);
        zombies[Balance.Z_BRUTE] = buildZombie(Balance.Z_BRUTE, 1.45f, true);
        zombies[Balance.Z_SPITTER] = buildZombie(Balance.Z_SPITTER, 1.02f, false);
        zombies[Balance.Z_BOSS] = buildBoss();
        zombies[Balance.Z_CRAWLER] = buildCrawler();
    }

    private static float[][] pivots(float[]... rows) {
        float[][] p = new float[8][3];
        for (int i = 0; i < rows.length && i < 8; i++) p[i] = rows[i];
        return p;
    }

    private CharModel buildHuman() {
        MeshBuilder b = new MeshBuilder();
        int suit = 0x37474F, accent = 0x66BB6A, skin = 0xD7A87C;
        // bacaklar
        b.bone(BONE_LEG_L).color(0x2E3B43).push().translate(-0.14f, 0.86f, 0f)
                .boxAt(0, -0.43f, 0, 0.22f, 0.86f, 0.24f).pop();
        b.bone(BONE_LEG_L).color(0x263238).push().translate(-0.14f, 0.07f, 0.04f)
                .boxAt(0, 0, 0, 0.24f, 0.14f, 0.34f).pop();
        b.bone(BONE_LEG_R).color(0x2E3B43).push().translate(0.14f, 0.86f, 0f)
                .boxAt(0, -0.43f, 0, 0.22f, 0.86f, 0.24f).pop();
        b.bone(BONE_LEG_R).color(0x263238).push().translate(0.14f, 0.07f, 0.04f)
                .boxAt(0, 0, 0, 0.24f, 0.14f, 0.34f).pop();
        // gövde
        b.bone(BONE_TORSO).color(suit).push().translate(0f, 1.18f, 0f)
                .boxAt(0, 0, 0, 0.56f, 0.66f, 0.34f).pop();
        b.bone(BONE_TORSO).color(accent).push().translate(0f, 1.3f, 0.18f)
                .boxAt(0, 0, 0, 0.3f, 0.26f, 0.06f).pop();
        b.bone(BONE_TORSO).color(0x455A64).push().translate(0f, 1.46f, -0.2f)
                .boxAt(0, 0, 0, 0.42f, 0.4f, 0.16f).pop();   // sırt çantası
        b.bone(BONE_TORSO).color(0x263238).push().translate(0f, 0.9f, 0f)
                .boxAt(0, 0, 0, 0.5f, 0.16f, 0.32f).pop();   // kemer
        // kafa
        b.bone(BONE_HEAD).color(skin).push().translate(0f, 1.66f, 0f)
                .boxAt(0, 0, 0, 0.3f, 0.3f, 0.3f).pop();
        b.bone(BONE_HEAD).color(0x2E7D32).push().translate(0f, 1.82f, 0f)
                .boxAt(0, 0, 0, 0.34f, 0.14f, 0.34f).pop();  // kask
        b.bone(BONE_HEAD).color(0x1B5E20).push().translate(0f, 1.72f, 0.16f)
                .boxAt(0, 0, 0, 0.26f, 0.08f, 0.04f).pop();  // gözlük
        // kollar
        b.bone(BONE_ARM_L).color(suit).push().translate(-0.38f, 1.34f, 0f)
                .boxAt(0, -0.26f, 0, 0.18f, 0.6f, 0.2f).pop();
        b.bone(BONE_ARM_L).color(skin).push().translate(-0.38f, 1.02f, 0f)
                .boxAt(0, 0, 0, 0.17f, 0.16f, 0.19f).pop();
        b.bone(BONE_ARM_R).color(suit).push().translate(0.38f, 1.34f, 0f)
                .boxAt(0, -0.26f, 0, 0.18f, 0.6f, 0.2f).pop();
        b.bone(BONE_ARM_R).color(skin).push().translate(0.38f, 1.02f, 0f)
                .boxAt(0, 0, 0, 0.17f, 0.16f, 0.19f).pop();
        Mesh m = b.build();
        return new CharModel(m, pivots(
                new float[]{0f, 0.9f, 0f},
                new float[]{0f, 0.92f, 0f},
                new float[]{0f, 1.52f, 0f},
                new float[]{-0.38f, 1.42f, 0f},
                new float[]{0.38f, 1.42f, 0f},
                new float[]{-0.14f, 0.86f, 0f},
                new float[]{0.14f, 0.86f, 0f},
                new float[]{0.38f, 1.06f, 0.18f}), 1.9f);
    }

    private CharModel buildZombie(int type, float scale, boolean bulky) {
        Balance.ZombieDef d = Balance.zombie(type);
        MeshBuilder b = new MeshBuilder();
        int skin = d.skin, cloth = d.cloth;
        float w = bulky ? 1.45f : 1f;
        float legH = 0.78f * scale;
        float torsoY = (legH + 0.35f) * scale;

        b.bone(BONE_LEG_L).shade(cloth, 0.08f).push().translate(-0.15f * w, legH, 0f)
                .boxAt(0, -legH * 0.5f, 0, 0.21f * w, legH, 0.23f * w).pop();
        b.bone(BONE_LEG_R).shade(cloth, 0.08f).push().translate(0.15f * w, legH, 0f)
                .boxAt(0, -legH * 0.5f, 0, 0.21f * w, legH, 0.23f * w).pop();
        b.bone(BONE_TORSO).shade(cloth, 0.06f).push().translate(0f, torsoY + 0.1f, 0f)
                .boxAt(0, 0, 0, 0.52f * w, 0.62f * scale, 0.32f * w).pop();
        // yırtık gömlek parçaları
        b.bone(BONE_TORSO).shade(skin, 0.1f).push().translate(0.1f * w, torsoY + 0.02f, 0.17f * w)
                .boxAt(0, 0, 0, 0.2f * w, 0.26f * scale, 0.05f).pop();
        b.bone(BONE_HEAD).shade(skin, 0.07f).push().translate(0f, torsoY + 0.55f * scale, 0.04f)
                .boxAt(0, 0, 0, 0.28f * w, 0.3f * scale, 0.3f * w).pop();
        b.bone(BONE_HEAD).color(0x2A2A22).push().translate(0f, torsoY + 0.58f * scale, 0.19f * w)
                .boxAt(0, 0, 0, 0.2f * w, 0.07f, 0.03f).pop();
        b.bone(BONE_HEAD).color(0xB71C1C).push().translate(0.06f * w, torsoY + 0.45f * scale, 0.16f * w)
                .boxAt(0, 0, 0, 0.1f, 0.14f, 0.03f).pop();
        b.bone(BONE_ARM_L).shade(skin, 0.09f).push().translate(-0.36f * w, torsoY + 0.3f * scale, 0f)
                .boxAt(0, -0.3f * scale, 0.05f, 0.17f * w, 0.66f * scale, 0.19f * w).pop();
        b.bone(BONE_ARM_R).shade(skin, 0.09f).push().translate(0.36f * w, torsoY + 0.3f * scale, 0f)
                .boxAt(0, -0.3f * scale, 0.05f, 0.17f * w, 0.66f * scale, 0.19f * w).pop();

        if (type == Balance.Z_SPITTER) {
            b.bone(BONE_TORSO).color(0x9CCC65).push().translate(0f, torsoY + 0.42f * scale, -0.2f)
                    .sphere(0.24f, 8, 5).pop();
        }
        if (bulky) {
            b.bone(BONE_TORSO).shade(skin, 0.06f).push().translate(0f, torsoY + 0.42f * scale, 0f)
                    .boxAt(0, 0, 0, 0.8f, 0.3f, 0.45f).pop();
        }
        Mesh m = b.build();
        return new CharModel(m, pivots(
                new float[]{0f, legH, 0f},
                new float[]{0f, legH, 0f},
                new float[]{0f, torsoY + 0.35f * scale, 0f},
                new float[]{-0.36f * w, torsoY + 0.36f * scale, 0f},
                new float[]{0.36f * w, torsoY + 0.36f * scale, 0f},
                new float[]{-0.15f * w, legH, 0f},
                new float[]{0.15f * w, legH, 0f},
                new float[]{0f, torsoY, 0f}), d.height);
    }

    private CharModel buildCrawler() {
        MeshBuilder b = new MeshBuilder();
        Balance.ZombieDef d = Balance.zombie(Balance.Z_CRAWLER);
        b.bone(BONE_TORSO).shade(d.cloth, 0.08f).push().translate(0f, 0.36f, 0f)
                .boxAt(0, 0, 0, 0.46f, 0.32f, 0.72f).pop();
        b.bone(BONE_HEAD).shade(d.skin, 0.08f).push().translate(0f, 0.46f, 0.42f)
                .boxAt(0, 0, 0, 0.28f, 0.26f, 0.28f).pop();
        b.bone(BONE_ARM_L).shade(d.skin, 0.1f).push().translate(-0.3f, 0.32f, 0.2f)
                .rotateZ(20f).boxAt(0, -0.16f, 0.1f, 0.14f, 0.42f, 0.16f).pop();
        b.bone(BONE_ARM_R).shade(d.skin, 0.1f).push().translate(0.3f, 0.32f, 0.2f)
                .rotateZ(-20f).boxAt(0, -0.16f, 0.1f, 0.14f, 0.42f, 0.16f).pop();
        b.bone(BONE_LEG_L).shade(d.cloth, 0.1f).push().translate(-0.16f, 0.3f, -0.4f)
                .boxAt(0, -0.1f, 0, 0.16f, 0.3f, 0.5f).pop();
        b.bone(BONE_LEG_R).shade(d.cloth, 0.1f).push().translate(0.16f, 0.3f, -0.4f)
                .boxAt(0, -0.1f, 0, 0.16f, 0.3f, 0.5f).pop();
        return new CharModel(b.build(), pivots(
                new float[]{0f, 0.3f, 0f},
                new float[]{0f, 0.3f, 0f},
                new float[]{0f, 0.42f, 0.28f},
                new float[]{-0.3f, 0.36f, 0.2f},
                new float[]{0.3f, 0.36f, 0.2f},
                new float[]{-0.16f, 0.3f, -0.2f},
                new float[]{0.16f, 0.3f, -0.2f},
                new float[]{0f, 0.3f, 0f}), 0.95f);
    }

    private CharModel buildBoss() {
        MeshBuilder b = new MeshBuilder();
        Balance.ZombieDef d = Balance.zombie(Balance.Z_BOSS);
        float legH = 1.6f;
        b.bone(BONE_LEG_L).shade(d.cloth, 0.06f).push().translate(-0.42f, legH, 0f)
                .boxAt(0, -legH * 0.5f, 0, 0.55f, legH, 0.6f).pop();
        b.bone(BONE_LEG_R).shade(d.cloth, 0.06f).push().translate(0.42f, legH, 0f)
                .boxAt(0, -legH * 0.5f, 0, 0.55f, legH, 0.6f).pop();
        b.bone(BONE_TORSO).shade(d.skin, 0.05f).push().translate(0f, legH + 0.75f, 0f)
                .boxAt(0, 0, 0, 1.5f, 1.5f, 0.95f).pop();
        b.bone(BONE_TORSO).color(0x4E342E).push().translate(0f, legH + 1.1f, 0.5f)
                .boxAt(0, 0, 0, 1.1f, 0.6f, 0.15f).pop();
        b.bone(BONE_TORSO).color(0xB71C1C).push().translate(0f, legH + 0.5f, 0.5f)
                .boxAt(0, 0, 0, 0.7f, 0.5f, 0.1f).pop();
        b.bone(BONE_HEAD).shade(d.skin, 0.05f).push().translate(0f, legH + 1.85f, 0.1f)
                .boxAt(0, 0, 0, 0.75f, 0.7f, 0.7f).pop();
        b.bone(BONE_HEAD).color(0xECEFF1).push().translate(-0.3f, legH + 2.25f, 0f)
                .rotateZ(-20f).cylinder(0.1f, 0.01f, 0.6f, 5).pop();
        b.bone(BONE_HEAD).color(0xECEFF1).push().translate(0.3f, legH + 2.25f, 0f)
                .rotateZ(20f).cylinder(0.1f, 0.01f, 0.6f, 5).pop();
        b.bone(BONE_ARM_L).shade(d.skin, 0.07f).push().translate(-1.0f, legH + 1.25f, 0f)
                .boxAt(0, -0.75f, 0.1f, 0.42f, 1.7f, 0.5f).pop();
        b.bone(BONE_ARM_R).shade(d.skin, 0.07f).push().translate(1.0f, legH + 1.25f, 0f)
                .boxAt(0, -0.75f, 0.1f, 0.5f, 1.8f, 0.6f).pop();
        b.bone(BONE_ARM_R).color(0x455A64).push().translate(1.05f, legH + 0.25f, 0.2f)
                .boxAt(0, 0, 0, 0.8f, 0.5f, 0.8f).pop();
        return new CharModel(b.build(), pivots(
                new float[]{0f, legH, 0f},
                new float[]{0f, legH, 0f},
                new float[]{0f, legH + 1.5f, 0f},
                new float[]{-1.0f, legH + 1.4f, 0f},
                new float[]{1.0f, legH + 1.4f, 0f},
                new float[]{-0.42f, legH, 0f},
                new float[]{0.42f, legH, 0f},
                new float[]{0f, legH, 0f}), d.height);
    }

    // ---- silahlar (elde taşınan) ----------------------------------------

    private void buildWeapons() {
        weapons[Balance.W_PISTOL] = gunMesh(0.34f, 0.1f, 0.09f, 0xB0BEC5, false, false);
        weapons[Balance.W_SMG] = gunMesh(0.5f, 0.12f, 0.1f, 0x616161, true, false);
        weapons[Balance.W_SHOTGUN] = gunMesh(0.78f, 0.12f, 0.12f, 0x6D4C41, false, true);
        weapons[Balance.W_RIFLE] = gunMesh(0.82f, 0.13f, 0.11f, 0x4E5B45, true, false);
        weapons[Balance.W_SNIPER] = gunMesh(1.05f, 0.11f, 0.1f, 0x37474F, false, true);
        weapons[Balance.W_LAUNCHER] = gunMesh(0.95f, 0.2f, 0.2f, 0x5D4037, false, false);
    }

    private Mesh gunMesh(float len, float h, float w, int color, boolean mag, boolean scope) {
        MeshBuilder b = new MeshBuilder();
        b.bone(BONE_EXTRA);
        b.color(color).boxAt(0, 0, len * 0.35f, w, h, len);
        b.color(MathX.mixColor(color, 0x000000, 0.45f)).boxAt(0, -h * 0.55f, 0f, w * 0.8f, h * 1.5f, w * 1.4f);
        b.color(0x263238).boxAt(0, 0.01f, len * 0.9f, w * 0.7f, h * 0.7f, len * 0.25f);
        if (mag) {
            b.color(0x37474F).boxAt(0, -h * 1.1f, len * 0.25f, w * 0.7f, h * 1.8f, w);
        }
        if (scope) {
            b.color(0x212121).boxAt(0, h * 0.85f, len * 0.3f, w * 0.55f, h * 0.7f, len * 0.35f);
        }
        return b.build();
    }

    // ---- yardımcı görseller --------------------------------------------

    private void buildHelpers() {
        MeshBuilder b = new MeshBuilder();
        b.color(0xFFFFFF).box(1f, 1f, 1f);
        unitBox = b.build();

        b = new MeshBuilder();
        b.color(0xFFFFFF).quadXZ(Balance.CELL - 0.16f, Balance.CELL - 0.16f);
        unitCell = b.build();

        // yarıçap 1 olan ince halka (menzil göstergesi)
        b = new MeshBuilder();
        int seg = 44;
        b.color(0xFFFFFF);
        for (int i = 0; i < seg; i++) {
            float a0 = MathX.TAU * i / seg, a1 = MathX.TAU * (i + 1) / seg;
            float r0 = 0.965f, r1 = 1f;
            b.addQuad((float) Math.cos(a0) * r0, 0f, (float) Math.sin(a0) * r0,
                    (float) Math.cos(a1) * r0, 0f, (float) Math.sin(a1) * r0,
                    (float) Math.cos(a1) * r1, 0f, (float) Math.sin(a1) * r1,
                    (float) Math.cos(a0) * r1, 0f, (float) Math.sin(a0) * r1, 0, 1, 0);
        }
        ringFlat = b.build();

        b = new MeshBuilder();
        b.color(0x4FC3F7).sphere(1f, 12, 8);
        coreOrb = b.build();

        // zombi doğma kapısı
        b = new MeshBuilder();
        b.color(0x37474F).cylinder(1.6f, 1.3f, 0.35f, 10);
        for (int i = 0; i < 3; i++) {
            float a = MathX.TAU * i / 3f;
            b.color(0x455A64).push()
                    .translate((float) Math.cos(a) * 1.15f, 0.3f, (float) Math.sin(a) * 1.15f)
                    .rotateY(-a / MathX.DEG).rotateX(12f)
                    .cylinder(0.16f, 0.08f, 2.6f, 6).pop();
        }
        b.color(0x8E2B2B).push().translate(0f, 0.36f, 0f).disc(1.25f, 14).pop();
        portal = b.build();
    }
}
