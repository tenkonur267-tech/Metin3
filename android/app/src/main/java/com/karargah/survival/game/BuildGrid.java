package com.karargah.survival.game;

import com.karargah.survival.engine.MathX;

/** Yapıların yerleştiği kare ızgara ve yerleştirme kuralları. */
public class BuildGrid {
    public static final int N = Balance.GRID;

    private final Structure[] cells = new Structure[N * N];

    public static int worldToCell(float w) {
        return (int) Math.floor((w + Balance.WORLD_HALF) / Balance.CELL);
    }

    public static float cellToWorld(int c) {
        return c * Balance.CELL - Balance.WORLD_HALF + Balance.CELL * 0.5f;
    }

    public static boolean inBounds(int gx, int gz) {
        return gx >= 0 && gz >= 0 && gx < N && gz < N;
    }

    public int index(int gx, int gz) {
        return gz * N + gx;
    }

    public Structure at(int gx, int gz) {
        if (!inBounds(gx, gz)) return null;
        Structure s = cells[index(gx, gz)];
        return (s != null && s.alive) ? s : null;
    }

    public Structure atWorld(float x, float z) {
        return at(worldToCell(x), worldToCell(z));
    }

    public void set(int gx, int gz, Structure s) {
        if (!inBounds(gx, gz)) return;
        cells[index(gx, gz)] = s;
    }

    public void clear(int gx, int gz) {
        if (!inBounds(gx, gz)) return;
        cells[index(gx, gz)] = null;
    }

    public void clearAll() {
        for (int i = 0; i < cells.length; i++) cells[i] = null;
    }

    /** Hücrenin merkezi inşa alanı içinde mi? */
    public static boolean inBuildArea(int gx, int gz) {
        float x = cellToWorld(gx), z = cellToWorld(gz);
        return MathX.len(x, z) <= Balance.BUILD_RADIUS;
    }

    /** Reaktörün kapladığı hücreler (merkezde CORE_CELLS x CORE_CELLS). */
    public static boolean isCoreCell(int gx, int gz) {
        int c0 = N / 2 - Balance.CORE_CELLS / 2;
        int c1 = c0 + Balance.CORE_CELLS - 1;
        return gx >= c0 && gx <= c1 && gz >= c0 && gz <= c1;
    }

    public static final int OK = 0;
    public static final int ERR_BOUNDS = 1;
    public static final int ERR_OCCUPIED = 2;
    public static final int ERR_CORE = 3;
    public static final int ERR_AREA = 4;

    public int canPlace(int gx, int gz) {
        if (!inBounds(gx, gz)) return ERR_BOUNDS;
        if (isCoreCell(gx, gz)) return ERR_CORE;
        if (!inBuildArea(gx, gz)) return ERR_AREA;
        if (at(gx, gz) != null) return ERR_OCCUPIED;
        return OK;
    }

    public static String placeError(int code) {
        switch (code) {
            case ERR_BOUNDS: return "Harita dışı";
            case ERR_OCCUPIED: return "Burada zaten bir yapı var";
            case ERR_CORE: return "Reaktörün üstüne inşa edilemez";
            case ERR_AREA: return "İnşa alanının dışı";
            default: return "";
        }
    }
}
