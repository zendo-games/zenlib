package com.zendogames.zenlib.input;

import static com.badlogic.gdx.Input.Keys.*;

/**
 * Keyboard bindings for one player, as libGDX key codes. A game declares its own layout
 * (and any extra keys it needs) with its own record or by extending these presets' use;
 * the library reads only these five.
 */
public record InputControls(
        int left,
        int right,
        int up,
        int down,
        int action
) {
    /** WASD steering with {@code action} for the action key. */
    public static InputControls wasd(int action) {
        return new InputControls(A, D, W, S, action);
    }

    /** Arrow-key steering with {@code action} for the action key. */
    public static InputControls arrows(int action) {
        return new InputControls(LEFT, RIGHT, UP, DOWN, action);
    }
}
