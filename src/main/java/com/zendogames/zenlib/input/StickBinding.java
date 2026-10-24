package com.zendogames.zenlib.input;

import com.badlogic.gdx.math.Vector2;

/**
 * Two {@link AxisBinding}s as a stick (blah's {@code StickBinding}). <b>{@link #y} is up-positive</b>,
 * like {@code Input.moveY} and the world: {@code W}, {@code DPAD_UP} and a stick pushed up all read
 * {@code signY() == +1}, so {@link #leftStick} wires the libGDX axis (up is negative there) backwards
 * on purpose. {@link #roundThreshold} is a radial deadzone on {@link #value}.
 */
public final class StickBinding {

    public final AxisBinding x = new AxisBinding();
    /** Up-positive. */
    public final AxisBinding y = new AxisBinding();
    /** A {@link #value} shorter than this reads as zero; 0 disables it. */
    public float roundThreshold = 0;

    private final Vector2 tmp = new Vector2();

    // ------------------------------------------------------------------------
    // Sources
    // ------------------------------------------------------------------------

    public StickBinding keys(int left, int right, int up, int down) {
        x.keys(left, right);
        y.keys(down, up);
        return this;
    }

    public StickBinding dpad(int gamepad) {
        x.pad(gamepad, Pad.DPAD_LEFT, Pad.DPAD_RIGHT);
        y.pad(gamepad, Pad.DPAD_DOWN, Pad.DPAD_UP);
        return this;
    }

    /** The left stick past {@code threshold}; libGDX reports up as negative, so LEFT_Y's negative side feeds {@code y.positive}. */
    public StickBinding leftStick(int gamepad, float threshold) {
        return stick(gamepad, PadAxis.LEFT_X, PadAxis.LEFT_Y, threshold);
    }

    public StickBinding rightStick(int gamepad, float threshold) {
        return stick(gamepad, PadAxis.RIGHT_X, PadAxis.RIGHT_Y, threshold);
    }

    private StickBinding stick(int gamepad, PadAxis xAxis, PadAxis yAxis, float threshold) {
        x.axis(gamepad, xAxis, threshold);
        y.negative.axis(gamepad, yAxis, true,  threshold);
        y.positive.axis(gamepad, yAxis, false, threshold);
        return this;
    }

    /** Retargets both axes' pad and axis sources. */
    public StickBinding gamepad(int index) {
        x.gamepad(index);
        y.gamepad(index);
        return this;
    }

    public void clear() {
        x.clear();
        y.clear();
    }

    // ------------------------------------------------------------------------
    // Update and reads
    // ------------------------------------------------------------------------

    public void update(InputState input) {
        x.update(input);
        y.update(input);
    }

    /** Both axes' values, zeroed when shorter than {@link #roundThreshold}. */
    public Vector2 value(Vector2 out) {
        out.set(x.value(), y.value());
        if (roundThreshold > 0 && out.len() < roundThreshold) out.setZero();
        return out;
    }

    public int signX() {
        return sign(value(tmp).x);
    }

    public int signY() {
        return sign(value(tmp).y);
    }

    /** Any side of either axis pressed. */
    public boolean pressed() {
        return x.pressed() || y.pressed();
    }

    public void consumePress() {
        x.consumePress();
        y.consumePress();
    }

    public void consumeRelease() {
        x.consumeRelease();
        y.consumeRelease();
    }

    public void zeroOut() {
        x.zeroOut();
        y.zeroOut();
    }

    private static int sign(float v) {
        return v > 0 ? 1 : v < 0 ? -1 : 0;
    }
}
