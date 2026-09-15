package com.karargah.survival.engine;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Tamamen kod içinde üretilen ses motoru: hiç ses dosyası yok. Dalga formları
 * açılışta sentezlenir, küçük bir yazılım mikseri AudioTrack'e yazar.
 */
public class Audio {
    private static final int RATE = 22050;
    private static final int MAX_VOICES = 24;

    public static final int SND_PISTOL = 0;
    public static final int SND_SMG = 1;
    public static final int SND_SHOTGUN = 2;
    public static final int SND_RIFLE = 3;
    public static final int SND_SNIPER = 4;
    public static final int SND_LAUNCH = 5;
    public static final int SND_EXPLOSION = 6;
    public static final int SND_HIT = 7;
    public static final int SND_THUD = 8;
    public static final int SND_CLICK = 9;
    public static final int SND_ERROR = 10;
    public static final int SND_BUILD = 11;
    public static final int SND_UPGRADE = 12;
    public static final int SND_GROWL = 13;
    public static final int SND_DIE = 14;
    public static final int SND_HORN = 15;
    public static final int SND_CHIME = 16;
    public static final int SND_TESLA = 17;
    public static final int SND_SPLAT = 18;
    public static final int SND_COUNT = 19;

    private final short[][] bank = new short[SND_COUNT][];

    private final short[][] voiceData = new short[MAX_VOICES][];
    private final float[] voicePos = new float[MAX_VOICES];
    private final float[] voiceRate = new float[MAX_VOICES];
    private final float[] voiceVol = new float[MAX_VOICES];
    private final boolean[] voiceOn = new boolean[MAX_VOICES];

    private AudioTrack track;
    private Thread mixThread;
    private volatile boolean running;
    private volatile boolean enabled = true;
    private float masterVolume = 0.8f;
    private long lastGrowl;

    public Audio() {
        synthesizeAll();
    }

