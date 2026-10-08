package com.zendogames.zenlib.ecs;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.ecs.Fixtures.Pos;
import com.zendogames.zenlib.ecs.Fixtures.Vel;
import org.junit.Before;
import org.junit.Test;

import static com.zendogames.zenlib.ecs.Fixtures.FROZEN;
import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

public class IteratingSystemTest {

    /** Moves every unfrozen entity with a position and velocity. */
    static final class MoveSystem extends IteratingSystem {
        final Array<Entity> processed = new Array<>();
        float lastDt = Float.NaN;

        MoveSystem() {
            super(Family.all(Pos.TYPE, Vel.TYPE).exclude(FROZEN), SystemOrder.PHYSICS);
        }

        @Override
        protected void process(Entity e, float dt) {
            processed.add(e);
            lastDt = dt;
            var pos = Pos.get(e);
            var vel = Vel.get(e);
            pos.x += vel.dx;
            pos.y += vel.dy;
        }
    }

    /** Despawns everything it processes, the LifetimeSystem shape. */
    static final class ReaperSystem extends IteratingSystem {
        int processed = 0;

        ReaperSystem() {
            super(Family.all(Pos.TYPE), SystemOrder.TIMERS);
        }

        @Override
        protected void process(Entity e, float dt) {
            processed++;
            world.despawn(e);
            assertTrue(e.isAlive());
        }
    }

    /** Strips the component its own family requires, which must not disturb the loop. */
    static final class StripSystem extends IteratingSystem {
        int processed = 0;

        StripSystem() {
            super(Family.all(Vel.TYPE), SystemOrder.CONTROL);
        }

        @Override
        protected void process(Entity e, float dt) {
            processed++;
            e.remove(Vel.TYPE);
            assertEquals(family().size(), family().entities().size);
        }
    }

    private World world;

    @Before
    public void setUp() {
        world = new World();
    }

    @Test
    public void processesEachMemberWithTheTickDelta() {
        var move = new MoveSystem();
        world.addSystem(move);
        var a = world.createEntity().add(new Pos(0, 0)).add(new Vel(1, 2));
        var b = world.createEntity().add(new Pos(10, 10)).add(new Vel(-1, 0));
        var still = world.createEntity().add(new Pos(5, 5));

        world.update(FIXED_DT);

        assertEquals(Array.with(a, b), move.processed);
        assertEquals(FIXED_DT, move.lastDt, 0f);
        assertEquals(1, Pos.get(a).x);
        assertEquals(2, Pos.get(a).y);
        assertEquals(9, Pos.get(b).x);
        assertEquals(5, Pos.get(still).x);
    }

    @Test
    public void familyResolvesOnAddAndClearsOnRemove() {
        var move = new MoveSystem();
        assertNull(move.family());

        world.addSystem(move);
        assertNotNull(move.family());
        assertSame(world.family(Family.all(Pos.TYPE, Vel.TYPE).exclude(FROZEN)), move.family());

        world.removeSystem(move);
        assertNull(move.family());
    }

    @Test
    public void excludedTagSkipsTheEntityFromTheNextTick() {
        var move = new MoveSystem();
        world.addSystem(move);
        var e = world.createEntity().add(new Pos(0, 0)).add(new Vel(1, 0));

        world.update(FIXED_DT);
        e.tag(FROZEN);
        world.update(FIXED_DT);
        e.untag(FROZEN);
        world.update(FIXED_DT);

        assertEquals(2, Pos.get(e).x);
        assertEquals(2, move.processed.size);
    }

    @Test
    public void despawningDuringProcessVisitsEveryMemberOnce() {
        var reaper = new ReaperSystem();
        world.addSystem(reaper);
        var a = world.createEntity().add(new Pos(0, 0));
        var b = world.createEntity().add(new Pos(0, 0));
        var c = world.createEntity().add(new Pos(0, 0));

        world.update(FIXED_DT);

        assertEquals(3, reaper.processed);
        assertFalse(a.isAlive());
        assertFalse(b.isAlive());
        assertFalse(c.isAlive());
        assertEquals(0, reaper.family().size());
        assertEquals(0, world.entities().size);

        world.update(FIXED_DT);
        assertEquals(3, reaper.processed);
    }

    @Test
    public void removingTheRequiredComponentDuringProcessIsSafe() {
        var strip = new StripSystem();
        world.addSystem(strip);
        world.createEntity().add(new Vel(0, 0));
        world.createEntity().add(new Vel(0, 0));

        world.update(FIXED_DT);
        assertEquals(2, strip.processed);
        assertEquals(0, strip.family().size());

        world.update(FIXED_DT);
        assertEquals(2, strip.processed);
    }
}
