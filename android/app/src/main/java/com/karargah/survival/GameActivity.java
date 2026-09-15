package com.karargah.survival;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.karargah.survival.engine.Audio;
import com.karargah.survival.game.GameWorld;
import com.karargah.survival.game.InputState;
import com.karargah.survival.game.SaveStore;
import com.karargah.survival.ui.HudView;

/** Tek etkinlik: GL yüzeyi + üstünde dokunmatik arayüz. */
public class GameActivity extends Activity implements HudView.Listener {
    private InputState input;
    private Audio audio;
    private GameWorld world;
    private GameSurface surface;
    private HudView hud;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        goFullscreen();

        input = new InputState();
        audio = new Audio();
        audio.setEnabled(SaveStore.soundOn(this));
        world = new GameWorld(audio, input);
        world.waveRecord = SaveStore.bestWave(this);
        world.paused = true;

        hud = new HudView(this, input);
        hud.setWorld(world);
        hud.setListener(this);
        hud.setScreen(HudView.SCREEN_MENU);

        surface = new GameSurface(this, world, hud, input);

        FrameLayout root = new FrameLayout(this);
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hud, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void goFullscreen() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goFullscreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        audio.start();
        surface.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (hud.screen() == HudView.SCREEN_GAME) {
            hud.setScreen(HudView.SCREEN_PAUSE);
        }
        // Önce simülasyonu durdur: kayıt alınırken listeler değişmesin.
        world.paused = true;
        SaveStore.save(this, world);
        audio.stop();
        surface.onPause();
    }

    @Override
    public void onBackPressed() {
        int s = hud.screen();
        if (s == HudView.SCREEN_GAME) {
            hud.setScreen(HudView.SCREEN_PAUSE);
        } else if (s == HudView.SCREEN_MENU) {
            super.onBackPressed();
        } else if (s == HudView.SCREEN_GAMEOVER) {
            hud.setScreen(HudView.SCREEN_MENU);
        } else {
            hud.setScreen(HudView.SCREEN_GAME);
        }
    }

    // ---- HudView.Listener ----------------------------------------------

    @Override
    public void onNewGame() {
        SaveStore.clear(this);
        surface.queueEvent(() -> {
            world.newGame();
            world.waveRecord = SaveStore.bestWave(this);
            world.paused = false;
        });
    }

    @Override
    public void onContinue() {
        surface.queueEvent(() -> {
            if (!SaveStore.load(this, world)) {
                world.newGame();
            }
            world.paused = false;
        });
    }

    @Override
    public void onQuit() {
        SaveStore.save(this, world);
        finish();
    }

    @Override
    public boolean hasSave() {
        return SaveStore.hasSave(this);
    }

    @Override
    public void onSaveRequested() {
        SaveStore.save(this, world);
    }

    @Override
    public boolean isSoundOn() {
        return audio.isEnabled();
    }

    @Override
    public void setSoundOn(boolean on) {
        audio.setEnabled(on);
        SaveStore.setSoundOn(this, on);
    }
}
