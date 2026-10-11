package com.zendogames.zenlib.ecs.movement.platformer;

/**
 * Tuning for a 2d platformer mover, in designer units ({@link #unit} pixels, typically one tile)
 * and seconds. Physics values are derived on demand, so a game swaps a whole tuning on its
 * {@link PlatformerMotion#tuning} (a power-up, a level with low gravity) or copies one with a
 * field changed, e.g. {@link #withJumpHeight}. Nothing in the library is fixed: the presets
 * {@link #player} and {@link #ballistic} take the game's unit and are only examples.
 * <p>
 * Jump physics follow Kyle Pittman's
 * <a href="https://media.gdcvault.com/gdc2016/Presentations/Pittman_Kyle_BuildingBetterJump.pdf">
 * Building a Better Jump</a> (GDC 2016): given apex height {@code h} and time to apex {@code t},
 * {@code a = -2h / t^2} and {@code v0 = 2h / t}. The {@code h} used here is the design height
 * plus {@link #jumpApexClearance}; see that field for why.
 *
 * @param unit                     pixels per tuning unit, typically the tile size
 * @param groundSpeed              max ground speed, units per second
 * @param groundAccelTime          seconds from rest to {@code groundSpeed}
 * @param groundStopTime           seconds from {@code groundSpeed} to rest with no input
 * @param airSpeed                 max horizontal air speed, units per second
 * @param airAccelTime             seconds from rest to {@code airSpeed}
 * @param airStopTime              seconds from {@code airSpeed} to rest with no input
 * @param maxFallSpeed             terminal velocity, units per second; clamps the downward
 *                                 velocity after gravity is applied, in every mode
 * @param jumpHeight               design apex height in units: a ledge this many units above the
 *                                 launch height is reachable, a wall this tall is not. The physics
 *                                 apex is {@code jumpApexClearance} higher.
 * @param jumpTimeToPeak           seconds from launch to apex; with {@code jumpHeight} this fixes
 *                                 {@link #gravity()} and {@link #jumpImpulse()}
 * @param jumpCutGravityMultiplier gravity multiplier while rising after the jump input is released
 *                                 (variable-height jump)
 * @param fallGravityMultiplier    gravity multiplier while falling (fast fall)
 * @param jumpBufferTime           seconds a jump press is remembered before landing
 * @param coyoteTime               seconds after walking off a ledge during which a jump still works
 * @param abilities                the optional moves; {@link Abilities#NONE} for a plain mover
 * @param jumpApexClearance        pixels added to the design jump height before deriving gravity
 *                                 and impulse. {@code MoveStep} keeps a mover's integer position
 *                                 as the floor of its exact trajectory (the sub-pixel remainder is
 *                                 carried exactly, so nothing is lost; the position simply never
 *                                 rounds up). A continuous apex equal to the design height
 *                                 therefore always quantizes one pixel short: the true peak falls
 *                                 between ticks, and even a tick-aligned peak lands at 79.99 rather
 *                                 than 80 in float. One pixel of clearance (the default) is exactly
 *                                 that gap: the integer path reaches the design height for a few
 *                                 ticks around the apex, enough to step onto a ledge at that height,
 *                                 while a wall one pixel taller still blocks. Anything larger than
 *                                 one pixel breaks the second half of that contract.
 */
