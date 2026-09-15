package com.karargah.survival.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import com.karargah.survival.engine.M4;
import com.karargah.survival.engine.MathX;
import com.karargah.survival.game.Balance;
import com.karargah.survival.game.BuildGrid;
import com.karargah.survival.game.Cmd;
import com.karargah.survival.game.FloatingText;
import com.karargah.survival.game.GameWorld;
import com.karargah.survival.game.InputState;
import com.karargah.survival.game.Npc;
import com.karargah.survival.game.Pickup;
import com.karargah.survival.game.Player;
import com.karargah.survival.game.Structure;
import com.karargah.survival.game.WaveManager;
import com.karargah.survival.game.Zombie;

/**
 * Tüm dokunmatik arayüz: HUD, sanal çubuk, düğmeler, inşa çubuğu, paneller ve
 * menüler. GLSurfaceView'in üstünde saydam bir View olarak çizilir.
 */
public class HudView extends View {
    public static final int SCREEN_MENU = 0;
    public static final int SCREEN_GAME = 1;
    public static final int SCREEN_PAUSE = 2;
    public static final int SCREEN_SKILLS = 3;
    public static final int SCREEN_WEAPONS = 4;
    public static final int SCREEN_GAMEOVER = 5;
    public static final int SCREEN_HELP = 6;
    public static final int SCREEN_SQUAD = 7;

    private static final int A_RELOAD = 2;
    private static final int A_DASH = 3;
    private static final int A_WEAPON_NEXT = 4;
    private static final int A_BUILD_TOGGLE = 5;
    private static final int A_PAUSE = 6;
    private static final int A_START_WAVE = 7;
    private static final int A_REPAIR_ALL = 8;
    private static final int A_PICK_BUILD = 9;
    private static final int A_UPGRADE = 10;
    private static final int A_REPAIR = 11;
    private static final int A_SELL = 12;
    private static final int A_DESELECT = 13;
    private static final int A_OPEN_SKILLS = 14;
    private static final int A_OPEN_WEAPONS = 15;
    private static final int A_RESUME = 16;
    private static final int A_RESTART = 17;
    private static final int A_MAIN_MENU = 18;
    private static final int A_SKILL_UP = 19;
    private static final int A_WEAPON_BUY = 20;
    private static final int A_WEAPON_UP = 21;
    private static final int A_WEAPON_SELECT = 22;
    private static final int A_NEW_GAME = 23;
    private static final int A_CONTINUE = 24;
    private static final int A_HELP = 25;
    private static final int A_SOUND = 26;
    private static final int A_CLOSE = 27;
    private static final int A_BUY_AMMO = 28;
    private static final int A_QUIT = 29;
    private static final int A_FIRE = 30;
    private static final int A_ROTATE = 31;
    private static final int A_OPEN_SQUAD = 32;
    private static final int A_SELECT_NPC = 33;
    private static final int A_ORDER = 34;
    private static final int A_RECRUIT = 35;
    private static final int A_DUTY = 36;
    private static final int A_PLAN_MODE = 37;
    private static final int A_CANCEL_PLANS = 38;
    private static final int A_AUTOBUILD = 39;
    private static final int A_SELF_IMPROVE = 40;

    public interface Listener {
        void onNewGame();
        void onContinue();
        void onQuit();
        boolean hasSave();
        void onSaveRequested();
        boolean isSoundOn();
        void setSoundOn(boolean on);
    }

    private final UiKit ui = new UiKit();
    private final InputState input;
    private volatile GameWorld world;
    private Listener listener;

    private int screen = SCREEN_MENU;
    private float sc = 1f;
    private float bgAnim;

    // kamera yansıtma verisi (GL iş parçacığından kopyalanır)
    private final float[] viewProj = new float[16];
    private final float[] invViewProj = new float[16];
    private int glW = 1, glH = 1;
    private final float[] tmp4 = new float[4];
    private final float[] tmpA = new float[4];
    private final float[] tmpB = new float[4];
    private final float[] outXY = new float[2];
    private final float[] outXZ = new float[2];
    private final int[] param = new int[1];

    // dokunma durumu
    private int movePointer = -1, camPointer = -1, firePointer = -1, buildPointer = -1;
    private int pinchA = -1, pinchB = -1;
    private float pinchDist;
    private float joyBaseX, joyBaseY, joyX, joyY;
    private float lastCamX, lastCamY;
    private int lastPlacedCell = -1;
    /** Ekip panelinde seçili yoldaş (-1 = tüm ekip). */
    private int squadSel = -1;
    /** Haritadan nokta bekleyen emir (-1 = yok). */
    private int pendingOrder = -1;
    /** İnşa modunda plan bırakma (yoldaşlar kursun) açık mı? */
    private boolean planMode;

    public HudView(Context ctx, InputState input) {
        super(ctx);
        this.input = input;
        setFocusable(true);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setWorld(GameWorld w) {
        this.world = w;
    }

    public int screen() {
        return screen;
    }

    public void setScreen(int s) {
        screen = s;
        input.paused = (s != SCREEN_GAME);
        GameWorld w = world;
        if (w != null) w.paused = input.paused;
        if (s != SCREEN_GAME) {
            input.firing = false;
            input.moveX = 0f;
            input.moveZ = 0f;
            movePointer = -1;
            firePointer = -1;
        }
    }

    /** GL iş parçacığı her karede kamera matrislerini buraya kopyalar. */
    public void publishCamera(float[] vp, float[] ivp, int w, int h) {
        synchronized (viewProj) {
            System.arraycopy(vp, 0, viewProj, 0, 16);
            System.arraycopy(ivp, 0, invViewProj, 0, 16);
            glW = w;
            glH = h;
        }
    }

    // ---- çizim ----------------------------------------------------------

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        sc = Math.max(0.65f, Math.min(h / 720f, w / 1280f * 1.15f));
        ui.begin(sc);
        bgAnim += 0.016f;

        GameWorld gw = world;
        if (gw != null && screen != SCREEN_MENU) {
            drawWorldOverlay(c, gw, w, h);
            drawTopBar(c, gw, w, h);
            if (screen == SCREEN_GAME) {
                drawDamageVignette(c, gw, w, h);
                drawBossBar(c, gw, w, h);
                drawMinimap(c, gw, w, h);
                drawControls(c, gw, w, h);
                if (input.buildMode) {
                    drawBuildBar(c, gw, w, h);
                    drawSelectedPanel(c, gw, w, h);
                }
                drawMessages(c, gw, w, h);
            }
        }

        switch (screen) {
            case SCREEN_MENU: drawMenu(c, w, h); break;
            case SCREEN_PAUSE: drawPause(c, w, h); break;
            case SCREEN_SKILLS: drawSkills(c, gw, w, h); break;
            case SCREEN_WEAPONS: drawWeapons(c, gw, w, h); break;
            case SCREEN_GAMEOVER: drawGameOver(c, gw, w, h); break;
            case SCREEN_SQUAD: drawSquad(c, gw, w, h); break;
            case SCREEN_HELP: drawHelp(c, w, h); break;
            default: break;
        }

        postInvalidateOnAnimation();
    }

    // ---- üst bilgi çubuğu -----------------------------------------------