    public void setEnabled(boolean on) {
        enabled = on;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setVolume(float v) {
        masterVolume = MathX.clamp(v, 0f, 1f);
    }

    public void start() {
        if (running) return;
        running = true;
        mixThread = new Thread(this::mixLoop, "GameAudio");
        mixThread.setPriority(Thread.MAX_PRIORITY - 2);
        mixThread.start();
    }

    public void stop() {
        running = false;
        Thread t = mixThread;
        mixThread = null;
        if (t != null) {
            try {
                t.join(400);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void mixLoop() {
        int frames = 512;
        int minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufBytes = Math.max(minBuf, frames * 2 * 4);
        AudioTrack at;
        try {
            at = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            at.play();
        } catch (Exception e) {
            running = false;
            return;
        }
        track = at;

        float[] mix = new float[frames];
        short[] out = new short[frames];
        while (running) {
            java.util.Arrays.fill(mix, 0f);
            synchronized (voiceOn) {
                for (int v = 0; v < MAX_VOICES; v++) {
                    if (!voiceOn[v]) continue;
                    short[] data = voiceData[v];
                    float pos = voicePos[v];
                    float rate = voiceRate[v];
                    float vol = voiceVol[v];
                    for (int i = 0; i < frames; i++) {
                        int idx = (int) pos;
                        if (idx >= data.length - 1) {
                            voiceOn[v] = false;
                            break;
                        }
                        float frac = pos - idx;
                        float s = data[idx] * (1f - frac) + data[idx + 1] * frac;
                        mix[i] += s * vol;
                        pos += rate;
                    }
                    voicePos[v] = pos;
                }
            }
            float m = masterVolume;
            for (int i = 0; i < frames; i++) {
                float s = mix[i] * m;
                // yumuşak kırpma
                if (s > 32000f) s = 32000f;
                if (s < -32000f) s = -32000f;
                out[i] = (short) s;
            }
            try {
                at.write(out, 0, frames);
            } catch (Exception e) {
                break;
            }
        }
        try {
            at.stop();
            at.release();
        } catch (Exception ignored) {
            // kapanışta önemli değil
        }
        track = null;
    }

    private void play(int id, float volume, float pitch) {
        if (!enabled || bank[id] == null) return;
        synchronized (voiceOn) {
            int slot = -1;
            for (int v = 0; v < MAX_VOICES; v++) {
                if (!voiceOn[v]) {
                    slot = v;
                    break;
                }
            }
            if (slot < 0) return;
            voiceData[slot] = bank[id];
            voicePos[slot] = 0f;
            voiceRate[slot] = MathX.clamp(pitch, 0.25f, 4f);
            voiceVol[slot] = volume;
            voiceOn[slot] = true;
        }
    }

    // ---- oyun olayları --------------------------------------------------

    public void playShot(int weapon) {
        switch (weapon) {
            case 1: play(SND_SMG, 0.32f, MathX.rnd(0.95f, 1.08f)); break;
            case 2: play(SND_SHOTGUN, 0.5f, MathX.rnd(0.94f, 1.06f)); break;
            case 3: play(SND_RIFLE, 0.38f, MathX.rnd(0.96f, 1.05f)); break;
            case 4: play(SND_SNIPER, 0.55f, MathX.rnd(0.97f, 1.03f)); break;
            case 5: play(SND_LAUNCH, 0.5f, MathX.rnd(0.95f, 1.05f)); break;
            default: play(SND_PISTOL, 0.34f, MathX.rnd(0.94f, 1.08f)); break;
        }
    }

    public void playTurret() {
        play(SND_SMG, 0.14f, MathX.rnd(1.1f, 1.35f));
    }

    public void playCannon() {
        play(SND_SHOTGUN, 0.34f, MathX.rnd(0.7f, 0.8f));
    }

    public void playSniper() {
        play(SND_SNIPER, 0.3f, MathX.rnd(0.9f, 1.0f));
    }

    public void playTesla() {
        play(SND_TESLA, 0.3f, MathX.rnd(0.9f, 1.2f));
    }

    public void playExplosion() {
        play(SND_EXPLOSION, 0.6f, MathX.rnd(0.85f, 1.12f));
    }

    public void playHit() {
        play(SND_HIT, 0.4f, MathX.rnd(0.9f, 1.15f));
    }

    public void playStructHit() {
        play(SND_THUD, 0.18f, MathX.rnd(0.85f, 1.25f));
    }

    public void playStructDown() {
        play(SND_EXPLOSION, 0.35f, MathX.rnd(0.6f, 0.75f));
    }

    public void playZombieDie(boolean boss) {
        play(SND_DIE, boss ? 0.7f : 0.3f, boss ? 0.55f : MathX.rnd(0.85f, 1.2f));
    }

    public void playGrowl(float x, float z) {
        long now = System.currentTimeMillis();
        if (now - lastGrowl < 900) return;
        lastGrowl = now;
        play(SND_GROWL, 0.22f, MathX.rnd(0.8f, 1.25f));
    }

    public void playSpit() {
        play(SND_SPLAT, 0.3f, MathX.rnd(1.1f, 1.4f));
    }

    public void playAcid() {
        play(SND_SPLAT, 0.35f, MathX.rnd(0.8f, 1.0f));
    }

    public void playEmpty() {
        play(SND_CLICK, 0.3f, 1.6f);
    }

    public void playNpcShot() {
        play(SND_RIFLE, 0.2f, MathX.rnd(1.02f, 1.18f));
    }

    public void playPickup() {
        play(SND_UPGRADE, 0.16f, MathX.rnd(1.5f, 1.9f));
    }

    public void playClick() {
        play(SND_CLICK, 0.22f, 1.2f);
    }

    public void playError() {
        play(SND_ERROR, 0.3f, 1f);
    }

    public void playBuild() {
        play(SND_BUILD, 0.36f, MathX.rnd(0.95f, 1.06f));
    }

    public void playSell() {
        play(SND_BUILD, 0.3f, 0.75f);
    }

    public void playUpgrade() {
        play(SND_UPGRADE, 0.4f, 1f);
    }

    public void playLevelUp() {
        play(SND_CHIME, 0.5f, 1f);
    }

    public void playWaveStart() {
        play(SND_HORN, 0.55f, 1f);
    }

    public void playWaveCleared() {
        play(SND_CHIME, 0.45f, 0.8f);
    }

    public void playPlayerDown() {
        play(SND_HORN, 0.45f, 0.55f);
    }

    public void playGameOver() {
        play(SND_HORN, 0.7f, 0.35f);
        play(SND_EXPLOSION, 0.8f, 0.5f);
    }

    // ---- sentez ---------------------------------------------------------

    private void synthesizeAll() {
        bank[SND_PISTOL] = gunshot(0.16f, 900f, 0.85f);
        bank[SND_SMG] = gunshot(0.10f, 1300f, 0.7f);
        bank[SND_SHOTGUN] = gunshot(0.34f, 420f, 1.0f);
        bank[SND_RIFLE] = gunshot(0.14f, 700f, 0.9f);
        bank[SND_SNIPER] = gunshot(0.42f, 260f, 1.0f);
        bank[SND_LAUNCH] = whoosh(0.45f);
        bank[SND_EXPLOSION] = explosion(0.9f);
        bank[SND_HIT] = impact(0.12f, 300f);
        bank[SND_THUD] = impact(0.09f, 150f);
        bank[SND_CLICK] = click();
        bank[SND_ERROR] = tone2(0.18f, 220f, 160f, 0.35f);
        bank[SND_BUILD] = tone2(0.22f, 420f, 640f, 0.3f);
        bank[SND_UPGRADE] = arpeggio(new float[]{523f, 659f, 784f}, 0.1f);
        bank[SND_GROWL] = growl(0.7f);
        bank[SND_DIE] = death(0.6f);
        bank[SND_HORN] = horn(1.5f);
        bank[SND_CHIME] = arpeggio(new float[]{659f, 784f, 988f, 1319f}, 0.13f);
        bank[SND_TESLA] = crackle(0.25f);
        bank[SND_SPLAT] = splat(0.22f);
    }

    private static int n(float seconds) {
        return Math.max(8, (int) (seconds * RATE));
    }

    private short[] gunshot(float dur, float bodyHz, float punch) {
        int len = n(dur);
        short[] s = new short[len];
        float phase = 0f;
        float lp = 0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.exp(-t * 16f);
            float noise = MathX.rnd(-1f, 1f);
            lp += (noise - lp) * 0.45f;
            phase += (bodyHz * (1f - t * 0.7f)) / RATE * MathX.TAU;
            float body = (float) Math.sin(phase) * punch * (float) Math.exp(-t * 26f);
            float v = (lp * 1.4f + body) * env;
            s[i] = (short) MathX.clamp(v * 22000f, -32000f, 32000f);
        }
        return s;
    }

    private short[] explosion(float dur) {
        int len = n(dur);
        short[] s = new short[len];
        float lp = 0f, lp2 = 0f, phase = 0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.exp(-t * 6.5f);
            float noise = MathX.rnd(-1f, 1f);
            lp += (noise - lp) * 0.16f;
            lp2 += (lp - lp2) * 0.22f;
            phase += (70f * (1f - t * 0.55f)) / RATE * MathX.TAU;
            float sub = (float) Math.sin(phase) * (float) Math.exp(-t * 8f);
            float v = (lp2 * 2.4f + sub * 0.9f) * env;
            s[i] = (short) MathX.clamp(v * 21000f, -32000f, 32000f);
        }
        return s;
    }

    private short[] whoosh(float dur) {
        int len = n(dur);
        short[] s = new short[len];
        float lp = 0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.sin(Math.PI * Math.min(1f, t * 1.4f)) * (float) Math.exp(-t * 3f);
            float noise = MathX.rnd(-1f, 1f);
            float k = 0.05f + t * 0.35f;
            lp += (noise - lp) * k;
            s[i] = (short) MathX.clamp(lp * env * 16000f, -32000f, 32000f);
        }
        return s;
    }

    private short[] impact(float dur, float hz) {
        int len = n(dur);
        short[] s = new short[len];
        float phase = 0f, lp = 0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.exp(-t * 20f);
            phase += (hz * (1f - t * 0.6f)) / RATE * MathX.TAU;
            float noise = MathX.rnd(-1f, 1f);
            lp += (noise - lp) * 0.3f;
            float v = ((float) Math.sin(phase) * 0.8f + lp * 0.7f) * env;
            s[i] = (short) MathX.clamp(v * 19000f, -32000f, 32000f);
        }
        return s;
    }

