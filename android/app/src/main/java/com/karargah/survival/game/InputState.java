package com.karargah.survival.game;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Dokunmatik girdinin oyun döngüsüne aktarıldığı paylaşımlı durum. */
public class InputState {
    public volatile float moveX, moveZ;
    public volatile boolean firing;
    public volatile boolean buildMode;
    public volatile int buildType = Balance.S_WALL;
    public volatile boolean paused;
    /** İnşa modunda parmağın altındaki hücre (hayalet önizleme için). */
    public volatile int hoverGx = -1;
    public volatile int hoverGz = -1;

    public final ConcurrentLinkedQueue<Cmd> commands = new ConcurrentLinkedQueue<>();

    private float camYawDelta, camPitchDelta, zoomDelta;

    public void push(Cmd c) {
        commands.add(c);
    }

    public synchronized void addCamDrag(float yaw, float pitch) {
        camYawDelta += yaw;
        camPitchDelta += pitch;
    }

    public synchronized void addZoom(float dz) {
        zoomDelta += dz;
    }

    /** out[0]=yaw, out[1]=pitch, out[2]=zoom; okunduktan sonra sıfırlanır. */
    public synchronized void consumeCamera(float[] out) {
        out[0] = camYawDelta;
        out[1] = camPitchDelta;
        out[2] = zoomDelta;
        camYawDelta = 0f;
        camPitchDelta = 0f;
        zoomDelta = 0f;
    }
}
