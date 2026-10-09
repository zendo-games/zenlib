package com.zendogames.zenlib.collision;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.collision.shapes.CollisionRect;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Position;
import org.junit.Test;

import static com.zendogames.zenlib.collision.TestMask.*;
import static org.junit.Assert.*;

public class CollisionWorldTest {

    private static Entity spawn(World world, int x, int y, Collider collider) {
        return world.createEntity().add(new Position(x, y)).add(collider);
    }

    /** A 10x10 PLAYER box blocked by SOLID and PLATFORM, detecting TRIGGER and PLAYER. */
    private static Entity player(World world, int x, int y) {
        return spawn(world, x, y, Collider.rect(PLAYER, 0, 0, 10, 10).blockedBy(SOLID, PLATFORM).detects(TRIGGER, PLAYER));
    }

    @Test
    public void maskBits() {
        assertEquals(0b0001, SOLID.bit());
        assertEquals(0b1000, TRIGGER.bit());
        assertEquals(0b0101, CollisionMask.of(SOLID, PLAYER));
        assertEquals(0, CollisionMask.of());
        assertTrue (PLAYER.in(CollisionMask.of(SOLID, PLAYER)));
        assertFalse(TRIGGER.in(CollisionMask.of(SOLID, PLAYER)));
    }

    @Test
    public void colliderCopiesAndFactories() {
        var base = Collider.rect(PLAYER, 0, 0, 10, 10);
        assertEquals(0, base.blockedBy());
        assertEquals(0, base.detects());

        var full = base.blockedBy(SOLID).detects(TRIGGER);
        assertEquals(0, base.blockedBy());               // the original is untouched
        assertTrue (full.isBlockedBy(SOLID));
        assertFalse(full.isBlockedBy(PLAYER));
        assertTrue (full.canDetect(TRIGGER));
        assertSame (base.shape(), full.shape());         // copies share the shape instance

        assertNotNull(full.shape(CollisionRect.class));
        assertThrows(com.badlogic.gdx.utils.GdxRuntimeException.class, () -> full.shape(com.zendogames.zenlib.collision.shapes.CollisionCirc.class));
        assertThrows(com.badlogic.gdx.utils.GdxRuntimeException.class, () -> new Collider(null, PLAYER));

        var platform = Collider.platform(PLATFORM, 64);
        var seg = platform.shape(com.zendogames.zenlib.collision.shapes.CollisionAASeg.class);
        assertTrue(seg.isHorizontal());
        assertEquals(64f, seg.length(), 0f);
        assertEquals(com.zendogames.zenlib.collision.shapes.CollisionAASeg.Facing.POSITIVE, seg.facing());

        var grid = Collider.grid(SOLID, 16, 3, 2).shape(com.zendogames.zenlib.collision.shapes.CollisionGrid.class);
        assertEquals(3, grid.cols());
        assertEquals(2, grid.rows());
    }

    @Test
    public void probeSolidAndLastContact() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var wall = spawn(world, 20, 0, Collider.rect(SOLID, 0, 0, 10, 10));
        var mover = player(world, 10, 0);                // flush against the wall

        assertEquals(Block.NONE, collision.probe(mover, 0, 0));   // excludes itself
        assertNull(collision.lastContact());
        assertEquals(Block.SOLID, collision.probe(mover, 1, 0));
        assertSame(wall, collision.lastContact());
        assertEquals(Block.NONE, collision.probe(mover, -1, 0));
        assertNull(collision.lastContact());                      // reset by every probe
    }

    @Test
    public void probeFiltersByBlockedByAndSkipsEmptyFilters() {
        var world = new World();
        var collision = new CollisionWorld(world);
        spawn(world, 20, 0, Collider.rect(TRIGGER, 0, 0, 10, 10));   // not in the player's blockedBy
        var mover = player(world, 10, 0);
        assertEquals(Block.NONE, collision.probe(mover, 1, 0));

        var shape = Collider.get(mover).shape();
        assertEquals(Block.NONE, collision.probe(shape, 10, 0, 1, 0, 0, mover));                       // nothing blocks
        assertEquals(Block.SOLID, collision.probe(shape, 10, 0, 1, 0, CollisionMask.of(TRIGGER), mover)); // same wall, now it does
    }

    @Test
    public void probeOneWayIsGeometry() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var platform = spawn(world, 0, 10, Collider.platform(PLATFORM, 100));   // line y = 10
        var mover = player(world, 20, 10);                                       // standing on it

        assertEquals(Block.ONE_WAY, collision.probe(mover, 0, -1));
        assertSame(platform, collision.lastContact());
        assertEquals(Block.NONE, collision.probe(mover, 1, 0));                 // walking along it

        Position.get(mover).set(20, 5);                                          // straddling, rising
        assertEquals(Block.NONE, collision.probe(mover, 0, 1));
        assertNull(collision.lastContact());

        Position.get(mover).set(20, 0);                                          // underneath, rising into it
        assertEquals(Block.NONE, collision.probe(mover, 0, 1));
    }

    @Test
    public void solidWinsOverOneWay() {
        var world = new World();
        var collision = new CollisionWorld(world);
        spawn(world, 0, 10, Collider.platform(PLATFORM, 100));                  // first in family order
        var floor = spawn(world, 0, 0, Collider.rect(SOLID, 0, 0, 100, 10));    // top at y = 10 too
        var mover = player(world, 20, 10);

        assertEquals(Block.SOLID, collision.probe(mover, 0, -1));
        assertSame(floor, collision.lastContact());
    }

    @Test
    public void overlapQueries() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var trigger = spawn(world, 0, 0, Collider.rect(TRIGGER, 0, 0, 50, 50));
        var other   = player(world, 5, 5);
        spawn(world, 100, 100, Collider.rect(SOLID, 0, 0, 10, 10));
        var mover = player(world, 0, 0);

        var out = new Array<Entity>();
        collision.detects(mover, out);
        assertEquals(2, out.size);
        assertSame(trigger, out.get(0));                 // family order
        assertSame(other, out.get(1));

        var shape = Collider.get(mover).shape();
        assertSame (trigger, collision.first(shape, 0, 0, 0, 0, CollisionMask.of(TRIGGER), mover));
        assertNull (collision.first(shape, 0, 0, 0, 0, CollisionMask.of(SOLID), mover));
        assertTrue (collision.any(shape, 95, 95, 0, 0, CollisionMask.of(SOLID), mover));
        assertFalse(collision.any(shape, 90, 90, 0, 0, CollisionMask.of(SOLID), mover));  // flush
        assertFalse(collision.any(shape, 0, 0, 0, 0, 0, mover));                           // empty filter

        out.clear();
        collision.all(shape, 0, 0, 0, 0, CollisionMask.of(PLAYER), null, out);            // nothing excluded: itself too
        assertEquals(2, out.size);
    }

    @Test
    public void familyIsLive() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var wall = spawn(world, 20, 0, Collider.rect(SOLID, 0, 0, 10, 10));
        var mover = player(world, 10, 0);
        assertEquals(2, collision.family().size());

        world.despawn(wall);
        assertEquals(Block.NONE, collision.probe(mover, 1, 0));

        var late = world.createEntity().add(Collider.rect(SOLID, 0, 0, 10, 10));   // no Position yet: not collidable
        assertEquals(Block.NONE, collision.probe(mover, 1, 0));
        late.add(new Position(20, 0));
        assertEquals(Block.SOLID, collision.probe(mover, 1, 0));
    }
}
