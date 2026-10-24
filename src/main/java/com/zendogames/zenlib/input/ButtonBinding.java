package com.zendogames.zenlib.input;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/**
 * One action over any number of sources: keys, mouse buttons, gamepad buttons and axis directions
 * (blah's {@code ButtonBinding}). The owner calls {@link #update(InputState)} once per frame or per
 * tick and then reads. {@link #pressed()} and {@link #released()} are the frame's edges, kept readable
 * for {@link #pressBuffer} / {@link #releaseBuffer} seconds after them and dropped early by
 * {@link #consumePress()} / {@link #consumeRelease()}, which is how a jump buffer reads:
 * {@code if (jump.pressed()) { jump.consumePress(); ... }}.
 * <p>
 * The state is per frame, so two updates in one frame see the same edge; a consume holds until the
 * next frame. Any source's edge counts: a pad press while the key is held is a new press, and a key
 * release while the pad holds is a release with {@link #down()} still true. Nothing allocates after setup.
 */
public final class ButtonBinding {

    private record PadSource(int gamepad, Pad pad) {}

    private record AxisSource(int gamepad, PadAxis axis, boolean positive, float threshold) {

        /** Past the threshold on this source's side. */
        boolean passes(float v) {
            if (positive ? v <= 0 : v >= 0) return false;
            return Math.abs(v) >= threshold;
        }

        /** threshold..1 mapped to 0..1 (blah's {@code clamped_map}). */
        float remap(float v) {
            if (threshold >= 1) return 1;
            return MathUtils.clamp((Math.abs(v) - threshold) / (1 - threshold), 0, 1);
        }
    }

    /** Seconds a press stays readable through {@link #pressed()} after its edge. */
    public float pressBuffer = 0;

    /** Seconds a release stays readable through {@link #released()} after its edge. */
    public float releaseBuffer = 0;

    private final IntArray keys = new IntArray(4);
    private final IntArray mouseButtons = new IntArray(2);
    private final Array<PadSource> pads = new Array<>(2);
    private final Array<AxisSource> axes = new Array<>(2);

    /** {@code input.time} at the last update; a change means a new frame. */
    private double time = -1;

    /** This frame's level, raw edges and analog value. */
    private boolean down, pressed, released;

    private float value;

    /** Seconds down since the last press; frozen on the release so a hold move reads it then. */
    private float heldTime;

    private double pressStart = -1;

    /** The last press and release, for the buffers; a consume forgets them. */
    private double lastPressTime = -1, lastReleaseTime = -1;

    private boolean pressConsumed, releaseConsumed;

    /** The last press, kept through a consume. */
    private double timestamp = -1;

    // ------------------------------------------------------------------------
    // Sources
    // ------------------------------------------------------------------------

    /** {@code Input.Keys} codes. */
    public ButtonBinding key(int... keycodes) {
        keys.addAll(keycodes);
        return this;
    }

    /** {@code Input.Buttons} codes. */
    public ButtonBinding mouse(int... buttons) {
        mouseButtons.addAll(buttons);
        return this;
    }

    public ButtonBinding pad(int gamepad, Pad... buttons) {
        for (var button : buttons) {
            pads.add(new PadSource(gamepad, button));
        }
        return this;
    }

    /**
     * One direction of an axis as a button: down while the axis is past {@code threshold} on the
     * {@code positive} (or negative) side; {@link #value()} maps threshold..1 to 0..1.
     */
    public ButtonBinding axis(int gamepad, PadAxis axis, boolean positive, float threshold) {
        axes.add(new AxisSource(gamepad, axis, positive, threshold));
        return this;
    }

    /** Retargets every pad and axis source to gamepad slot {@code index} (blah's {@code set_controller}). */
    public ButtonBinding gamepad(int index) {
        for (int i = 0; i < pads.size; i++) {
            pads.set(i, new PadSource(index, pads.get(i).pad));
        }
        for (int i = 0; i < axes.size; i++) {
            var a = axes.get(i);
            axes.set(i, new AxisSource(index, a.axis, a.positive, a.threshold));
        }
        return this;
    }