    private short[] click() {
        int len = n(0.05f);
        short[] s = new short[len];
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.exp(-t * 40f);
            s[i] = (short) (MathX.rnd(-1f, 1f) * env * 12000f);
        }
        return s;
    }

    private short[] tone2(float dur, float hz0, float hz1, float decay) {
        int len = n(dur);
        short[] s = new short[len];
        float phase = 0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float hz = MathX.lerp(hz0, hz1, t);
            phase += hz / RATE * MathX.TAU;
            float env = (float) Math.exp(-t / Math.max(0.01f, decay)) * (1f - t * 0.2f);
            float v = (float) Math.sin(phase) * 0.7f + (float) Math.sin(phase * 2f) * 0.2f;
            s[i] = (short) MathX.clamp(v * env * 15000f, -32000f, 32000f);
        }
        return s;
    }

    private short[] arpeggio(float[] notes, float noteDur) {
        int per = n(noteDur);
        short[] s = new short[per * notes.length];
        for (int k = 0; k < notes.length; k++) {
            float phase = 0f;
            for (int i = 0; i < per; i++) {
                float t = (float) i / per;
                phase += notes[k] / RATE * MathX.TAU;
                float env = (float) Math.exp(-t * 4.5f);
                float v = (float) Math.sin(phase) * 0.6f + (float) Math.sin(phase * 2.01f) * 0.25f;
                int idx = k * per + i;
                s[idx] = (short) MathX.clamp(v * env * 13000f, -32000f, 32000f);
            }
        }
        return s;
    }

    private short[] growl(float dur) {
        int len = n(dur);
        short[] s = new short[len];
        float phase = 0f, lp = 0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.sin(Math.PI * Math.min(1f, t * 1.15f));
            float vib = 1f + 0.22f * (float) Math.sin(t * 40f);
            phase += (78f * vib) / RATE * MathX.TAU;
            float noise = MathX.rnd(-1f, 1f);
            lp += (noise - lp) * 0.08f;
            float saw = (phase % MathX.TAU) / MathX.TAU * 2f - 1f;
            float v = (saw * 0.55f + lp * 1.2f) * env;
            s[i] = (short) MathX.clamp(v * 14000f, -32000f, 32000f);
        }
        return s;
    }

    private short[] death(float dur) {
        int len = n(dur);
        short[] s = new short[len];
        float phase = 0f, lp = 0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.exp(-t * 3.2f);
            phase += (150f * (1f - t * 0.75f)) / RATE * MathX.TAU;
            float noise = MathX.rnd(-1f, 1f);
            lp += (noise - lp) * 0.12f;
            float v = ((float) Math.sin(phase) * 0.5f + lp * 1.3f) * env;
            s[i] = (short) MathX.clamp(v * 15000f, -32000f, 32000f);
        }
        return s;
    }

    private short[] horn(float dur) {
        int len = n(dur);
        short[] s = new short[len];
        float p1 = 0f, p2 = 0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.min(1f, t * 8f) * (float) Math.exp(-t * 2.2f);
            p1 += 110f / RATE * MathX.TAU;
            p2 += 165f / RATE * MathX.TAU;
            float v = (float) Math.sin(p1) * 0.6f + (float) Math.sin(p2) * 0.35f
                    + (float) Math.sin(p1 * 0.5f) * 0.3f;
            s[i] = (short) MathX.clamp(v * env * 14000f, -32000f, 32000f);
        }
        return s;
    }

    private short[] crackle(float dur) {
        int len = n(dur);
        short[] s = new short[len];
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.exp(-t * 9f);
            float v = MathX.rnd(-1f, 1f);
            if (MathX.chance(0.55f)) v *= 0.15f;
            s[i] = (short) MathX.clamp(v * env * 16000f, -32000f, 32000f);
        }
        return s;
    }

    private short[] splat(float dur) {
        int len = n(dur);
        short[] s = new short[len];
        float lp = 0f;
        for (int i = 0; i < len; i++) {
            float t = (float) i / len;
            float env = (float) Math.exp(-t * 12f);
            float noise = MathX.rnd(-1f, 1f);
            lp += (noise - lp) * (0.5f - t * 0.4f);
            s[i] = (short) MathX.clamp(lp * env * 17000f, -32000f, 32000f);
        }
        return s;
    }
}
