package com.zendogames.zenlib.ecs.components;

import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.sim.SimulationStep;

/** Ticks until {@code LifetimeSystem} despawns the entity. Seconds authored, ticks stored. */
public class Lifetime implements Component {

    public static final ComponentType<Lifetime> TYPE = ComponentType.register(Lifetime.class);
    public static Lifetime get(Entity e) { return TYPE.get(e); }
    public static boolean  has(Entity e) { return TYPE.has(e); }

    public int ticks;

    public Lifetime(float seconds) {
        this.ticks = SimulationStep.ticks(seconds);
    }

    /** For callers that already count in ticks; the seconds constructor is the usual way in. */
    public static Lifetime ofTicks(int ticks) {
        var lifetime = new Lifetime(0f);
        lifetime.ticks = ticks;
        return lifetime;
    }
}
