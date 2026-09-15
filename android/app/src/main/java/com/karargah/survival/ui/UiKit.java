package com.karargah.survival.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;

/**
 * Canvas üstünde çalışan küçük "anlık mod" arayüz kiti. Çizim sırasında
 * dokunulabilir bölgeler kaydedilir; dokunma olayı aynı listeden çözülür.
 */
public class UiKit {
    public static final int COL_BG = 0xE6141A18;
    public static final int COL_PANEL = 0xF01B2320;
    public static final int COL_LINE = 0x552E3A34;
    public static final int COL_TEXT = 0xFFE8F0E8;
    public static final int COL_DIM = 0xFF9BA8A0;
    public static final int COL_ACCENT = 0xFF5FD38A;
    public static final int COL_WARN = 0xFFE9A13B;
    public static final int COL_DANGER = 0xFFE05C4B;
    public static final int COL_GOLD = 0xFFFFD75E;
    public static final int COL_CYAN = 0xFF4DD0E1;

    public static final int STYLE_NORMAL = 0;
    public static final int STYLE_PRIMARY = 1;
    public static final int STYLE_DANGER = 2;
    public static final int STYLE_GHOST = 3;
    public static final int STYLE_GOLD = 4;

    public final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();
    private final Path path = new Path();

    public float sc = 1f;

    public static class Hit {
        public final RectF rect = new RectF();
        public int action;
        public int param;
        public boolean enabled;
    }

    private final ArrayList<Hit> hits = new ArrayList<>();
    private int hitCount;
    public int pressedAction = -1;
    public int pressedParam = -1;

    public UiKit() {
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        text.setTextAlign(Paint.Align.LEFT);
    }

    public void begin(float scale) {
        this.sc = scale;
        hitCount = 0;
    }

    public Hit addHit(float l, float t, float r, float b, int action, int param, boolean enabled) {
        Hit h;
        if (hitCount < hits.size()) {
            h = hits.get(hitCount);
        } else {
            h = new Hit();
            hits.add(h);
        }
        hitCount++;
        h.rect.set(l, t, r, b);
        h.action = action;
        h.param = param;
        h.enabled = enabled;
        return h;
    }

