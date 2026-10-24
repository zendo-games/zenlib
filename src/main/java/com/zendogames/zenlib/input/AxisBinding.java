package com.zendogames.zenlib.input;

/**
 * A -1..1 axis from two {@link ButtonBinding}s, {@link #negative} and {@link #positive} (blah's
 * {@code AxisBinding}). Each side takes any sources; the helpers bind the common pairs. When both
 * sides are down, {@link #overlap} decides: the newer press (default), the older, or zero.
 */
public final class AxisBinding {

    /** What {@link #value()} reads while both sides are down. */
    public enum Overlap {
        /** The side pressed last; a tie (both in one frame) goes to positive. */
        NEWER,
        /** The side pressed first. */
        OLDER,
        /** Zero. */
        CANCEL
    }

    public final ButtonBinding negative = new ButtonBinding();
    public final ButtonBinding positive = new ButtonBinding();
    public Overlap overlap = Overlap.NEWER;

    // ------------------------------------------------------------------------
    // Sources
    // ------------------------------------------------------------------------

    public AxisBinding keys(int negativeKey, int positiveKey) {
        negative.key(negativeKey);
        positive.key(positiveKey);
        return this;
    }

    public AxisBinding pad(int gamepad, Pad negativeButton, Pad positiveButton) {
        negative.pad(gamepad, negativeButton);
        positive.pad(gamepad, positiveButton);
        return this;
    }

    /** One stick axis: its negative side feeds {@link #negative}, its positive side {@link #positive}. */
    public AxisBinding axis(int gamepad, PadAxis axis, float threshold) {
        negative.axis(gamepad, axis, false, threshold);
        positive.axis(gamepad, axis, true, threshold);
        return this;
    }

    /** Retargets both sides' pad and axis sources. */
    public AxisBinding gamepad(int index) {
        negative.gamepad(index);
        positive.gamepad(index);
        return this;
    }

    public void clear() {
        negative.clear();
        positive.clear();
    }

    // ------------------------------------------------------------------------
    // Update and reads
    // ------------------------------------------------------------------------

    public void update(InputState input) {
        negative.update(input);
        positive.update(input);
    }

    /** -1..1: the down side's {@link ButtonBinding#value()} with its sign, both down resolved by {@link #overlap}. */
    public float value() {
        var neg = negative.down();
        var pos = positive.down();
        if (neg && pos) {
            return switch (overlap) {
                case CANCEL -> 0;
                case OLDER  -> negative.timestamp() <  positive.timestamp() ? -negative.value() : positive.value();
                case NEWER  -> negative.timestamp() >  positive.timestamp() ? -negative.value() : positive.value();
            };
        }
        if (pos) return positive.value();
        if (neg) return -negative.value();
        return 0;
    }

    /** -1, 0 or 1: the sign of {@link #value()}. */
    public int sign() {
        var v = value();
        return v > 0 ? 1 : v < 0 ? -1 : 0;
    }

    /** Either side pressed. */
    public boolean pressed() {
        return negative.pressed() || positive.pressed();
    }

    public void consumePress() {
        negative.consumePress();
        positive.consumePress();
    }

    public void consumeRelease() {
        negative.consumeRelease();
        positive.consumeRelease();
    }

    public void zeroOut() {
        negative.zeroOut();
        positive.zeroOut();
    }
}
