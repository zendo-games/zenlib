package com.zendogames.zenlib.ecs;

/**
 * A unit of work the {@link World} runs in {@link #priority} order, lowest first, with
 * {@link SystemOrder} supplying the slots. Tick systems run once per fixed step from
 * {@link World#update}; render-rate systems ({@link #runsOnTick()} false) run once per frame
 * from {@link World#render}.
 */
public abstract class EntitySystem {

    /** Nonzero, see {@link SystemOrder}; equal priorities run in add order. */
    public final int priority;

    /** Skipped by the world while false. */
    public boolean enabled = true;

    /** Set before {@link #addedToWorld}, null again after {@link #removedFromWorld}. */
    protected World world;

    public EntitySystem(int priority) {
        this.priority = priority;
    }

    /** False for render-rate systems, which {@link World#render} runs instead of the tick. */
    public boolean runsOnTick() {
        return true;
    }

    public void addedToWorld(World world) {}

    public void removedFromWorld(World world) {}

    public abstract void update(float dt);
}
