package com.zendogames.zenlib.ecs.movement;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.components.Mover;

/**
 * One tick of movement for one mover, no ECS: {@link Motion#control}, a pixel sweep along x then
 * y, {@link Motion#after}. The sweep is the one piece every genre shares, so it lives here and
 * nowhere else. A {@link Mover} never enters an overlapped state: each pixel is probed for a
 * possible block before it is moved to.
 */
public final class MoveStep {

    private MoveStep() {}

    /**
     * @param shape the mover's collision shape, null when it has none (then {@code world} is {@link MoveWorld#OPEN})
     * @param world probes and block reports for this mover
     */
    public static void step(Mover mover, PointI2 pos, CollisionShape shape, MoveIntent intent, MoveWorld world, float dt) {
        // Events from the last tick are consumed, this tick writes its own
        mover.xHit = 0;
        mover.yHit = 0;

        mover.motion.control(mover, intent, dt);

        int stepUp = (shape == null) ? 0 : mover.motion.stepUp(shape);
        sweep(mover, pos, shape, world, /*x-axis*/ true,  stepUp, dt);
        sweep(mover, pos, shape, world, /*x-axis*/ false, 0,      dt);

        mover.motion.after(mover, intent, pos, shape, world);
    }

    /**
     * Sweeps one axis for one tick. Position advances by the tick's average velocity (exact for
     * constant acceleration), truncated to whole pixels with the fraction carried in the remainder,
     * so the integer position is always the floor of the exact trajectory. Each pixel is probed
     * before it is taken; a block stops the walk, zeroes velocity and remainder, and reports the
     * hit direction in {@code xHit}/{@code yHit}. On the x axis a blocked pixel may instead be
     * stepped over when the motion's stepUp reach allows it (ramps, low lips).
     */
    private static void sweep(Mover mover, PointI2 pos, CollisionShape shape, MoveWorld world, boolean isAxisX, int stepUp, float dt) {
        // Velocity, remainder, acceleration and speed limit for this axis
        var vel      = isAxisX ? mover.xVel      : mover.yVel;
        var rem      = isAxisX ? mover.xRem      : mover.yRem;
        var accel    = isAxisX ? mover.xAccel    : mover.yAccel;
        var terminal = isAxisX ? mover.terminalX : mover.terminalY;

        // Exact constant-acceleration form: position advances by the tick's average velocity,
        // then the velocity advances by the full tick. Exact at tick boundaries for constant acceleration.
        var total  = rem + (vel + 0.5f * accel * dt) * dt;
        int pixels = (int) total;
        vel += accel * dt;
        rem  = total - pixels;

        // Terminal speed in the direction of the acceleration, applied after it so the step above kept
        // the exact integrator; the clamped value is what the next tick starts from.
        if (terminal > 0) {
            if      (accel < 0) vel = Math.max(vel, -terminal);
            else if (accel > 0) vel = Math.min(vel, +terminal);
        }

        // Probe offset in the movement direction (-1/0/+1), one pixel ahead
        int dir = Integer.signum(pixels);
        int dx  = isAxisX ? dir : 0;
        int dy  = isAxisX ? 0   : dir;

        // Step in the movement direction, one pixel at a time
        int steps = Math.abs(pixels);
        for (int i = 0; i < steps; i++) {
            var block   = world.probe(shape, pos.x, pos.y, dx, dy);
            var blocked = (block == Block.SOLID)
                       || (block == Block.ONE_WAY && !mover.passThrough);

            if (blocked && stepUp > 0) {
                // A low obstruction (a ramp's slope, a lip no taller than the reach): lift over it and keep walking
                int lift = stepUp(shape, pos, world, dx, stepUp);
                if (lift > 0) {
                    pos.add(dx, lift);
                    continue;
                }
            }

            if (blocked) {
                // Let the world record who stopped us, then stop all movement on this axis
                world.onBlocked(dx, dy);
                vel = 0;
                rem = 0;
                if (isAxisX) mover.xHit = dir;
                else         mover.yHit = dir;
                break;
            }

            // No blockers, consume one pixel of movement for this step
            pos.add(dx, dy);
        }

        if (isAxisX) { mover.xVel = vel; mover.xRem = rem; }
        else         { mover.yVel = vel; mover.yRem = rem; }
    }

    /** The smallest lift in {@code 1..max} at which moving {@code (dx, lift)} is open, or 0 when the block is a wall. */
    private static int stepUp(CollisionShape shape, PointI2 pos, MoveWorld world, int dx, int max) {
        for (int lift = 1; lift <= max; lift++) {
            if (world.probe(shape, pos.x, pos.y, dx, lift) == Block.NONE) {
                return lift;
            }
        }
        return 0;
    }
}
