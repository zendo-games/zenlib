package com.zendogames.zenlib.ecs.movement.platformer;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.components.Mover;
import com.zendogames.zenlib.ecs.movement.Motion;
import com.zendogames.zenlib.ecs.movement.MoveIntent;
import com.zendogames.zenlib.ecs.movement.MoveWorld;

/**
 * Side-view movement under gravity: run, jump (coyote time, input buffer, variable height),
 * wall slide and wall jump, ledge grab, dash, one-way platforms. The behavior lives in the
 * {@link PlatformerMode}s; this class holds the per-mover state that the modes read and write
 * (the facts refreshed after every sweep, the jump memory, the timers) and the {@link #tuning},
 * which is a mutable field so a game can swap in another {@link PlatformerTuning} at any time.
 * <p>
 * Game code reads the facts for presentation ({@code grounded, landed, facing}) through
 * {@code Mover.get(entity).motion(PlatformerMotion.class)} and pushes the mover around with
 * {@link #launch}.
 */
public final class PlatformerMotion implements Motion {

    public PlatformerTuning tuning;

    // ------------------------------------------------------------------------
    // Mode
    // ------------------------------------------------------------------------

    /** The active mode this tick. */
    public PlatformerMode mode;

    /** Seconds remaining in the current mode, mode-defined meaning. */
    public float modeTime;

    /** The mode's direction, -1/+1, mode-defined meaning. */
    public int modeDir;

    // ------------------------------------------------------------------------
    // Timers, seconds remaining
    // ------------------------------------------------------------------------

    /** After a dash, during which a dash can't be re-triggered. */
    public float dashCooldown;

    /** After a wall jump, during which {@code moveX} intent is ignored. */
    public float steerLock;

    /** After letting go of a ledge, during which it can't be grabbed again. */
    public float ledgeRegrabLock;

    /** Direction the mover faces, -1/+1. Follows steering input; a wall jump turns it away from the wall. */
    public int facing = 1;

    // ------------------------------------------------------------------------
    // Facts refreshed at the end of every step
    // ------------------------------------------------------------------------

    /** On the ground this tick. */
    public boolean grounded;

    /** Was on the ground last tick. */
    public boolean wasGrounded;

    /** Standing on a one-way platform (a subset of grounded). */
    public boolean onOneWay;

    /** -1/+1 when a SOLID is one pixel left/right, else 0. */
    public int wallDir;

    // Jump memory
    public boolean jumpActive, jumpCut, dropping;
    public float   jumpBuffer, coyote;

    // Events valid for the tick just stepped; consumers read, the step overwrites
    public boolean jumped, landed;

    public PlatformerMotion(PlatformerTuning tuning) {
        this(tuning, PlatformerMode.PLAYER);
    }

    public PlatformerMotion(PlatformerTuning tuning, PlatformerMode mode) {
        this.tuning = tuning;
        this.mode = mode;
    }

    // ------------------------------------------------------------------------
    // Motion
    // ------------------------------------------------------------------------

    @Override
    public void control(Mover mover, MoveIntent intent, float dt) {
        jumped = false;
        landed = false;

        mover.terminalY = tuning.maxFallVel();
        mode = mode.control(this, mover, intent, dt);

        // The sweep treats ONE_WAY as open while dropping through a platform
        mover.passThrough = dropping;
    }

    @Override
    public void after(Mover mover, MoveIntent intent, PointI2 pos, CollisionShape shape, MoveWorld world) {
        settle(mover, pos, shape, world);
        reportPush(mover, intent, pos, shape, world);
        mode = mode.after(this, mover, intent, pos, shape, world);
    }

    /** Refreshes the ground and wall facts from the settled position and fires a landing. */
    private void settle(Mover mover, PointI2 pos, CollisionShape shape, MoveWorld world) {
        wasGrounded = grounded;
        var underneath = world.probe(shape, pos.x, pos.y, 0, -1);
        onOneWay = (underneath == Block.ONE_WAY) && !dropping;
        grounded = (underneath == Block.SOLID)   ||  onOneWay;

        if (grounded && !wasGrounded) {
            landed     = true;
            jumpActive = false;
            if (mover.yVel < 0) {
                mover.yVel = 0;
            }

            // A landing is a block of further descent even when the walk ended flush and never probed
            // into the floor. Report it so ContactSystem sees BLOCKED (0, -1) from the support entity;
            // the probe above left that entity as the world's last contact.
            mover.yHit = -1;
            world.onBlocked(0, -1); // must directly follow the 'underneath' probe: reads the world's last contact
        }

        if (dropping && underneath != Block.ONE_WAY) {
            dropping = false; // fully through, or walked off
        }

        // Wall facts last: every probe overwrites the world's last contact
        wallDir = (world.probe(shape, pos.x, pos.y, -1, 0) == Block.SOLID) ? -1
                : (world.probe(shape, pos.x, pos.y, +1, 0) == Block.SOLID) ? +1 : 0;
    }

    /**
     * Pushing into an adjacent wall counts as being blocked by it. The sweep only reports a block
     * when it tries a pixel, and the control half holds a pusher at rest, so a mover that arrived
     * flush would otherwise report nothing while pressing against the wall: no {@code xHit}, no
     * {@code onHitX}, no BLOCKED contact. The landing report in {@link #settle} is the same rule
     * for the floor. The re-probe leaves the blocker as the world's last contact for {@code onBlocked}.
     */
    private void reportPush(Mover mover, MoveIntent intent, PointI2 pos, CollisionShape shape, MoveWorld world) {
        if (wallDir == 0 || intent.moveX() != wallDir || mover.xHit != 0) return;
        if (world.probe(shape, pos.x, pos.y, wallDir, 0) != Block.SOLID) return;
        mover.xHit = wallDir;
        mover.xVel = 0; // a block stops the axis, as the sweep's does
        mover.xRem = 0;
        world.onBlocked(wallDir, 0); // must directly follow the probe: reads the world's last contact
    }

    // ------------------------------------------------------------------------
    // Derived facts, from the mover's velocity
    // ------------------------------------------------------------------------

    public boolean isRising(Mover mover)  { return !grounded && (mover.yVel > 0); }
    public boolean isFalling(Mover mover) { return !grounded && (mover.yVel < 0); }
    public boolean isJumping(Mover mover) { return jumpActive && isRising(mover); }

    // ------------------------------------------------------------------------
    // Pushes from outside
    // ------------------------------------------------------------------------

    /**
     * Throws the mover {@code dist} units sideways over an arc {@code height} units high; both hold
     * exactly on flat ground when {@code lock} is 1. {@code lock} (0..1) is the fraction of the arc
     * flown without control. The mode is {@link PlatformerMode#LAUNCHED} until it lands or the lock runs out.
     */
    public void launch(Mover mover, int dir, float dist, float height, float lock) {
        PlatformerMode.Launched.start(this, mover, dir, dist, height, lock);
    }

    /** Clears every jump helper and timer, for a respawn or a mode that takes full control. */
    void clearJump() {
        jumpActive = false;
        jumpCut    = false;
        jumpBuffer = 0;
        coyote     = 0;
    }
}
