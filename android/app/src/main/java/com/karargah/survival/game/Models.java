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
    /** Açık dünya zemini: beyaz kare, her parça kendi biyom rengiyle boyanır. */
    public Mesh groundTile;
    /** Şehir binası: taban 1x1, yüksekliği 1; çizerken ölçeklenir. */
    public Mesh cityBuilding;
    /** İnşa modunda tek hücrelik ızgara çerçevesi. */
    public Mesh gridCell;
    public Mesh rock, deadTree, barrel, crate, grassTuft;
    public final Mesh[][] structBase = new Mesh[Balance.STRUCTS.length][6];
    /** Dönen kule başlıkları: [tür][seviye] — her seviyede farklı görünür. */
    public final Mesh[][] structHead = new Mesh[Balance.STRUCTS.length][6];
    /** Duvarlar komşularına göre birleşir: [seviye][komşu maskesi 0..15]. */
    public final Mesh[][] wallMesh = new Mesh[6][16];
    public final Mesh[] weapons = new Mesh[Balance.WEAPONS.length];
    public CharModel player;
    public final CharModel[] zombies = new CharModel[Balance.ZOMBIES.length];
    /** Yoldaş modelleri (rol başına). */
    public final CharModel[] npcModels = new CharModel[Balance.NPC_COUNT];
    public Mesh scrapPickup, corePickup;

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
        // Dünya 100.000 x 100.000 birim: tek bir dev ağ olamaz. Bunun yerine
        // beyaz bir kare üretip her parçayı kendi biyom rengiyle boyuyoruz.
        MeshBuilder t = new MeshBuilder();
        float h = WorldRenderer.CHUNK * 0.5f;
        t.color(0xFFFFFF);
        t.addQuad(-h, 0f, h, h, 0f, h, h, 0f, -h, -h, 0f, -h, 0, 1, 0);
        groundTile = t.build();

        // üs zemini: beton daire + kenar şeridi
        MeshBuilder p = new MeshBuilder();
        p.color(0x56564C).translate(0f, 0.02f, 0f).disc(Balance.BUILD_RADIUS, 64);
        p.resetTransform();
        int seg = 64;
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

        // Şehir binası: yüzleri hafif farklı tonda bir kutu (taban 1x1, boy 1)
        MeshBuilder bd = new MeshBuilder();
        bd.shade(0xFFFFFF, 0.14f).push().translate(0f, 0.5f, 0f).box(1f, 1f, 1f).pop();
        bd.color(0xBDBDBD).push().translate(0f, 1.01f, 0f).box(1.06f, 0.03f, 1.06f).pop();
        cityBuilding = bd.build();

        // İnşa ızgarası: tek hücrelik çerçeve, gerektiği kadar çizilir
        MeshBuilder g = new MeshBuilder();
        g.color(0xBBD7FF);
        float pad = 0.09f;
        float ch = Balance.CELL * 0.5f - pad;
        float th = 0.055f;
        g.addQuad(-ch, 0.05f, ch, ch, 0.05f, ch, ch, 0.05f, ch - th, -ch, 0.05f, ch - th, 0, 1, 0);
        g.addQuad(-ch, 0.05f, -ch + th, ch, 0.05f, -ch + th, ch, 0.05f, -ch, -ch, 0.05f, -ch, 0, 1, 0);
        g.addQuad(-ch, 0.05f, ch, -ch + th, 0.05f, ch, -ch + th, 0.05f, -ch, -ch, 0.05f, -ch, 0, 1, 0);
        g.addQuad(ch - th, 0.05f, ch, ch, 0.05f, ch, ch, 0.05f, -ch, ch - th, 0.05f, -ch, 0, 1, 0);
        gridCell = g.build();
        gridOverlay = gridCell;
        ground = groundTile;
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
        for (int lv = 1; lv <= 5; lv++) {
            for (int mask = 0; mask < 16; mask++) {
                wallMesh[lv][mask] = buildWallPiece(lv, mask);
            }
        }
        for (int t = 0; t < Balance.STRUCTS.length; t++) {
            for (int lv = 1; lv <= 5; lv++) {
                structBase[t][lv] = buildStructure(t, lv);
            }
        }
        for (int lv = 1; lv <= 5; lv++) {
            structHead[Balance.S_MG][lv] = buildMgHead(lv);
            structHead[Balance.S_CANNON][lv] = buildCannonHead(lv);
            structHead[Balance.S_FLAME][lv] = buildFlameHead(lv);
            structHead[Balance.S_TESLA][lv] = buildTeslaHead(lv);
            structHead[Balance.S_SNIPER_TOWER][lv] = buildSniperHead(lv);
        }
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
            case Balance.S_WALL: return wallMesh[level][0];   // yalnız duran duvar
            case Balance.S_SPIKE: return buildSpike(b, level);
            case Balance.S_GENERATOR: return buildGenerator(b, level);
            case Balance.S_AMMO: return buildCrateStation(b, level, 0x8BC34A);
            case Balance.S_REPAIR: return buildStation(b, level, 0x26A69A, true);
            case Balance.S_MED: return buildStation(b, level, 0xE57373, false);
            case Balance.S_COLLECTOR: return buildCollector(b, level);
            case Balance.S_BARRACKS: return buildBarracks(b, level);
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

    /**
     * Duvar parçası. Duvarlar komşularına göre birleşir: ortada bir direk,
     * her bağlı yöne bir kol. Böylece hem düz sıralar hem köşeler boşluksuz
     * oturur ve ayrıca döndürmeye gerek kalmaz.
     * @param mask bit0 +X, bit1 -X, bit2 +Z, bit3 -Z
     */
    private Mesh buildWallPiece(int level, int mask) {
        MeshBuilder b = new MeshBuilder();
        float half = Balance.CELL * 0.5f;          // 1.0
        float post = 0.94f;
        float h = 1.45f + level * 0.14f;           // 1.59 .. 2.15
        int body, trim;
        switch (level) {
            case 1: body = 0x8A6B45; trim = 0x6B5436; break;
            case 2: body = 0x8E8E85; trim = 0x6F6F68; break;
            case 3: body = 0x9AA3A8; trim = 0x55646C; break;
            case 4: body = 0x77808A; trim = 0xFFB74D; break;
            default: body = 0x62707A; trim = 0x4DD0E1; break;
        }

        // --- orta direk -------------------------------------------------
        if (level == 1) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j += 2) {
                    if (i == 0 && j == -1) continue;
                    b.shade(body, 0.12f).push()
                            .translate(i * 0.3f, 0f, j * 0.28f)
                            .boxGround(0.32f, h + MathX.rnd(-0.08f, 0.08f), 0.3f).pop();
                }
            }
            b.color(trim).push().translate(0f, h * 0.62f, 0f)
                    .boxAt(0, 0, 0, post + 0.1f, 0.12f, post + 0.1f).pop();
        } else {
            b.shade(body, 0.05f).boxGround(post, h, post);
            b.color(trim).push().translate(0f, h, 0f)
                    .boxAt(0, 0.06f, 0, post + 0.14f, 0.12f, post + 0.14f).pop();
        }

        // --- kollar ------------------------------------------------------
        boolean any = false;
        for (int d = 0; d < 4; d++) {
            if ((mask & (1 << d)) == 0) continue;
            any = true;
            buildWallArm(b, d, level, body, trim, h, post, half, false);
        }
        if (!any) {
            // Yalnız duran duvar yine de duvar gibi görünsün: iki yana kısa kanat
            buildWallArm(b, 0, level, body, trim, h, post, half, true);
            buildWallArm(b, 1, level, body, trim, h, post, half, true);
        }
        return b.build();
    }

    /** d: 0=+X, 1=-X, 2=+Z, 3=-Z */
    private void buildWallArm(MeshBuilder b, int d, int level, int body, int trim,
                              float h, float post, float half, boolean stub) {
        float yaw = d == 0 ? 90f : (d == 1 ? -90f : (d == 2 ? 0f : 180f));
        float reach = stub ? half * 0.55f : half + 0.02f;
        float len = reach - post * 0.5f + 0.04f;
        float cz = (post * 0.5f + reach) * 0.5f;
        float w = 0.66f;
        float ah = h - 0.18f;

        b.push().rotateY(yaw);
        switch (level) {
            case 1:   // ahşap: yatay kalaslar
                for (int i = 0; i < 3; i++) {
                    b.shade(body, 0.12f).push().translate(0f, ah * (0.22f + i * 0.3f), cz)
                            .boxAt(0, 0, 0, w, ah * 0.24f, len).pop();
                }
                break;
            case 2:   // taş: bloklar
                b.shade(body, 0.07f).push().translate(0f, 0f, cz).boxGround(w, ah, len).pop();
                for (int i = 0; i < 3; i++) {
                    b.shade(trim, 0.1f).push()
                            .translate(w * 0.5f, ah * (0.25f + i * 0.28f), cz + MathX.rnd(-0.2f, 0.2f))
                            .boxAt(0, 0, 0, 0.06f, ah * 0.16f, len * 0.4f).pop();
                }
                break;
            case 3:   // çelik panel + raylar
                b.shade(body, 0.05f).push().translate(0f, 0f, cz).boxGround(w, ah, len).pop();
                for (int i = 0; i < 2; i++) {
                    b.color(trim).push().translate(0f, ah * (0.35f + i * 0.4f), cz)
                            .boxAt(0, 0, 0, w + 0.06f, 0.12f, len * 0.94f).pop();
                }
                break;
            case 4:   // takviyeli + dikenli tel
                b.shade(body, 0.05f).push().translate(0f, 0f, cz).boxGround(w + 0.06f, ah, len).pop();
                b.color(trim).push().translate(0f, ah * 0.55f, cz)
                        .boxAt(0, 0, 0, w + 0.12f, 0.14f, len * 0.92f).pop();
                for (int i = 0; i < 3; i++) {
                    b.color(0xB0BEC5).push()
                            .translate(0f, ah + 0.12f, cz - len * 0.3f + i * len * 0.3f)
                            .rotateY(MathX.rnd(0f, 180f)).rotateZ(MathX.rnd(-28f, 28f))
                            .boxAt(0, 0.08f, 0, 0.06f, 0.28f, 0.06f).pop();
                }
                break;
            default:  // enerji kalkanı
                b.shade(body, 0.05f).push().translate(0f, 0f, cz)
                        .boxGround(w, ah * 0.45f, len).pop();
                b.color(trim).push().translate(0f, ah * 0.78f, cz)
                        .boxAt(0, 0, 0, w * 0.55f, ah * 0.66f, len * 0.96f).pop();
                b.color(0x80DEEA).push().translate(0f, ah * 0.45f, cz)
                        .boxAt(0, 0, 0, w + 0.05f, 0.1f, len).pop();
                break;
        }
        b.pop();
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

    // Kule başlıkları seviyeyle birlikte belirgin şekilde değişir:
    // namlu sayısı/uzunluğu artar, üst seviyelerde zırh plakası ve parlayan
    // enerji hatları eklenir.

    private static int steel(int level) {
        switch (level) {
            case 1: return 0x546E7A;
            case 2: return 0x5E7A86;
            case 3: return 0x6B8794;
            case 4: return 0x7C6A4A;
            default: return 0x4A6B74;
        }
    }

    /** Üst seviyelerde eklenen zırh plakası. */
    private void armorPlate(MeshBuilder b, int level, float y, float w, float d) {
        if (level < 4) return;
        b.color(level >= 5 ? 0x37474F : 0x6D4C41).push().translate(0f, y, d)
                .boxAt(0, 0, 0, w, 0.34f, 0.08f).pop();
    }

    /** 5. seviyede parlayan enerji hattı. */
    private void energyTrim(MeshBuilder b, int level, float y, float w) {
        if (level < 5) return;
        b.color(0x4DD0E1).push().translate(0f, y, 0f)
                .boxAt(0, 0, 0, w, 0.07f, 0.07f).pop();
    }

    private Mesh buildMgHead(int level) {
        MeshBuilder b = new MeshBuilder();
        int col = steel(level);
        float bodyW = 0.66f + level * 0.05f;
        b.color(col).boxAt(0f, 1.3f, 0f, bodyW, 0.42f + level * 0.02f, 0.8f);
        b.color(0x263238).push().translate(0f, 1.58f, -0.15f)
                .boxAt(0, 0, 0, 0.45f, 0.2f, 0.4f).pop();

        int barrels = level >= 5 ? 4 : (level >= 3 ? 2 : 1);
        float bl = 0.8f + level * 0.09f;
        for (int i = 0; i < barrels; i++) {
            float off = barrels == 1 ? 0f : (-0.09f * (barrels - 1) + i * 0.18f);
            float yoff = barrels == 4 && i % 2 == 1 ? 0.13f : 0f;
            b.color(0x37474F).push().translate(off, 1.3f + yoff, 0.45f).rotateX(90f)
                    .cylinder(0.085f, 0.075f, bl, 7).pop();
        }
        if (level >= 4) {   // ağız freni
            b.color(0x90A4AE).push().translate(0f, 1.3f, 0.45f + bl * 0.92f).rotateX(90f)
                    .cylinder(0.14f, 0.14f, 0.16f, 8).pop();
        }
        if (level >= 2) {   // cephane kutusu
            b.color(0x4E5B45).push().translate(-bodyW * 0.62f, 1.22f, -0.1f)
                    .boxGround(0.26f, 0.3f, 0.44f).pop();
        }
        armorPlate(b, level, 1.32f, bodyW + 0.1f, 0.42f);
        energyTrim(b, level, 1.55f, bodyW);
        return b.build();
    }

    private Mesh buildCannonHead(int level) {
        MeshBuilder b = new MeshBuilder();
        int col = level >= 4 ? 0x7C5A44 : 0x6D4C41;
        b.color(col).boxAt(0f, 1.35f, -0.1f, 0.9f + level * 0.04f, 0.55f, 0.95f);
        b.color(0x3E2723).push().translate(0f, 1.72f, -0.35f)
                .boxAt(0, 0, 0, 0.5f, 0.22f, 0.3f).pop();

        int barrels = level >= 4 ? 2 : 1;
        float bl = 1.3f + level * 0.12f;
        float rad = 0.17f + level * 0.012f;
        for (int i = 0; i < barrels; i++) {
            float off = barrels == 1 ? 0f : (i == 0 ? -0.2f : 0.2f);
            b.color(0x4E342E).push().translate(off, 1.42f, 0.4f).rotateX(83f)
                    .cylinder(rad, rad - 0.02f, bl, 9).pop();
            b.color(0x8D6E63).push().translate(off, 1.42f, 0.42f).rotateX(83f)
                    .cylinder(rad + 0.07f, rad + 0.07f, 0.25f, 9).pop();
            if (level >= 3) {   // ağız freni
                b.color(0x90A4AE).push().translate(off, 1.42f + bl * 0.12f, 0.4f + bl * 0.94f)
                        .rotateX(83f).cylinder(rad + 0.05f, rad + 0.05f, 0.2f, 8).pop();
            }
        }
        if (level >= 2) {   // karşı ağırlık
            b.color(0x5D4037).push().translate(0f, 1.35f, -0.6f)
                    .boxAt(0, 0, 0, 0.5f, 0.34f, 0.24f).pop();
        }
        armorPlate(b, level, 1.4f, 1.0f, 0.5f);
        energyTrim(b, level, 1.66f, 0.8f);
        return b.build();
    }

    private Mesh buildFlameHead(int level) {
        MeshBuilder b = new MeshBuilder();
        int col = level >= 4 ? 0xF57C00 : 0xEF6C00;
        b.color(col).boxAt(0f, 1.25f, 0f, 0.62f + level * 0.05f, 0.4f, 0.7f);
        int nozzles = level >= 5 ? 3 : (level >= 3 ? 2 : 1);
        for (int i = 0; i < nozzles; i++) {
            float off = nozzles == 1 ? 0f : (-0.11f * (nozzles - 1) + i * 0.22f);
            b.color(0x424242).push().translate(off, 1.28f, 0.4f).rotateX(90f)
                    .cylinder(0.11f, 0.16f + level * 0.01f, 0.7f, 8).pop();
        }
        float tank = 0.15f + level * 0.012f;
        b.color(0xB71C1C).push().translate(-0.3f, 1.35f, -0.3f).cylinder(tank, tank, 0.62f, 7).pop();
        b.color(0xB71C1C).push().translate(0.3f, 1.35f, -0.3f).cylinder(tank, tank, 0.62f, 7).pop();
        if (level >= 4) {   // pilot alev halkası
            b.color(0xFFD54F).push().translate(0f, 1.28f, 0.72f).rotateX(90f)
                    .cylinder(0.2f, 0.2f, 0.06f, 10).pop();
        }
        armorPlate(b, level, 1.28f, 0.7f, 0.38f);
        energyTrim(b, level, 1.5f, 0.6f);
        return b.build();
    }

    private Mesh buildTeslaHead(int level) {
        MeshBuilder b = new MeshBuilder();
        float r = 0.36f + level * 0.035f;
        b.color(0x4DD0E1).push().translate(0f, 2.6f, 0f).sphere(r, 10, 7).pop();
        int prongs = 4 + (level - 1);
        for (int i = 0; i < prongs; i++) {
            float a = MathX.TAU * i / prongs;
            b.color(0xB2EBF2).push()
                    .translate((float) Math.cos(a) * r, 2.6f, (float) Math.sin(a) * r)
                    .rotateY(-a / MathX.DEG).rotateZ(-60f)
                    .cylinder(0.06f, 0.02f, 0.45f + level * 0.05f, 5).pop();
        }
        if (level >= 3) {   // bilezik
            b.color(0x80DEEA).push().translate(0f, 2.6f - r * 0.85f, 0f)
                    .cylinder(r + 0.16f, r + 0.16f, 0.07f, 12).pop();
        }
        if (level >= 5) {
            b.color(0xE0F7FA).push().translate(0f, 2.6f + r + 0.18f, 0f).sphere(0.12f, 8, 5).pop();
        }
        return b.build();
    }

    private Mesh buildSniperHead(int level) {
        MeshBuilder b = new MeshBuilder();
        int col = steel(level);
        b.color(col).boxAt(0f, 3.2f, -0.1f, 0.58f + level * 0.04f, 0.36f, 0.75f);
        float bl = 1.5f + level * 0.16f;
        b.color(0x263238).push().translate(0f, 3.24f, 0.5f).rotateX(90f)
                .cylinder(0.075f + level * 0.008f, 0.065f, bl, 7).pop();
        b.color(0x78909C).push().translate(0f, 3.46f, 0f)
                .boxAt(0, 0, 0, 0.2f, 0.16f, 0.5f).pop();
        if (level >= 2) {   // dürbün
            b.color(0x212121).push().translate(0f, 3.54f, 0.15f).rotateX(90f)
                    .cylinder(0.09f, 0.09f, 0.5f, 8).pop();
        }
        if (level >= 3) {   // sehpa
            for (int i = -1; i <= 1; i += 2) {
                b.color(0x455A64).push().translate(i * 0.16f, 3.1f, 0.75f)
                        .rotateZ(i * 24f).cylinder(0.04f, 0.03f, 0.55f, 5).pop();
            }
        }
        if (level >= 4) {
            b.color(0x90A4AE).push().translate(0f, 3.24f, 0.5f + bl * 0.95f).rotateX(90f)
                    .cylinder(0.12f, 0.12f, 0.18f, 8).pop();
        }
        if (level >= 5) {   // ray tabancası bobinleri
            for (int i = 0; i < 3; i++) {
                b.color(0x4DD0E1).push().translate(0f, 3.24f, 0.7f + i * 0.4f).rotateX(90f)
                        .cylinder(0.13f, 0.13f, 0.08f, 10).pop();
            }
        }
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

    private Mesh buildBarracks(MeshBuilder b, int level) {
        b.shade(0x5A5348, 0.05f).boxGround(1.9f, 0.2f, 1.9f);
        // kum torbası duvarlar
        for (int i = -1; i <= 1; i++) {
            b.shade(0x8D7B5B, 0.1f).push().translate(i * 0.6f, 0.2f, -0.75f)
                    .boxGround(0.58f, 0.34f, 0.34f).pop();
            b.shade(0x8D7B5B, 0.1f).push().translate(i * 0.6f, 0.54f, -0.75f)
                    .boxGround(0.5f, 0.3f, 0.3f).pop();
        }
        // çadır gövdesi
        b.color(0x4E5B45).push().translate(0f, 0.2f, 0.15f).boxGround(1.5f, 0.85f, 1.3f).pop();
        b.color(0x3E4A33).push().translate(0f, 1.05f, 0.15f).rotateY(45f)
                .pyramid(1.5f, 0.6f).pop();
        b.color(0x2F3B2A).push().translate(0f, 0.45f, 0.82f)
                .boxAt(0, 0, 0, 0.55f, 0.75f, 0.05f).pop();       // kapı
        // bayrak: seviyeyle büyür
        b.color(0x8D6E63).push().translate(-0.8f, 0.2f, -0.7f)
                .cylinder(0.05f, 0.04f, 1.6f + level * 0.12f, 6).pop();
        b.color(0xC62828).push().translate(-0.8f, 1.5f + level * 0.12f, -0.7f)
                .boxAt(0.3f, 0, 0, 0.55f + level * 0.06f, 0.34f, 0.03f).pop();
        // yatak/sandık: seviye kadar
        for (int i = 0; i < level; i++) {
            b.shade(0x6D4C41, 0.1f).push()
                    .translate(0.55f - (i % 3) * 0.5f, 0.2f, 0.55f - (i / 3) * 0.45f)
                    .boxGround(0.36f, 0.22f, 0.3f).pop();
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
        player = buildHuman(0x37474F, 0x66BB6A, 0);
        npcModels[Balance.NPC_GUARD] = buildHuman(0x3E4A33, 0x8BC34A, 1);
        npcModels[Balance.NPC_ENGINEER] = buildHuman(0x4E5B66, 0xFFB74D, 2);
        npcModels[Balance.NPC_SCAVENGER] = buildHuman(0x5D4037, 0xFFD54F, 3);
        npcModels[Balance.NPC_MEDIC] = buildHuman(0x37474F, 0xE57373, 4);
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

    /**
     * İnsan karakter. flavor: 0 oyuncu, 1 muhafız, 2 mühendis (baret),
     * 3 toplayıcı (kasket + çuval), 4 sağlıkçı (kızılhaç).
     */
    private CharModel buildHuman(int suit, int accent, int flavor) {
        MeshBuilder b = new MeshBuilder();
        int skin = 0xD7A87C;
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
        b.bone(BONE_TORSO).color(flavor == 3 ? 0x795548 : 0x455A64).push()
                .translate(0f, 1.46f, -0.2f)
                .boxAt(0, 0, 0, 0.42f, flavor == 3 ? 0.52f : 0.4f, 0.16f).pop();   // sırt çantası
        if (flavor == 2) {   // mühendis: sırtta alet
            b.bone(BONE_TORSO).color(0xFFB74D).push().translate(0.16f, 1.5f, -0.3f)
                    .rotateZ(28f).boxAt(0, 0, 0, 0.07f, 0.44f, 0.07f).pop();
        }
        if (flavor == 4) {   // sağlıkçı: yan çanta
            b.bone(BONE_TORSO).color(0xECEFF1).push().translate(-0.32f, 1.06f, 0f)
                    .boxAt(0, 0, 0, 0.16f, 0.2f, 0.22f).pop();
            b.bone(BONE_TORSO).color(0xE53935).push().translate(-0.41f, 1.06f, 0f)
                    .boxAt(0, 0, 0, 0.02f, 0.1f, 0.04f).pop();
        }
        b.bone(BONE_TORSO).color(0x263238).push().translate(0f, 0.9f, 0f)
                .boxAt(0, 0, 0, 0.5f, 0.16f, 0.32f).pop();   // kemer
        // kafa
        b.bone(BONE_HEAD).color(skin).push().translate(0f, 1.66f, 0f)
                .boxAt(0, 0, 0, 0.3f, 0.3f, 0.3f).pop();
        switch (flavor) {
            case 2:   // mühendis bareti
                b.bone(BONE_HEAD).color(0xFFC107).push().translate(0f, 1.83f, 0f)
                        .boxAt(0, 0, 0, 0.33f, 0.13f, 0.33f).pop();
                b.bone(BONE_HEAD).color(0xFFA000).push().translate(0f, 1.79f, 0.17f)
                        .boxAt(0, 0, 0, 0.3f, 0.05f, 0.1f).pop();
                break;
            case 3:   // kasket
                b.bone(BONE_HEAD).color(0x6D4C41).push().translate(0f, 1.82f, 0f)
                        .boxAt(0, 0, 0, 0.32f, 0.1f, 0.32f).pop();
                b.bone(BONE_HEAD).color(0x5D4037).push().translate(0f, 1.79f, 0.2f)
                        .boxAt(0, 0, 0, 0.3f, 0.04f, 0.14f).pop();
                break;
            case 4:   // sağlıkçı beresi + haç
                b.bone(BONE_HEAD).color(0xECEFF1).push().translate(0f, 1.82f, 0f)
                        .boxAt(0, 0, 0, 0.33f, 0.12f, 0.33f).pop();
                b.bone(BONE_HEAD).color(0xE53935).push().translate(0f, 1.83f, 0.17f)
                        .boxAt(0, 0, 0, 0.14f, 0.05f, 0.03f).pop();
                b.bone(BONE_HEAD).color(0xE53935).push().translate(0f, 1.83f, 0.17f)
                        .boxAt(0, 0, 0, 0.05f, 0.14f, 0.03f).pop();
                break;
            default:  // kask
                b.bone(BONE_HEAD).color(flavor == 1 ? 0x33691E : 0x2E7D32).push()
                        .translate(0f, 1.82f, 0f).boxAt(0, 0, 0, 0.34f, 0.14f, 0.34f).pop();
                break;
        }
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
    // Modeller kabza üstü (elin tuttuğu nokta) orijinde, namlu +Z yönünde
    // olacak şekilde kurulur. Balance.WeaponDef.muzzleZ/muzzleY bu modellerin
    // namlu ucunu gösterir; mermi tam oradan çıkar.

    /** Oyuncu modelinde sağ elin durduğu nokta (silah buraya bağlanır). */
    public static final float HAND_X = 0.38f;
    public static final float HAND_Y = 1.04f;
    public static final float HAND_Z = 0.06f;

    private static final int METAL = 0x4A5257;
    private static final int METAL_DARK = 0x2C3236;
    private static final int POLYMER = 0x23282B;
    private static final int WOOD = 0x6D4C41;

    private void buildWeapons() {
        weapons[Balance.W_PISTOL] = buildPistol();
        weapons[Balance.W_SMG] = buildSmg();
        weapons[Balance.W_SHOTGUN] = buildShotgun();
        weapons[Balance.W_RIFLE] = buildRifle();
        weapons[Balance.W_SNIPER] = buildSniper();
        weapons[Balance.W_LAUNCHER] = buildLauncher();
    }

    /** Kabza: orijinden aşağı-geri doğru eğimli. */
    private void gunGrip(MeshBuilder b, int color, float angle, float len, float w) {
        b.color(color).push().translate(0f, -0.02f, -0.02f).rotateX(angle)
                .boxAt(0, -len * 0.5f, 0, w, len, 0.13f).pop();
        b.color(METAL_DARK).push().translate(0f, -0.02f, -0.02f).rotateX(angle)
                .boxAt(0, -len, 0, w + 0.01f, 0.04f, 0.14f).pop();
    }

    /** Tetik korkuluğu. */
    private void triggerGuard(MeshBuilder b, float z) {
        b.color(METAL_DARK);
        b.boxAt(0f, -0.045f, z, 0.045f, 0.03f, 0.17f);
        b.boxAt(0f, -0.13f, z + 0.005f, 0.045f, 0.16f, 0.03f);
        b.boxAt(0f, -0.09f, z - 0.075f, 0.045f, 0.1f, 0.03f);
        b.color(0x8D8D8D).boxAt(0f, -0.075f, z - 0.02f, 0.03f, 0.075f, 0.025f);
    }

    /** Üstte nişangâh rayı (dişli görünüm). */
    private void topRail(MeshBuilder b, float z0, float len, float y, float w) {
        b.color(METAL_DARK).boxAt(0f, y, z0 + len * 0.5f, w, 0.022f, len);
        int n = Math.max(3, (int) (len / 0.07f));
        for (int i = 0; i < n; i++) {
            b.color(0x3A4247).boxAt(0f, y + 0.018f, z0 + 0.03f + i * (len / n),
                    w * 0.85f, 0.018f, 0.028f);
        }
    }

    private void frontSight(MeshBuilder b, float z, float y) {
        b.color(METAL_DARK).boxAt(0f, y + 0.045f, z, 0.02f, 0.075f, 0.03f);
        b.color(METAL_DARK).boxAt(0f, y + 0.02f, z, 0.07f, 0.03f, 0.035f);
    }

    private void rearSight(MeshBuilder b, float z, float y) {
        b.color(METAL_DARK).boxAt(-0.035f, y + 0.04f, z, 0.022f, 0.06f, 0.03f);
        b.color(METAL_DARK).boxAt(0.035f, y + 0.04f, z, 0.022f, 0.06f, 0.03f);
    }

    /** Eğimli şarjör. */
    private void magazine(MeshBuilder b, int color, float z, float len, float tilt, float w) {
        b.color(color).push().translate(0f, -0.04f, z).rotateX(tilt)
                .boxAt(0, -len * 0.5f, 0, w, len, 0.1f).pop();
        b.color(METAL_DARK).push().translate(0f, -0.04f, z).rotateX(tilt)
                .boxAt(0, -len, 0, w + 0.012f, 0.03f, 0.11f).pop();
    }

    private Mesh buildPistol() {
        MeshBuilder b = new MeshBuilder();
        b.bone(BONE_EXTRA);
        // sürgü + gövde
        b.color(0x656D72).boxAt(0f, 0.065f, 0.19f, 0.085f, 0.11f, 0.42f);
        b.color(0x7C858B).boxAt(0f, 0.115f, 0.19f, 0.07f, 0.02f, 0.42f);   // üst pah
        b.color(METAL_DARK).boxAt(0f, 0.02f, 0.13f, 0.09f, 0.06f, 0.3f);   // alt gövde
        // namlu ucu
        b.color(METAL_DARK).push().translate(0f, 0.07f, 0.4f).rotateX(90f)
                .cylinder(0.026f, 0.026f, 0.05f, 8).pop();
        // nişangâh
        b.color(METAL_DARK).boxAt(0f, 0.125f, 0.36f, 0.018f, 0.022f, 0.02f);
        b.color(METAL_DARK).boxAt(-0.028f, 0.125f, 0.03f, 0.016f, 0.022f, 0.022f);
        b.color(METAL_DARK).boxAt(0.028f, 0.125f, 0.03f, 0.016f, 0.022f, 0.022f);
        triggerGuard(b, 0.05f);
        gunGrip(b, POLYMER, 14f, 0.3f, 0.085f);
        return b.build();
    }

    private Mesh buildSmg() {
        MeshBuilder b = new MeshBuilder();
        b.bone(BONE_EXTRA);
        b.color(0x3B4145).boxAt(0f, 0.06f, 0.2f, 0.095f, 0.14f, 0.52f);     // gövde
        b.color(METAL_DARK).boxAt(0f, 0.115f, 0.12f, 0.06f, 0.05f, 0.2f);   // kurma kolu yuvası
        b.color(0x8A9196).boxAt(0.06f, 0.09f, 0.16f, 0.03f, 0.04f, 0.09f);  // kurma kolu
        // namlu kılıfı (havalandırma delikleri)
        b.color(METAL).push().translate(0f, 0.07f, 0.55f).rotateX(90f)
                .cylinder(0.05f, 0.05f, 0.18f, 8).pop();
        for (int i = 0; i < 3; i++) {
            b.color(METAL_DARK).boxAt(0f, 0.115f, 0.5f + i * 0.055f, 0.06f, 0.02f, 0.025f);
        }
        b.color(METAL_DARK).push().translate(0f, 0.07f, 0.66f).rotateX(90f)
                .cylinder(0.028f, 0.028f, 0.07f, 8).pop();
        frontSight(b, 0.68f, 0.09f);
        rearSight(b, 0.08f, 0.1f);
        topRail(b, 0.12f, 0.3f, 0.135f, 0.05f);
        // katlanır dipçik
        b.color(METAL_DARK).boxAt(-0.045f, 0.07f, -0.14f, 0.022f, 0.03f, 0.26f);
        b.color(METAL_DARK).boxAt(0.045f, 0.07f, -0.14f, 0.022f, 0.03f, 0.26f);
        b.color(POLYMER).boxAt(0f, 0.07f, -0.27f, 0.13f, 0.09f, 0.035f);
        triggerGuard(b, 0.06f);
        gunGrip(b, 12f, 0.3f, 0.09f);
        magazine(b, POLYMER, 0.22f, 0.34f, 6f, 0.075f);
        return b.build();
    }

    private void gunGrip(MeshBuilder b, float angle, float len, float w) {
        gunGrip(b, POLYMER, angle, len, w);
    }

    private Mesh buildShotgun() {
        MeshBuilder b = new MeshBuilder();
        b.bone(BONE_EXTRA);
        b.color(METAL_DARK).boxAt(0f, 0.06f, 0.16f, 0.1f, 0.13f, 0.42f);    // gövde
        // namlu + altında fişek tüpü
        b.color(0x3F464A).push().translate(0f, 0.09f, 0.35f).rotateX(90f)
                .cylinder(0.042f, 0.042f, 0.66f, 9).pop();
        b.color(0x565E63).push().translate(0f, 0.005f, 0.35f).rotateX(90f)
                .cylinder(0.032f, 0.032f, 0.56f, 8).pop();
        b.color(METAL_DARK).push().translate(0f, 0.09f, 0.96f).rotateX(90f)
                .cylinder(0.05f, 0.05f, 0.06f, 9).pop();                    // ağız
        // ahşap kundak ve pompa
        b.shade(WOOD, 0.06f).boxAt(0f, 0.04f, 0.52f, 0.11f, 0.09f, 0.22f);  // pompa
        for (int i = 0; i < 4; i++) {
            b.color(0x5A3E33).boxAt(0f, 0.09f, 0.45f + i * 0.05f, 0.115f, 0.015f, 0.02f);
        }
        b.shade(WOOD, 0.05f).push().translate(0f, 0.02f, -0.06f).rotateX(-6f)
                .boxAt(0, 0f, -0.16f, 0.1f, 0.12f, 0.34f).pop();
        b.color(METAL_DARK).push().translate(0f, 0.0f, -0.36f).rotateX(-6f)
                .boxAt(0, 0f, 0f, 0.11f, 0.15f, 0.03f).pop();               // dipçik altlığı
        frontSight(b, 0.93f, 0.11f);
        triggerGuard(b, 0.04f);
        return b.build();
    }

    private Mesh buildRifle() {
        MeshBuilder b = new MeshBuilder();
        b.bone(BONE_EXTRA);
        b.color(0x4A5340).boxAt(0f, 0.06f, 0.18f, 0.095f, 0.145f, 0.48f);   // gövde
        b.color(0x3A4232).boxAt(0f, 0.125f, 0.18f, 0.07f, 0.03f, 0.46f);
        // el kundağı + delikler
        b.color(0x39412F).boxAt(0f, 0.06f, 0.56f, 0.09f, 0.1f, 0.34f);
        for (int i = 0; i < 4; i++) {
            b.color(0x23291D).boxAt(0.046f, 0.07f, 0.45f + i * 0.07f, 0.02f, 0.045f, 0.035f);
            b.color(0x23291D).boxAt(-0.046f, 0.07f, 0.45f + i * 0.07f, 0.02f, 0.045f, 0.035f);
        }
        // namlu + alev gizleyen
        b.color(METAL_DARK).push().translate(0f, 0.07f, 0.72f).rotateX(90f)
                .cylinder(0.026f, 0.026f, 0.24f, 8).pop();
        b.color(0x1F2429).push().translate(0f, 0.07f, 0.95f).rotateX(90f)
                .cylinder(0.042f, 0.038f, 0.09f, 8).pop();
        frontSight(b, 0.83f, 0.1f);
        rearSight(b, 0.02f, 0.13f);
        topRail(b, 0.0f, 0.42f, 0.15f, 0.055f);
        // dipçik
        b.color(0x39412F).boxAt(0f, 0.06f, -0.12f, 0.085f, 0.11f, 0.18f);
        b.color(0x39412F).push().translate(0f, 0.045f, -0.22f).rotateX(-4f)
                .boxAt(0, 0f, -0.13f, 0.09f, 0.14f, 0.28f).pop();
        b.color(METAL_DARK).boxAt(0f, 0.04f, -0.39f, 0.1f, 0.17f, 0.035f);
        triggerGuard(b, 0.05f);
        gunGrip(b, 16f, 0.3f, 0.09f);
        magazine(b, 0x2E3428, 0.2f, 0.36f, 8f, 0.08f);
        return b.build();
    }

    private Mesh buildSniper() {
        MeshBuilder b = new MeshBuilder();
        b.bone(BONE_EXTRA);
        b.color(0x2F383D).boxAt(0f, 0.06f, 0.2f, 0.1f, 0.15f, 0.56f);       // gövde
        b.color(0x39434A).boxAt(0f, 0.055f, 0.62f, 0.085f, 0.1f, 0.34f);    // el kundağı
        // ağır namlu + ağız freni
        b.color(METAL_DARK).push().translate(0f, 0.08f, 0.78f).rotateX(90f)
                .cylinder(0.032f, 0.03f, 0.42f, 9).pop();
        b.color(0x1F2429).push().translate(0f, 0.08f, 1.2f).rotateX(90f)
                .cylinder(0.05f, 0.05f, 0.1f, 9).pop();
        for (int i = 0; i < 2; i++) {
            b.color(0x151A1D).boxAt(0f, 0.08f, 1.23f + i * 0.04f, 0.11f, 0.02f, 0.02f);
        }
        // dürbün
        b.color(METAL_DARK).boxAt(-0.0f, 0.17f, 0.06f, 0.03f, 0.06f, 0.05f);
        b.color(METAL_DARK).boxAt(-0.0f, 0.17f, 0.32f, 0.03f, 0.06f, 0.05f);
        b.color(0x1B2226).push().translate(0f, 0.215f, 0.2f).rotateX(90f)
                .cylinder(0.055f, 0.055f, 0.42f, 10).pop();
        b.color(0x0E1215).push().translate(0f, 0.215f, 0.41f).rotateX(90f)
                .cylinder(0.065f, 0.065f, 0.06f, 10).pop();
        b.color(0x4DB6E6).push().translate(0f, 0.215f, 0.415f).rotateX(90f)
                .cylinder(0.05f, 0.05f, 0.012f, 10).pop();                   // mercek parıltısı
        // sehpa
        for (int i = -1; i <= 1; i += 2) {
            b.color(METAL_DARK).push().translate(i * 0.03f, -0.01f, 0.74f)
                    .rotateZ(i * 26f).cylinder(0.014f, 0.011f, 0.22f, 5).pop();
        }
        // dipçik + yanak dayama
        b.color(0x39434A).push().translate(0f, 0.05f, -0.2f).rotateX(-3f)
                .boxAt(0, 0f, -0.16f, 0.095f, 0.14f, 0.34f).pop();
        b.color(0x2F383D).boxAt(0f, 0.14f, -0.26f, 0.08f, 0.06f, 0.2f);
        b.color(METAL_DARK).boxAt(0f, 0.03f, -0.41f, 0.1f, 0.19f, 0.035f);
        triggerGuard(b, 0.05f);
        gunGrip(b, 14f, 0.3f, 0.09f);
        magazine(b, 0x2A3136, 0.22f, 0.22f, 4f, 0.075f);
        return b.build();
    }

    private Mesh buildLauncher() {
        MeshBuilder b = new MeshBuilder();
        b.bone(BONE_EXTRA);
        // ana tüp
        b.color(0x4E5B45).push().translate(0f, 0.08f, 0.32f).rotateX(90f)
                .cylinder(0.105f, 0.105f, 0.78f, 12).pop();
        b.color(0x3A4433).push().translate(0f, 0.08f, 0.1f).rotateX(90f)
                .cylinder(0.12f, 0.12f, 0.1f, 12).pop();
        // arka huni
        b.color(0x323B2C).push().translate(0f, 0.08f, -0.12f).rotateX(-90f)
                .cylinder(0.105f, 0.15f, 0.22f, 12).pop();
        // roket başlığı görünüyor
        b.color(0xB5442B).push().translate(0f, 0.08f, 0.72f).rotateX(90f)
                .cylinder(0.075f, 0.055f, 0.14f, 10).pop();
        b.color(0xE0E0E0).push().translate(0f, 0.08f, 0.86f).rotateX(90f)
                .cylinder(0.055f, 0.0f, 0.1f, 10).pop();
        // nişan düzeneği ve kollar
        b.color(METAL_DARK).boxAt(0f, 0.2f, 0.18f, 0.05f, 0.09f, 0.14f);
        b.color(0x1B2226).boxAt(0f, 0.255f, 0.18f, 0.07f, 0.03f, 0.16f);
        b.color(POLYMER).push().translate(0f, -0.02f, 0.42f).rotateX(18f)
                .boxAt(0, -0.14f, 0, 0.075f, 0.28f, 0.1f).pop();            // ön tutamak
        triggerGuard(b, 0.06f);
        gunGrip(b, 14f, 0.3f, 0.09f);
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

        // yere düşen hurda yığını
        b = new MeshBuilder();
        b.shade(0xB0A08A, 0.15f).boxAt(0f, 0.1f, 0f, 0.3f, 0.16f, 0.26f);
        b.shade(0x8D7F6B, 0.15f).push().translate(0.1f, 0.2f, 0.06f).rotateY(28f)
                .boxAt(0, 0, 0, 0.22f, 0.1f, 0.2f).pop();
        b.color(0xFFD54F).push().translate(-0.1f, 0.22f, -0.06f).rotateY(-18f)
                .boxAt(0, 0, 0, 0.16f, 0.08f, 0.14f).pop();
        b.color(0xCFA23C).push().translate(0f, 0.3f, 0f).rotateY(40f)
                .cylinder(0.05f, 0.05f, 0.1f, 6).pop();
        scrapPickup = b.build();

        // enerji çekirdeği
        b = new MeshBuilder();
        b.color(0x4DD0E1).push().translate(0f, 0.26f, 0f).scale(1f, 1.5f, 1f)
                .sphere(0.17f, 6, 4).pop();
        b.color(0xB2EBF2).push().translate(0f, 0.26f, 0f).rotateY(45f)
                .cylinder(0.1f, 0.1f, 0.04f, 4).pop();
        corePickup = b.build();

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
