package zendo.games.zenlib.utils;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

public class Calc {

    public static float modf(float x, float m) {
        return x - (int) (x / m) * m;
    }

    public static int clampInt(int t, int min, int max) {
        if (t < min) return min;
        else if (t > max) return max;
        else return t;
    }

    public static float floor(float value) {
        return MathUtils.floor(value);
    }

    public static float ceiling(float value) {
        return MathUtils.ceil(value);
    }

    public static float min(float a, float b) {
        return (a < b) ? a : b;
    }

    public static float max(float a, float b) {
        return (a > b) ? a : b;
    }

    public static int min(int a, int b) {
        return (a < b) ? a : b;
    }

    public static int max(int a, int b) {
        return (a > b) ? a : b;
    }

    public static float approach(float t, float target, float delta) {
        return (t < target) ? min(t + delta, target) : max(t - delta, target);
    }

    public static int sign(int val) {
        return (val < 0) ? -1 : (val > 0) ? 1 : 0;
    }

    public static float sign(float val) {
        return (val < 0) ? -1 : (val > 0) ? 1 : 0;
    }

    public static int abs(int val) {
        return (val < 0) ? -val : val;
    }

    public static float abs(float val) {
        return (val < 0) ? -val : val;
    }

    public static float pow(float base, int exponent) {
        float r = base;
        for (int i = 0; i < exponent; i++) {
            r *= r;
        }
        return r;
    }

    /**
     * Exponential interpolation (better for multiplicative quantities, like zooming)
     */
    public static float eerp(float a, float b, float t) {
        return (float) (Math.pow(a, 1f - t) * Math.pow(b, t));
    }

    public static float clampf(float value, float min, float max) {
        if (max < min) {
            float temp = min;
            min = max;
            max = temp;
        }
        return value < min ? min : value > max ? max : value;
    }

    public static boolean between(float value, float min, float max) {
        if (min > max) {
            float temp = max;
            max = min;
            min = temp;
        }
        return (value >= min && value <= max);
    }

    public static float vectorToAngle(Vector2 vector) {
        return vectorToAngle(vector.x, vector.y);
    }

    public static float vectorToAngle(float x, float y) {
        return (float) Math.atan2(-x, y);
    }

    public static Vector2 angleToVector(Vector2 outVector, float angle) {
        outVector.x = -(float) Math.sin(angle);
        outVector.y = (float) Math.cos(angle);
        return outVector;
    }

    public static int randomBetween(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }
}