    /** Removes every source. */
    public void clear() {
        keys.clear();
        mouseButtons.clear();
        pads.clear();
        axes.clear();
    }

    // ------------------------------------------------------------------------
    // Update and reads
    // ------------------------------------------------------------------------

    public void update(InputState input) {
        if (input.time != time) {
            time = input.time;
            pressConsumed = false;
            releaseConsumed = false;
        }
        var now = input.now;
        var last = input.last;

        down = false;
        pressed = false;
        released = false;
        value = 0;

        for (int i = 0; i < keys.size; i++) {
            read(now.keyboard, keys.get(i));
        }
        for (int i = 0; i < mouseButtons.size; i++) {
            read(now.mouse, mouseButtons.get(i));
        }
        for (int i = 0; i < pads.size; i++) {
            var source = pads.get(i);
            var gamepad = connected(now, source.gamepad);
            if (gamepad != null) read(gamepad.buttons, source.pad.ordinal());
        }
        if (down) value = 1;

        for (int i = 0; i < axes.size; i++) {
            var source = axes.get(i);
            var gamepad = connected(now, source.gamepad);
            if (gamepad == null) continue;
            var axis = source.axis.ordinal();
            var isDown  = source.passes(gamepad.axes[axis]);
            var wasDown = source.passes(last.gamepads[source.gamepad].axes[axis]);
            if (isDown) {
                down = true;
                value = Math.max(value, source.remap(gamepad.axes[axis]));
            }
            pressed  |= isDown && !wasDown;
            released |= !isDown && wasDown;
        }

        if (pressed) {
            timestamp = time;
            lastPressTime = time;
            pressStart = time;
        }
        if (released) {
            lastReleaseTime = time;
        }
        if (down) {
            if (pressStart < 0) pressStart = time; // already held when first seen: count from here
            heldTime = (float) (time - pressStart);
        }
    }

    /** Pressed this frame, or within {@link #pressBuffer} seconds, and not consumed. */
    public boolean pressed() {
        if (pressConsumed) return false;
        if (pressed) return true;
        return lastPressTime >= 0 && time - lastPressTime <= pressBuffer;
    }

    /** Released this frame, or within {@link #releaseBuffer} seconds, and not consumed. */
    public boolean released() {
        if (releaseConsumed) return false;
        if (released) return true;
        return lastReleaseTime >= 0 && time - lastReleaseTime <= releaseBuffer;
    }

    /** Any source is down. */
    public boolean down() {
        return down;
    }

    /** 1 while a digital source is down, else the largest remapped axis deflection, else 0. */
    public float value() {
        return value;
    }

    /** 1 while down, else 0. */
    public int sign() {
        return down ? 1 : 0;
    }

    /** Seconds held: 0 on the press frame, counting while down, kept after the release until the next press. */
    public float heldTime() {
        return heldTime;
    }

    /** {@link InputState#time} of the last press, -1 if never. Not affected by {@link #consumePress()}. */
    public double timestamp() {
        return timestamp;
    }

    /** Eats the press and its buffer until the next frame's edge. */
    public void consumePress() {
        pressConsumed = true;
        lastPressTime = -1;
    }

    /** Eats the release and its buffer until the next frame's edge. */
    public void consumeRelease() {
        releaseConsumed = true;
        lastReleaseTime = -1;
    }

    /** Everything up and forgotten, with no release edge; the sources stay. */
    public void zeroOut() {
        down = false;
        pressed = false;
        released = false;
        value = 0;
        heldTime = 0;
        pressStart = -1;
        lastPressTime = -1;
        lastReleaseTime = -1;
        timestamp = -1;
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private void read(DeviceState.Buttons buttons, int code) {
        if (buttons.down(code))     down = true;
        if (buttons.pressed(code))  pressed = true;
        if (buttons.released(code)) released = true;
    }

    /** The slot if it is in range and has a pad, else null. */
    private static DeviceState.Gamepad connected(DeviceState state, int index) {
        if (index < 0 || index >= DeviceState.GAMEPADS) return null;
        var gamepad = state.gamepads[index];
        return gamepad.connected ? gamepad : null;
    }
}
