package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Lifetime;

/** Counts every {@link Lifetime} down one per tick and despawns the entity when it reaches zero. */
public class LifetimeSystem extends IteratingSystem {

    public LifetimeSystem() {
        super(Family.all(Lifetime.TYPE), SystemOrder.TIMERS);
    }

    @Override
    protected void process(Entity e, float dt) {
        var lifetime = Lifetime.get(e);

        lifetime.ticks--;
        if (lifetime.ticks <= 0) {
            world.despawn(e);
        }
    }
}
