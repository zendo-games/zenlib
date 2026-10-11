package com.zendogames.zenlib.ecs.movement;


import com.zendogames.zenlib.ecs.components.Input;

/**
 * What a mover wants this tick, in genre-neutral terms. Built from an {@link Input} for a human
 * player; an AI writes the same fields into an {@code Input} from a {@code DECIDE} system, so
 * every mover is driven the same way. Each motion reads the fields it understands: a platformer
 * takes {@code moveX}, jumps on {@code action} and drops through a platform on {@code moveY < 0};
 * a top-down motion steers with both axes.
 * <p>
 * TODO: action/dash intents (and Input) are still game-specific, need to iterate on this
 *
 * @param moveX         -1 / 0 / +1
 * @param moveY         -1 / 0 / +1, up is positive like the world
 * @param actionPressed the action key went down this tick
 * @param actionHeld    the action key is down
 * @param dashPressed   a double tap in a direction this tick
 */
public record MoveIntent(
        int moveX,
        int moveY,
        boolean actionPressed,
        boolean actionHeld,
        boolean dashPressed
) {
    public static final MoveIntent NONE = new MoveIntent(0, 0, false, false, false);

    public static MoveIntent of(Input input) {
        return new MoveIntent(input.moveX, input.moveY, input.actionPressed, input.actionHeld, input.dashPressed);
    }

    /** Steering only. */
    public static MoveIntent move(int moveX, int moveY) {
        return new MoveIntent(moveX, moveY, false, false, false);
    }

    public boolean downHeld() { return moveY < 0; }
    public boolean upHeld()   { return moveY > 0; }
}
