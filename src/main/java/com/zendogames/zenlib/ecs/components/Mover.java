package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.movement.Motion;

/**
 * Kinematics for anything that moves through the world a pixel at a time, plus the {@link Motion}
 * that drives it. {@code MoveStep} integrates {@code xVel, yVel} and {@code xAccel, yAccel}
 * onto an integer {@code Position} with sub-pixel remainders, probing each pixel; a block zeroes
 * the axis and reports it in {@code xHit, yHit}. Nothing here knows about gravity or ground:
 * those are facts that the motion keeps.
 * <pre>{@code
 * entity.add(new Mover(new PlatformerMotion(PlatformerTuning.player(16))));
 * entity.add(new Mover(Motion.NONE, 40f, 0f));   // a bullet: constant velocity, stops on block
 *
 * var mover  = Mover.get(entity);
 * var motion = mover.motion(PlatformerMotion.class);
 * if (motion.landed) animator.scale.set(1.2f, 0.8f);
 * }</pre>
 */
public class Mover implements Component {

    public static final ComponentType<Mover> TYPE = ComponentType.register(Mover.class);
    public static Mover   get(Entity e) { return TYPE.get(e); }
    public static boolean has(Entity e) { return TYPE.has(e); }

    private static final String TAG = Mover.class.getSimpleName();

    /** Per-entity: a stateful motion must not be shared between movers. {@link Motion#NONE} may be shared. */
    public final Motion motion;

    /** Pixels per second. */
    public float xVel, yVel;

    /** Sub-pixel remainder carried between ticks, so the integer position is the floor of the exact trajectory. */
    public float xRem, yRem;

    /** Pixels per second squared, integrated by the sweep with the exact constant-acceleration form. Written by the motion each tick. */
    public float xAccel, yAccel;

    /**
     * Speed limit on each axis in the direction of that axis's acceleration, applied after the
     * acceleration is integrated; 0 is no limit. A platformer's terminal fall speed goes in
     * {@code terminalY}, a top-down mover leaves both at 0 and clamps in its {@link Motion}.
     */
    public float terminalX, terminalY;

    /** While true the sweep treats a {@code ONE_WAY} probe result as open. A platformer sets it while dropping through a platform. */
    public boolean passThrough;

    /** Events for the tick just stepped: -1/0/+1 when the walk on that axis was stopped, in that direction. Reset at the start of every step. */
    public int xHit, yHit;

    public Mover(Motion motion) {
        this(motion, 0, 0);
    }

    public Mover(Motion motion, float xVel, float yVel) {
        if (motion == null) {
            throw new GdxRuntimeException(TAG + ": motion must be non-null, use Motion.NONE");
        }
        this.motion = motion;
        this.xVel = xVel;
        this.yVel = yVel;
    }

    /** The motion as a specific type, throwing with both names when it is different. */
    public <T extends Motion> T motion(Class<T> clazz) {
        if (!is(clazz)) {
            throw new GdxRuntimeException(TAG + ": motion is a %s, not a %s".formatted(motion.getClass().getSimpleName(), clazz.getSimpleName()));
        }
        return clazz.cast(motion);
    }

    /** True when the motion is of (or extends) {@code clazz}. */
    public boolean is(Class<? extends Motion> clazz) {
        return clazz.isInstance(motion);
    }

    /** Sets velocity and remainders to zero. */
    public void stop() {
        xVel = 0;
        yVel = 0;
        xRem = 0;
        yRem = 0;
    }

    public boolean isMoving() {
        return (xVel != 0) || (yVel != 0);
    }
}
