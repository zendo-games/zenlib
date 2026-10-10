package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Timers;

/**
 * Ticks every {@link Timers} once per simulation tick. Actions run inside this system, so a
 * despawn or a component change made by one is deferred by the world's usual rule.
 */
public class TimerSystem extends IteratingSystem {

    public TimerSystem() {
        this(SystemOrder.TIMERS);
    }

    public TimerSystem(int priority) {
        super(Family.all(Timers.TYPE), priority);
    }

    @Override
    protected void process(Entity e, float dt) {
        Timers.get(e).update();
    }
}
