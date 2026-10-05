package com.zendogames.zenlib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public class Prefs {

    private static Preferences prefs;

    static void init(String preferencesName) {
        Prefs.prefs = Gdx.app.getPreferences(preferencesName);
    }

    public static boolean get(String key, boolean fallback) { return prefs.getBoolean(key, fallback); }
    public static float   get(String key, float fallback)   { return prefs.getFloat(key, fallback); }
    public static String  get(String key, String fallback)  { return prefs.getString(key, fallback); }

    // Writes are flushed immediately because preferences are small and infrequently written
    public static void put(String key, boolean value) { prefs.putBoolean(key, value).flush(); }
    public static void put(String key, float value)   { prefs.putFloat(key, value).flush(); }
    public static void put(String key, String value)  { prefs.putString(key, value).flush(); }
}
