package com.zendogames.zenlib.ecs;

import com.zendogames.zenlib.ecs.Fixtures.Hooked;
import com.zendogames.zenlib.ecs.Fixtures.Pos;
import com.zendogames.zenlib.ecs.Fixtures.Vel;
import org.junit.Before;
import org.junit.Test;

import static com.zendogames.zenlib.ecs.Fixtures.FROZEN;
import static com.zendogames.zenlib.ecs.Fixtures.HURT;
import static org.junit.Assert.*;

public class EntityTest {

    private World world;
    private Entity entity;

    @Before
    public void setUp() {
        world = new World();
        entity = world.createEntity();
    }

    @Test
    public void typesAndTagsGetDistinctMaskBits() {
        assertNotEquals(Pos.TYPE.index, Vel.TYPE.index);
        assertNotEquals(Pos.TYPE.index, Hooked.TYPE.index);
        assertNotEquals(Pos.TYPE.index, HURT.index);
        assertNotEquals(HURT.index, FROZEN.index);
        assertEquals("Pos", Pos.TYPE.toString());
        assertEquals("Hurt", HURT.toString());
    }

    @Test
    public void addThenGetAndHas() {
        var pos = new Pos(3, 4);
        assertSame(entity, entity.add(pos));

        assertTrue(entity.has(Pos.TYPE));
        assertTrue(Pos.has(entity));
        assertSame(pos, entity.get(Pos.TYPE));
        assertSame(pos, Pos.get(entity));
        assertSame(Pos.TYPE, pos.type());
    }

    @Test
    public void missingComponentIsNullAndHasIsFalse() {
        assertNull(entity.get(Pos.TYPE));
        assertFalse(entity.has(Pos.TYPE));
        assertNull(entity.remove(Pos.TYPE));
    }

    @Test
    public void componentsOfDifferentTypesCoexist() {
        var pos = new Pos(1, 2);
        var vel = new Vel(3, 4);
        entity.add(pos).add(vel);

        assertSame(pos, entity.get(Pos.TYPE));
        assertSame(vel, entity.get(Vel.TYPE));
    }

    @Test
    public void removeReturnsComponentRunsHookAndClearsSlot() {
        var hooked = new Hooked();
        entity.add(hooked);
        assertEquals(1, hooked.added);
        assertEquals(0, hooked.removed);

        var removed = entity.remove(Hooked.TYPE);

        assertSame(hooked, removed);
        assertEquals(1, hooked.removed);
        assertSame(entity, hooked.removedFrom);
        assertNull(entity.get(Hooked.TYPE));
        assertFalse(entity.has(Hooked.TYPE));
    }

    @Test
    public void addingSameTypeReplacesAndRemovesTheOldOneFirst() {
        var first = new Hooked();
        var second = new Hooked();
        entity.add(first);
        entity.add(second);

        assertSame(second, entity.get(Hooked.TYPE));
        assertEquals(1, first.removed);
        assertSame(entity, first.removedFrom);
        assertEquals(1, second.added);
    }

    /** The DEBUG_ECS path in {@code Entity.add}; the flag itself can't be toggled headless (it logs), so the check is called directly. */
    @Test
    public void debugGuardRejectsAnInstanceAlreadyInStorage() {
        var pos = new Pos(0, 0);
        var free = new Pos(1, 1);
        entity.add(pos);

        world.requireUnattached(Pos.TYPE, free);                        // not stored anywhere: fine
        world.requireUnattached(Vel.TYPE, new Vel(0, 0));               // type with no storage yet: fine
        var ex = assertThrows(IllegalStateException.class, () -> world.requireUnattached(Pos.TYPE, pos));
        assertTrue(ex.getMessage(), ex.getMessage().contains("Entity#" + entity.id));

        entity.remove(Pos.TYPE);
        world.requireUnattached(Pos.TYPE, pos);                         // detached again: fine
    }

    /** The lookup is by exact class, so a class without its own registered TYPE (a subclass of one included) fails at add. */
    @Test
    public void unregisteredClassIsRejectedAtAdd() {
        var unregistered = new Component() {};
        var ex = assertThrows(IllegalStateException.class, () -> entity.add(unregistered));
        assertTrue(ex.getMessage(), ex.getMessage().contains("not a registered component type"));
        assertEquals(0, countComponents());
    }

    private int countComponents() {
        int n = 0;
        for (var key : new Family.Key[] { Pos.TYPE, Vel.TYPE, Hooked.TYPE }) {
            if (entity.mask.get(key.index())) n++;
        }
        return n;
    }

    @Test
    public void removedComponentCanBeAddedElsewhere() {
        var pos = new Pos(0, 0);
        entity.add(pos);
        entity.remove(Pos.TYPE);

        var other = world.createEntity();
        other.add(pos);

        assertNull(entity.get(Pos.TYPE));
        assertSame(pos, other.get(Pos.TYPE));
    }

    @Test
    public void tagsSetClearAndQuery() {
        assertFalse(entity.hasTag(HURT));

        entity.tag(HURT);
        assertTrue(entity.hasTag(HURT));
        assertFalse(entity.hasTag(FROZEN));

        entity.tag(HURT); // idempotent
        assertTrue(entity.hasTag(HURT));

        entity.untag(HURT);
        assertFalse(entity.hasTag(HURT));
        entity.untag(HURT); // idempotent
    }

    @Test
    public void deadHandleThrowsOnGetAndMutation() {
        entity.add(new Pos(0, 0)).tag(HURT);
        world.despawn(entity);

        assertFalse(entity.isAlive());
        assertFalse(entity.has(Pos.TYPE));
        assertFalse(entity.hasTag(HURT));
        assertThrows(IllegalStateException.class, () -> entity.get(Pos.TYPE));
        assertThrows(IllegalStateException.class, () -> entity.add(new Vel(0, 0)));
        assertThrows(IllegalStateException.class, () -> entity.remove(Pos.TYPE));
        assertThrows(IllegalStateException.class, () -> entity.tag(FROZEN));
        assertThrows(IllegalStateException.class, () -> entity.untag(HURT));
    }

    @Test
    public void deadHandleDoesNotAliasTheIdsNextOccupant() {
        entity.add(new Pos(1, 1));
        world.despawn(entity);

        var next = world.createEntity();
        assertEquals(entity.id, next.id);
        next.add(new Pos(2, 2));

        assertEquals(2, Pos.get(next).x);
        assertThrows(IllegalStateException.class, () -> Pos.get(entity));
        assertFalse(Pos.has(entity));
    }

    @Test
    public void worldAndToString() {
        assertSame(world, entity.world());
        assertEquals("Entity#" + entity.id, entity.toString());
    }
}
