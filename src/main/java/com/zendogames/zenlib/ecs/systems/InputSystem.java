package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Input;
import com.zendogames.zenlib.input.HeldKeys;

/**
 * Writes every {@link Input} from the keyboard, once per tick, first in the tick ({@code INPUT}).
 * The one-tick events are cleared on every {@code Input}, then the ones with {@link Input#controls}
 * set are sampled from {@link HeldKeys} (the screen's input processor, so the imgui capture is
 * already applied); the rest are left for a {@code DECIDE} system to write.
 * TODO: Controllers are not read yet
 */
public class InputSystem extends IteratingSystem {

    public static final int PRIORITY = SystemOrder.INPUT;

    private final HeldKeys keys;

    public InputSystem(HeldKeys keys) {
        super(Family.all(Input.TYPE), PRIORITY);
        this.keys = (keys != null) ? keys : HeldKeys.NONE;
    }

    @Override
    protected void process(Entity e, float dt) {
        var input = Input.get(e);
        input.clearFrameEvents();

        var controls = input.controls;
        if (controls == null) return; // written by something else this tick

        var up    = keys.isHeld(controls.up());
        var down  = keys.isHeld(controls.down());
        var left  = keys.isHeld(controls.left());
        var right = keys.isHeld(controls.right());

        input.setActionHeld(keys.isHeld(controls.action()));

        input.upHeld    = up;
        input.downHeld  = down;
        input.leftHeld  = left;
        input.rightHeld = right;

        // Opposite keys cancel; x goes through the setter for the double-tap dash
        input.setMoveX((left ? -1 : 0) + (right ? 1 : 0), dt);
        input.moveY  = (down ? -1 : 0) + (up    ? 1 : 0);
    }
}
