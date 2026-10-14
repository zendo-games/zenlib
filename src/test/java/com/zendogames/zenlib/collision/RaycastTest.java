package com.zendogames.zenlib.collision;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.collision.shapes.CollisionGrid;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Position;
import org.junit.Test;

import static com.zendogames.zenlib.collision.CollisionMask.of;
import static com.zendogames.zenlib.collision.TestMask.*;
import static org.junit.Assert.*;

/** {@link CollisionWorld#raycast}: the nearest hit over the family, filtered by mask and exclusion, into a caller-owned {@link RayHit}. */
public class RaycastTest {

    private static final float EPS = 1e-4f;

    private static Entity spawn(World world, int x, int y, Collider collider) {
        return world.createEntity().add(new Position(x, y)).add(collider);
    }

    private static Entity wall(World world, int x, int y) {
        return spawn(world, x, y, Collider.rect(SOLID, 0, 0, 10, 10));
    }

    @Test
    public void nearestHitFillsTheResultAndExclusionSkips() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var far  = wall(world, 80, 0);
        var near = wall(world, 40, 0);
        var hit = new RayHit();

        assertTrue(collision.raycast(0, 5, 1, 0, 200, of(SOLID), null, hit));
        assertSame(near, hit.entity);
        assertEquals(40, hit.distance, EPS);
        assertEquals(40, hit.x, EPS);
        assertEquals(5, hit.y, EPS);
        assertEquals(Block.SOLID, hit.block);
        assertEquals(SOLID, hit.mask());
        assertTrue (hit.hits(of(SOLID, PLAYER)));
        assertFalse(hit.hits(of(PLAYER)));

