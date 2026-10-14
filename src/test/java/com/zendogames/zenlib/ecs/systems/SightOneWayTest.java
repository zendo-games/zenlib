package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.CollisionMask;
import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.collision.RayHit;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.components.Sensor;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static com.zendogames.zenlib.collision.CollisionMask.of;
import static org.junit.Assert.*;

/**
 * Sight rays pass over one-way segments ({@link CollisionWorld#raycast} with {@code throughOneWays}): a
 * platform has no thickness to hide anything, and a body resting on it sits at the platform's exact
 * distance, which the plain ray ordered by rounding and family order. Whiskers keep reading platforms.
 */
public class SightOneWayTest {

    enum Mask implements CollisionMask {
        SOLID, PLAYER, PLATFORM;
        @Override public int bit() { return 1 << ordinal(); }
    }

    static final int TERRAIN = of(Mask.SOLID, Mask.PLATFORM);
    static final int THREATS = of(Mask.PLAYER);
    static final float EPS = 1e-4f;

    final World world = new World();
    final CollisionWorld collision = new CollisionWorld(world);

    public SightOneWayTest() {
        world.addSystem(new SensorSystem(collision));
    }

    /** A feet-centered 8x16 body like the demo hero: collider center at {@code (x, y + 8)}. */
    Entity body(int x, int y, Mask mask) {
        return world.createEntity().add(new Position(x, y)).add(Collider.rect(mask, -4, 0, 8, 16));
    }

    Entity solid(int x, int y, int w, int h) {
        return world.createEntity().add(new Position(x, y)).add(Collider.rect(Mask.SOLID, 0, 0, w, h));
    }

    /** A one-way platform from {@code (x, y)} to {@code (x + width, y)}, stood on from above. */
    Entity platform(int x, int y, int width) {
        return world.createEntity().add(new Position(x, y)).add(Collider.platform(Mask.PLATFORM, width));
    }

    void tick() {
        world.update(SimulationStep.FIXED_DT);
    }

    @Test
    public void throughOneWaysPassesOverSegmentsAndKeepsEverythingElse() {
        var platform = platform(0, 20, 100);                                        // y 20, x 0..100
        var wall     = solid(0, 40, 100, 10);                                       // y 40..50 behind it
        var hit = new RayHit();

        assertTrue(collision.raycast(50, 0, 0, 1, 100, TERRAIN, null, hit));       // the plain ray reads the platform
        assertSame(platform, hit.entity);
        assertEquals(20, hit.distance, EPS);
        assertEquals(Block.ONE_WAY, hit.block);

        assertTrue(collision.raycast(50, 0, 0, 1, 100, TERRAIN, null, true, hit)); // through it: the wall behind
        assertSame(wall, hit.entity);
        assertEquals(40, hit.distance, EPS);
        assertEquals(Block.SOLID, hit.block);

        assertFalse(collision.raycast(50, 0, 0, 1, 30, TERRAIN, null, true, hit)); // only the platform in range: a miss
        assertFalse(hit.isHit());
        assertEquals(30, hit.distance, EPS);
    }

    @Test
    public void aBodyRestingOnAPlatformIsSeenFromBelowAtAnyAngle() {
        platform(320, 80, 160);                                                     // the demo's jumpthru: 5 tiles up, x 320..480
        var hero    = body(470, 80, Mask.PLAYER);                                   // standing on it, center (470, 88)
        var watcher = body(516, 0, Mask.PLAYER).add(Sensor.sight(160, TERRAIN, THREATS, 2));
        var sensor  = Sensor.get(watcher);
        tick();

        assertTrue(sensor.sees(hero));                                              // 46 right and 80 below: the ray enters the hero's side, past the platform
        var sighting = sensor.sighting(hero);
        assertSame(hero, sighting.entity);
        assertEquals(Block.SOLID, sighting.block);

        for (int x : new int[] { 470, 480, 440, 600 }) {                            // under it (the ray enters through the feet, at the platform's distance), both sides
            Position.get(watcher).teleport(x, 0);
            tick();
            assertTrue("from x " + x, sensor.sees(hero));
        }

        solid(490, 0, 10, 100);                                                     // solid terrain still hides: a pillar between them
        Position.get(watcher).teleport(516, 0);
        tick();
        assertFalse(sensor.sees(hero));
        assertEquals(Block.SOLID, sensor.sighting(hero).block);
    }

    @Test
    public void whiskersStillReadPlatforms() {
        var platform = platform(-50, 40, 100);
        var hero     = body(0, 0, Mask.PLAYER).add(Sensor.rays(100, TERRAIN, THREATS, 90).sightRays(1)); // one whisker straight up
        var sensor   = Sensor.get(hero);
        tick();

        var up = sensor.whiskers[0];
        assertSame(platform, up.entity);
        assertEquals(Block.ONE_WAY, up.block);
        assertEquals(32, up.distance, EPS);                                         // from the center at y 8 to the platform at 40
        assertEquals(0, sensor.sightingCount);                                      // no threat in range
    }
}
