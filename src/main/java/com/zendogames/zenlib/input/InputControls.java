package com.zendogames.zenlib.input;

import static com.badlogic.gdx.Input.Keys.*;

/**
 * One player's bindings for the library's {@code Input} component: a {@link #move} stick and an
 * {@link #action} button. The presets bind a keyboard layout; {@link #gamepad} adds a pad on top, so
 * keys and pad drive the same bindings (the newer press wins an overlap). {@code InputSystem} calls
 * {@link #update} every tick and derives its per-tick edges itself, so {@link #action} carries no
 * press buffer. A game with more actions keeps its own bindings beside these.
 */
public final class InputControls {

    /** How far the left stick must travel to count as a direction: LD60's deadzone (tiny_link uses 0.2). */
    public static final float STICK_THRESHOLD = 0.5f;

    public final StickBinding move = new StickBinding();
    public final ButtonBinding action = new ButtonBinding();

    /** WASD steering with {@code actionKey} for the action. */
    public static InputControls wasd(int actionKey) {
        return new InputControls().keys(A, D, W, S, actionKey);
    }

    /** Arrow-key steering with {@code actionKey} for the action. */
    public static InputControls arrows(int actionKey) {
        return new InputControls().keys(LEFT, RIGHT, UP, DOWN, actionKey);
    }

    /** Adds a keyboard layout. */
    public InputControls keys(int left, int right, int up, int down, int action) {
        move.keys(left, right, up, down);
        this.action.key(action);
        return this;
    }

    /** Adds gamepad slot {@code index}: d-pad and left stick (at {@link #STICK_THRESHOLD}) steer, {@code A} is the action. */
    public InputControls gamepad(int index) {
        move.dpad(index).leftStick(index, STICK_THRESHOLD);
        action.pad(index, Pad.A);
        return this;
    }

    public void update(InputState input) {
        move.update(input);
        action.update(input);
    }

    public void zeroOut() {
        move.zeroOut();
        action.zeroOut();
    }
}