        assertTrue(collision.raycast(0, 5, 1, 0, 200, of(SOLID), near, hit));   // skipping the near one
        assertSame(far, hit.entity);
        assertEquals(80, hit.distance, EPS);
    }

    @Test
    public void clearReadsTheRayEndAndMasksFilter() {
        var world = new World();
        var collision = new CollisionWorld(world);
        spawn(world, 40, 0, Collider.rect(TRIGGER, 0, 0, 10, 10));
        var hit = new RayHit().hit(1, 2, 3, world.createEntity(), Block.SOLID);   // stale reading, must be overwritten

        assertFalse(collision.raycast(0, 5, 1, 0, 100, of(SOLID), null, hit));   // a TRIGGER is not SOLID
        assertFalse(hit.isHit());
        assertNull(hit.entity);
        assertNull(hit.mask());
        assertFalse(hit.hits(of(TRIGGER)));
        assertEquals(Block.NONE, hit.block);
        assertEquals(100, hit.distance, 0);
        assertEquals(100, hit.x, EPS);
        assertEquals(5, hit.y, EPS);

        assertTrue(collision.raycast(0, 5, 1, 0, 100, of(TRIGGER), null, hit));
        assertEquals(TRIGGER, hit.mask());

        assertFalse(collision.raycast(0, 5, 1, 0, 100, 0, null, hit));          // no masks: clear, nothing tested
        assertFalse(hit.isHit());
    }

    @Test
    public void directionIsNormalisedAndMustBeNonZero() {
        var world = new World();
        var collision = new CollisionWorld(world);
        wall(world, 40, 0);
        spawn(world, 30, 40, Collider.rect(SOLID, 0, 0, 10, 10));   // its bottom-left corner (30, 40) is 50 along (3, 4)
        var hit = new RayHit();

        assertTrue(collision.raycast(0, 5, 10, 0, 200, of(SOLID), null, hit));
        assertEquals(40, hit.distance, EPS);
        assertEquals(40, hit.x, EPS);

        assertTrue(collision.raycast(0, 0, 3, 4, 200, of(SOLID), null, hit));
        assertEquals(50, hit.distance, 1e-3);
        assertEquals(30, hit.x, 1e-3);
        assertEquals(40, hit.y, 1e-3);

        assertThrows(GdxRuntimeException.class, () -> collision.raycast(0, 0, 0, 0, 100, of(SOLID), null, hit));
    }

    @Test
    public void rangeIsInclusive() {
        var world = new World();
        var collision = new CollisionWorld(world);
        wall(world, 40, 0);
        var hit = new RayHit();
        assertFalse(collision.raycast(0, 5, 1, 0, 39.9f, of(SOLID), null, hit));
        assertEquals(39.9f, hit.distance, 0);
        assertTrue (collision.raycast(0, 5, 1, 0, 40, of(SOLID), null, hit));
        assertEquals(40, hit.distance, EPS);
    }

    @Test
    public void oneWayIsSeenFromBothSidesAndLosesATieToSolid() {
        for (var solidFirst : new boolean[] { true, false }) {
            var world = new World();
            var collision = new CollisionWorld(world);
            Entity solid = null, platform = null;
            if (solidFirst) solid = spawn(world, 40, 10, Collider.rect(SOLID, 0, 0, 10, 10));   // top edge y 20, x 40..50
            platform = spawn(world, 0, 20, Collider.platform(PLATFORM, 100));                    // line y 20, x 0..100
            if (!solidFirst) solid = spawn(world, 40, 10, Collider.rect(SOLID, 0, 0, 10, 10));
            var hit = new RayHit();
            var masks = of(SOLID, PLATFORM);

            assertTrue(collision.raycast(70, 40, 0, -1, 100, masks, null, hit));    // from above, beside the solid
            assertSame(platform, hit.entity);
            assertEquals(Block.ONE_WAY, hit.block);
            assertEquals(20, hit.distance, EPS);

            assertTrue(collision.raycast(70, 0, 0, 1, 100, masks, null, hit));      // from below: the one-way rule is the mover's, not perception's
            assertSame(platform, hit.entity);
            assertEquals(Block.ONE_WAY, hit.block);
            assertEquals(20, hit.distance, EPS);

            assertTrue(collision.raycast(45, 40, 0, -1, 100, masks, null, hit));    // both at 20: SOLID wins whichever was spawned first
            assertSame(solid, hit.entity);
            assertEquals(Block.SOLID, hit.block);
        }
    }

    @Test
    public void everyShapeKindDispatches() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var circ = spawn(world, 50, 8, Collider.circ(SOLID, 0, 0, 5));                 // center (50, 8): left edge x 45
        var caps = spawn(world, 50, 40, Collider.capsule(SOLID, 0, 0, 10, 20));        // x 50..60, y 40..60
        var grid = spawn(world, 100, 0, Collider.grid(SOLID, 16, 4, 4));
        Collider.get(grid).shape(CollisionGrid.class).set(0, 0, true);                 // x 100..116, y 0..16
        var hit = new RayHit();

        assertTrue(collision.raycast(0, 8, 1, 0, 200, of(SOLID), null, hit));
        assertSame(circ, hit.entity);
        assertEquals(45, hit.distance, EPS);

        assertTrue(collision.raycast(0, 50, 1, 0, 200, of(SOLID), null, hit));
        assertSame(caps, hit.entity);
        assertEquals(50, hit.distance, EPS);

        assertTrue(collision.raycast(70, 8, 1, 0, 200, of(SOLID), null, hit));
        assertSame(grid, hit.entity);
        assertEquals(30, hit.distance, EPS);
    }

    @Test
    public void aDespawnedHitReadsNoMask() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var target = wall(world, 40, 0);
        var hit = new RayHit();
        assertTrue(collision.raycast(0, 5, 1, 0, 100, of(SOLID), null, hit));

        world.despawn(target);                 // outside a system: immediate
        assertTrue(hit.isHit());               // the reading stands until the next cast
        assertNull(hit.mask());                // but nothing is read through the dead handle
        assertFalse(hit.hits(of(SOLID)));
    }
}
