package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Lifetime;
import org.junit.Test;

import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

public class LifetimeSystemTest {

    @Test
    public void secondsBecomeTicks() {
        assertEquals(60, new Lifetime(1f).ticks);
        assertEquals(30, new Lifetime(0.5f).ticks);
        assertEquals(1, new Lifetime(0f).ticks);          // never zero: at least one tick alive
        assertEquals(5, Lifetime.ofTicks(5).ticks);
    }

    @Test
    public void despawnsWhenTicksRunOut() {
        var world = new World();
        world.addSystem(new LifetimeSystem());
        var e = world.createEntity().add(Lifetime.ofTicks(3));

        world.update(FIXED_DT);
        world.update(FIXED_DT);
        assertTrue(e.isAlive());
        assertEquals(1, Lifetime.get(e).ticks);

        world.update(FIXED_DT);
        assertFalse(e.isAlive());
        assertEquals(0, world.entities().size);
    }

    @Test
    public void replacingLifetimeRestartsTheCount() {
        var world = new World();
        world.addSystem(new LifetimeSystem());
        var e = world.createEntity().add(Lifetime.ofTicks(1));

        e.add(Lifetime.ofTicks(2)); // replaces, old one detached
        world.update(FIXED_DT);
        assertTrue(e.isAlive());
        world.update(FIXED_DT);
        assertFalse(e.isAlive());
    }
}
