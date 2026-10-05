package com.zendogames.zenlib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.TimeUtils;

import static com.zendogames.zenlib.ZenFlag.*;

public class Log {

    public static void info(String tag, String msg, Object... args) {
        if (LOG_INFO.off()) return;
        Gdx.app.log(stamp(), tag + ": " + format(msg, args));
    }

    /** Logs only while {@code channel} is enabled, eg. a game's LOG_SENSOR flag. */
    public static void info(Flag channel, String tag, String msg, Object... args) {
        if (channel.off()) return;
        info(tag, msg, args);
    }

    public static void debug(String tag, String msg, Object... args) {
        if (LOG_DEBUG.off()) return;
        Gdx.app.log(stamp(), tag + ": " + format(msg, args));
    }

    public static void warn(String tag, String msg, Object... args) {
        if (LOG_WARN.off()) return;
        Gdx.app.error(stamp(), tag + ": WARN: " + format(msg, args));
    }

    /** Errors are never gated by a flag. */
    public static void error(String tag, String msg, Object... args) {
        Gdx.app.error(stamp(), tag + ": ERROR: " + format(msg, args));
    }

    /**
     * Produce a consistent timestamp string. libGDX prints its 'tag' argument first,
     * so the stamp goes there to keep lines aligned: "[   12.345 #742] Flag: SHOW_IMGUI=true"
     */
    private static String stamp() {
        return "%8.3f #%d".formatted(Time.secondsSinceStart(), Gdx.graphics.getFrameId());
    }

    private static String format(String msg, Object... args) {
        return (args.length == 0) ? msg : msg.formatted(args);
    }
}
