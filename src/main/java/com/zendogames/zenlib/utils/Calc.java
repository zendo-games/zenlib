package com.zendogames.zenlib.utils;

import com.badlogic.gdx.math.MathUtils;

public class Calc {

    private Calc() {}

    /** Moves {@code value} toward {@code target} by at most {@code delta}, never overshooting. */
    public static float approach(float value, float target, float delta) {
        return (value < target) ? Math.min(value + delta, target) : Math.max(value - delta, target);
    }

    /** Clamps to {@code [min, max]}, swapping the bounds if they are reversed. */
    public static float clampf(float value, float min, float max) {
        if (max < min) {
            float tmp = min;
            min = max;
            max = tmp;
        }
        return value < min ? min
             : value > max ? max
             : value;
    }

    /**
     * Exponential smoothing: sppeed proportional to distance (ease-out), frame--rate independent.
     * Higher {@code lambda} responds faster; typical values are 5 to 25.
     */
    public static float smoothDamp(float current, float target, float lambda, float delta) {
        var t = 1f - (float) Math.exp(-lambda * delta);
        return MathUtils.lerp(current, target, t);
    }

    public static int   abs(int value)   { return (value < 0) ? -value : value; }
    public static float abs(float value) { return (value < 0) ? -value : value; }

    public static int   sign(int value)   { return (value < 0) ? -1 : (value > 0) ? 1 : 0; }
    public static float sign(float value) { return (value < 0) ? -1 : (value > 0) ? 1 : 0; }
}
