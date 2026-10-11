package com.zendogames.zenlib.ecs.components;

import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.systems.InputSystem;
import com.zendogames.zenlib.input.InputControls;

/**
 * One entity's control state for this tick, in device-neutral terms: a steer on each axis, the
 * four directions as held flags, an action with its edges, and a double-tap dash. Written by
 * {@link InputSystem} from the held keys when {@link #controls} is set; an AI-driven entity has a
 * null {@code controls} and a {@code DECIDE} system writes the same fields instead, so movers
 * never know who is steering them. {@code MoveIntent.of(input)} is the read side.
 */
public class Input implements Component {

    public static final ComponentType<Input> TYPE = ComponentType.register(Input.class);
    public static Input   get(Entity e) { return TYPE.get(e); }
    public static boolean has(Entity e) { return TYPE.has(e); }

    /** Seconds within which a second tap in the same direction counts as a double tap. */
    public static final float DOUBLE_TAP_WINDOW = 0.25f;

    /** Key bindings for {@link InputSystem}; null means something other than the keyboard writes this component. */
    public InputControls controls;

    /** -1 / 0 / +1 on each axis; up is positive like the world. */
    public int moveX, moveY;

    public boolean upHeld, downHeld, leftHeld, rightHeld;

    public boolean actionPressed, actionHeld, actionReleased;

    /** A double tap in a horizontal direction this tick; see {@link #setMoveX}. */
    public boolean dashPressed;

    /** Direction of the most recent tap, or 0 when the next tap starts a new pair. */
    private int tapDirX;

    /** Seconds since the most recent tap. */
    private float tapAge;

    public Input() {
        reset();
    }

    public Input(InputControls controls) {
        reset();
        this.controls = controls;
    }

    /** Sets the horizontal steer and detects a double tap: a second tap in the same direction within {@link #DOUBLE_TAP_WINDOW}. */
    public void setMoveX(int dirX, float dt) {
        var prevMoveX = moveX;
        moveX = dirX;

        tapAge += dt;

        // A tap is the direction changing to left or right (from neutral, or straight from the other side)
        var isTap = (moveX != 0) && (moveX != prevMoveX);
        if (!isTap) return;

        dashPressed = (moveX == tapDirX) && (tapAge <= DOUBLE_TAP_WINDOW);

        // After a double tap, forget the pair so a third quick tap does not trigger again
        tapDirX = dashPressed ? 0 : moveX;
        tapAge = 0;
    }

    /** Sets the action's held state and derives its pressed / released edges. */
    public void setActionHeld(boolean held) {
        var wasHeld = actionHeld;
        actionHeld     = held;
        actionPressed  = !wasHeld &&  held;
        actionReleased =  wasHeld && !held;
    }

    /** Clears the one-tick events; held state and steer are kept. Called by {@link InputSystem} at the top of every tick. */
    public void clearFrameEvents() {
        dashPressed    = false;
        actionPressed  = false;
        actionReleased = false;
    }

    /** Clears everything, bindings included. */
    public void reset() {
        controls  = null;
        tapDirX   = 0;
        tapAge    = 0;
        moveX     = 0;
        moveY     = 0;
        upHeld    = false;
        downHeld  = false;
        leftHeld  = false;
        rightHeld = false;
        dashPressed    = false;
        actionPressed  = false;
        actionHeld     = false;
        actionReleased = false;
    }

    @Override
    public String toString() {
        return "Input(move %+d,%+d action %s%s%s%s)".formatted(moveX, moveY,
                actionHeld ? "held" : "up",
                actionPressed ? " pressed" : "",
                actionReleased ? " released" : "",
                dashPressed ? " dash" : "");
    }
}