public record PlatformerTuning(
        float unit,
        float groundSpeed,
        float groundAccelTime,
        float groundStopTime,
        float airSpeed,
        float airAccelTime,
        float airStopTime,
        float maxFallSpeed,
        float jumpHeight,
        float jumpTimeToPeak,
        float jumpCutGravityMultiplier,
        float fallGravityMultiplier,
        float jumpBufferTime,
        float coyoteTime,
        Abilities abilities,
        float jumpApexClearance
) {
    /** The default {@link #jumpApexClearance}; see the record doc for why it is one pixel. */
    public static final float DEFAULT_APEX_CLEARANCE_PX = 1f;

    /** With the default apex clearance. */
    public PlatformerTuning(float unit,
                            float groundSpeed, float groundAccelTime, float groundStopTime,
                            float airSpeed, float airAccelTime, float airStopTime, float maxFallSpeed,
                            float jumpHeight, float jumpTimeToPeak,
                            float jumpCutGravityMultiplier, float fallGravityMultiplier,
                            float jumpBufferTime, float coyoteTime,
                            Abilities abilities) {
        this(unit, groundSpeed, groundAccelTime, groundStopTime,
                airSpeed, airAccelTime, airStopTime, maxFallSpeed,
                jumpHeight, jumpTimeToPeak, jumpCutGravityMultiplier, fallGravityMultiplier,
                jumpBufferTime, coyoteTime, abilities, DEFAULT_APEX_CLEARANCE_PX);
    }

    /**
     * The optional moves, all in units and seconds. A zero duration or reach disables that ability.
     *
     * @param wallSlideSpeed   downward speed while pushing into a wall in the air; 0 disables
     * @param wallJumpSpeedX   horizontal kick away from the wall on a wall jump
     * @param wallJumpLockTime seconds after a wall jump during which steering is ignored
     * @param ledgeGrab        what the mover must be doing, besides a ledge being in reach, to grab it
     * @param ledgeGrabReach   units above or below the head a ledge may be; 0 disables
     * @param ledgeRegrabTime  seconds after letting go before the same ledge can be grabbed again
     * @param dashSpeed        horizontal speed during a dash
     * @param dashTime         seconds a dash lasts; 0 disables
     * @param dashCooldown     seconds after a dash before another can start
     */
    public record Abilities(
            float wallSlideSpeed,
            float wallJumpSpeedX,
            float wallJumpLockTime,
            LedgeGrab ledgeGrab,
            float ledgeGrabReach,
            float ledgeRegrabTime,
            float dashSpeed,
            float dashTime,
            float dashCooldown
    ) {
        public enum LedgeGrab {
            /** Facing the wall, no keypress required. */
            AUTO,
            /** Steering into the wall on the tick of the grab. */
            PUSH
        }

        public static final Abilities NONE = new Abilities(0, 0, 0, LedgeGrab.AUTO, 0, 0, 0, 0, 0);

        /**
         * Every ability on, with the values the demo hero uses.
         */
        public static Abilities all() {
            return new Abilities(
                    6, 20, 0.2f,                  // wall: slide speed, jump speed x, jump steer-lock time
                    LedgeGrab.PUSH, 0.25f, 0.1f,  // ledge: grab rule, reach, regrab time
                    40, 0.2f, 0.4f                // dash: speed, time, cooldown
            );
        }
    }

    // ------------------------------------------------------------------------
    // Presets: examples a game may use as a starting point
    // ------------------------------------------------------------------------

    /** A responsive hero with every ability, {@code unit} pixels per tile. */
    public static PlatformerTuning player(float unit) {
        return new PlatformerTuning(
                unit,
                12, 0.12f, 0.1f,        // ground: speed (units/s), accel time, stop time
                11, 0.25f, 0.3f, 22,    // air: speed, accel time, stop time, max fall speed
                5, 0.38f,               // jump: apex height (units), time to peak
                2.5f, 2f,               // gravity multipliers: jump cut, fall
                0.1f, 0.12f,            // jump helpers: buffer time, coyote time
                Abilities.all()
        );
    }

    /** A thrown or dropped body under the same gravity as {@link #player}; the steering fields are unread by {@link PlatformerMode#BALLISTIC}. */
    public static PlatformerTuning ballistic(float unit) {
        return new PlatformerTuning(
                unit,
                0, 1, 1,                // ground: unread
                0, 1, 1, 22,            // air: unread apart from max fall speed
                5, 0.38f,               // jump: same as player(), so the body falls under the player's gravity
                1f, 1f, 0, 0,           // multipliers and helpers: unread
                Abilities.NONE
        );
    }

    // ------------------------------------------------------------------------
    // Copies with one field changed
    // ------------------------------------------------------------------------

    public PlatformerTuning withJumpHeight(float jumpHeight) {
        return new PlatformerTuning(unit, groundSpeed, groundAccelTime, groundStopTime,
                airSpeed, airAccelTime, airStopTime, maxFallSpeed,
                jumpHeight, jumpTimeToPeak, jumpCutGravityMultiplier, fallGravityMultiplier,
                jumpBufferTime, coyoteTime, abilities, jumpApexClearance);
    }

    public PlatformerTuning withAbilities(Abilities abilities) {
        return new PlatformerTuning(unit, groundSpeed, groundAccelTime, groundStopTime,
                airSpeed, airAccelTime, airStopTime, maxFallSpeed,
                jumpHeight, jumpTimeToPeak, jumpCutGravityMultiplier, fallGravityMultiplier,
                jumpBufferTime, coyoteTime, abilities, jumpApexClearance);
    }

    // ------------------------------------------------------------------------
    // Derived values in pixel space, computed on demand
    // ------------------------------------------------------------------------

    /** Continuous apex height in pixels: the design height plus {@link #jumpApexClearance}. */
    private float jumpApexPx() {
        return jumpHeight * unit + jumpApexClearance;
    }

    /** Launch speed in px/s to reach a peak of {@code heightUnits} above the launch point under plain {@link #gravity()}. */
    public float impulseForHeight(float heightUnits) {
        return (float) Math.sqrt(2f * -gravity() * (heightUnits * unit + jumpApexClearance));
    }

    /** Seconds to rise to an apex of {@code heightUnits} and fall back to the launch height under plain {@link #gravity()} (no fall multiplier). */
    public float hangTime(float heightUnits) {
        return 2f * impulseForHeight(heightUnits) / -gravity();
    }

    /** Gravity in px/s^2, negative. Derived so a jump peaks at the apex after {@link #jumpTimeToPeak} seconds. */
    public float gravity() {
        return -2f * jumpApexPx() / (jumpTimeToPeak * jumpTimeToPeak);
    }

    /** Initial jump velocity in px/s. Derived with {@link #gravity()}; the two are a pair, change one via the design fields, never alone. */
    public float jumpImpulse() {
        return  2f * jumpApexPx() / jumpTimeToPeak;
    }

    /** Terminal velocity in px/s, positive; the sweep clamps the downward velocity to its negation. */
    public float maxFallVel() {
        return unit * maxFallSpeed;
    }

    public float maxSpeed(boolean grounded) { return unit * (grounded ? groundSpeed : airSpeed); }
    public float accel(boolean grounded)    { return maxSpeed(grounded) / (grounded ? groundAccelTime : airAccelTime); }
    public float friction(boolean grounded) { return maxSpeed(grounded) / (grounded ? groundStopTime : airStopTime); }
}
