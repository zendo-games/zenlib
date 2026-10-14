package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointF2;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.CollisionMask;
import com.zendogames.zenlib.collision.CollisionWorld;
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
 * {@link SensorSystem} aims one sight ray per threat in range at its collider center, nearest first and
 * bounded by the sensor's room; a {@link Sensor.Sighting} is the target when in view, else what hides it.
 */
public class SightRaysTest {

    enum Mask implements CollisionMask {
        SOLID, PLAYER, PLATFORM;
        @Override public int bit() { return 1 << ordinal(); }
    }

    static final int TERRAIN = of(Mask.SOLID, Mask.PLATFORM);
    static final int THREATS = of(Mask.PLAYER);
    static final float EPS = 1e-4f;

    final World world = new World();
    final CollisionWorld collision = new CollisionWorld(world);

    public SightRaysTest() {
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
    public void oneRayPerThreatInRangeAimedAtItsCenterNearestFirst() {
        var far    = body(200, 0, Mask.PLAYER);                                     // center 200 away: out of range
        var near   = body(30, 0, Mask.PLAYER);                                      // left edge at 26
        var hidden = body(-60, 0, Mask.PLAYER);                                     // behind the wall
        var wall   = solid(-30, 0, 4, 40);                                          // x -30..-26
        var above  = body(0, 60, Mask.PLAYER);                                      // bottom at 60, center 60 up
        var hero   = body(0, 0, Mask.PLAYER).add(Sensor.rays(100, TERRAIN, THREATS, 0).sightRays(4));
        var sensor = Sensor.get(hero);
        tick();

        assertEquals(3, sensor.sightingCount);
        assertSame(near,   sensor.sightings[0].target);                             // center to center 30, 60, 60: ties keep family order
        assertSame(hidden, sensor.sightings[1].target);
        assertSame(above,  sensor.sightings[2].target);
        assertNull(sensor.sighting(far));
        assertNull(sensor.sighting(hero));                                          // never itself

        var s = sensor.sightings[0];
        assertEquals(0, s.startX, EPS);                                             // cast from the collider center
        assertEquals(8, s.startY, EPS);
        assertEquals(30, s.targetX, EPS);                                           // at the target's
        assertEquals(8, s.targetY, EPS);
        assertEquals(30, s.targetDistance, EPS);
        assertEquals(1, s.dirX, EPS);
        assertEquals(0, s.dirY, EPS);
        assertTrue(s.seen());
        assertSame(near, s.entity);
        assertEquals(26, s.distance, EPS);                                          // the ray hits the edge, not the center
        assertEquals(Block.SOLID, s.block);

        s = sensor.sightings[1];
        assertFalse(s.seen());
        assertTrue(s.isHit());
        assertSame(wall, s.entity);                                                 // what hides it, and how far that is
        assertEquals(26, s.distance, EPS);
        assertEquals(-1, s.dirX, EPS);

        s = sensor.sightings[2];
        assertTrue(s.seen());
        assertEquals(0, s.dirX, EPS);
        assertEquals(1, s.dirY, EPS);
        assertEquals(52, s.distance, EPS);

        assertSame(sensor.sightings[0], sensor.nearestSeen());
        assertEquals(2, sensor.seenCount());
        assertTrue(sensor.sees(near));
        assertFalse(sensor.sees(hidden));
        assertFalse(sensor.sees(far));
        assertSame(sensor.sightings[1], sensor.sighting(hidden));
    }

    @Test
    public void roomIsBoundedKeepingTheNearestWhateverTheFamilyOrder() {
        var c = body(90, 0, Mask.PLAYER);                                           // added farthest first
        var a = body(20, 0, Mask.PLAYER);
        var b = body(-50, 0, Mask.PLAYER);
        var d = body(0, 70, Mask.PLAYER);
        var hero = body(0, 0, Mask.PLAYER).add(Sensor.sight(100, TERRAIN, THREATS, 2));
        var sensor = Sensor.get(hero);
        tick();

        assertEquals(2, sensor.sightingCount);
        assertSame(a, sensor.sightings[0].target);
        assertSame(b, sensor.sightings[1].target);
        assertNull(sensor.sighting(c));
        assertNull(sensor.sighting(d));
        assertEquals(2, sensor.sightings.length);                                   // nothing grew

        Position.get(c).teleport(5, 0);                                             // now the nearest: it displaces b
        tick();
        assertSame(c, sensor.sightings[0].target);
        assertSame(a, sensor.sightings[1].target);
        assertNull(sensor.sighting(b));
    }

    @Test
    public void sightingsAreRecastEveryTickAndForgetWhatLeft() {
        var other = body(30, 0, Mask.PLAYER);
        var hero  = body(0, 0, Mask.PLAYER).add(Sensor.sight(100, TERRAIN, THREATS, 2));
        var sensor = Sensor.get(hero);
        tick();
        assertEquals(1, sensor.sightingCount);

        Position.get(other).teleport(300, 0);                                       // out of range
        tick();
        assertEquals(0, sensor.sightingCount);
        assertNull(sensor.nearestSeen());
        assertEquals(0, sensor.seenCount());
        assertFalse(sensor.sees(other));

        Position.get(other).teleport(30, 0);
        tick();
        world.despawn(other);                                                       // outside a system: immediate
        assertEquals(1, sensor.sightingCount);                                      // last tick's reading is still there
        assertFalse(sensor.sightings[0].seen());                                    // but a dead target is not in view
        assertFalse(sensor.sees(other));
        tick();
        assertEquals(0, sensor.sightingCount);
    }

    @Test
    public void anotherThreatInTheWayHidesTheOneBehindIt() {
        var front = body(30, 0, Mask.PLAYER);
        var back  = body(60, 0, Mask.PLAYER);
        var hero  = body(0, 0, Mask.PLAYER).add(Sensor.sight(100, TERRAIN, THREATS, 4));
        var sensor = Sensor.get(hero);
        tick();

        assertTrue(sensor.sees(front));
        assertFalse(sensor.sees(back));
        assertSame(front, sensor.sighting(back).entity);                            // hidden by the nearer threat
        assertEquals(1, sensor.seenCount());
    }

    @Test
    public void sharingACenterIsSeenAtZeroWithoutACast() {
        var twin = body(0, 0, Mask.PLAYER);
        var hero = body(0, 0, Mask.PLAYER).add(Sensor.sight(100, TERRAIN, THREATS, 1));
        var sensor = Sensor.get(hero);
        tick();

        var s = sensor.sightings[0];
        assertSame(twin, s.target);
        assertTrue(s.seen());
        assertEquals(0, s.distance, 0);
        assertEquals(0, s.targetDistance, 0);
        assertEquals(0, s.x, EPS);
        assertEquals(8, s.y, EPS);
    }

    @Test
    public void repulsionOverSightingsIgnoresHiddenThreats() {
        body(30, 0, Mask.PLAYER);                                                   // seen at 26 to the right
        body(0, 60, Mask.PLAYER);                                                   // seen at 52 above
        body(-60, 0, Mask.PLAYER);                                                  // hidden behind the wall
        solid(-30, 0, 4, 40);
        var hero = body(0, 0, Mask.PLAYER).add(Sensor.sight(100, TERRAIN, THREATS, 4));
        var sensor = Sensor.get(hero);
        tick();

        var out = sensor.repulsion(1, new PointF2());
        assertEquals(-(1 - 0.26f), out.x, EPS);                                     // the hidden one adds nothing
        assertEquals(-(1 - 0.52f), out.y, EPS);

        sensor.repulsion(2, out);                                                   // squared falloff
        assertEquals(-(0.74f * 0.74f), out.x, EPS);
        assertEquals(-(0.48f * 0.48f), out.y, EPS);
    }

    @Test
    public void sightOnlySensorHasNoWhiskersAndWhiskerHelpersReadClear() {
        solid(20, 0, 10, 40);
        body(30, 0, Mask.PLAYER);
        var hero = body(0, 0, Mask.PLAYER).add(Sensor.sight(100, TERRAIN, THREATS, 2));
        var sensor = Sensor.get(hero);
        tick();

        assertEquals(0, sensor.whiskers.length);
        assertNull(sensor.nearest(TERRAIN));
        assertTrue(sensor.clear(THREATS));
        assertEquals(0, sensor.count(THREATS));
        assertEquals(0, sensor.repulsion(THREATS, 2, new PointF2()).x, 0);
        assertFalse(sensor.sees(sensor.sightings[0].target));                       // the wall is in the way
    }

    @Test
    public void withoutSightRaysNothingIsGathered() {
        body(30, 0, Mask.PLAYER);
        var hero = body(0, 0, Mask.PLAYER).add(Sensor.rays(100, TERRAIN, THREATS, 0));
        var sensor = Sensor.get(hero);
        tick();

        assertNull(sensor.sightings);
        assertEquals(0, sensor.sightingCount);
        assertNull(sensor.nearestSeen());
        assertNull(sensor.sighting(hero));
        assertEquals(0, sensor.repulsion(2, new PointF2()).x, 0);
        assertThrows(GdxRuntimeException.class, () -> sensor.sightRays(0));
    }
}
