package com.zendogames.zenlib;

public class Zen {

    public static ZenConfig config;

    /** Call first thing in {@code ApplicationListener.create()}, before any other zenlib usage. */
    public static void init(ZenConfig config) {
        Zen.config = config;
        Time.init();
        Prefs.init(config.preferencesName);
        Flags.init();
        Log.info(Zen.class.getSimpleName(), "library initialized " + config);
    }
}
