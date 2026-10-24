package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Input;
import com.zendogames.zenlib.input.InputState;

/**
 * Writes every {@link Input} from the game's {@link InputState}, once per tick, first in the tick
 * ({@code INPUT}). The one-tick events are cleared on every {@code Input}; the ones with
 * {@link Input#controls} set update their bindings against the state (imgui's capture is already
 * applied, the processor never wrote what imgui took) and are filled from them; the rest are left
 * for a {@code DECIDE} system to write.
 * <p>
 * The state is per frame and ticks are not frames, so the per-tick edges are derived here from the
 * levels: the action is held this tick when its binding is down <em>or was pressed this frame</em>,
 * which makes a press and release between two slow frames still a one-tick press, and the second of
 * two ticks in one frame see a level, not another edge.
 */
public class InputSystem extends IteratingSystem {

    public static final int PRIORITY = SystemOrder.INPUT;

    private final InputState input;

    public InputSystem(InputState input) {
        super(Family.all(Input.TYPE), PRIORITY);
        this.input = input;
    }

    @Override
    protected void process(Entity e, float dt) {
        var in = Input.get(e);
        in.clearFrameEvents();

        var controls = in.controls;
        if (controls == null) return; // written by something else this tick

        controls.update(input);
        var move = controls.move;
        var action = controls.action;

        in.leftHeld  = move.x.negative.down();
        in.rightHeld = move.x.positive.down();
        in.upHeld    = move.y.positive.down();
        in.downHeld  = move.y.negative.down();

        in.setActionHeld(action.down() || action.pressed());

        // x goes through the setter for the double-tap dash; an overlap reads per the stick's Overlap rule
        in.setMoveX(move.signX(), dt);
        in.moveY = move.signY();
    }
}
