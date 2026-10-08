package com.zendogames.zenlib.ecs;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.ecs.Fixtures.Action;
import com.zendogames.zenlib.ecs.Fixtures.Action2;
import com.zendogames.zenlib.ecs.Fixtures.Pos;
import com.zendogames.zenlib.ecs.Fixtures.Vel;
import org.junit.Before;
import org.junit.Test;

import static com.zendogames.zenlib.ecs.Fixtures.FROZEN;
import static com.zendogames.zenlib.ecs.Fixtures.HURT;
import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

public class FamilyTest {

    private World world;

    @Before
    public void setUp() {
        world = new World();
    }

    @Test
    public void allRequiresEveryKey() {
        var movers = world.family(Family.all(Pos.TYPE, Vel.TYPE));
        var e = world.createEntity();
        assertEquals(0, movers.size());

        e.add(new Pos(0, 0));
        assertEquals(0, movers.size());
        assertFalse(movers.matches(e));

        e.add(new Vel(0, 0));
        assertEquals(1, movers.size());
        assertTrue(movers.contains(e));
        assertSame(e, movers.entities().first());

        e.remove(Pos.TYPE);
        assertEquals(0, movers.size());
        assertFalse(movers.contains(e));
    }

    @Test
    public void oneMatchesAnyOfItsKeys() {
        var drawn = world.family(Family.one(Pos.TYPE, Vel.TYPE));
        var a = world.createEntity().add(new Pos(0, 0));
        var b = world.createEntity().add(new Vel(0, 0));
        var c = world.createEntity();

        assertTrue(drawn.contains(a));
        assertTrue(drawn.contains(b));
        assertFalse(drawn.contains(c));
        assertEquals(2, drawn.size());
    }

    @Test
    public void excludeDropsOnTagAndReturnsOnUntag() {
        var active = world.family(Family.all(Pos.TYPE).exclude(FROZEN));
        var e = world.createEntity().add(new Pos(0, 0));
        assertTrue(active.contains(e));

        e.tag(FROZEN);
        assertFalse(active.contains(e));

        e.untag(FROZEN);
        assertTrue(active.contains(e));
    }

    @Test
    public void tagsMatchLikeComponents() {
        var hurt = world.family(Family.all(HURT));
        var e = world.createEntity();
        assertFalse(hurt.contains(e));

        e.tag(HURT);
        assertTrue(hurt.contains(e));
    }

    @Test
    public void emptyFilterMatchesEveryEntityIncludingFreshOnes() {
        var everything = world.family(Family.all());
        var e = world.createEntity();

        assertTrue(everything.contains(e));
        assertEquals(1, everything.size());
    }

    @Test
    public void equalFiltersResolveToOneFamilyPerWorld() {
        var a = world.family(Family.all(Pos.TYPE, Vel.TYPE).exclude(FROZEN));
        var b = world.family(Family.all(Vel.TYPE, Pos.TYPE).exclude(FROZEN));
        var c = world.family(Family.all(Pos.TYPE, Vel.TYPE));
        var other = new World().family(Family.all(Pos.TYPE, Vel.TYPE).exclude(FROZEN));

        assertSame(a, b);
        assertNotSame(a, c);
        assertNotSame(a, other);
    }

    @Test
    public void existingEntitiesJoinANewFamily() {
        var e = world.createEntity().add(new Pos(0, 0));
        var family = world.family(Family.all(Pos.TYPE));

        assertEquals(1, family.size());
        assertTrue(family.contains(e));
    }

    @Test
    public void hooksFireOnJoinAndLeave() {
        var family = world.family(Family.all(Pos.TYPE));
        var joined = new Array<Entity>();
        var left = new Array<Entity>();
        family.onAdd(joined::add);
        family.onRemove(left::add);

        var e = world.createEntity();
        assertEquals(0, joined.size);

        e.add(new Pos(0, 0));
        assertEquals(1, joined.size);
        assertSame(e, joined.first());
        assertTrue(family.contains(e)); // listener saw it as a member

        e.remove(Pos.TYPE);
        assertEquals(1, left.size);
        assertSame(e, left.first());
    }

    @Test
    public void despawnLeavesFamilyWithComponentsStillAttached() {
        var family = world.family(Family.all(Pos.TYPE));
        var seen = new Array<Pos>();
        family.onRemove(e -> seen.add(Pos.get(e)));

        var e = world.createEntity().add(new Pos(7, 7));
        world.despawn(e);

        assertEquals(1, seen.size);
        assertEquals(7, seen.first().x);
        assertEquals(0, family.size());
    }

    @Test
    public void membershipIsDeferredUntilTheCurrentSystemReturns() {
        var family = world.family(Family.all(Pos.TYPE));
        var e = world.createEntity();
        var sizeDuringA = new int[1];
        var sizeDuringB = new int[1];

        world.addSystem(new Action(SystemOrder.INPUT, (w, dt) -> {
            e.add(new Pos(0, 0));
            sizeDuringA[0] = family.size();
        }));
        world.addSystem(new Action2(SystemOrder.SENSE, (w, dt) -> sizeDuringB[0] = family.size()));

        world.update(FIXED_DT);

        assertEquals(0, sizeDuringA[0]);
        assertEquals(1, sizeDuringB[0]);
        assertTrue(family.contains(e));
    }

    @Test
    public void changesInsideASystemCollapseToOneRefresh() {
        var family = world.family(Family.all(Pos.TYPE));
        var joins = new int[1];
        family.onAdd(e -> joins[0]++);
        var e = world.createEntity();

        world.addSystem(new Action(SystemOrder.INPUT, (w, dt) -> {
            e.add(new Pos(0, 0));
            e.tag(HURT);
            e.add(new Vel(0, 0));
        }));
        world.update(FIXED_DT);

        assertEquals(1, joins[0]);
    }

    @Test
    public void hookThatChangesCompositionSettlesInTheSameFlush() {
        var hasPos = world.family(Family.all(Pos.TYPE));
        var hasVel = world.family(Family.all(Vel.TYPE));
        hasPos.onAdd(e -> e.add(new Vel(1, 1)));
        var e = world.createEntity();

        world.addSystem(new Action(SystemOrder.INPUT, (w, dt) -> e.add(new Pos(0, 0))));
        world.update(FIXED_DT);

        assertTrue(hasPos.contains(e));
        assertTrue(hasVel.contains(e));
    }

    @Test
    public void entitiesKeepJoinOrder() {
        var family = world.family(Family.all(Pos.TYPE));
        var a = world.createEntity().add(new Pos(0, 0));
        var b = world.createEntity().add(new Pos(0, 0));
        var c = world.createEntity().add(new Pos(0, 0));
        b.remove(Pos.TYPE);
        b.add(new Pos(0, 0));

        var entities = family.entities();
        assertEquals(3, entities.size);
        assertSame(a, entities.get(0));
        assertSame(c, entities.get(1));
        assertSame(b, entities.get(2));
    }
}