    private void drawTopBar(Canvas c, GameWorld gw, float w, float h) {
        Player p = gw.player;
        float pad = 14 * sc;
        float barW = 250 * sc;
        float top = pad;

        // can + seviye
        ui.rect(c, pad, top, pad + barW + 74 * sc, top + 62 * sc, 12 * sc, 0xB0101613);
        ui.bar(c, pad + 66 * sc, top + 9 * sc, pad + 66 * sc + barW - 10 * sc, top + 27 * sc,
                p.hp / Math.max(1f, p.maxHp), 0xFF2A1F1F, p.hp / p.maxHp < 0.3f ? 0xFFE05C4B : 0xFF5FD38A, true);
        ui.bar(c, pad + 66 * sc, top + 33 * sc, pad + 66 * sc + barW - 10 * sc, top + 45 * sc,
                (float) p.xp / Balance.xpForLevel(p.level), 0xFF1B2430, 0xFF4DA3E1, true);
        ui.labelShadow(c, String.valueOf(Math.round(p.hp)), pad + 72 * sc, top + 24 * sc,
                16 * sc, 0xFFFFFFFF, Paint.Align.LEFT);
        ui.labelShadow(c, "Sv " + p.level, pad + 12 * sc, top + 30 * sc, 24 * sc,
                UiKit.COL_GOLD, Paint.Align.LEFT);
        ui.labelShadow(c, p.skillPoints > 0 ? "+" + p.skillPoints + " puan" : "XP",
                pad + 12 * sc, top + 50 * sc, 15 * sc,
                p.skillPoints > 0 ? UiKit.COL_ACCENT : UiKit.COL_DIM, Paint.Align.LEFT);

        // kaynaklar
        float rx = w - pad;
        ui.rect(c, rx - 250 * sc, top, rx - 58 * sc, top + 34 * sc, 10 * sc, 0xB0101613);
        ui.labelShadow(c, "KASA " + p.scrap, rx - 240 * sc, top + 24 * sc, 20 * sc,
                UiKit.COL_GOLD, Paint.Align.LEFT);
        if (!gw.npcs.isEmpty() && gw.scrapFromNpcs > 0) {
            ui.labelShadow(c, "ekip +" + gw.scrapFromNpcs, rx - 128 * sc, top + 24 * sc,
                    13 * sc, UiKit.COL_DIM, Paint.Align.LEFT);
        }
        int loose = gw.looseScrap();
        if (loose > 0) {
            ui.labelShadow(c, "yerde " + loose, rx - 128 * sc, top + 12 * sc, 13 * sc,
                    0xFFFFE082, Paint.Align.LEFT);
        }
        ui.rect(c, rx - 250 * sc, top + 38 * sc, rx - 58 * sc, top + 68 * sc, 10 * sc, 0xB0101613);
        ui.labelShadow(c, "ÇEKİRDEK " + p.cores, rx - 240 * sc, top + 60 * sc, 18 * sc,
                UiKit.COL_CYAN, Paint.Align.LEFT);

        ui.button(c, rx - 50 * sc, top, rx, top + 44 * sc, "| |", null,
                UiKit.STYLE_GHOST, true, A_PAUSE, 0);

        // dalga bilgisi
        float cx = w * 0.5f;
        WaveManager wm = gw.waves;
        String title;
        int col = UiKit.COL_TEXT;
        if (wm.phase == WaveManager.PHASE_WAVE) {
            title = wm.wave + ". DALGA";
            col = 0xFFE9A13B;
        } else if (wm.phase == WaveManager.PHASE_CLEARED) {
            title = "TEMİZLENDİ";
            col = UiKit.COL_ACCENT;
        } else {
            title = "HAZIRLIK";
            col = UiKit.COL_CYAN;
        }
        ui.rect(c, cx - 130 * sc, top, cx + 130 * sc, top + 52 * sc, 12 * sc, 0xB0101613);
        ui.labelShadow(c, title, cx, top + 24 * sc, 22 * sc, col, Paint.Align.CENTER);
        String sub;
        if (wm.phase == WaveManager.PHASE_WAVE) {
            sub = "kalan zombi: " + wm.remaining(gw);
        } else if (wm.phase == WaveManager.PHASE_PREPARE) {
            sub = "sonraki dalga: " + Math.max(0, Math.round(wm.timer)) + " sn";
        } else {
            sub = "hazırlanıyor...";
        }
        ui.labelShadow(c, sub, cx, top + 44 * sc, 16 * sc, UiKit.COL_DIM, Paint.Align.CENTER);

        // güç durumu
        if (gw.powerUse > 0.1f) {
            float pw = 150 * sc;
            float py = top + 58 * sc;
            ui.bar(c, cx - pw * 0.5f, py, cx + pw * 0.5f, py + 10 * sc,
                    gw.powerGen / Math.max(1f, gw.powerUse), 0xFF2A2418,
                    gw.powerEff >= 0.999f ? 0xFFFFD75E : 0xFFE05C4B, false);
            ui.labelShadow(c, "ENERJİ " + Math.round(gw.powerGen) + "/" + Math.round(gw.powerUse),
                    cx, py + 26 * sc, 14 * sc,
                    gw.powerEff >= 0.999f ? UiKit.COL_DIM : UiKit.COL_DANGER, Paint.Align.CENTER);
        }

        // reaktör canı
        Structure core = gw.core;
        if (core != null) {
            float coreW = 210 * sc;
            float cy = h - 26 * sc;
            ui.labelShadow(c, "REAKTÖR", pad + 4 * sc, cy - 14 * sc, 15 * sc,
                    UiKit.COL_DIM, Paint.Align.LEFT);
            ui.bar(c, pad, cy - 10 * sc, pad + coreW, cy + 4 * sc,
                    core.hpFraction(), 0xFF151E22, 0xFF4DD0E1, true);
        }

        if (gw.waves.isPrepare() && screen == SCREEN_GAME) {
            float bw = 200 * sc, bh = 46 * sc;
            ui.button(c, cx - bw * 0.5f, h - bh - 12 * sc, cx + bw * 0.5f, h - 12 * sc,
                    "DALGAYI BAŞLAT", null, UiKit.STYLE_PRIMARY, true, A_START_WAVE, 0);
        }
    }

    // ---- dünya üstü göstergeler -----------------------------------------

