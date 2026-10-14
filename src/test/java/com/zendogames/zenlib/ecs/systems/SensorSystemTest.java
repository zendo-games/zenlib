package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointF2;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.CollisionMask;
import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.EntitySystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.components.Sensor;
import com.zendogames.zenlib.ecs.components.Sensor.Whisker;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static com.zendogames.zenlib.collision.CollisionMask.of;
import static org.junit.Assert.*;

/**
 * {@link SensorSystem} fills every {@link Sensor}'s whiskers from the collider center at {@code SENSE},
 * mirrored by {@code facing}, skipping the entity's own collider; the read helpers work on those readings.
 */
public class SensorSystemTest {

    enum Mask implements CollisionMask {
        SOLID, PLAYER, PLATFORM;
        @Override public int bit() { return 1 << ordinal(); }
    }

    static final int TERRAIN = of(Mask.SOLID, Mask.PLATFORM);
    static final int THREATS = of(Mask.PLAYER);
    static final float EPS = 1e-4f;

    final World world = new World();
    final CollisionWorld collision = new CollisionWorld(world);

    public SensorSystemTest() {
        world.addSystem(new SensorSystem(collision));
    }

    /** A feet-centered 8x16 body like the demo hero: collider center at {@code (x, y + 8)}. */
    Entity body(int x, int y, Mask mask) {
        return world.createEntity().add(new Position(x, y)).add(Collider.rect(mask, -4, 0, 8, 16));
    }

    Entity solid(int x, int y, int w, int h) {
        return world.createEntity().add(new Position(x, y)).add(Collider.rect(Mask.SOLID, 0, 0, w, h));
    }

    void tick() {
        world.update(SimulationStep.FIXED_DT);
    }

    @Test
    public void namedRaysReadTerrainAndThreatsFromTheCenter() {
        var floor = solid(-100, -10, 300, 10);                                      // top at y 0
        var wall  = solid(50, 0, 10, 40);                                           // x 50..60
        var other = body(-30, 0, Mask.PLAYER);                                      // right edge x -26
        var hero  = body(0, 0, Mask.PLAYER).add(Sensor.rays(100, TERRAIN, THREATS, 0, 180, -90));   // ahead, back, down
        tick();

        var sensor = Sensor.get(hero);
        var ahead = sensor.whiskers[0];
        var back  = sensor.whiskers[1];
        var down  = sensor.whiskers[2];
        for (var w : sensor.whiskers) {
            assertEquals(0, w.startX, EPS);                                         // cast from the collider center
            assertEquals(8, w.startY, EPS);
        }

        assertSame(wall, ahead.entity);
        assertEquals(50, ahead.distance, EPS);
        assertEquals(Block.SOLID, ahead.block);
        assertEquals(1, ahead.dirX, EPS);
        assertEquals(0, ahead.dirY, 0);

        assertSame(other, back.entity);
        assertEquals(26, back.distance, EPS);
        assertEquals(Mask.PLAYER, back.mask());
        assertEquals(-1, back.dirX, EPS);

        assertSame(floor, down.entity);                                             // not the hero's own collider
        assertEquals(8, down.distance, EPS);
        assertEquals(0, down.y, EPS);
        assertEquals(-1, down.dirY, EPS);

        assertSame(down, sensor.nearest(TERRAIN));
        assertSame(back, sensor.nearest(THREATS));
        assertNull(sensor.nearest(of(Mask.PLATFORM)));
        assertTrue (sensor.clear(of(Mask.PLATFORM)));
        assertFalse(sensor.clear(THREATS));
        assertEquals(2, sensor.count(TERRAIN));
        assertEquals(1, sensor.count(THREATS));
        assertEquals(TERRAIN | THREATS, sensor.masks());
    }

    @Test
    public void facingMirrorsTheLayout() {
        solid(50, 0, 10, 40);
        body(-30, 0, Mask.PLAYER);
        var hero = body(0, 0, Mask.PLAYER).add(Sensor.rays(100, TERRAIN, THREATS, 0, 180));
        var sensor = Sensor.get(hero);
        sensor.facing = -1;
        tick();

        assertEquals(26, sensor.whiskers[0].distance, EPS);                         // "ahead" now reads to the left
        assertEquals(-1, sensor.whiskers[0].dirX, EPS);
        assertEquals(0, sensor.whiskers[0].angleDeg, 0);                            // the local angle is unchanged
        assertEquals(50, sensor.whiskers[1].distance, EPS);

        sensor.facing = 1;
        tick();
        assertEquals(50, sensor.whiskers[0].distance, EPS);
    }

