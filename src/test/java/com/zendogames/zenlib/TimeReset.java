package com.zendogames.zenlib;

/**
 * Lets a test in another package put {@code Time} back to its initial state ({@code Time.init()} is
 * package-private): after a {@code Time.pauseFor}, which nothing but {@code Time.update} and a live
 * {@code Gdx.graphics} would otherwise end.
 */
public final class TimeReset {

    private TimeReset() {}

    public static void reset() {
        Time.init();
    }
}
