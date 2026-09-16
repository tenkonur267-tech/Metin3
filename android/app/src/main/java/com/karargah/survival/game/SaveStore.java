package com.karargah.survival.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Oyunun kaydedilmesi/yüklenmesi. Durum JSON olarak SharedPreferences'a yazılır.
 * Dalga ortasında kaydedilirse zombiler atılır ve hazırlık aşamasına dönülür.
 */
public final class SaveStore {
    private static final String TAG = "SaveStore";
    private static final String PREFS = "son_karargah";
    private static final String KEY_STATE = "state";
    private static final String KEY_RECORD = "record";
    private static final String KEY_SOUND = "sound";
    private static final int VERSION = 1;

    private SaveStore() {}

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean hasSave(Context ctx) {
        return prefs(ctx).contains(KEY_STATE);
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().remove(KEY_STATE).apply();
    }

    public static int bestWave(Context ctx) {
        return prefs(ctx).getInt(KEY_RECORD, 0);
    }

    public static boolean soundOn(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SOUND, true);
    }

    public static void setSoundOn(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_SOUND, on).apply();
    }

    public static void save(Context ctx, GameWorld w) {
        if (w == null || w.gameOver) {
            prefs(ctx).edit().remove(KEY_STATE)
                    .putInt(KEY_RECORD, Math.max(bestWave(ctx), w == null ? 0 : w.waveRecord))
                    .apply();
            return;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("v", VERSION);
            root.put("wave", w.waves.wave);
            root.put("record", w.waveRecord);
            root.put("kills", w.totalKills);
            root.put("time", w.playTime);
            root.put("dayTime", w.openWorld.timeOfDay);
            root.put("built", w.structuresBuilt);
            root.put("lost", w.structuresLost);

            Player p = w.player;
            JSONObject jp = new JSONObject();
            jp.put("hp", p.hp);
            jp.put("level", p.level);
            jp.put("xp", p.xp);
            jp.put("sp", p.skillPoints);
            jp.put("scrap", p.scrap);
            jp.put("cores", p.cores);
            jp.put("kills", p.kills);
            jp.put("deaths", p.deaths);
            jp.put("weapon", p.currentWeapon);
            jp.put("skills", toArray(p.skills));
            JSONArray weapons = new JSONArray();
            for (int i = 0; i < Balance.WEAPONS.length; i++) {
                JSONObject jw = new JSONObject();
                jw.put("u", p.unlocked[i]);
                jw.put("l", p.weaponLevel[i]);
                jw.put("m", p.magazine[i]);
                jw.put("r", p.reserve[i]);
                weapons.put(jw);
            }
            jp.put("weapons", weapons);
            root.put("player", jp);

            JSONArray arr = new JSONArray();
            for (int i = 0; i < w.structures.size(); i++) {
                Structure s = w.structures.get(i);
                if (!s.alive) continue;
                JSONObject js = new JSONObject();
                js.put("t", s.type);
                js.put("l", s.level);
                js.put("x", s.gx);
                js.put("z", s.gz);
                js.put("hp", s.hp);
                js.put("r", s.rotation);
                arr.put(js);
            }
            root.put("structures", arr);

            JSONArray npcArr = new JSONArray();
            for (int i = 0; i < w.npcs.size(); i++) {
                Npc n = w.npcs.get(i);
                JSONObject jn = new JSONObject();
                jn.put("role", n.role);
                jn.put("lvl", n.level);
                jn.put("name", n.name);
                jn.put("hp", n.hp);
                jn.put("stance", n.stance);
                jn.put("duties", n.duties);
                jn.put("ox", n.orderX);
                jn.put("oz", n.orderZ);
                jn.put("col", n.collected);
                jn.put("built", n.built);
                jn.put("wlvl", n.weaponLevel);
                jn.put("imp", n.selfImprove);
                jn.put("x", n.x);
                jn.put("z", n.z);
                npcArr.put(jn);
            }
            root.put("npcs", npcArr);

            JSONArray planArr = new JSONArray();
            for (int i = 0; i < w.plans.size(); i++) {
                BuildPlan bp = w.plans.get(i);
                if (bp.isUpgrade()) continue;   // yapı referansı taşır, yeniden üretilir
                JSONObject jpl = new JSONObject();
                jpl.put("t", bp.type);
                jpl.put("x", bp.gx);
                jpl.put("z", bp.gz);
                jpl.put("r", bp.rotation);
                jpl.put("p", bp.progress);
                jpl.put("paid", bp.paid);
                jpl.put("auto", bp.auto);
                planArr.put(jpl);
            }
            root.put("plans", planArr);
            root.put("autoRebuild", w.autoRebuild);

            prefs(ctx).edit()
                    .putString(KEY_STATE, root.toString())
                    .putInt(KEY_RECORD, Math.max(bestWave(ctx), w.waveRecord))
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Kayıt başarısız", e);
        }
    }

    private static JSONArray toArray(int[] values) {
        JSONArray a = new JSONArray();
        for (int v : values) a.put(v);
        return a;
    }

    /** @return true ise yükleme başarılı. */
    public static boolean load(Context ctx, GameWorld w) {
        String json = prefs(ctx).getString(KEY_STATE, null);
        if (json == null) return false;
        try {
            JSONObject root = new JSONObject(json);
            w.newGame();
            w.structures.clear();
            w.grid.clearAll();
            w.zombies.clear();

            w.waveRecord = root.optInt("record", 0);
            w.totalKills = root.optInt("kills", 0);
            w.playTime = (float) root.optDouble("time", 0);
            w.openWorld.timeOfDay = (float) root.optDouble("dayTime", 0.30);
            w.structuresBuilt = root.optInt("built", 0);
            w.structuresLost = root.optInt("lost", 0);

            int wave = root.optInt("wave", 0);
            w.waves.reset();
            w.waves.wave = wave;
            w.waves.phase = WaveManager.PHASE_PREPARE;
            w.waves.timer = Balance.buildTime(wave + 1);
            w.waves.activeSpawnPoints = Math.max(3, Math.min(WaveManager.MAX_SPAWN_POINTS,
                    2 + (wave + 2) / 3));

            Player p = w.player;
            JSONObject jp = root.getJSONObject("player");
            p.level = jp.optInt("level", 1);
            p.xp = jp.optInt("xp", 0);
            p.skillPoints = jp.optInt("sp", 0);
            p.scrap = jp.optInt("scrap", 200);
            p.cores = jp.optInt("cores", 0);
            p.kills = jp.optInt("kills", 0);
            p.deaths = jp.optInt("deaths", 0);
            JSONArray sk = jp.optJSONArray("skills");
            if (sk != null) {
                for (int i = 0; i < Math.min(sk.length(), p.skills.length); i++) {
                    p.skills[i] = sk.optInt(i, 0);
                }
            }
            JSONArray weapons = jp.optJSONArray("weapons");
            if (weapons != null) {
                for (int i = 0; i < Math.min(weapons.length(), Balance.WEAPONS.length); i++) {
                    JSONObject jw = weapons.getJSONObject(i);
                    p.unlocked[i] = jw.optBoolean("u", i == Balance.W_PISTOL);
                    p.weaponLevel[i] = Math.max(1, jw.optInt("l", 1));
                    p.magazine[i] = jw.optInt("m", Balance.weapon(i).magazine);
                    p.reserve[i] = jw.optInt("r", 0);
                }
            }
            p.currentWeapon = Math.max(0, Math.min(Balance.WEAPONS.length - 1,
                    jp.optInt("weapon", Balance.W_PISTOL)));
            if (!p.unlocked[p.currentWeapon]) p.currentWeapon = Balance.W_PISTOL;
            p.maxHp = p.maxHp();
            p.hp = Math.min(p.maxHp, (float) jp.optDouble("hp", p.maxHp));
            if (p.hp <= 1f) p.hp = p.maxHp * 0.5f;
            p.alive = true;

            // yapılar
            JSONArray arr = root.optJSONArray("structures");
            w.createCoreForLoad();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject js = arr.getJSONObject(i);
                    int type = js.optInt("t", Balance.S_WALL);
                    if (type == Balance.S_CORE) {
                        if (w.core != null) {
                            w.core.level = js.optInt("l", 1);
                            w.core.maxHp = w.core.def().hpAt(w.core.level);
                            w.core.hp = Math.min(w.core.maxHp, (float) js.optDouble("hp", w.core.maxHp));
                        }
                        continue;
                    }
                    Structure s = w.placeFree(type, js.optInt("x", 0), js.optInt("z", 0),
                            Math.max(1, js.optInt("l", 1)), js.optInt("r", 0));
                    if (s != null) {
                        s.hp = Math.min(s.maxHp, (float) js.optDouble("hp", s.maxHp));
                        s.buildAnim = 0f;
                    }
                }
            }
            w.player.x = 0f;
            w.player.z = 7f;
            w.camera.targetX = 0f;
            w.camera.targetZ = 7f;
            w.camera.snapToTarget();
            JSONArray npcArr = root.optJSONArray("npcs");
            if (npcArr != null) {
                for (int i = 0; i < npcArr.length(); i++) {
                    JSONObject jn = npcArr.getJSONObject(i);
                    Npc n = new Npc();
                    n.init(jn.optInt("role", 0), Math.max(1, jn.optInt("lvl", 1)), i,
                            (float) jn.optDouble("x", 0), (float) jn.optDouble("z", 6));
                    String nm = jn.optString("name", null);
                    if (nm != null && !nm.isEmpty()) n.name = nm;
                    n.hp = Math.min(n.maxHp, (float) jn.optDouble("hp", n.maxHp));
                    if (n.hp <= 1f) n.hp = n.maxHp * 0.6f;
                    n.setStance(jn.optInt("stance", Balance.STANCE_FOLLOW),
                            (float) jn.optDouble("ox", 0), (float) jn.optDouble("oz", 0));
                    n.duties = jn.optInt("duties", Balance.defaultDuties(n.role));
                    n.collected = jn.optInt("col", 0);
                    n.built = jn.optInt("built", 0);
                    n.weaponLevel = Math.max(1, jn.optInt("wlvl", 1));
                    n.selfImprove = jn.optBoolean("imp", true);
                    w.npcs.add(n);
                }
            }
            JSONArray planArr = root.optJSONArray("plans");
            if (planArr != null) {
                for (int i = 0; i < planArr.length(); i++) {
                    JSONObject jpl = planArr.getJSONObject(i);
                    BuildPlan bp = new BuildPlan(jpl.optInt("t", Balance.S_WALL),
                            jpl.optInt("x", 0), jpl.optInt("z", 0), jpl.optInt("r", 0),
                            jpl.optBoolean("auto", false));
                    bp.progress = (float) jpl.optDouble("p", 0);
                    bp.paid = jpl.optBoolean("paid", false);
                    if (w.grid.canPlace(bp.gx, bp.gz) == BuildGrid.OK) w.plans.add(bp);
                }
            }
            w.autoRebuild = root.optBoolean("autoRebuild", true);
            w.advisor.reset();
            w.refreshAllWalls();
            w.flow.compute(w.grid);
            w.message("Kayıt yüklendi — " + (wave + 1) + ". dalgaya hazırlan", 3.5f);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Kayıt okunamadı", e);
            return false;
        }
    }
}