    private void drawWorldOverlay(Canvas c, GameWorld gw, float w, float h) {
        synchronized (viewProj) {
            System.arraycopy(viewProj, 0, tmpVP, 0, 16);
        }
        int vw = getWidth(), vh = getHeight();

        // zombi can çubukları
        for (int i = 0; i < gw.zombies.size(); i++) {
            Zombie z;
            try {
                z = gw.zombies.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (z == null || !z.alive) continue;
            boolean damaged = z.hp < z.maxHp - 0.5f;
            if (!damaged && !z.isBoss()) continue;
            if (!M4.project(tmpVP, z.x, z.y + z.height() + 0.35f, z.z, vw, vh, tmp4, outXY)) continue;
            float bw = (z.isBoss() ? 90f : 40f) * sc;
            float bh = (z.isBoss() ? 9f : 5f) * sc;
            ui.bar(c, outXY[0] - bw * 0.5f, outXY[1], outXY[0] + bw * 0.5f, outXY[1] + bh,
                    z.hp / z.maxHp, 0xAA1A1212, z.elite ? 0xFFCE93D8 : 0xFFE05C4B, false);
        }

        // hasarlı yapıların can çubukları
        for (int i = 0; i < gw.structures.size(); i++) {
            Structure s;
            try {
                s = gw.structures.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (s == null || !s.alive || s.hp >= s.maxHp - 0.5f) continue;
            float top = s.type == Balance.S_CORE ? 4.2f : 2.4f;
            if (!M4.project(tmpVP, s.x, top, s.z, vw, vh, tmp4, outXY)) continue;
            float bw = 46 * sc, bh = 5 * sc;
            ui.bar(c, outXY[0] - bw * 0.5f, outXY[1], outXY[0] + bw * 0.5f, outXY[1] + bh,
                    s.hpFraction(), 0xAA141A18, 0xFF7ECB6B, false);
        }

        // yoldaş etiketleri: ad, can ve emir
        for (int i = 0; i < gw.npcs.size(); i++) {
            Npc n;
            try {
                n = gw.npcs.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (n == null || !n.alive) continue;
            if (!M4.project(tmpVP, n.x, n.downed ? 0.7f : 2.05f, n.z, vw, vh, tmp4, outXY)) continue;
            float bw = 52 * sc;
            ui.bar(c, outXY[0] - bw * 0.5f, outXY[1], outXY[0] + bw * 0.5f, outXY[1] + 5 * sc,
                    n.hp / Math.max(1f, n.maxHp), 0xAA141A18,
                    n.downed ? 0xFFE05C4B : 0xFF7ECB6B, false);
            ui.labelShadow(c, n.name + (n.downed ? " (yerde)" : ""),
                    outXY[0], outXY[1] - 6 * sc, 14 * sc,
                    n.downed ? UiKit.COL_DANGER : 0xFFB3E5FC, Paint.Align.CENTER);
            if (!n.downed) {
                ui.labelShadow(c, Balance.STANCE_SHORT[n.stance] + " · " + n.dutyLetters(),
                        outXY[0], outXY[1] + 18 * sc, 12 * sc,
                        n.task != Npc.TASK_NONE ? UiKit.COL_GOLD : UiKit.COL_DIM,
                        Paint.Align.CENTER);
            }
        }

        // konuşma baloncukları (en yakın üç tanesi)
        int bubbles = 0;
        for (int i = 0; i < gw.npcs.size() && bubbles < 3; i++) {
            Npc n;
            try {
                n = gw.npcs.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (n == null || !n.alive || n.bubbleTimer <= 0f || n.bubble == null
                    || n.bubble.isEmpty()) {
                continue;
            }
            if (!M4.project(tmpVP, n.x, 2.45f, n.z, vw, vh, tmp4, outXY)) continue;
            drawBubble(c, outXY[0], outXY[1] - 26 * sc, n.bubble,
                    Math.min(1f, n.bubbleTimer));
            bubbles++;
        }

        // süzülen yazılar
        for (int i = 0; i < gw.texts.size(); i++) {
            FloatingText t;
            try {
                t = gw.texts.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (t == null || !t.alive || t.text == null) continue;
            if (!M4.project(tmpVP, t.x, t.y, t.z, vw, vh, tmp4, outXY)) continue;
            int col = UiKit.withAlpha(t.color, t.alpha());
            ui.labelShadow(c, t.text, outXY[0], outXY[1], 18 * sc * t.size, col, Paint.Align.CENTER);
        }

        // inşa modunda seçili yapı vurgusu zaten 3B'de çiziliyor
    }

    private final float[] tmpVP = new float[16];

    /** Karakterin başının üstünde kuyruklu konuşma baloncuğu. */
    private void drawBubble(Canvas c, float cx, float cy, String text, float alpha) {
        float size = 15 * sc;
        float maxW = 230 * sc;
        String line1 = text;
        String line2 = null;
        if (ui.textWidth(text, size) > maxW) {
            int split = text.length() / 2;
            int space = text.lastIndexOf(' ', split + 6);
            if (space < 3) space = split;
            line1 = text.substring(0, space).trim();
            line2 = text.substring(space).trim();
        }
        float w1 = ui.textWidth(line1, size);
        float w2 = line2 == null ? 0 : ui.textWidth(line2, size);
        float bw = Math.min(maxW + 24 * sc, Math.max(w1, w2) + 22 * sc);
        float bh = (line2 == null ? 30f : 48f) * sc;
        float l = cx - bw * 0.5f, t = cy - bh;

        int bg = UiKit.withAlpha(0x0E1713, 0.88f * alpha);
        int border = UiKit.withAlpha(0x7FD4E8, 0.75f * alpha);
        ui.rect(c, l, t, l + bw, t + bh, 10 * sc, bg);
        ui.border(c, l, t, l + bw, t + bh, 10 * sc, border, 1.4f * sc);
        // kuyruk
        ui.fill.setStyle(Paint.Style.FILL);
        ui.fill.setColor(bg);
        android.graphics.Path tail = new android.graphics.Path();
        tail.moveTo(cx - 7 * sc, t + bh - 1);
        tail.lineTo(cx + 7 * sc, t + bh - 1);
        tail.lineTo(cx, t + bh + 11 * sc);
        tail.close();
        c.drawPath(tail, ui.fill);

        int col = UiKit.withAlpha(0xE8F4EC, alpha);
        if (line2 == null) {
            ui.label(c, line1, cx, t + bh * 0.66f, size, col, Paint.Align.CENTER);
        } else {
            ui.label(c, line1, cx, t + 19 * sc, size, col, Paint.Align.CENTER);
            ui.label(c, line2, cx, t + 37 * sc, size, col, Paint.Align.CENTER);
        }
    }
    private LinearGradient vignetteTop, vignetteBottom;
    private float vignetteW, vignetteH;

    // ---- kontroller -----------------------------------------------------

    private void drawControls(Canvas c, GameWorld gw, float w, float h) {
        Player p = gw.player;

        // sanal çubuk
        if (movePointer >= 0) {
            ui.fill.setStyle(Paint.Style.FILL);
            ui.fill.setColor(0x33FFFFFF);
            c.drawCircle(joyBaseX, joyBaseY, 78 * sc, ui.fill);
            ui.stroke.setColor(0x66FFFFFF);
            ui.stroke.setStrokeWidth(2.4f * sc);
            c.drawCircle(joyBaseX, joyBaseY, 78 * sc, ui.stroke);
            ui.fill.setColor(0x99FFFFFF);
            c.drawCircle(joyX, joyY, 34 * sc, ui.fill);
        } else {
            ui.stroke.setColor(0x22FFFFFF);
            ui.stroke.setStrokeWidth(2f * sc);
            c.drawCircle(150 * sc, h - 130 * sc, 72 * sc, ui.stroke);
            ui.labelShadow(c, "HAREKET", 150 * sc, h - 126 * sc, 15 * sc, 0x55FFFFFF, Paint.Align.CENTER);
        }

        float rx = w - 105 * sc;
        float ry = h - 105 * sc;

        if (!input.buildMode) {
            // ateş
            ui.circleButton(c, rx, ry, 62 * sc, "ATEŞ", 0xCC8E3A31, p.alive, A_FIRE, 0, 0f);
            // şarjör
            ui.circleButton(c, rx - 130 * sc, ry - 16 * sc, 40 * sc, "ŞARJ", 0xCC2F4A5C,
                    p.alive, A_RELOAD, 0, p.reloading ? 1f - p.reloadTimer / Math.max(0.01f, p.reloadTotal) : 0f);
            // atılma
            ui.circleButton(c, rx - 22 * sc, ry - 130 * sc, 40 * sc, "ATIL", 0xCC2E5D4A,
                    p.alive && p.dashCd <= 0f, A_DASH, 0,
                    p.dashCd > 0f ? p.dashCd / Math.max(0.1f, p.dashCooldown()) : 0f);
            // silah değiştir
            ui.circleButton(c, rx - 138 * sc, ry - 118 * sc, 36 * sc, "SİLAH", 0xCC4A4430,
                    p.alive, A_WEAPON_NEXT, 0, 0f);

            // cephane göstergesi
            Balance.WeaponDef d = p.weapon();
            String ammo = p.reserve[p.currentWeapon] >= 9000
                    ? p.magazine[p.currentWeapon] + " / ∞"
                    : p.magazine[p.currentWeapon] + " / " + p.reserve[p.currentWeapon];
            ui.rect(c, w - 300 * sc, h - 232 * sc, w - 180 * sc, h - 196 * sc, 8 * sc, 0xAA101613);
            ui.labelShadow(c, d.name, w - 294 * sc, h - 218 * sc, 15 * sc, UiKit.COL_DIM, Paint.Align.LEFT);
            ui.labelShadow(c, ammo, w - 294 * sc, h - 202 * sc, 18 * sc,
                    p.magazine[p.currentWeapon] == 0 ? UiKit.COL_DANGER : UiKit.COL_TEXT, Paint.Align.LEFT);
        }

        // inşa modu düğmesi
        ui.button(c, w - 190 * sc, 84 * sc, w - 14 * sc, 126 * sc,
                input.buildMode ? "SAVAŞ MODU" : "İNŞA MODU", null,
                input.buildMode ? UiKit.STYLE_GOLD : UiKit.STYLE_NORMAL, true, A_BUILD_TOGGLE, 0);

        // yetenek / silah kısayolları
        ui.button(c, w - 190 * sc, 132 * sc, w - 104 * sc, 172 * sc, "YETENEK",
                p.skillPoints > 0 ? "+" + p.skillPoints : null,
                p.skillPoints > 0 ? UiKit.STYLE_PRIMARY : UiKit.STYLE_GHOST, true, A_OPEN_SKILLS, 0);
        ui.button(c, w - 100 * sc, 132 * sc, w - 14 * sc, 172 * sc, "SİLAH", null,
                UiKit.STYLE_GHOST, true, A_OPEN_WEAPONS, 0);
        int alive = 0;
        for (int i = 0; i < gw.npcs.size(); i++) {
            if (!gw.npcs.get(i).downed) alive++;
        }
        ui.button(c, w - 190 * sc, 178 * sc, w - 14 * sc, 218 * sc, "EKİP",
                gw.npcs.isEmpty() ? "yoldaş yok" : alive + "/" + gw.npcs.size() + " hazır",
                gw.npcs.isEmpty() ? UiKit.STYLE_GHOST : UiKit.STYLE_NORMAL, true, A_OPEN_SQUAD, 0);

        if (pendingOrder >= 0) {
            ui.rect(c, w * 0.5f - 210 * sc, h * 0.12f, w * 0.5f + 210 * sc, h * 0.12f + 42 * sc,
                    10 * sc, 0xCC1B2320);
            ui.labelShadow(c, "Haritada hedef noktaya dokun — " + Balance.STANCE_NAMES[pendingOrder],
                    w * 0.5f, h * 0.12f + 28 * sc, 20 * sc, UiKit.COL_CYAN, Paint.Align.CENTER);
        }

        if (!p.alive) {
            ui.labelShadow(c, "YENİDEN AYAĞA KALKIYORSUN: " + Math.max(0, Math.round(p.reviveTimer)),
                    w * 0.5f, h * 0.42f, 34 * sc, UiKit.COL_DANGER, Paint.Align.CENTER);
        }
    }

    /** Sol üstte küçük harita: üs, yapılar, zombiler ve doğma kapıları. */
    private void drawMinimap(Canvas c, GameWorld gw, float w, float h) {
        float size = 132 * sc;
        float l = 14 * sc, t = 84 * sc;
        float cx = l + size * 0.5f, cy = t + size * 0.5f;
        float scale = (size * 0.5f) / Balance.WORLD_HALF;

        ui.rect(c, l, t, l + size, t + size, 10 * sc, 0xB00C120F);
        ui.border(c, l, t, l + size, t + size, 10 * sc, 0x44FFFFFF, 1.4f * sc);

        ui.stroke.setColor(0x334DD0E1);
        ui.stroke.setStrokeWidth(1.4f * sc);
        c.drawCircle(cx, cy, Balance.BUILD_RADIUS * scale, ui.stroke);

        ui.fill.setStyle(Paint.Style.FILL);
        // yapılar
        for (int i = 0; i < gw.structures.size(); i++) {
            Structure s;
            try {
                s = gw.structures.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (s == null || !s.alive) continue;
            boolean core = s.type == Balance.S_CORE;
            ui.fill.setColor(core ? 0xFF4DD0E1
                    : (s.isTurret() ? 0xFF9CCC65 : 0x99C8C8B0));
            float r = core ? 4.5f * sc : 1.7f * sc;
            c.drawCircle(cx + s.x * scale, cy + s.z * scale, r, ui.fill);
        }
        // doğma kapıları
        for (int i = 0; i < gw.waves.activeSpawnPoints; i++) {
            ui.fill.setColor(0xFF8E2B2B);
            c.drawCircle(cx + gw.waves.spawnX[i] * scale, cy + gw.waves.spawnZ[i] * scale,
                    3f * sc, ui.fill);
        }
        // zombiler
        for (int i = 0; i < gw.zombies.size(); i++) {
            Zombie z;
            try {
                z = gw.zombies.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (z == null || !z.alive) continue;
            ui.fill.setColor(z.isBoss() ? 0xFFFF5252 : (z.elite ? 0xFFCE93D8 : 0xFFE05C4B));
            c.drawCircle(cx + z.x * scale, cy + z.z * scale, z.isBoss() ? 4f * sc : 2f * sc, ui.fill);
        }
        // yere düşen ganimet
        for (int i = 0; i < gw.pickups.size(); i++) {
            Pickup p;
            try {
                p = gw.pickups.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (p == null || !p.alive) continue;
            ui.fill.setColor(p.kind == Pickup.CORE ? 0xFF4DD0E1 : 0xFFFFD54F);
            c.drawCircle(cx + p.x * scale, cy + p.z * scale, 1.6f * sc, ui.fill);
        }
        // yoldaşlar
        for (int i = 0; i < gw.npcs.size(); i++) {
            Npc n;
            try {
                n = gw.npcs.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (n == null || !n.alive) continue;
            ui.fill.setColor(n.downed ? 0xFF8E3A31 : (0xFF000000 | Balance.npc(n.role).accent));
            c.drawCircle(cx + n.x * scale, cy + n.z * scale, 2.6f * sc, ui.fill);
        }

        // oyuncu
        ui.fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx + gw.player.x * scale, cy + gw.player.z * scale, 3f * sc, ui.fill);
        ui.stroke.setColor(0xFFFFFFFF);
        ui.stroke.setStrokeWidth(1.6f * sc);
        float fx = (float) Math.sin(gw.player.aimYaw), fz = (float) Math.cos(gw.player.aimYaw);
        c.drawLine(cx + gw.player.x * scale, cy + gw.player.z * scale,
                cx + gw.player.x * scale + fx * 9 * sc,
                cy + gw.player.z * scale + fz * 9 * sc, ui.stroke);
    }

    /** Hasar alınca kenarlarda kırmızı parlama. */
    private void drawDamageVignette(Canvas c, GameWorld gw, float w, float h) {
        float f = gw.player.alive ? gw.player.hurtFlash : 0.55f;
        float low = gw.player.alive && gw.player.hp < gw.player.maxHp * 0.3f ? 0.22f : 0f;
        float a = Math.max(Math.min(f, 0.55f), low);
        if (a <= 0.01f) return;
        float band = Math.min(w, h) * 0.13f;
        if (vignetteTop == null || vignetteW != w || vignetteH != h) {
            vignetteW = w;
            vignetteH = h;
            vignetteTop = new LinearGradient(0, 0, 0, band, 0xFFC02020, 0x00C02020,
                    Shader.TileMode.CLAMP);
            vignetteBottom = new LinearGradient(0, h, 0, h - band, 0xFFC02020, 0x00C02020,
                    Shader.TileMode.CLAMP);
        }
        ui.fill.setStyle(Paint.Style.FILL);
        ui.fill.setColor(0xFFC02020);
        ui.fill.setAlpha((int) (a * 190));
        ui.fill.setShader(vignetteTop);
        c.drawRect(0, 0, w, band, ui.fill);
        ui.fill.setShader(vignetteBottom);
        c.drawRect(0, h - band, w, h, ui.fill);
        ui.fill.setShader(null);
        ui.fill.setAlpha(255);
    }

    /** Sahadaki en güçlü boss için ekranın üstünde büyük can çubuğu. */
    private void drawBossBar(Canvas c, GameWorld gw, float w, float h) {
        Zombie boss = null;
        for (int i = 0; i < gw.zombies.size(); i++) {
            Zombie z;
            try {
                z = gw.zombies.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (z == null || !z.alive || !z.isBoss()) continue;
            if (boss == null || z.hp > boss.hp) boss = z;
        }
        if (boss == null) return;
        float bw = Math.min(w * 0.52f, 560 * sc);
        float cx = w * 0.5f;
        float t = 96 * sc;
        ui.labelShadow(c, "MUTANT DEV", cx, t - 6 * sc, 20 * sc, 0xFFFF7043, Paint.Align.CENTER);
        ui.bar(c, cx - bw * 0.5f, t, cx + bw * 0.5f, t + 16 * sc,
                boss.hp / boss.maxHp, 0xCC1A0E0E, 0xFFD32F2F, true);
    }

    private void drawMessages(Canvas c, GameWorld gw, float w, float h) {
        if (gw.messageTimer > 0f && gw.message != null) {
            float a = Math.min(1f, gw.messageTimer);
            ui.labelShadow(c, gw.message, w * 0.5f, h * 0.76f, 20 * sc,
                    UiKit.withAlpha(UiKit.COL_TEXT, a), Paint.Align.CENTER);
        }
        if (gw.bigMessageTimer > 0f && gw.bigMessage != null) {
            float a = Math.min(1f, gw.bigMessageTimer * 0.8f);
            float y = h * 0.30f;
            ui.labelShadow(c, gw.bigMessage, w * 0.5f, y, 40 * sc,
                    UiKit.withAlpha(UiKit.COL_GOLD, a), Paint.Align.CENTER);
        }
    }

    // ---- inşa arayüzü ---------------------------------------------------

    private void drawBuildBar(Canvas c, GameWorld gw, float w, float h) {
        Player p = gw.player;
        int n = Balance.STRUCTS.length - 1;   // reaktör hariç
        float cell = Math.min(96 * sc, (w - 40 * sc) / n);
        float bh = 82 * sc;
        float y1 = h - 14 * sc;
        float y0 = y1 - bh;
        float x0 = (w - cell * n) * 0.5f;

        ui.rect(c, x0 - 10 * sc, y0 - 8 * sc, x0 + cell * n + 10 * sc, y1 + 4 * sc, 12 * sc, 0xCC0E1411);

        // Seçili yapının ne işe yaradığını anlatan şerit
        Balance.StructDef chosen = Balance.struct(input.buildType);
        String info = chosen.desc;
        if (chosen.kind == Balance.KIND_TURRET) {
            info += "   [hasar " + Math.round(chosen.damage) + " · menzil " + Math.round(chosen.range)
                    + " · enerji " + Math.round(-chosen.power) + "]";
        } else if (chosen.power > 0) {
            info += "   [enerji +" + Math.round(chosen.power) + "]";
        }
        float stripY = y0 - 16 * sc;
        ui.rect(c, x0 - 10 * sc, stripY - 22 * sc, x0 + cell * n + 10 * sc, stripY + 6 * sc,
                8 * sc, 0xB00E1411);
        ui.label(c, chosen.name, x0 + 4 * sc, stripY, 17 * sc, UiKit.COL_GOLD, Paint.Align.LEFT);
        ui.label(c, info, x0 + 14 * sc + ui.textWidth(chosen.name, 17 * sc), stripY, 15 * sc,
                UiKit.COL_DIM, Paint.Align.LEFT);

        for (int i = 1; i <= n; i++) {
            Balance.StructDef d = Balance.struct(i);
            float l = x0 + (i - 1) * cell;
            float r = l + cell - 4 * sc;
            boolean locked = gw.waves.wave < d.unlockWave;
            int cost = p.buildCost(d.cost);
            boolean affordable = p.scrap >= cost && !locked;
            boolean sel = input.buildType == i;

            int bg = sel ? 0xFF2E7D52 : (affordable ? 0xFF25302B : 0xFF1E2320);
            ui.rect(c, l, y0, r, y1, 10 * sc, bg);
            ui.border(c, l, y0, r, y1, 10 * sc, sel ? 0xFF9CFFC4 : 0x33FFFFFF, 1.4f * sc);
            ui.addHit(l, y0, r, y1, A_PICK_BUILD, i, !locked);

            ui.structIcon(c, (l + r) * 0.5f, y0 + 26 * sc, 34 * sc, i,
                    locked ? 0xFF555C58 : d.color);
            String name = d.name.length() > 11 ? d.name.substring(0, 10) + "." : d.name;
            ui.label(c, name, (l + r) * 0.5f, y0 + 54 * sc, 14 * sc,
                    locked ? 0xFF6B736E : UiKit.COL_TEXT, Paint.Align.CENTER);
            ui.label(c, locked ? "Dalga " + d.unlockWave : String.valueOf(cost),
                    (l + r) * 0.5f, y0 + 72 * sc, 15 * sc,
                    locked ? UiKit.COL_DIM : (affordable ? UiKit.COL_GOLD : UiKit.COL_DANGER),
                    Paint.Align.CENTER);
        }

        // plan modu: yapıyı kendin kurmak yerine yoldaşlara şantiye bırak
        int planCount = gw.plans.size();
        ui.button(c, 14 * sc, h - 308 * sc, 100 * sc, h - 266 * sc,
                planMode ? "PLAN ✓" : "PLAN", planMode ? "yoldaş kursun" : "kendin kur",
                planMode ? UiKit.STYLE_PRIMARY : UiKit.STYLE_NORMAL, true, A_PLAN_MODE, 0);
        ui.button(c, 104 * sc, h - 308 * sc, 190 * sc, h - 266 * sc,
                gw.autoRebuild ? "OTO ✓" : "OTO", "yıkılanı dik",
                gw.autoRebuild ? UiKit.STYLE_PRIMARY : UiKit.STYLE_GHOST, true, A_AUTOBUILD, 0);
        if (planCount > 0) {
            ui.button(c, 14 * sc, h - 262 * sc, 190 * sc, h - 220 * sc,
                    "PLANLARI İPTAL", planCount + " şantiye", UiKit.STYLE_DANGER,
                    true, A_CANCEL_PLANS, 0);
        }

        // döndürme: seçili yapı varsa onu, yoksa yerleştirme yönünü çevirir
        Structure selStruct = gw.selected;
        boolean wallSel = (selStruct != null && selStruct.type == Balance.S_WALL)
                || (selStruct == null && input.buildType == Balance.S_WALL);
        String rotTitle = selStruct != null && selStruct.alive ? "DÖNDÜR" : "YÖN";
        String rotSub = wallSel ? "duvar otomatik" : (input.buildRotation * 90) + "°";
        ui.button(c, 14 * sc, h - 216 * sc, 190 * sc, h - 174 * sc,
                rotTitle, rotSub, wallSel ? UiKit.STYLE_GHOST : UiKit.STYLE_NORMAL,
                true, A_ROTATE, 0);

        // tümünü onar
        int repairCost = gw.repairAllCost();
        if (repairCost > 0) {
            ui.button(c, 14 * sc, h - 170 * sc, 190 * sc, h - 128 * sc,
                    "TÜMÜNÜ ONAR", repairCost + " hurda", UiKit.STYLE_NORMAL,
                    p.scrap >= repairCost, A_REPAIR_ALL, 0);
        }
        ui.button(c, 14 * sc, h - 124 * sc, 190 * sc, h - 82 * sc,
                "CEPHANE AL", "60 hurda", UiKit.STYLE_NORMAL, p.scrap >= 60, A_BUY_AMMO, 0);
    }

    private void drawSelectedPanel(Canvas c, GameWorld gw, float w, float h) {
        Structure s = gw.selected;
        if (s == null || !s.alive) return;
        Player p = gw.player;
        Balance.StructDef d = s.def();

        float pw = 280 * sc, ph = 250 * sc;
        float l = w - pw - 14 * sc, t = 180 * sc;
        ui.panel(c, l, t, l + pw, t + ph, d.name + "  Sv." + s.level);

        float y = t + 70 * sc;
        ui.label(c, "Can", l + 18 * sc, y, 16 * sc, UiKit.COL_DIM, Paint.Align.LEFT);
        wrapText(c, d.desc, l + 18 * sc, t + ph - 118 * sc, pw - 36 * sc, 13.5f * sc);
        ui.bar(c, l + 70 * sc, y - 12 * sc, l + pw - 18 * sc, y + 2 * sc, s.hpFraction(),
                0xFF1B2320, 0xFF7ECB6B, true);
        ui.label(c, Math.round(s.hp) + " / " + Math.round(s.maxHp), l + 74 * sc, y - 1 * sc,
                13 * sc, 0xFFFFFFFF, Paint.Align.LEFT);
        y += 26 * sc;

        if (d.kind == Balance.KIND_TURRET) {
            ui.label(c, "Hasar " + Math.round(d.damageAt(s.level))
                            + "   Menzil " + Math.round(d.rangeAt(s.level))
                            + "   Hız " + String.format("%.1f", d.rateAt(s.level)),
                    l + 18 * sc, y, 15 * sc, UiKit.COL_TEXT, Paint.Align.LEFT);
            y += 22 * sc;
        } else if (d.kind == Balance.KIND_SUPPORT && d.damage > 0) {
            ui.label(c, "Etki " + Math.round(d.damageAt(s.level))
                            + "   Menzil " + Math.round(d.rangeAt(s.level)),
                    l + 18 * sc, y, 15 * sc, UiKit.COL_TEXT, Paint.Align.LEFT);
            y += 22 * sc;
        }
        if (d.power != 0f) {
            float pow = d.powerAt(s.level);
            ui.label(c, (pow > 0 ? "Enerji üretimi +" : "Enerji tüketimi ") + Math.round(pow),
                    l + 18 * sc, y, 15 * sc, pow > 0 ? UiKit.COL_GOLD : UiKit.COL_DIM, Paint.Align.LEFT);
            y += 22 * sc;
        }

        float by = t + ph - 96 * sc;
        boolean maxed = s.level >= d.maxLevel;
        int upCost = p.buildCost(d.upgradeCost(s.level));
        int upCores = d.upgradeCores(s.level);
        String sub = maxed ? "azami" : (upCost + (upCores > 0 ? " + " + upCores + " çekirdek" : ""));
        ui.button(c, l + 14 * sc, by, l + pw - 14 * sc, by + 42 * sc,
                maxed ? "AZAMİ SEVİYE" : "GELİŞTİR  Sv." + (s.level + 1), sub,
                UiKit.STYLE_PRIMARY, !maxed && p.scrap >= upCost && p.cores >= upCores,
                A_UPGRADE, 0);
        by += 48 * sc;
        int rCost = gw.repairCost(s);
        ui.button(c, l + 14 * sc, by, l + pw * 0.5f - 4 * sc, by + 40 * sc, "ONAR",
                rCost > 0 ? rCost + " hurda" : "sağlam", UiKit.STYLE_NORMAL,
                rCost > 0 && p.scrap >= rCost, A_REPAIR, 0);
        if (s != gw.core) {
            ui.button(c, l + pw * 0.5f + 4 * sc, by, l + pw - 14 * sc, by + 40 * sc, "SAT",
                    "+" + d.sellValue(s.level, s.hpFraction()), UiKit.STYLE_DANGER, true, A_SELL, 0);
        }
    }

    /** Paneldeki açıklamayı verilen genişliğe sığdırarak yazar. */
    private void wrapText(Canvas c, String text, float x, float y, float maxW, float size) {
        if (text == null) return;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float ly = y;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (ui.textWidth(candidate, size) > maxW && line.length() > 0) {
                ui.label(c, line.toString(), x, ly, size, UiKit.COL_DIM, Paint.Align.LEFT);
                ly += size * 1.25f;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) {
            ui.label(c, line.toString(), x, ly, size, UiKit.COL_DIM, Paint.Align.LEFT);
        }
    }

    // ---- ekranlar -------------------------------------------------------

    private void dimBackground(Canvas c, float w, float h, float alpha) {
        ui.fill.setStyle(Paint.Style.FILL);
        ui.fill.setColor(UiKit.withAlpha(0x000000, alpha));
        c.drawRect(0, 0, w, h, ui.fill);
    }

    private void drawMenu(Canvas c, float w, float h) {
        ui.fill.setStyle(Paint.Style.FILL);
        ui.fill.setColor(0xFF0C1310);
        c.drawRect(0, 0, w, h, ui.fill);

        // arka planda hareketli çizgiler
        ui.stroke.setStrokeWidth(1.2f * sc);
        for (int i = 0; i < 22; i++) {
            float y = (i * 47 + (bgAnim * 22) % 47) % h;
            ui.stroke.setColor(UiKit.withAlpha(0x2E7D52, 0.10f + 0.05f * (float) Math.sin(i + bgAnim)));
            c.drawLine(0, y, w, y - 26 * sc, ui.stroke);
        }

        float cx = w * 0.5f;
        ui.labelShadow(c, "SON KARARGÂH", cx, h * 0.22f, 62 * sc, UiKit.COL_ACCENT, Paint.Align.CENTER);
        ui.labelShadow(c, "üssünü kur · dalgalara dayan · hayatta kal", cx, h * 0.28f,
                20 * sc, UiKit.COL_DIM, Paint.Align.CENTER);

        float bw = 300 * sc, bh = 52 * sc;
        float y = h * 0.40f;
        boolean hasSave = listener != null && listener.hasSave();
        if (hasSave) {
            ui.button(c, cx - bw * 0.5f, y, cx + bw * 0.5f, y + bh, "DEVAM ET", null,
                    UiKit.STYLE_PRIMARY, true, A_CONTINUE, 0);
            y += bh + 14 * sc;
        }
        ui.button(c, cx - bw * 0.5f, y, cx + bw * 0.5f, y + bh, "YENİ OYUN", null,
                hasSave ? UiKit.STYLE_NORMAL : UiKit.STYLE_PRIMARY, true, A_NEW_GAME, 0);
        y += bh + 14 * sc;
        ui.button(c, cx - bw * 0.5f, y, cx + bw * 0.5f, y + bh, "NASIL OYNANIR", null,
                UiKit.STYLE_NORMAL, true, A_HELP, 0);
        y += bh + 14 * sc;
        boolean snd = listener == null || listener.isSoundOn();
        ui.button(c, cx - bw * 0.5f, y, cx + bw * 0.5f, y + bh,
                snd ? "SES: AÇIK" : "SES: KAPALI", null, UiKit.STYLE_GHOST, true, A_SOUND, 0);

        GameWorld gw = world;
        if (gw != null && gw.waveRecord > 0) {
            ui.labelShadow(c, "En iyi dalga: " + gw.waveRecord + "   ·   Toplam öldürme: " + gw.totalKills,
                    cx, h - 30 * sc, 18 * sc, UiKit.COL_DIM, Paint.Align.CENTER);
        }
    }

    private void drawPause(Canvas c, float w, float h) {
        dimBackground(c, w, h, 0.72f);
        float cx = w * 0.5f;
        ui.labelShadow(c, "DURAKLATILDI", cx, h * 0.22f, 44 * sc, UiKit.COL_TEXT, Paint.Align.CENTER);
        float bw = 280 * sc, bh = 50 * sc;
        float y = h * 0.32f;
        ui.button(c, cx - bw * 0.5f, y, cx + bw * 0.5f, y + bh, "DEVAM", null,
                UiKit.STYLE_PRIMARY, true, A_RESUME, 0);
        y += bh + 12 * sc;
        ui.button(c, cx - bw * 0.5f, y, cx + bw * 0.5f, y + bh, "YETENEKLER", null,
                UiKit.STYLE_NORMAL, true, A_OPEN_SKILLS, 0);
        y += bh + 12 * sc;
        ui.button(c, cx - bw * 0.5f, y, cx + bw * 0.5f, y + bh, "SİLAHLAR", null,
                UiKit.STYLE_NORMAL, true, A_OPEN_WEAPONS, 0);
        y += bh + 12 * sc;
        ui.button(c, cx - bw * 0.5f, y, cx + bw * 0.5f, y + bh, "ANA MENÜ", null,
                UiKit.STYLE_GHOST, true, A_MAIN_MENU, 0);
    }

    private void drawSkills(Canvas c, GameWorld gw, float w, float h) {
        dimBackground(c, w, h, 0.8f);
        if (gw == null) return;
        Player p = gw.player;
        float pad = 20 * sc;
        ui.panel(c, pad, pad, w - pad, h - pad, "YETENEKLER   ·   kullanılabilir puan: " + p.skillPoints);

        int cols = 3;
        int rows = (Balance.SKILL_COUNT + cols - 1) / cols;
        float gx0 = pad + 16 * sc, gy0 = pad + 58 * sc;
        float cw = (w - pad * 2 - 32 * sc) / cols;
        float ch = Math.min(84 * sc, (h - pad * 2 - 118 * sc) / rows);

        for (int i = 0; i < Balance.SKILL_COUNT; i++) {
            Balance.SkillDef d = Balance.SKILLS[i];
            int col = i % cols, row = i / cols;
            float l = gx0 + col * cw, t = gy0 + row * ch;
            float r = l + cw - 10 * sc, b = t + ch - 8 * sc;
            boolean maxed = p.skills[i] >= d.maxLevel;
            boolean can = p.skillPoints > 0 && !maxed;
            ui.rect(c, l, t, r, b, 10 * sc, maxed ? 0xFF243028 : 0xFF1A2220);
            ui.border(c, l, t, r, b, 10 * sc, can ? 0x665FD38A : 0x22FFFFFF, 1.4f * sc);
            ui.label(c, d.name, l + 12 * sc, t + 22 * sc, 18 * sc,
                    maxed ? UiKit.COL_GOLD : UiKit.COL_TEXT, Paint.Align.LEFT);
            ui.label(c, d.desc, l + 12 * sc, t + 42 * sc, 13.5f * sc, UiKit.COL_DIM, Paint.Align.LEFT);
            // seviye noktaları
            for (int k = 0; k < d.maxLevel; k++) {
                float dx = l + 12 * sc + k * 14 * sc;
                ui.fill.setStyle(Paint.Style.FILL);
                ui.fill.setColor(k < p.skills[i] ? UiKit.COL_ACCENT : 0x55FFFFFF);
                c.drawCircle(dx, b - 14 * sc, 5 * sc, ui.fill);
            }
            ui.button(c, r - 54 * sc, b - 34 * sc, r - 10 * sc, b - 4 * sc,
                    maxed ? "TAM" : "+", null,
                    maxed ? UiKit.STYLE_GHOST : UiKit.STYLE_PRIMARY, can, A_SKILL_UP, i);
        }

        ui.button(c, w * 0.5f - 90 * sc, h - pad - 50 * sc, w * 0.5f + 90 * sc, h - pad - 8 * sc,
                "KAPAT", null, UiKit.STYLE_NORMAL, true, A_CLOSE, 0);
    }

    private void drawWeapons(Canvas c, GameWorld gw, float w, float h) {
        dimBackground(c, w, h, 0.8f);
        if (gw == null) return;
        Player p = gw.player;
        float pad = 20 * sc;
        ui.panel(c, pad, pad, w - pad, h - pad, "SİLAHLAR   ·   hurda: " + p.scrap);

        int n = Balance.WEAPONS.length;
        float rowH = Math.min(74 * sc, (h - pad * 2 - 110 * sc) / n);
        float y = pad + 56 * sc;
        for (int i = 0; i < n; i++) {
            Balance.WeaponDef d = Balance.weapon(i);
            float l = pad + 14 * sc, r = w - pad - 14 * sc;
            float t = y + i * rowH, b = t + rowH - 8 * sc;
            boolean owned = p.unlocked[i];
            boolean equipped = owned && p.currentWeapon == i;
            ui.rect(c, l, t, r, b, 10 * sc, equipped ? 0xFF24352C : 0xFF1A2220);
            ui.border(c, l, t, r, b, 10 * sc, equipped ? 0x885FD38A : 0x22FFFFFF, 1.4f * sc);
            ui.weaponIcon(c, l + 40 * sc, (t + b) * 0.5f, 46 * sc, i,
                    owned ? d.color : 0xFF555C58);
            int lvl = p.weaponLevel[i];
            ui.label(c, d.name + (owned ? "  Sv." + lvl : ""), l + 78 * sc, t + 26 * sc, 20 * sc,
                    owned ? UiKit.COL_TEXT : UiKit.COL_DIM, Paint.Align.LEFT);
            String stats = "hasar " + Math.round(d.damageAt(owned ? lvl : 1))
                    + "  ·  şarjör " + d.magazineAt(owned ? lvl : 1)
                    + "  ·  atış/sn " + String.format("%.1f", d.fireRate)
                    + (d.pellets > 1 ? "  ·  " + d.pellets + " saçma" : "")
                    + (d.splash > 0 ? "  ·  patlayıcı" : "");
            ui.label(c, stats, l + 78 * sc, t + 48 * sc, 14 * sc, UiKit.COL_DIM, Paint.Align.LEFT);

            float bx = r - 12 * sc;
            if (!owned) {
                ui.button(c, bx - 150 * sc, t + 10 * sc, bx, b - 10 * sc,
                        "SATIN AL", d.price + " hurda", UiKit.STYLE_GOLD,
                        p.scrap >= d.price, A_WEAPON_BUY, i);
            } else {
                boolean maxed = lvl >= Balance.WEAPON_MAX_LEVEL;
                int cost = d.upgradeCost(lvl);
                ui.button(c, bx - 150 * sc, t + 10 * sc, bx - 6 * sc, b - 10 * sc,
                        maxed ? "AZAMİ" : "GELİŞTİR", maxed ? null : cost + " hurda",
                        UiKit.STYLE_PRIMARY, !maxed && p.scrap >= cost, A_WEAPON_UP, i);
                ui.button(c, bx - 290 * sc, t + 10 * sc, bx - 158 * sc, b - 10 * sc,
                        equipped ? "KUŞANILI" : "KUŞAN", null,
                        equipped ? UiKit.STYLE_GHOST : UiKit.STYLE_NORMAL, !equipped,
                        A_WEAPON_SELECT, i);
            }
        }
        ui.button(c, w * 0.5f - 90 * sc, h - pad - 46 * sc, w * 0.5f + 90 * sc, h - pad - 6 * sc,
                "KAPAT", null, UiKit.STYLE_NORMAL, true, A_CLOSE, 0);
    }

    private void drawSquad(Canvas c, GameWorld gw, float w, float h) {
        dimBackground(c, w, h, 0.82f);
        if (gw == null) return;
        float pad = 18 * sc;
        int cap = gw.npcCapacity();
        ui.panel(c, pad, pad, w - pad, h - pad,
                "EKİP   ·   " + gw.npcs.size() + "/" + cap + " yoldaş   ·   hurda: "
                        + gw.player.scrap);

        float listW = (w - pad * 2) * 0.54f;
        float y = pad + 56 * sc;
        float rowH = Math.min(58 * sc, (h - pad * 2 - 190 * sc) / Math.max(1, gw.npcs.size()));

        // "tüm ekip" satırı
        boolean allSel = squadSel < 0;
        ui.rect(c, pad + 12 * sc, y, pad + listW, y + 34 * sc, 8 * sc,
                allSel ? 0xFF24352C : 0xFF1A2220);
        ui.border(c, pad + 12 * sc, y, pad + listW, y + 34 * sc, 8 * sc,
                allSel ? 0x885FD38A : 0x22FFFFFF, 1.3f * sc);
        ui.addHit(pad + 12 * sc, y, pad + listW, y + 34 * sc, A_SELECT_NPC, -1, true);
        ui.label(c, "TÜM EKİP", pad + 24 * sc, y + 23 * sc, 19 * sc,
                allSel ? UiKit.COL_ACCENT : UiKit.COL_TEXT, Paint.Align.LEFT);
        y += 40 * sc;

        for (int i = 0; i < gw.npcs.size(); i++) {
            Npc n = gw.npcs.get(i);
            boolean sel = squadSel == i;
            float t = y + i * rowH;
            float b = t + rowH - 6 * sc;
            ui.rect(c, pad + 12 * sc, t, pad + listW, b, 8 * sc, sel ? 0xFF24352C : 0xFF1A2220);
            ui.border(c, pad + 12 * sc, t, pad + listW, b, 8 * sc,
                    sel ? 0x885FD38A : 0x22FFFFFF, 1.3f * sc);
            ui.addHit(pad + 12 * sc, t, pad + listW, b, A_SELECT_NPC, i, true);

            int roleColor = Balance.npc(n.role).accent;
            ui.fill.setStyle(Paint.Style.FILL);
            ui.fill.setColor(0xFF000000 | roleColor);
            c.drawCircle(pad + 32 * sc, (t + b) * 0.5f, 9 * sc, ui.fill);

            ui.label(c, n.name + "  ·  " + n.roleName() + " Sv." + n.level
                            + "  ⚒" + n.weaponLevel,
                    pad + 50 * sc, t + 22 * sc, 18 * sc,
                    n.downed ? UiKit.COL_DANGER : UiKit.COL_TEXT, Paint.Align.LEFT);
            ui.label(c, n.statusText(), pad + 50 * sc, t + 40 * sc, 14 * sc,
                    UiKit.COL_DIM, Paint.Align.LEFT);
            ui.bar(c, pad + listW - 120 * sc, t + 14 * sc, pad + listW - 14 * sc, t + 26 * sc,
                    n.hp / Math.max(1f, n.maxHp), 0xFF241A1A,
                    n.downed ? 0xFF8E3A31 : 0xFF7ECB6B, false);
            if (n.role == Balance.NPC_SCAVENGER && n.collected > 0) {
                ui.label(c, "topladığı: " + n.collected, pad + listW - 120 * sc, t + 42 * sc,
                        13 * sc, UiKit.COL_GOLD, Paint.Align.LEFT);
            }
        }

        // --- duruş ve görevler ---
        float ox = pad + listW + 18 * sc;
        float ow = w - pad - 12 * sc - ox;
        boolean validSel = squadSel >= 0 && squadSel < gw.npcs.size();
        ui.label(c, validSel ? "EMİR: " + gw.npcs.get(squadSel).name : "TÜM EKİBE EMİR",
                ox, pad + 74 * sc, 20 * sc, UiKit.COL_ACCENT, Paint.Align.LEFT);
        float by = pad + 84 * sc;
        float bh = 40 * sc;

        ui.label(c, "DURUŞ — nerede duracak", ox, by, 15 * sc, UiKit.COL_DIM, Paint.Align.LEFT);
        by += 8 * sc;
        float sw = (ow - 8 * sc) / 3f;
        for (int i = 0; i < Balance.STANCE_COUNT; i++) {
            float bx = ox + (i % 3) * (sw + 4 * sc);
            float byy = by + (i / 3) * (bh + 6 * sc);
            boolean positional = i == Balance.STANCE_HOLD || i == Balance.STANCE_ATTACK;
            boolean active = validSel ? gw.npcs.get(squadSel).stance == i : false;
            ui.button(c, bx, byy, bx + sw - 4 * sc, byy + bh, Balance.STANCE_SHORT[i],
                    positional ? "nokta" : null,
                    active ? UiKit.STYLE_PRIMARY
                            : (i == Balance.STANCE_ATTACK ? UiKit.STYLE_DANGER : UiKit.STYLE_NORMAL),
                    !gw.npcs.isEmpty(), A_ORDER, i);
        }

        // Görevler: aynı anda birden fazlası açık olabilir.
        float dy = by + 2 * (bh + 6 * sc) + 10 * sc;
        ui.label(c, "GÖREVLER — birden fazlası aynı anda açık olabilir",
                ox, dy, 15 * sc, UiKit.COL_DIM, Paint.Align.LEFT);
        dy += 8 * sc;
        float dw = (ow - 8 * sc) / 3f;
        for (int i = 0; i < Balance.DUTY_BITS.length; i++) {
            float bx = ox + (i % 3) * (dw + 4 * sc);
            float byy = dy + (i / 3) * (bh + 6 * sc);
            boolean on;
            if (validSel) {
                on = gw.npcs.get(squadSel).hasDuty(Balance.DUTY_BITS[i]);
            } else {
                on = false;
                for (int k = 0; k < gw.npcs.size(); k++) {
                    if (gw.npcs.get(k).hasDuty(Balance.DUTY_BITS[i])) on = true;
                }
            }
            String sub = null;
            if (validSel) {
                float mul = Balance.npc(gw.npcs.get(squadSel).role).mulFor(Balance.DUTY_BITS[i]);
                sub = "verim %" + Math.round(mul * 100);
            }
            ui.button(c, bx, byy, bx + dw - 4 * sc, byy + bh,
                    (on ? "✓ " : "") + Balance.DUTY_SHORT[i], sub,
                    on ? UiKit.STYLE_PRIMARY : UiKit.STYLE_GHOST,
                    !gw.npcs.isEmpty(), A_DUTY, i);
        }

        // kendini geliştirme izni
        float gy = dy + 2 * (bh + 6 * sc) + 6 * sc;
        boolean improveOn;
        if (validSel) {
            improveOn = gw.npcs.get(squadSel).selfImprove;
        } else {
            improveOn = false;
            for (int k = 0; k < gw.npcs.size(); k++) {
                if (gw.npcs.get(k).selfImprove) improveOn = true;
            }
        }
        ui.button(c, ox, gy, ox + ow, gy + 40 * sc,
                improveOn ? "KENDİNİ GELİŞTİRSİN ✓" : "KENDİNİ GELİŞTİRSİN",
                "hazırlıkta kasada bolluk varsa seviye/silah alır",
                improveOn ? UiKit.STYLE_PRIMARY : UiKit.STYLE_GHOST,
                !gw.npcs.isEmpty(), A_SELF_IMPROVE, 0);

        // --- yoldaş alma ---
        float ry = gy + 52 * sc;
        boolean hasBarracks = gw.barracks() != null;
        ui.label(c, hasBarracks ? "YOLDAŞ AL" : "YOLDAŞ AL — önce Kışla kur",
                ox, ry, 19 * sc, hasBarracks ? UiKit.COL_GOLD : UiKit.COL_DIM, Paint.Align.LEFT);
        ry += 10 * sc;
        float rw = (ow - 12 * sc) * 0.5f;
        for (int i = 0; i < Balance.NPC_COUNT; i++) {
            Balance.NpcDef d = Balance.npc(i);
            int cost = gw.player.buildCost(d.hire);
            float bx = ox + (i % 2) * (rw + 12 * sc);
            float byy = ry + (i / 2) * (52 * sc);
            boolean can = hasBarracks && gw.npcs.size() < cap && gw.player.scrap >= cost;
            ui.button(c, bx, byy, bx + rw, byy + 46 * sc, d.name, cost + " hurda",
                    UiKit.STYLE_GOLD, can, A_RECRUIT, i);
        }
        float infoY = ry + 2 * (52 * sc) + 14 * sc;
        if (validSel) {
            wrapText(c, Balance.npc(gw.npcs.get(squadSel).role).desc, ox, infoY, ow, 14.5f * sc);
        } else {
            wrapText(c, "Bir yoldaşa aynı anda birden çok görev verebilirsin; hangisinin "
                    + "daha acil olduğuna kendisi karar verir. İNŞA görevi olanlar plan "
                    + "bıraktığın yerlere yapıyı kurar ve yıkılanları yeniden diker. "
                    + "Hurda ortak kasadan harcanır.", ox, infoY, ow, 14.5f * sc);
        }

        ui.button(c, w * 0.5f - 90 * sc, h - pad - 46 * sc, w * 0.5f + 90 * sc, h - pad - 6 * sc,
                "KAPAT", null, UiKit.STYLE_NORMAL, true, A_CLOSE, 0);
    }

    private void drawGameOver(Canvas c, GameWorld gw, float w, float h) {
        dimBackground(c, w, h, 0.82f);
        float cx = w * 0.5f;
        ui.labelShadow(c, "REAKTÖR DÜŞTÜ", cx, h * 0.24f, 54 * sc, UiKit.COL_DANGER, Paint.Align.CENTER);
        if (gw != null) {
            int mins = (int) (gw.playTime / 60f);
            int secs = (int) (gw.playTime % 60f);
            ui.labelShadow(c, "Ulaşılan dalga: " + gw.waves.wave, cx, h * 0.36f, 26 * sc,
                    UiKit.COL_GOLD, Paint.Align.CENTER);
            ui.labelShadow(c, "Öldürülen zombi: " + gw.totalKills
                            + "   ·   Kurulan yapı: " + gw.structuresBuilt
                            + "   ·   Süre: " + mins + "dk " + secs + "sn",
                    cx, h * 0.44f, 19 * sc, UiKit.COL_DIM, Paint.Align.CENTER);
            ui.labelShadow(c, "Karakter seviyesi: " + gw.player.level, cx, h * 0.50f, 19 * sc,
                    UiKit.COL_DIM, Paint.Align.CENTER);
        }
        float bw = 260 * sc, bh = 52 * sc;
        ui.button(c, cx - bw - 10 * sc, h * 0.62f, cx - 10 * sc, h * 0.62f + bh,
                "TEKRAR DENE", null, UiKit.STYLE_PRIMARY, true, A_RESTART, 0);
        ui.button(c, cx + 10 * sc, h * 0.62f, cx + bw + 10 * sc, h * 0.62f + bh,
                "ANA MENÜ", null, UiKit.STYLE_NORMAL, true, A_MAIN_MENU, 0);
    }

    private static final String[] HELP_LINES = {
            "AMAÇ: Haritanın ortasındaki reaktörü zombi dalgalarından koru.",
            "Reaktörün canı biterse oyun biter.",
            "",
            "HAZIRLIK: Her dalga arasında inşa süresi var. İNŞA MODU düğmesine bas,",
            "alttan bir yapı seç ve ızgaraya dokun. Parmağını sürükleyerek arka arkaya",
            "duvar dizebilirsin; duvarlar komşularına göre kendiliğinden birleşir.",
            "Diğer yapıların yönünü soldaki YÖN/DÖNDÜR düğmesiyle çevirebilirsin.",
            "Kurulu bir yapıya dokunursan geliştir/onar/sat paneli açılır.",
            "",
            "SAVAŞ: Sol yarıda parmağını basılı tut ve sürükle, karakter o yöne gider.",
            "ATEŞ düğmesi en yakın hedefe otomatik nişan alır. ATIL ile kısa mesafe sıçra.",
            "Sağ yarıda sürükleyerek kamerayı döndür, iki parmakla yakınlaştır.",
            "",
            "GELİŞİM: Zombi öldürdükçe hurda ve tecrübe kazanırsın. Seviye atlayınca",
            "yetenek puanı alırsın (can, zırh, hasar, mühendislik...). Hurdayla yeni silah",
            "alıp geliştirebilir, yapılarını 5. seviyeye kadar yükseltebilirsin.",
            "",
            "ENERJİ: Kuleler enerji tüketir, jeneratörler üretir. Enerji açığı varsa",
            "kulelerin atış hızı düşer. Her 5. dalgada Mutant Dev gelir, enerji çekirdeği bırakır.",
            "",
            "EKİP: Kışla kurup yoldaş alabilirsin (muhafız, mühendis, toplayıcı, sağlıkçı).",
            "EKİP panelinde iki şey ayarlanır: DURUŞ (nerede duracak) ve GÖREVLER.",
            "Bir yoldaşa aynı anda birden çok görev verilebilir: savaş + onar + inşa + topla.",
            "Hangisinin acil olduğuna kendisi karar verir; TUT/SALDIR için haritada nokta seç.",
            "",
            "İNŞAAT: İnşa modunda PLAN düğmesini aç, zemine dokun — oraya şantiye bırakırsın.",
            "İnşa görevi olan yoldaş gider, ortak kasadan ödeyip yapıyı kurar. OTO açıksa",
            "yıkılan yapılar için kendiliğinden plan açılır, ekip üssü kendi onarır.",
            "",
            "GANİMET: Ölen zombiler yere hurda düşürür ve toplanana kadar orada kalır.",
            "Üstüne gidersen kendiliğinden çekilir, TOPLA görevli yoldaş senin için toplar.",
            "Üst çubuktaki \"yerde\" sayısı sahada kaç hurda beklediğini gösterir.",
            "",
            "AKILLI YOLDAŞ: SERBEST duruşta nerede duracağına kendisi karar verir —",
            "reaktör tehdit altındaysa oraya koşar, üsse sızan olursa keser, sen",
            "zor durumdaysan yanına gelir, dalga sırasında en yoğun cepheye geçer.",
            "Ne yapacağını başının üstündeki baloncukta söyler. KENDİNİ GELİŞTİRSİN",
            "açıkken hazırlıkta kasada bolluk varsa silahını ve seviyesini yükseltir",
            "(kasada asgari yedek bırakır, harcadığını da söyler)."
    };

    private void drawHelp(Canvas c, float w, float h) {
        dimBackground(c, w, h, 0.88f);
        float pad = 20 * sc;
        ui.panel(c, pad, pad, w - pad, h - pad, "NASIL OYNANIR");
        float y = pad + 78 * sc;
        for (String line : HELP_LINES) {
            ui.label(c, line, pad + 24 * sc, y, 17 * sc,
                    line.startsWith("AMAÇ") || line.startsWith("HAZIRLIK") || line.startsWith("SAVAŞ")
                            || line.startsWith("GELİŞİM") || line.startsWith("ENERJİ")
                            ? UiKit.COL_ACCENT : UiKit.COL_TEXT,
                    Paint.Align.LEFT);
            y += 21 * sc;
        }
        ui.button(c, w * 0.5f - 90 * sc, h - pad - 50 * sc, w * 0.5f + 90 * sc, h - pad - 8 * sc,
                "KAPAT", null, UiKit.STYLE_NORMAL, true, A_CLOSE, 0);
    }

    // ---- dokunma --------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int index = e.getActionIndex();
        int id = e.getPointerId(index);
        float x = e.getX(index), y = e.getY(index);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleDown(id, x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    handleMove(e.getPointerId(i), e.getX(i), e.getY(i));
                }
                handlePinch(e);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                handleUp(id);
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        return true;
    }

    private void releaseAll() {
        movePointer = camPointer = firePointer = buildPointer = -1;
        pinchA = pinchB = -1;
        input.moveX = 0f;
        input.moveZ = 0f;
        input.firing = false;
        ui.pressedAction = -1;
    }

    private void handleDown(int id, float x, float y) {
        int act = ui.hitTest(x, y, param);
        if (act == -2) return;          // kapalı düğme
        if (act >= 0) {
            ui.pressedAction = act;
            ui.pressedParam = param[0];
            if (act == A_FIRE) {
                firePointer = id;
                input.firing = true;
                return;
            }
            performAction(act, param[0]);
            ui.pressedAction = -1;
            return;
        }
        if (screen != SCREEN_GAME) return;

        // Konumlu emir bekleniyorsa ilk dokunuş hedefi belirler.
        if (pendingOrder >= 0) {
            GameWorld gw = world;
            if (gw != null) {
                int vw, vh;
                synchronized (viewProj) {
                    System.arraycopy(invViewProj, 0, tmpVP, 0, 16);
                    vw = getWidth();
                    vh = getHeight();
                }
                if (M4.unprojectToPlane(tmpVP, x, y, vw, vh, 0.05f, tmpA, tmpB, outXZ)) {
                    int ogx = BuildGrid.worldToCell(outXZ[0]);
                    int ogz = BuildGrid.worldToCell(outXZ[1]);
                    if (BuildGrid.inBounds(ogx, ogz)) {
                        input.push(new Cmd(Cmd.ORDER_AT, squadSel,
                                ogz * BuildGrid.N + ogx, pendingOrder));
                    }
                }
            }
            pendingOrder = -1;
            return;
        }

        float w = getWidth(), h = getHeight();
        // İnşa modunda sol alt köşe hareket için ayrılır, gerisi yerleştirme alanıdır.
        boolean joyZone = input.buildMode
                ? (x < w * 0.30f && y > h * 0.45f)
                : (x < w * 0.46f);
        if (input.buildMode && !joyZone && buildPointer < 0 && y < h - 100 * sc) {
            buildPointer = id;
            lastPlacedCell = -1;
            handleBuildTouch(x, y, true);
            return;
        }
        if (joyZone && movePointer < 0) {
            movePointer = id;
            joyBaseX = x;
            joyBaseY = y;
            joyX = x;
            joyY = y;
            return;
        }
        if (camPointer < 0) {
            camPointer = id;
            lastCamX = x;
            lastCamY = y;
        } else if (pinchA < 0) {
            pinchA = camPointer;
            pinchB = id;
        }
    }

    private void handleMove(int id, float x, float y) {
        if (id == movePointer) {
            float dx = x - joyBaseX, dy = y - joyBaseY;
            float max = 78 * sc;
            float len = MathX.len(dx, dy);
            if (len > max) {
                dx = dx / len * max;
                dy = dy / len * max;
            }
            joyX = joyBaseX + dx;
            joyY = joyBaseY + dy;
            float nx = dx / max, ny = dy / max;
            float dead = 0.14f;
            float mag = MathX.len(nx, ny);
            if (mag < dead) {
                input.moveX = 0f;
                input.moveZ = 0f;
            } else {
                float k = (mag - dead) / (1f - dead) / Math.max(0.0001f, mag);
                input.moveX = MathX.clamp(nx * k, -1f, 1f);
                input.moveZ = MathX.clamp(-ny * k, -1f, 1f);
            }
        } else if (id == camPointer && pinchA < 0) {
            float dx = x - lastCamX, dy = y - lastCamY;
            lastCamX = x;
            lastCamY = y;
            // "Döner tabla" mantığı: sahneyi parmakla çevirirsin.
            input.addCamDrag(dx * 0.006f, dy * 0.004f);
        } else if (id == buildPointer) {
            handleBuildTouch(x, y, false);
        }
    }

    private void handlePinch(MotionEvent e) {
        if (pinchA < 0 || pinchB < 0) return;
        int ia = e.findPointerIndex(pinchA), ib = e.findPointerIndex(pinchB);
        if (ia < 0 || ib < 0) return;
        float dx = e.getX(ia) - e.getX(ib), dy = e.getY(ia) - e.getY(ib);
        float d = MathX.len(dx, dy);
        if (pinchDist > 0f) {
            input.addZoom((d - pinchDist) * 0.0022f);
        }
        pinchDist = d;
    }

    private void handleUp(int id) {
        if (id == movePointer) {
            movePointer = -1;
            input.moveX = 0f;
            input.moveZ = 0f;
        }
        if (id == firePointer) {
            firePointer = -1;
            input.firing = false;
        }
        if (id == camPointer) camPointer = -1;
        if (id == buildPointer) {
            buildPointer = -1;
            input.hoverGx = -1;
            input.hoverGz = -1;
        }
        if (id == pinchA || id == pinchB) {
            pinchA = pinchB = -1;
            pinchDist = 0f;
        }
        ui.pressedAction = -1;
    }

    /** İnşa modunda ekran dokunuşunu ızgara hücresine çevirir. */
    private void handleBuildTouch(float x, float y, boolean isDown) {
        GameWorld gw = world;
        if (gw == null) return;
        int vw, vh;
        synchronized (viewProj) {
            System.arraycopy(invViewProj, 0, tmpVP, 0, 16);
            vw = getWidth();
            vh = getHeight();
        }
        // Yerleştirme için zemin düzlemi doğru sonucu verir.
        if (!M4.unprojectToPlane(tmpVP, x, y, vw, vh, 0.05f, tmpA, tmpB, outXZ)) return;
        int gx = BuildGrid.worldToCell(outXZ[0]);
        int gz = BuildGrid.worldToCell(outXZ[1]);
        if (!BuildGrid.inBounds(gx, gz)) return;

        // Boş hücreye dokunulduysa, aslında yakındaki bir yapının gövdesine
        // dokunulmuş olabilir (kule zeminden yüksek durur): gövde hizasını da dene.
        if (gw.grid.at(gx, gz) == null
                && M4.unprojectToPlane(tmpVP, x, y, vw, vh, 1.1f, tmpA, tmpB, outXZ)) {
            int bx = BuildGrid.worldToCell(outXZ[0]);
            int bz = BuildGrid.worldToCell(outXZ[1]);
            if (BuildGrid.inBounds(bx, bz) && gw.grid.at(bx, bz) != null) {
                gx = bx;
                gz = bz;
            }
        }
        input.hoverGx = gx;
        input.hoverGz = gz;
        int cell = gz * BuildGrid.N + gx;
        if (cell == lastPlacedCell) return;
        lastPlacedCell = cell;

        Structure existing = gw.grid.at(gx, gz);
        if (existing != null) {
            if (isDown) input.push(new Cmd(Cmd.SELECT, 0, gx, gz));
            return;
        }
        if (gw.planAt(gx, gz) != null) {
            if (isDown) input.push(new Cmd(Cmd.CANCEL_PLAN, 0, gx, gz));   // plana dokun = iptal
            return;
        }
        input.push(new Cmd(planMode ? Cmd.PLAN : Cmd.PLACE, input.buildType, gx, gz));
    }

    private void performAction(int act, int p) {
        GameWorld gw = world;
        switch (act) {
            case A_RELOAD: input.push(new Cmd(Cmd.RELOAD)); break;
            case A_DASH: input.push(new Cmd(Cmd.DASH)); break;
            case A_WEAPON_NEXT: nextWeapon(gw); break;
            case A_BUILD_TOGGLE:
                input.buildMode = !input.buildMode;
                input.firing = false;
                if (!input.buildMode) input.push(new Cmd(Cmd.DESELECT));
                break;
            case A_PAUSE: setScreen(SCREEN_PAUSE); break;
            case A_START_WAVE: input.push(new Cmd(Cmd.START_WAVE)); break;
            case A_REPAIR_ALL: input.push(new Cmd(Cmd.REPAIR_ALL)); break;
            case A_BUY_AMMO: input.push(new Cmd(Cmd.BUY_AMMO)); break;
            case A_ROTATE: input.push(new Cmd(Cmd.ROTATE)); break;
            case A_OPEN_SQUAD: setScreen(SCREEN_SQUAD); break;
            case A_SELECT_NPC: squadSel = p; break;
            case A_RECRUIT: input.push(new Cmd(Cmd.RECRUIT, p)); break;
            case A_DUTY:
                input.push(new Cmd(Cmd.DUTY, squadSel, Balance.DUTY_BITS[p], 0));
                break;
            case A_PLAN_MODE:
                planMode = !planMode;
                break;
            case A_CANCEL_PLANS: input.push(new Cmd(Cmd.CANCEL_ALL_PLANS)); break;
            case A_AUTOBUILD: input.push(new Cmd(Cmd.TOGGLE_AUTOBUILD)); break;
            case A_SELF_IMPROVE: input.push(new Cmd(Cmd.SELF_IMPROVE, squadSel)); break;
            case A_ORDER:
                if (p == Balance.STANCE_HOLD || p == Balance.STANCE_ATTACK) {
                    pendingOrder = p;      // haritadan nokta bekle
                    setScreen(SCREEN_GAME);
                } else if (squadSel < 0) {
                    input.push(new Cmd(Cmd.ORDER_ALL, p));
                } else {
                    input.push(new Cmd(Cmd.ORDER, squadSel, p, 0));
                }
                break;
            case A_PICK_BUILD: input.buildType = p; break;
            case A_UPGRADE: input.push(new Cmd(Cmd.UPGRADE_SELECTED)); break;
            case A_REPAIR: input.push(new Cmd(Cmd.REPAIR_SELECTED)); break;
            case A_SELL: input.push(new Cmd(Cmd.SELL_SELECTED)); break;
            case A_DESELECT: input.push(new Cmd(Cmd.DESELECT)); break;
            case A_OPEN_SKILLS: setScreen(SCREEN_SKILLS); break;
            case A_OPEN_WEAPONS: setScreen(SCREEN_WEAPONS); break;
            case A_RESUME: setScreen(SCREEN_GAME); break;
            case A_CLOSE: setScreen(SCREEN_GAME); break;
            case A_RESTART:
                input.push(new Cmd(Cmd.RESTART));
                setScreen(SCREEN_GAME);
                break;
            case A_MAIN_MENU:
                if (listener != null) listener.onSaveRequested();
                setScreen(SCREEN_MENU);
                break;
            case A_SKILL_UP: input.push(new Cmd(Cmd.SKILL_UP, p)); break;
            case A_WEAPON_BUY: input.push(new Cmd(Cmd.BUY_WEAPON, p)); break;
            case A_WEAPON_UP: input.push(new Cmd(Cmd.WEAPON_UP, p)); break;
            case A_WEAPON_SELECT: input.push(new Cmd(Cmd.SWITCH_WEAPON, p)); break;
            case A_NEW_GAME:
                if (listener != null) listener.onNewGame();
                setScreen(SCREEN_GAME);
                break;
            case A_CONTINUE:
                if (listener != null) listener.onContinue();
                setScreen(SCREEN_GAME);
                break;
            case A_HELP: setScreen(SCREEN_HELP); break;
            case A_SOUND:
                if (listener != null) listener.setSoundOn(!listener.isSoundOn());
                break;
            case A_QUIT:
                if (listener != null) listener.onQuit();
                break;
            default: break;
        }
    }

    private void nextWeapon(GameWorld gw) {
        if (gw == null) return;
        Player p = gw.player;
        for (int i = 1; i <= Balance.WEAPONS.length; i++) {
            int id = (p.currentWeapon + i) % Balance.WEAPONS.length;
            if (p.unlocked[id]) {
                input.push(new Cmd(Cmd.SWITCH_WEAPON, id));
                return;
            }
        }
    }
}
