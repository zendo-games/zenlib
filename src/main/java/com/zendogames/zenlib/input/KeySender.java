package com.zendogames.zenlib.input;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.Array;

/**
 * Drives an {@link InputProcessor} (a scene2d {@code Stage}, a {@code ControllerMenuStage}) from
 * bindings, so a menu written for the keyboard works from a pad: a stick direction entered sends
 * {@code keyDown}, left sends {@code keyUp}, and a direction held past {@link #repeatDelay} sends
 * another {@code keyDown} every {@link #repeatInterval} with no {@code keyUp} between them, as OS key
 * repeat does. A button sends {@code keyDown} on its press and {@code keyUp} on its release, no repeat.
 * <p>
 * {@link #update} updates the bindings it was given, so a screen calls it once per frame from
 * {@code alwaysUpdate}; sharing a binding with {@code InputSystem} is fine, both read the same frame.
 */
public final class KeySender {

    /** blah's delay before a held direction repeats. */
    public float repeatDelay = 0.35f;
    /** A menu rate; blah's 0.025 is for text. */
    public float repeatInterval = 0.1f;

    /** One synthetic key: sent while a stick direction is held, repeating. */
    private static final class Direction {
        final int keycode;
        boolean held;
        float heldTime;
        int repeats;

        Direction(int keycode) {
            this.keycode = keycode;
        }
    }

    private record Stick(StickBinding binding, Direction left, Direction right, Direction up, Direction down) {}

    private static final class Button {
        final ButtonBinding binding;
        final int keycode;
        boolean held;

        Button(ButtonBinding binding, int keycode) {
            this.binding = binding;
            this.keycode = keycode;
        }
    }

    private final InputProcessor target;
    private final Array<Stick> sticks = new Array<>(1);
    private final Array<Button> buttons = new Array<>(2);

    public KeySender(InputProcessor target) {
        this.target = target;
    }

    /** The four directions of {@code stick} as four keys. */
    public KeySender stick(StickBinding stick, int left, int right, int up, int down) {
        sticks.add(new Stick(stick, new Direction(left), new Direction(right), new Direction(up), new Direction(down)));
        return this;
    }

    public KeySender button(ButtonBinding button, int keycode) {
        buttons.add(new Button(button, keycode));
        return this;
    }

    /** Updates the bindings against {@code input}, then sends what changed; {@code dt} drives the repeat. */
    public void update(InputState input, float dt) {
        for (int i = 0; i < sticks.size; i++) {
            var stick = sticks.get(i);
            stick.binding.update(input);
            var sx = stick.binding.signX();
            var sy = stick.binding.signY();
            direction(stick.left,  sx < 0, dt);
            direction(stick.right, sx > 0, dt);
            direction(stick.up,    sy > 0, dt);
            direction(stick.down,  sy < 0, dt);
        }
        for (int i = 0; i < buttons.size; i++) {
            var button = buttons.get(i);
            button.binding.update(input);
            if (button.binding.pressed() && !button.held) {
                button.held = true;
                target.keyDown(button.keycode);
            } else if (button.binding.released() && button.held) {
                button.held = false;
                target.keyUp(button.keycode);
            }
        }
    }

    /** Forgets what is held without sending key ups, for a screen change or a {@code zeroOut}. */
    public void clear() {
        for (int i = 0; i < sticks.size; i++) {
            var stick = sticks.get(i);
            stick.left.held = false;
            stick.right.held = false;
            stick.up.held = false;
            stick.down.held = false;
        }
        for (int i = 0; i < buttons.size; i++) {
            buttons.get(i).held = false;
        }
    }

    private void direction(Direction d, boolean active, float dt) {
        if (active) {
            if (!d.held) {
                d.held = true;
                d.heldTime = 0;
                d.repeats = 0;
                target.keyDown(d.keycode);
            } else {
                d.heldTime += dt;
                if (d.heldTime >= repeatDelay + d.repeats * repeatInterval) {
                    d.repeats++;
                    target.keyDown(d.keycode);
                }
            }
        } else if (d.held) {
            d.held = false;
            target.keyUp(d.keycode);
        }
    }
}
