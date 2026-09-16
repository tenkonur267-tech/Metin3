package com.karargah.survival;

import android.content.Context;
import android.opengl.GLSurfaceView;

import com.karargah.survival.game.GameWorld;
import com.karargah.survival.game.InputState;
import com.karargah.survival.game.WorldRenderer;
import com.karargah.survival.ui.HudView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** OpenGL yüzeyi: oyun döngüsü ve çizim bu iş parçacığında koşar. */
public class GameSurface extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final GameWorld world;
    private final HudView hud;
    private final InputState input;
    private WorldRenderer renderer;
    private long lastNanos;
    private boolean gameOverNotified;

    private final android.content.res.AssetManager assets;

    public GameSurface(Context ctx, GameWorld world, HudView hud, InputState input) {
        super(ctx);
        this.assets = ctx.getAssets();
        this.world = world;
        this.hud = hud;
        this.input = input;
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Bağlam kaybolmuş olabilir: tüm GL kaynaklarını yeniden kur.
        world.particles.disposeGl();
        renderer = new WorldRenderer();
        renderer.init(assets);
        lastNanos = System.nanoTime();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (renderer == null) return;
        renderer.resize(width, height);
        world.camera.setAspect(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (renderer == null) return;
        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1.0e9f;
        lastNanos = now;
        if (dt > 0.08f) dt = 0.08f;
        if (dt < 0f) dt = 0f;

        world.update(dt);
        renderer.render(world, input.buildMode);
        hud.publishCamera(renderer.renderer().viewProj, renderer.renderer().invViewProj,
                renderer.renderer().viewportW, renderer.renderer().viewportH);

        if (world.gameOver && !gameOverNotified) {
            gameOverNotified = true;
            hud.post(() -> hud.setScreen(HudView.SCREEN_GAMEOVER));
        } else if (!world.gameOver) {
            gameOverNotified = false;
        }
    }
}
