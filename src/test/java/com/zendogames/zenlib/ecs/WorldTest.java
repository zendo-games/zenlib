package com.zendogames.zenlib.ecs;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.ecs.Fixtures.Action;
import com.zendogames.zenlib.ecs.Fixtures.Action2;
import com.zendogames.zenlib.ecs.Fixtures.Hooked;
import com.zendogames.zenlib.ecs.Fixtures.Pos;
import com.zendogames.zenlib.ecs.Fixtures.Recorder;
import com.zendogames.zenlib.ecs.Fixtures.RecorderA;
import com.zendogames.zenlib.ecs.Fixtures.RecorderB;
import com.zendogames.zenlib.ecs.Fixtures.RecorderC;
import com.zendogames.zenlib.ecs.Fixtures.RenderRecorder;
import org.junit.Before;
import org.junit.Test;

import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

public class WorldTest {

    private World world;
    private Array<String> log;

    @Before
    public void setUp() {
        world = new World();
        log = new Array<>();
    }

    // ------------------------------------------------------------------------
    // Entities and ids
    // ------------------------------------------------------------------------

    @Test
    public void idsAreSequentialThenRecycled() {
        var a = world.createEntity();
        var b = world.createEntity();
        var c = world.createEntity();
        assertEquals(0, a.id);
        assertEquals(1, b.id);
        assertEquals(2, c.id);
        assertEquals(3, world.entities().size);

        world.despawn(b);
        assertEquals(2, world.entities().size);
        assertFalse(world.entities().contains(b, true));

        var d = world.createEntity();
        assertEquals(1, d.id);
        assertNotSame(b, d);
        assertEquals(3, world.createEntity().id);
    }

    @Test
    public void despawnOutsideASystemIsImmediateAndRunsRemoveHooks() {
        var hooked = new Hooked();
        var e = world.createEntity().add(hooked);

        world.despawn(e);

        assertFalse(e.isAlive());
        assertEquals(1, hooked.removed);
        assertSame(e, hooked.removedFrom);
        assertNull(hooked.entity());
    }

    @Test
    public void despawnTwiceIsANoop() {
        var hooked = new Hooked();
        var e = world.createEntity().add(hooked);
        world.despawn(e);
        world.despawn(e);
        assertEquals(1, hooked.removed);
        assertEquals(0, world.entities().size);
    }

    @Test
    public void despawnInsideASystemWaitsForThatSystemToReturn() {
        var e = world.createEntity().add(new Pos(5, 5));
        var aliveInA = new boolean[1];
        var xInA = new int[1];
        var aliveInB = new boolean[1];

        world.addSystem(new Action(SystemOrder.INPUT, (w, dt) -> {
            w.despawn(e);
            aliveInA[0] = e.isAlive();
            xInA[0] = Pos.get(e).x; // still readable
            assertTrue(e.isRemoving());
            assertEquals(1, w.entities().size);
        }));
        world.addSystem(new Action2(SystemOrder.SENSE, (w, dt) -> aliveInB[0] = e.isAlive()));

        world.update(FIXED_DT);

        assertTrue(aliveInA[0]);
        assertEquals(5, xInA[0]);
        assertFalse(aliveInB[0]);
        assertEquals(0, world.entities().size);
    }

    @Test
    public void createEntityInsideASystemIsVisibleAtOnce() {
        var created = new Entity[1];
        world.addSystem(new Action(SystemOrder.INPUT, (w, dt) -> {
            created[0] = w.createEntity();
            assertTrue(w.entities().contains(created[0], true));
        }));
        world.update(FIXED_DT);
        assertTrue(created[0].isAlive());
        assertEquals(1, world.entities().size);
    }

    @Test
    public void componentStorageGrowsWithEntityCount() {
        var entities = new Array<Entity>();
        for (int i = 0; i < 300; i++) {
            entities.add(world.createEntity().add(new Pos(i, -i)));
        }
        for (int i = 0; i < 300; i++) {
            var pos = Pos.get(entities.get(i));
            assertEquals(i, pos.x);
            assertEquals(-i, pos.y);
        }
    }

    @Test
    public void twoWorldsKeepSeparateStorageForTheSameIds() {
        var other = new World();
        var a = world.createEntity().add(new Pos(1, 0));
        var b = other.createEntity().add(new Pos(2, 0));
        assertEquals(a.id, b.id);

        assertEquals(1, Pos.get(a).x);
        assertEquals(2, Pos.get(b).x);
        other.despawn(b);
        assertEquals(1, Pos.get(a).x);
    }

    // ------------------------------------------------------------------------
    // Systems
    // ------------------------------------------------------------------------

    @Test
    public void systemsRunByPriorityWithTiesInAddOrder() {
        world.addSystem(new RecorderC(SystemOrder.PHYSICS, log));
        world.addSystem(new RecorderA(SystemOrder.INPUT, log));
        world.addSystem(new RecorderB(SystemOrder.INPUT, log));

        world.update(FIXED_DT);

        assertEquals(Array.with("A", "B", "C"), log);
        assertEquals("A", ((Recorder) world.systems().get(0)).name);
    }

