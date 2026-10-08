package com.zendogames.zenlib.ecs;

/**
 * Priority slots for {@link EntitySystem}. Games place their systems at {@code SLOT + n};
 * {@link World#addSystem} rejects a priority of zero so every system states its slot.
 */
public final class SystemOrder {
    // @formatter:off
    public static final int INPUT   = 100; // devices -> Input (skipping AI-driven entities)
    public static final int SENSE   = 200; // perceive the world as it is now
    public static final int DECIDE  = 300; // AI -> Input
    public static final int TIMERS  = 400; // cooldowns, interpolation, coyote/buffer countdowns
    public static final int CONTROL = 500; // input + facts -> velocity changes, gravity, accel, friction
    public static final int PHYSICS = 600; // integrate, collide, then recompute grounded and fire landing
    public static final int PRESENT = 700; // animation, view: presentation layer sees final positions
    public static final int MANUAL  = Integer.MAX_VALUE; // render-rate systems (runsOnTick() false), driven by World.render
    // @formatter:on

    private SystemOrder() {}
}