    /** @return dokunulan eylem, yoksa -1. Param outParam[0]'a yazılır. */
    public int hitTest(float x, float y, int[] outParam) {
        for (int i = hitCount - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.rect.contains(x, y)) {
                if (outParam != null) outParam[0] = h.param;
                return h.enabled ? h.action : -2;
            }
        }
        return -1;
    }

    public boolean isPressed(int action, int param) {
        return pressedAction == action && pressedParam == param;
    }

    // ---- çizim yardımcıları --------------------------------------------

    public void rect(Canvas c, float l, float t, float r, float b, float radius, int color) {
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        tmpRect.set(l, t, r, b);
        c.drawRoundRect(tmpRect, radius, radius, fill);
    }

    public void border(Canvas c, float l, float t, float r, float b, float radius,
                       int color, float width) {
        stroke.setColor(color);
        stroke.setStrokeWidth(width);
        tmpRect.set(l, t, r, b);
        c.drawRoundRect(tmpRect, radius, radius, stroke);
    }

    public void panel(Canvas c, float l, float t, float r, float b, String title) {
        rect(c, l, t, r, b, 14 * sc, COL_PANEL);
        border(c, l, t, r, b, 14 * sc, 0x66FFFFFF & (COL_ACCENT | 0x00FFFFFF), 1.6f * sc);
        if (title != null) {
            label(c, title, l + 18 * sc, t + 30 * sc, 20 * sc, COL_ACCENT, Paint.Align.LEFT);
            fill.setColor(COL_LINE);
            c.drawRect(l + 14 * sc, t + 42 * sc, r - 14 * sc, t + 43.5f * sc, fill);
        }
    }

    public void label(Canvas c, String s, float x, float y, float size, int color,
                      Paint.Align align) {
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align);
        text.setFakeBoldText(false);
        c.drawText(s, x, y, text);
    }

    public void labelShadow(Canvas c, String s, float x, float y, float size, int color,
                            Paint.Align align) {
        text.setTextSize(size);
        text.setTextAlign(align);
        text.setColor(0xCC000000);
        c.drawText(s, x + 1.8f * sc, y + 1.8f * sc, text);
        text.setColor(color);
        c.drawText(s, x, y, text);
    }

    public float textWidth(String s, float size) {
        text.setTextSize(size);
        return text.measureText(s);
    }

    public void bar(Canvas c, float l, float t, float r, float b, float frac,
                    int bg, int fg, boolean showBorder) {
        frac = Math.max(0f, Math.min(1f, frac));
        float radius = (b - t) * 0.5f;
        rect(c, l, t, r, b, radius, bg);
        if (frac > 0.002f) {
            rect(c, l, t, l + (r - l) * frac, b, radius, fg);
        }
        if (showBorder) border(c, l, t, r, b, radius, 0x55FFFFFF, 1.2f * sc);
    }

    /** Dokunulabilir düğme. */
    public void button(Canvas c, float l, float t, float r, float b, String title,
                       String sub, int style, boolean enabled, int action, int param) {
        addHit(l, t, r, b, action, param, enabled);
        boolean pressed = isPressed(action, param) && enabled;
        int base;
        switch (style) {
            case STYLE_PRIMARY: base = 0xFF2E7D52; break;
            case STYLE_DANGER: base = 0xFF8E3A31; break;
            case STYLE_GOLD: base = 0xFF8A7220; break;
            case STYLE_GHOST: base = 0x66202A26; break;
            default: base = 0xFF25302B; break;
        }
        if (!enabled) base = 0xFF1E2320;
        if (pressed) base = blend(base, 0xFFFFFFFF, 0.22f);
        float radius = 12 * sc;
        rect(c, l, t, r, b, radius, base);
        border(c, l, t, r, b, radius,
                enabled ? (style == STYLE_PRIMARY ? 0xFF5FD38A : 0x44FFFFFF) : 0x22FFFFFF, 1.5f * sc);
        float cx = (l + r) * 0.5f;
        float cy = (t + b) * 0.5f;
        int tc = enabled ? COL_TEXT : 0xFF6B736E;
        if (sub == null) {
            label(c, title, cx, cy + 7 * sc, 21 * sc, tc, Paint.Align.CENTER);
        } else {
            label(c, title, cx, cy - 2 * sc, 20 * sc, tc, Paint.Align.CENTER);
            label(c, sub, cx, cy + 20 * sc, 16 * sc, enabled ? COL_DIM : 0xFF5A625E, Paint.Align.CENTER);
        }
    }

    public void circleButton(Canvas c, float cx, float cy, float radius, String title,
                             int color, boolean enabled, int action, int param, float cooldown) {
        addHit(cx - radius, cy - radius, cx + radius, cy + radius, action, param, enabled);
        boolean pressed = isPressed(action, param) && enabled;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(enabled ? (pressed ? blend(color, 0xFFFFFFFF, 0.35f) : color) : 0x55303634);
        c.drawCircle(cx, cy, radius, fill);
        stroke.setColor(0x66FFFFFF);
        stroke.setStrokeWidth(2f * sc);
        c.drawCircle(cx, cy, radius, stroke);
        if (cooldown > 0f) {
            fill.setColor(0xAA000000);
            tmpRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            c.drawArc(tmpRect, -90f, 360f * Math.min(1f, cooldown), true, fill);
        }
        label(c, title, cx, cy + 8 * sc, 22 * sc, enabled ? 0xFFFFFFFF : 0xFF8A918C, Paint.Align.CENTER);
    }

    public static int blend(int a, int b, float t) {
        int aa = Color.alpha(a), ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int ba = Color.alpha(b), br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        return Color.argb((int) (aa + (ba - aa) * t), (int) (ar + (br - ar) * t),
                (int) (ag + (bg - ag) * t), (int) (ab + (bb - ab) * t));
    }

    public static int withAlpha(int color, float alpha) {
        int a = (int) (255 * Math.max(0f, Math.min(1f, alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Yapı türü için basit vektör simge. */
    public void structIcon(Canvas c, float cx, float cy, float size, int type, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        stroke.setColor(color);
        stroke.setStrokeWidth(Math.max(2f, size * 0.12f));
        float h = size * 0.5f;
        path.reset();
        switch (type) {
            case 1: // duvar
                c.drawRect(cx - h, cy - h * 0.55f, cx + h, cy + h * 0.75f, fill);
                fill.setColor(0x55000000);
                c.drawRect(cx - h, cy - h * 0.05f, cx + h, cy + h * 0.05f, fill);
                c.drawRect(cx - h * 0.05f, cy - h * 0.55f, cx + h * 0.05f, cy + h * 0.75f, fill);
                break;
            case 2: // dikenler
                for (int i = -1; i <= 1; i++) {
                    path.moveTo(cx + i * h * 0.6f - h * 0.24f, cy + h * 0.7f);
                    path.lineTo(cx + i * h * 0.6f, cy - h * 0.7f);
                    path.lineTo(cx + i * h * 0.6f + h * 0.24f, cy + h * 0.7f);
                    path.close();
                }
                c.drawPath(path, fill);
                break;
            case 3: // makineli
            case 7: // nişancı kulesi
                c.drawRect(cx - h * 0.7f, cy + h * 0.2f, cx + h * 0.7f, cy + h * 0.8f, fill);
                c.drawRect(cx - h * 0.35f, cy - h * 0.3f, cx + h * 0.35f, cy + h * 0.25f, fill);
                c.drawRect(cx - h * 0.1f, cy - h * (type == 7 ? 1.0f : 0.8f), cx + h * 0.1f, cy - h * 0.2f, fill);
                break;
            case 4: // top
                c.drawRect(cx - h * 0.7f, cy + h * 0.25f, cx + h * 0.7f, cy + h * 0.8f, fill);
                c.drawCircle(cx, cy - h * 0.05f, h * 0.42f, fill);
                c.drawRect(cx - h * 0.16f, cy - h * 0.85f, cx + h * 0.16f, cy - h * 0.1f, fill);
                break;
            case 5: // alev
                path.moveTo(cx, cy - h * 0.85f);
                path.lineTo(cx + h * 0.55f, cy + h * 0.25f);
                path.lineTo(cx, cy + h * 0.8f);
                path.lineTo(cx - h * 0.55f, cy + h * 0.25f);
                path.close();
                c.drawPath(path, fill);
                break;
            case 6: // tesla
                path.moveTo(cx + h * 0.35f, cy - h * 0.85f);
                path.lineTo(cx - h * 0.35f, cy + h * 0.1f);
                path.lineTo(cx + h * 0.08f, cy + h * 0.1f);
                path.lineTo(cx - h * 0.28f, cy + h * 0.85f);
                path.lineTo(cx + h * 0.45f, cy - h * 0.12f);
                path.lineTo(cx - h * 0.02f, cy - h * 0.12f);
                path.close();
                c.drawPath(path, fill);
                break;
            case 8: // jeneratör
                c.drawRect(cx - h * 0.7f, cy - h * 0.5f, cx + h * 0.7f, cy + h * 0.7f, fill);
                fill.setColor(0x66000000);
                c.drawRect(cx - h * 0.4f, cy - h * 0.2f, cx + h * 0.4f, cy + h * 0.4f, fill);
                break;
            case 9: // cephanelik
                c.drawRect(cx - h * 0.75f, cy - h * 0.3f, cx + h * 0.75f, cy + h * 0.7f, fill);
                fill.setColor(0x66000000);
                c.drawRect(cx - h * 0.75f, cy - h * 0.05f, cx + h * 0.75f, cy + h * 0.08f, fill);
                break;
            case 10: // tamir
            case 11: // tıbbi
                c.drawRect(cx - h * 0.22f, cy - h * 0.8f, cx + h * 0.22f, cy + h * 0.8f, fill);
                c.drawRect(cx - h * 0.8f, cy - h * 0.22f, cx + h * 0.8f, cy + h * 0.22f, fill);
                break;
            case 12: // toplayıcı
                path.moveTo(cx, cy - h * 0.8f);
                path.lineTo(cx + h * 0.8f, cy);
                path.lineTo(cx, cy + h * 0.8f);
                path.lineTo(cx - h * 0.8f, cy);
                path.close();
                c.drawPath(path, fill);
                break;
            default:
                c.drawCircle(cx, cy, h * 0.7f, fill);
                break;
        }
    }

    /** Silah simgesi (basit siluet). */
    public void weaponIcon(Canvas c, float cx, float cy, float size, int id, int color) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
        float h = size * 0.5f;
        float bodyW = 0.5f + id * 0.12f;
        c.drawRect(cx - h * bodyW, cy - h * 0.16f, cx + h * 0.95f, cy + h * 0.12f, fill);
        c.drawRect(cx - h * bodyW * 0.7f, cy + h * 0.1f, cx - h * bodyW * 0.25f, cy + h * 0.7f, fill);
        if (id == 1 || id == 3) {
            c.drawRect(cx - h * 0.05f, cy + h * 0.12f, cx + h * 0.2f, cy + h * 0.65f, fill);
        }
        if (id == 4 || id == 2) {
            c.drawRect(cx - h * 0.1f, cy - h * 0.42f, cx + h * 0.45f, cy - h * 0.18f, fill);
        }
        if (id == 5) {
            c.drawRect(cx + h * 0.5f, cy - h * 0.32f, cx + h * 1.0f, cy + h * 0.28f, fill);
        }
    }
}
