package com.zendogames.zenlib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.TimeUtils;

public class Time {

    /** Longest frame the game will simulate, longer frames are treated as this duration. */
    private static final float MAX_DELTA = 1f / 30f;

    /** Seconds since the previous frame, clamped. Zero while paused by {@link #pauseFor}*/
    public static float delta = 0;

    /** Seconds of unpaused time since startup. {@code double} value because {@code float} loses sub-frame precision after around a day of uptime. */
    public static double elapsed = 0;

    private static long startMillis = 0;
    private static float pauseRemaining = 0;

    static void init() {
        startMillis = TimeUtils.millis();
        delta = 0;
        elapsed = 0;
        pauseRemaining = 0;
    }

    /** Call once at the start of each frame. */
    public static void update() {
        delta = Math.min(MAX_DELTA, Gdx.graphics.getDeltaTime());

        if (pauseRemaining > 0) {
            pauseRemaining -= delta;
            // The frame that ends a pause keeps only the time left over after it
            delta = (pauseRemaining < 0) ? -pauseRemaining : 0;
        }

        elapsed += delta;
    }

    /** Freezes {@link #delta} at zero for a duration, eg. on-hit. Longer of overlapping pauses wins. */
    public static void pauseFor(float seconds) {
        pauseRemaining = Math.max(pauseRemaining, seconds);
    }

    public static boolean isPaused() {
        return (pauseRemaining > 0);
    }

    /** Wall-clock seconds since startup, unaffected by pauses or clamping. */
    public static float secondsSinceStart() {
        return TimeUtils.timeSinceMillis(startMillis) / 1_000f;
    }

    /** True on the one frame where {@link #elapsed} crosses a multiple of {@code interval}. */
    public static boolean onInterval(float interval, float offset) {
        return Math.floor((elapsed - offset - delta) / interval) < Math.floor((elapsed - offset) / interval);
    }

    /** Alternatives false/true every {@code interval} seconds, eg. for blinking. */
    public static boolean betweenInterval(float interval, float offset) {
        return ((elapsed - offset) % (2.0 * interval)) >= interval;
    }
}
