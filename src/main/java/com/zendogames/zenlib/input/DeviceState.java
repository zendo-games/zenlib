package com.zendogames.zenlib.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;

import java.util.Arrays;

/**
 * One snapshot of every device: keyboard, mouse and {@value #GAMEPADS} gamepad slots. {@link InputState}
 * keeps two ({@code now} and {@code last}) and is the only writer: every mutator here is package-private.
 * Reads are bounds-checked, so an unmapped or out-of-range code is simply never down.
 */
public final class DeviceState {

    /** {@code Input.Keys} codes are 0..255 ({@code MAX_KEYCODE}). */
    public static final int KEYS = 256;
    /** {@code Input.Buttons} LEFT, RIGHT, MIDDLE, BACK, FORWARD. */
    public static final int MOUSE_BUTTONS = 5;
    public static final int GAMEPADS = 4;

    private static final Pad[] PADS = Pad.values();
    private static final PadAxis[] AXES = PadAxis.values();

    /** A set of buttons: the level ({@link #down}), this frame's edges, and the clock reading of the last press. */
    public static final class Buttons {

        private final boolean[] down, pressed, released;
        private final double[] pressTime;

        Buttons(int count) {
            down = new boolean[count];
            pressed = new boolean[count];
            released = new boolean[count];
            pressTime = new double[count];
        }

        public boolean down(int i) {
            return inRange(i) && down[i];
        }

        public boolean pressed(int i) {
            return inRange(i) && pressed[i];
        }

        public boolean released(int i) {
            return inRange(i) && released[i];
        }

        /**
         * {@link InputState#time} when button {@code i} last went down, 0 if never.
         */
        public double pressTime(int i) {
            return inRange(i) ? pressTime[i] : 0;
        }

        boolean inRange(int i) {
            return i >= 0 && i < down.length;
        }

        /** An event: the button went down between frames. A repeat while already down is not a new press. */
        void press(int i, double time) {
            if (!inRange(i) || down[i]) return;
            down[i] = true;
            pressed[i] = true;
            pressTime[i] = time;
        }

        /** An event: the button came up. A tap inside one frame keeps both edges; an up without a down is nothing. */
        void release(int i) {
            if (!inRange(i) || !down[i]) return;
            down[i] = false;
            released[i] = true;
        }

        /** A polled level, with edges derived against the previous frame's level. */
        void poll(int i, boolean isDown, Buttons last, double time) {
            boolean wasDown = last.down[i];
            down[i] = isDown;
            pressed[i] = isDown && !wasDown;
            released[i] = !isDown && wasDown;
            if (pressed[i]) pressTime[i] = time;
        }

        boolean anyPressed() {
            for (boolean p : pressed) if (p) return true;
            return false;
        }

        void set(Buttons other) {
            System.arraycopy(other.down, 0, down, 0, down.length);
            System.arraycopy(other.pressed, 0, pressed, 0, pressed.length);
            System.arraycopy(other.released, 0, released, 0, released.length);
            System.arraycopy(other.pressTime, 0, pressTime, 0, pressTime.length);
        }

        void clearEdges() {
            Arrays.fill(pressed, false);
            Arrays.fill(released, false);
        }

        /** Everything up with no edges, as if nothing had ever been touched. */
        void zeroOut() {
            Arrays.fill(down, false);
            clearEdges();
            Arrays.fill(pressTime, 0);
        }
    }

    /** One controller slot. {@link #buttons} is indexed by {@link Pad#ordinal()}, {@link #axes} by {@link PadAxis#ordinal()}. */
    public static final class Gamepad {

        public boolean connected;
        public String name;
        public final Buttons buttons = new Buttons(PADS.length);
        /** Raw libGDX values in -1..1: left and up are negative. */
        public final float[] axes = new float[AXES.length];

        /** Reads every button and axis of {@code controller}, edges against {@code last}. */
        void poll(Controller controller, Gamepad last, double time) {
            connected = true;
            name = controller.getName();
            var mapping = controller.getMapping();
            for (var pad : PADS) {
                int code = mapping == null ? ControllerMapping.UNDEFINED : pad.code(mapping);
                boolean isDown = code != ControllerMapping.UNDEFINED && controller.getButton(code);
                buttons.poll(pad.ordinal(), isDown, last.buttons, time);
            }
            for (var axis : AXES) {
                int code = mapping == null ? ControllerMapping.UNDEFINED : axis.code(mapping);
                axes[axis.ordinal()] = code == ControllerMapping.UNDEFINED ? 0f : controller.getAxis(code);
            }
        }

        /** No controller in this slot: a release edge for whatever was down, axes at rest. */
        void disconnect(Gamepad last, double time) {
            connected = false;
            name = null;
            for (int i = 0; i < PADS.length; i++) {
                buttons.poll(i, false, last.buttons, time);
            }
            Arrays.fill(axes, 0f);
        }

        void set(Gamepad other) {
            connected = other.connected;
            name = other.name;
            buttons.set(other.buttons);
            System.arraycopy(other.axes, 0, axes, 0, axes.length);
        }

        void clearEdges() {
            buttons.clearEdges();
        }

        /** Buttons up and sticks centered; the pad stays connected. */
        void zeroOut() {
            buttons.zeroOut();
            Arrays.fill(axes, 0f);
        }
    }

    /** {@code Input.Keys} codes. */
    public final Buttons keyboard = new Buttons(KEYS);
    /** {@code Input.Buttons} codes. */
    public final Buttons mouse = new Buttons(MOUSE_BUTTONS);
    /** Window pixels, top-left origin, as libGDX reports them. Carries across frames. */
    public int mouseX, mouseY;
    /** This frame's scroll, summed over its events; zero after {@link InputState#endFrame}. */
    public float wheelX, wheelY;
    /** {@value #GAMEPADS} slots, by position in the controller list. */
    public final Gamepad[] gamepads = new Gamepad[GAMEPADS];

    DeviceState() {
        for (int i = 0; i < GAMEPADS; i++) {
            gamepads[i] = new Gamepad();
        }
    }

    boolean anyPressed() {
        if (keyboard.anyPressed() || mouse.anyPressed()) return true;
        for (var gamepad : gamepads) {
            if (gamepad.connected && gamepad.buttons.anyPressed()) return true;
        }
        return false;
    }

    void set(DeviceState other) {
        keyboard.set(other.keyboard);
        mouse.set(other.mouse);
        mouseX = other.mouseX;
        mouseY = other.mouseY;
        wheelX = other.wheelX;
        wheelY = other.wheelY;
        for (int i = 0; i < GAMEPADS; i++) {
            gamepads[i].set(other.gamepads[i]);
        }
    }

    /** Edges and the wheel are per frame; levels and the mouse position carry. */
    void clearEdges() {
        keyboard.clearEdges();
        mouse.clearEdges();
        for (var gamepad : gamepads) {
            gamepad.clearEdges();
        }
        wheelX = 0;
        wheelY = 0;
    }

    /** Everything up, nothing pressed or released, sticks centered. The mouse position is kept. */
    void zeroOut() {
        keyboard.zeroOut();
        mouse.zeroOut();
        for (var gamepad : gamepads) {
            gamepad.zeroOut();
        }
        wheelX = 0;
        wheelY = 0;
    }
}