    @Test
    public void originOffsetsMoveTheCastAndMirrorWithFacing() {
        solid(20, 0, 10, 6);                                                        // a low crate on the right, x 20..30, y 0..6
        solid(-30, 0, 10, 6);                                                       // and one on the left, x -30..-20
        var hero = body(0, 0, Mask.PLAYER).add(new Sensor(100, TERRAIN, 0,
                new Whisker(0),                                                     // from the center (0, 8): over the crate
                new Whisker(0, 3, -6)));                                            // from (3, 2), knee height: into it
        var sensor = Sensor.get(hero);
        tick();

        assertFalse(sensor.whiskers[0].isHit());
        assertEquals(100, sensor.whiskers[0].distance, 0);
        assertEquals(100, sensor.whiskers[0].x, EPS);                               // the ray end
        assertEquals(3, sensor.whiskers[1].startX, EPS);
        assertEquals(2, sensor.whiskers[1].startY, EPS);
        assertEquals(17, sensor.whiskers[1].distance, EPS);

        sensor.facing = -1;
        tick();
        assertEquals(-3, sensor.whiskers[1].startX, EPS);                           // the x offset mirrors with the direction
        assertEquals(2, sensor.whiskers[1].startY, EPS);
        assertEquals(17, sensor.whiskers[1].distance, EPS);                         // the left crate, symmetric
    }

    @Test
    public void readingsAreTakenBeforeDecideAndTheMovers() {
        solid(50, 0, 10, 40);
        var hero = body(0, 0, Mask.PLAYER).add(Sensor.rays(100, TERRAIN, THREATS, 0));
        world.addSystem(new EntitySystem(SystemOrder.DECIDE) {
            @Override public void update(float dt) { Position.get(hero).teleport(20, 0); }
        });

        tick();
        assertEquals(50, Sensor.get(hero).whiskers[0].distance, EPS);              // sensed from (0, 8), then moved
        tick();
        assertEquals(30, Sensor.get(hero).whiskers[0].distance, EPS);              // sensed from where the last tick left it
        assertEquals(SystemOrder.SENSE, new SensorSystem(collision).priority);
    }

    @Test
    public void entitiesWithoutAColliderAreNotSensed() {
        solid(50, 0, 10, 40);
        var sensor = Sensor.rays(100, TERRAIN, THREATS, 0);
        world.createEntity().add(new Position(0, 0)).add(sensor);
        tick();
        assertFalse(sensor.whiskers[0].isHit());
        assertEquals(0, sensor.whiskers[0].distance, 0);                            // never cast
    }

    @Test
    public void fanAndRaysLayouts() {
        var full = Sensor.fan(4, 360, 0, 50, TERRAIN, THREATS);
        assertAngles(full, 0, 90, 180, 270);                                        // a full circle does not cast the seam twice
        assertEquals(50, full.range, 0);
        assertEquals(TERRAIN, full.terrain);
        assertEquals(THREATS, full.threats);
        assertEquals(1, full.facing);

        assertAngles(Sensor.fan(3, 90, -45, 50, TERRAIN, THREATS), -45, 0, 45);    // a partial arc includes both ends
        assertAngles(Sensor.fan(1, 90, 30, 50, TERRAIN, THREATS), 30);
        assertAngles(Sensor.rays(50, TERRAIN, THREATS, 0, -45, -90), 0, -45, -90);

        var down = Sensor.rays(50, TERRAIN, THREATS, -90).whiskers[0];
        assertEquals(0, down.cos, 0);                                               // axis-aligned whiskers are exact
        assertEquals(-1, down.sin, 0);

        assertThrows(GdxRuntimeException.class, () -> Sensor.fan(0, 360, 0, 50, TERRAIN, THREATS));
        assertThrows(GdxRuntimeException.class, () -> Sensor.rays(50, TERRAIN, THREATS));
        assertThrows(GdxRuntimeException.class, () -> Sensor.rays(0, TERRAIN, THREATS, 0));
    }

    private static void assertAngles(Sensor sensor, float... expected) {
        assertEquals(expected.length, sensor.whiskers.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("whisker " + i, expected[i], sensor.whiskers[i].angleDeg, EPS);
        }
    }

    @Test
    public void repulsionPointsAwayFromHitsWeightedByCloseness() {
        body(30, 0, Mask.PLAYER);                                                   // to the right: left edge x 26, read at 26
        body(0, 60, Mask.PLAYER);                                                   // above: bottom y 60, read at 52 from (0, 8)
        var hero = body(0, 0, Mask.PLAYER).add(Sensor.fan(4, 360, 0, 100, TERRAIN, THREATS));
        var sensor = Sensor.get(hero);
        tick();

        var out = sensor.repulsion(THREATS, 1, new PointF2());
        assertEquals(-(1 - 0.26f), out.x, EPS);
        assertEquals(-(1 - 0.52f), out.y, EPS);

        sensor.repulsion(THREATS, 2, out);                                          // squared falloff
        assertEquals(-(0.74f * 0.74f), out.x, EPS);
        assertEquals(-(0.48f * 0.48f), out.y, EPS);

        sensor.repulsion(TERRAIN, 2, out);                                          // nothing solid in this world
        assertEquals(0, out.x, 0);
        assertEquals(0, out.y, 0);
    }
}