    @Test
    public void priorityZeroIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> world.addSystem(new RecorderA(0, log)));
        assertEquals(0, world.systems().size);
    }

    @Test
    public void oneSystemPerClass() {
        world.addSystem(new RecorderA(SystemOrder.INPUT, log));
        assertThrows(IllegalStateException.class, () -> world.addSystem(new RecorderA(SystemOrder.SENSE, log)));
        assertEquals(1, world.systems().size);
    }

    @Test
    public void getSystemFindsByClassOrSuperclass() {
        var a = new RecorderA(SystemOrder.INPUT, log);
        world.addSystem(a);

        assertSame(a, world.getSystem(RecorderA.class));
        assertSame(a, world.getSystem(Recorder.class));
        assertNull(world.getSystem(RecorderB.class));
    }

    @Test
    public void addedToWorldSetsWorldAndRemoveClearsIt() {
        var a = new RecorderA(SystemOrder.INPUT, log);
        assertNull(a.world);

        world.addSystem(a);
        assertSame(world, a.world);

        world.removeSystem(a);
        assertNull(a.world);
        assertEquals(0, world.systems().size);

        world.update(FIXED_DT);
        assertEquals(0, a.updates);
    }

    @Test
    public void disabledSystemIsSkipped() {
        var a = new RecorderA(SystemOrder.INPUT, log);
        world.addSystem(a);
        a.enabled = false;

        world.update(FIXED_DT);
        assertEquals(0, a.updates);

        a.enabled = true;
        world.update(FIXED_DT);
        assertEquals(1, a.updates);
    }

    @Test
    public void renderSystemsRunFromRenderNotUpdate() {
        var tick = new RecorderA(SystemOrder.PRESENT, log);
        var render = new RenderRecorder(log);
        world.addSystem(render);
        world.addSystem(tick);

        world.update(FIXED_DT);
        assertEquals(Array.with("A"), log);

        world.render(0.5f * FIXED_DT);
        assertEquals(Array.with("A", "R"), log);
        assertEquals(0.5f * FIXED_DT, render.lastDt, 0f);
        assertEquals(1, tick.updates);
    }

    @Test
    public void updateTicksOncePerFixedStep() {
        var a = new RecorderA(SystemOrder.INPUT, log);
        world.addSystem(a);

        world.update(FIXED_DT);
        assertEquals(1, a.updates);
        assertEquals(FIXED_DT, a.lastDt, 0f);
        assertEquals(0f, world.alpha(), 1e-6f);

        world.update(2 * FIXED_DT);
        assertEquals(3, a.updates);

        world.update(0.5f * FIXED_DT);
        assertEquals(3, a.updates);
        assertEquals(0.5f, world.alpha(), 1e-5f);
    }

    @Test
    public void updateFromInsideASystemIsRejected() {
        world.addSystem(new Action(SystemOrder.INPUT, (w, dt) -> {
            assertTrue(w.isUpdating());
            assertThrows(IllegalStateException.class, () -> w.update(FIXED_DT));
            assertThrows(IllegalStateException.class, () -> w.render(FIXED_DT));
            assertThrows(IllegalStateException.class, w::clear);
        }));
        world.update(FIXED_DT);
        assertFalse(world.isUpdating());
    }

    @Test
    public void systemThatThrowsLeavesTheWorldUsable() {
        world.addSystem(new Action(SystemOrder.INPUT, (w, dt) -> { throw new RuntimeException("boom"); }));
        assertThrows(RuntimeException.class, () -> world.update(FIXED_DT));
        assertFalse(world.isUpdating());
        world.removeSystem(world.getSystem(Action.class));
        world.update(FIXED_DT);
    }

    // ------------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------------

    @Test
    public void clearDestroysEntitiesResetsIdsAndKeepsSystemsAndFamilies() {
        var hooked = new Hooked();
        var family = world.family(Family.all(Pos.TYPE));
        var a = new RecorderA(SystemOrder.INPUT, log);
        world.addSystem(a);
        var e1 = world.createEntity().add(hooked).add(new Pos(0, 0));
        var e2 = world.createEntity().add(new Pos(0, 0));
        assertEquals(2, family.size());

        world.clear();

        assertFalse(e1.isAlive());
        assertFalse(e2.isAlive());
        assertEquals(1, hooked.removed);
        assertEquals(0, world.entities().size);
        assertEquals(0, family.size());
        assertEquals(1, world.systems().size);
        assertSame(world, a.world);

        var fresh = world.createEntity().add(new Pos(0, 0));
        assertEquals(0, fresh.id);
        assertEquals(1, world.createEntity().id);
        assertTrue(family.contains(fresh));
        assertEquals(1, family.size());
    }

    @Test
    public void disposeClearsAndRemovesSystemsInReverseOrder() {
        var removed = new Array<String>();
        var a = new RecorderA(SystemOrder.INPUT, log) {
            @Override public void removedFromWorld(World w) { removed.add("A"); }
        };
        var b = new RecorderB(SystemOrder.SENSE, log) {
            @Override public void removedFromWorld(World w) { removed.add("B"); }
        };
        world.addSystem(a);
        world.addSystem(b);
        var e = world.createEntity();

        world.dispose();

        assertEquals(Array.with("B", "A"), removed);
        assertEquals(0, world.systems().size);
        assertFalse(e.isAlive());
        assertNull(a.world);
        assertNull(b.world);
    }
}
