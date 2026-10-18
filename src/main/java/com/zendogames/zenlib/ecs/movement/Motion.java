package com.zendogames.zenlib.ecs.movement;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.components.Mover;

/**
 * The genre half of a {@link Mover}: what turns intent and facts into velocity. {@code MoveStep}
 * calls {@link #control} before the pixel sweep and {@link #after} behind it, every tick, so a
 * motion is a pair of hooks around one neutral integrator. The platformer motion owns gravity,
 * jumps and the grounded / wall facts; a top-down motion owns 8-way steering and facing; a game
 * writes its own for anything else (a homing missile, a bouncing ball, etc...).
 * <p>
 * A motion holds its own state and tuning, and belongs to one mover.
 * {@link #NONE} is the exception: stateless, shared, it integrates whatever velocity others wrote
 * and stops when the {@link Mover} is blocked.
 */
public interface Motion {

    /** Write velocity, acceleration and {@code passThrough} on the mover from intent and the facts left by the last tick. */
    void control(Mover mover, MoveIntent intent, float dt);

    /**
     * After the sweep: refresh facts (probe the world for ground or walls), react to
     * {@code xHit, yHit}, change mode. {@code shape} is null when the mover has no collider,
     * in which case {@code world} is {@link MoveWorld#OPEN} and every probe is {@code NONE}.
     */
    default void after(Mover mover, MoveIntent intent, PointI2 pos, CollisionShape shape, MoveWorld world) {}

    /**
     * Pixels the x sweep may step the mover up to clear a low obstruction (a ramp, a lip) instead of
     * stopping; 0 for a genre with no "up" (e.g. top-down). The platformer answers with the shape's
     * {@link CollisionShape#slideUpHeight()}.
     */
    default int stepUp(CollisionShape shape) { return 0; }

    /**
     * Pixels that sweep may shift the mover along the <em>other</em> axis to slip past a blocked pixel
     * instead of stopping: a diagonal wall in the floor plane (a top-down or beat-em-up genre, where a
     * ramp cell is a 45-degree wall) deflects the mover along itself at full speed. 0 = stop, the default;
     * a platformer steps up instead ({@link #stepUp}) since its y axis is gravity.
     */
    default int slide(CollisionShape shape) { return 0; }

    /** Integrates velocity as written by others; the sweep still stops it on a block. */
    Motion NONE = (mover, intent, dt) -> {};
}
