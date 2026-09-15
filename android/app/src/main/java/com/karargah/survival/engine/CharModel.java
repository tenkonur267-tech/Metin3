package com.karargah.survival.engine;

/** Kemikli karakter modeli: mesh + her kemiğin dönme merkezi. */
public class CharModel {
    public final Mesh mesh;
    /** Her kemik için [x,y,z] dönme merkezi (model uzayında). */
    public final float[][] pivots;
    public final float height;

    public CharModel(Mesh mesh, float[][] pivots, float height) {
        this.mesh = mesh;
        this.pivots = pivots;
        this.height = height;
    }
}
