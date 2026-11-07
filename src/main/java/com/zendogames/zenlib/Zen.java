package com.zendogames.zenlib;

import com.zendogames.zenlib.audio.Audio;
import com.zendogames.zenlib.events.Signals;

public class Zen {

    public static ZenConfig config;

    /** Call first thing in {@code ApplicationListener.create()}, before any other zenlib usage. */
    public static void init(ZenConfig config) {
        Zen.config = config;
        Time.init();
        Prefs.init(config.preferencesName());
        Flags.init();
        FramePool.init();
        Audio.init();
        Signals.init();
        Log.info(Zen.class.getSimpleName(), "library initialized " + config);
    }
}
