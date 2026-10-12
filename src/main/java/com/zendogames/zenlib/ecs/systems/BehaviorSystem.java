package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Behavior;

/**
 * Ticks every {@link Behavior}'s state machine with the entity as context. Defaults to
 * {@link SystemOrder#CONTROL}: states see this tick's input and last tick's physics facts
 * (grounded, landed, blocks) and write velocity and animation choices before the physics step.
 * A game that wants its states to react to this tick's facts constructs one at
 * {@code PHYSICS + n} instead. States that despawn or change components are safe: the ECS
 * defers membership changes until this system returns.
 */
public class BehaviorSystem extends IteratingSystem {

    public BehaviorSystem() {
        this(SystemOrder.CONTROL);
    }

    public BehaviorSystem(int priority) {
        super(Family.all(Behavior.TYPE), priority);
    }

    @Override
    protected void process(Entity e, float dt) {
        Behavior.get(e).update(e, dt);
    }
}
