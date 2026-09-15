package com.karargah.survival.game;

/** Arayüz iş parçacığından oyun döngüsüne gönderilen komut. */
public class Cmd {
    public static final int RELOAD = 1;
    public static final int DASH = 2;
    public static final int SWITCH_WEAPON = 3;
    public static final int PLACE = 4;
    public static final int SELECT = 5;
    public static final int UPGRADE_SELECTED = 6;
    public static final int SELL_SELECTED = 7;
    public static final int REPAIR_SELECTED = 8;
    public static final int REPAIR_ALL = 9;
    public static final int START_WAVE = 10;
    public static final int SKILL_UP = 11;
    public static final int WEAPON_UP = 12;
    public static final int BUY_WEAPON = 13;
    public static final int RESTART = 14;
    public static final int SAVE = 15;
    public static final int DESELECT = 16;
    public static final int BUY_AMMO = 17;
    public static final int ROTATE = 18;
    public static final int RECRUIT = 19;
    public static final int ORDER = 20;
    public static final int ORDER_AT = 21;
    public static final int ORDER_ALL = 22;
    public static final int CLEAR_ADVICE = 23;
    public static final int DUTY = 24;
    public static final int PLAN = 25;
    public static final int CANCEL_PLAN = 26;
    public static final int CANCEL_ALL_PLANS = 27;
    public static final int TOGGLE_AUTOBUILD = 28;

    public final int op;
    public final int a, b, c;

    public Cmd(int op) {
        this(op, 0, 0, 0);
    }

    public Cmd(int op, int a) {
        this(op, a, 0, 0);
    }

    public Cmd(int op, int a, int b, int c) {
        this.op = op;
        this.a = a;
        this.b = b;
        this.c = c;
    }
}
