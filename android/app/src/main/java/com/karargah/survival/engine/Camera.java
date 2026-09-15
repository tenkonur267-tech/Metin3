package com.karargah.survival.engine;

/** Hedefi yumuşak takip eden üçüncü şahıs yörünge kamerası. */
public class Camera {
    public float targetX, targetY = 1.1f, targetZ;
    public float focusX, focusY = 1.1f, focusZ;
    public float yaw = 0.6f;
    public float pitch = 0.62f;
    public float distance = 13f;
    public float fov = 58f;
    public float near = 0.35f;
    public float far = 520f;

    public float eyeX, eyeY, eyeZ;
    public final float[] view = new float[16];
    public final float[] proj = new float[16];
    public final float[] viewProj = new float[16];

    private float shake, shakeDecay = 3.4f;
    private float aspect = 1.6f;

    public void setAspect(int w, int h) {
        aspect = (float) w / Math.max(1, h);
    }

    public void snapToTarget() {
        focusX = targetX;
        focusY = targetY;
        focusZ = targetZ;
    }

    public void addShake(float amount) {
        shake = Math.min(1.3f, shake + amount);
    }

    public void update(float dt) {
        focusX = MathX.damp(focusX, targetX, 9f, dt);
        focusY = MathX.damp(focusY, targetY, 7f, dt);
        focusZ = MathX.damp(focusZ, targetZ, 9f, dt);
        if (shake > 0f) {
            shake = Math.max(0f, shake - shakeDecay * dt);
        }

        pitch = MathX.clamp(pitch, 0.18f, 1.40f);
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        float ex = focusX - (float) Math.sin(yaw) * cp * distance;
        float ez = focusZ - (float) Math.cos(yaw) * cp * distance;
        float ey = focusY + sp * distance;

        float s = shake * shake * 0.55f;
        if (s > 0.0001f) {
            ex += MathX.rnd(-s, s);
            ey += MathX.rnd(-s, s);
            ez += MathX.rnd(-s, s);
        }

        eyeX = ex;
        eyeY = Math.max(0.6f, ey);
        eyeZ = ez;

        M4.perspective(proj, fov, aspect, near, far);
        M4.lookAt(view, eyeX, eyeY, eyeZ, focusX, focusY, focusZ);
        M4.mul(viewProj, proj, view);
    }

    /** Kameranın baktığı yön (XZ düzleminde, ileri). */
    public float forwardX() {
        return (float) Math.sin(yaw);
    }

    public float forwardZ() {
        return (float) Math.cos(yaw);
    }
}
