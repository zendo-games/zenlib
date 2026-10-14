package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointF2;
import com.zendogames.zenlib.collision.RayHit;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;

/**
 * Local perception, cast from the collider's center every tick by the {@code SensorSystem}:
 * <ul>
 *   <li><b>Whiskers</b>, fixed rays in a layout ({@link #fan}, {@link #rays}), each keeping its latest
 *       {@link RayHit}: what the terrain looks like around the body. {@link #facing} mirrors the layout
 *       so a platformer's "ahead" stays ahead.</li>
 *   <li><b>Sight rays</b> ({@link #sightRays}), one ray per threat within range aimed at its collider
 *       center, each a {@link Sighting} that says whether the threat is in view or what hides it. A fan
 *       only notices a body where a whisker happens to cross it; a sight ray always finds it.</li>
 * </ul>
 * {@link #terrain} and {@link #threats} say what the rays read. The read helpers are computed on demand
 * from the readings, nothing aggregate is stored: a consumer with a different steering model loops over
 * {@link #whiskers} or {@link #sightings} itself.
 * <pre>{@code
 * var range     = 10 * UNIT;
 * var terrain   = CollisionMasks.of(SOLID);
 * var threat    = CollisionMasks.of(PLAYER);
 * var anglesDeg = new float[] { 0, -45, -90 };
 * entity.add(Sensor.rays(range, terrain, threat, anglesDeg).sightRays(4));  // ahead, ahead-down, down; up to 4 threats
 * var threat = Sensor.get(entity).nearestSeen();
 * if (threat != null) input.moveX = (threat.dirX < 0) ? +1 : -1;            // run the other way
 * }</pre>
 */
public class Sensor implements Component {

    public static final ComponentType<Sensor> TYPE = ComponentType.register(Sensor.class);
    public static Sensor  get(Entity e) { return TYPE.get(e); }
    public static boolean has(Entity e) { return TYPE.has(e); }

    private static final String TAG = Sensor.class.getSimpleName();

    /**
     * One ray and its latest reading. {@code angleDeg} is local to the sensor (0 = +x,
     * counter-clockwise) and the origin is an offset from the collider center; both are mirrored
     * about the y axis when the sensor's {@link #facing} is negative. The system writes where the
     * ray was cast from and which way ({@code startX/Y}, {@code dirX/Y}) along with the hit.
     */
    public static final class Whisker extends RayHit {
        public final float angleDeg;
        public final float originX, originY;
        public final float cos, sin;

        /** World origin and unit direction of the latest cast. */
        public float startX, startY, dirX, dirY;

        public Whisker(float angleDeg) {
            this(angleDeg, 0, 0);
        }

        public Whisker(float angleDeg, float originX, float originY) {
            this.angleDeg = angleDeg;
            this.originX = originX;
            this.originY = originY;
            var radians = Math.toRadians(angleDeg);
            this.cos = snap((float) Math.cos(radians));
            this.sin = snap((float) Math.sin(radians));
        }

        /** Axis-aligned whiskers get exact zeros, so a 90 degree ray is a vertical one. */
        private static float snap(float value) {
            return (Math.abs(value) < 1e-6f) ? 0f : value;
        }

        @Override
        public String toString() {
            return "%+.0f deg: %s".formatted(angleDeg, super.toString());
        }
    }

    /**
     * One threat in range and the ray cast at its collider center. The {@link RayHit} half is what the
     * ray met first: the target itself when it is in view ({@link #seen()}), else whatever hides it, with
     * {@code distance} to that. A one-way segment never hides a sighting: it has no thickness, and a body
     * resting on it sits at its exact distance. {@code targetDistance} is center to center, the order
     * sightings are kept in.
     */
    public static final class Sighting extends RayHit {

        /** The threat aimed at; null in an unused slot. */
        public Entity target;

        /** The target's collider center and how far it is from the sensor's, center to center. */
        public float targetX, targetY, targetDistance;

        /** World origin and unit direction of the latest cast. */
        public float startX, startY, dirX, dirY;

        /** True when the ray reached the target and it is still alive: nothing in the terrain or another threat is in the way. */
        public boolean seen() {
            return isHit() && entity == target && target.isAlive();
        }

        Sighting aim(Entity target, float targetX, float targetY, float targetDistance) {
            this.target = target;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetDistance = targetDistance;
            return this;
        }

        @Override
        public String toString() {
            if (seen()) return "%s seen at %.1f".formatted(target, distance);
            if (isHit()) return "%s hidden by %s at %.1f".formatted(target, entity, distance);
            return "%s not reached".formatted(target);
        }
    }

    public final Whisker[] whiskers;

    /**
     * The sight rays, nearest target first, {@link #sightingCount} of them valid this tick; null until
     * {@link #sightRays} sizes it. The objects are reused, so hold a copy of what you need, not the slot.
     */
    public Sighting[] sightings;

    /** How many of {@link #sightings} were cast this tick. */
    public int sightingCount;

    /** Cast length, pixels: whiskers end here and sight rays reach only targets whose center is within it. */
    public float range;

    /** Mask bits read as terrain (SOLID, one-ways, ...). */
    public int terrain;

    /** Mask bits read as threats (PLAYER, PROJECTILE, ...): what the whiskers report and the sight rays aim at. */
    public int threats;

    /** +1 or -1, set by the game: -1 mirrors every whisker about the y axis. Sight rays ignore it. */
    public int facing = 1;

    public Sensor(float range, int terrain, int threats, Whisker... whiskers) {
        if (range <= 0) throw new GdxRuntimeException(TAG + ": range must be positive");
        this.range = range;
        this.terrain = terrain;
        this.threats = threats;
        this.whiskers = whiskers;
    }

    /**
     * {@code count} whiskers evenly over {@code arcDeg} starting at {@code startDeg}. A full circle
     * ({@code arcDeg >= 360}) spaces them {@code 360 / count} apart so the seam is not cast twice; a
     * partial arc includes both ends: {@code fan(3, 90, -45, ...)} is -45, 0, 45.
     */
    public static Sensor fan(int count, float arcDeg, float startDeg, float range, int terrain, int threats) {
        if (count <= 0) throw new GdxRuntimeException(TAG + ": a fan needs at least one whisker");
        var whiskers = new Whisker[count];
        var step = (arcDeg >= 360f) ? 360f / count : (count > 1) ? arcDeg / (count - 1) : 0f;
        for (int i = 0; i < count; i++) {
            whiskers[i] = new Whisker(startDeg + i * step);
        }
        return new Sensor(range, terrain, threats, whiskers);
    }

    /** Named directions, for a platformer: {@code rays(range, terrain, threats, 0, -45, -90)} is ahead, ahead-down, down. */
    public static Sensor rays(float range, int terrain, int threats, float... anglesDeg) {
        if (anglesDeg.length == 0) throw new GdxRuntimeException(TAG + ": rays needs at least one angle");
        var whiskers = new Whisker[anglesDeg.length];
        for (int i = 0; i < anglesDeg.length; i++) {
            whiskers[i] = new Whisker(anglesDeg[i]);
        }
        return new Sensor(range, terrain, threats, whiskers);
    }

    /** Sight rays only, no whiskers: a lookout that needs to know who is in view, not what the ground does. */
    public static Sensor sight(float range, int terrain, int threats, int maxSightings) {
        return new Sensor(range, terrain, threats).sightRays(maxSightings);
    }

    /**
     * Adds sight rays: every tick, up to {@code max} threats within {@link #range} get a ray aimed at
     * their collider center, the nearest first; farther ones are dropped when there are more. The
     * {@link Sighting} objects are allocated here once.
     */
    public Sensor sightRays(int max) {
        if (max <= 0) throw new GdxRuntimeException(TAG + ": sightRays needs room for at least one target");
        sightings = new Sighting[max];
        for (int i = 0; i < max; i++) {
            sightings[i] = new Sighting();
        }
        sightingCount = 0;
        return this;
    }

    /** Every mask the rays are cast against: whiskers read both, a sight ray is hidden by either (solid shapes only, never a one-way segment). */
    public int masks() {
        return terrain | threats;
    }

    // ------------------------------------------------------------------------
    // Sighting bookkeeping, driven by the system each tick
    // ------------------------------------------------------------------------

    /** Forgets this tick's sightings; the system calls it before gathering targets. */
    public void clearSightings() {
        sightingCount = 0;
    }

    /**
     * Records a threat in range, keeping {@link #sightings} ordered nearest first and bounded: returns the
     * slot to cast into, or null when every slot holds a nearer target and this one is dropped.
     */
    public Sighting addSighting(Entity target, float targetX, float targetY, float targetDistance) {
        var capacity = sightings.length;
        int at = 0;
        while (at < sightingCount && sightings[at].targetDistance <= targetDistance) at++;
        if (at == capacity) return null;

        // The slot that falls off the end (or the first unused one) is reused for the insert
        var last  = Math.min(sightingCount, capacity - 1);
        var spare = sightings[last];
        for (int i = last; i > at; i--) {
            sightings[i] = sightings[i - 1];
        }
        sightings[at] = spare.aim(target, targetX, targetY, targetDistance);
        if (sightingCount < capacity) sightingCount++;
        return spare;
    }

    // ------------------------------------------------------------------------
    // Read helpers over the latest readings, computed on demand
    // ------------------------------------------------------------------------

    /** The nearest whisker hitting an entity whose mask is in {@code masks}, null when none does. */
    public Whisker nearest(int masks) {
        Whisker best = null;
        for (var w : whiskers) {
            if (w.hits(masks) && (best == null || w.distance < best.distance)) best = w;
        }
        return best;
    }

    /** True when no whisker hits those masks. */
    public boolean clear(int masks) {
        return nearest(masks) == null;
    }

    /** How many whiskers hit those masks. */
    public int count(int masks) {
        int n = 0;
        for (var w : whiskers) {
            if (w.hits(masks)) n++;
        }
        return n;
    }

    /**
     * Steering away from everything hit in {@code masks}: the sum over hitting whiskers of the
     * direction back along the whisker, weighted by {@code (1 - distance / range) ^ exponent}, so
     * near hits dominate (2 is the usual squared falloff). Zero when nothing hits; not normalised,
     * the length says how crowded the sensor is.
     */
    public PointF2 repulsion(int masks, float exponent, PointF2 out) {
        out.set(0f, 0f);
        for (var w : whiskers) {
            if (!w.hits(masks)) continue;
            var weight = (float) Math.pow(1f - w.distance / range, exponent);
            out.add(-w.dirX * weight, -w.dirY * weight);
        }
        return out;
    }

    /** The nearest threat in view this tick, null when none is (or there are no sight rays). */
    public Sighting nearestSeen() {
        for (int i = 0; i < sightingCount; i++) {
            if (sightings[i].seen()) return sightings[i];
        }
        return null;
    }

    /** How many threats are in view this tick. */
    public int seenCount() {
        int n = 0;
        for (int i = 0; i < sightingCount; i++) {
            if (sightings[i].seen()) n++;
        }
        return n;
    }

    /** This tick's sighting of {@code target}, null when it was out of range, dropped, or there are no sight rays. */
    public Sighting sighting(Entity target) {
        for (int i = 0; i < sightingCount; i++) {
            if (sightings[i].target == target) return sightings[i];
        }
        return null;
    }

    /** True when {@code target} is in view this tick. */
    public boolean sees(Entity target) {
        var sighting = sighting(target);
        return sighting != null && sighting.seen();
    }

    /**
     * Steering away from every threat in view: like {@link #repulsion(int, float, PointF2)} over the
     * sight rays, each weighted by {@code (1 - distance / range) ^ exponent} with {@code distance} to the
     * target's edge. Hidden threats do not count. Zero when nothing is seen.
     */
    public PointF2 repulsion(float exponent, PointF2 out) {
        out.set(0f, 0f);
        for (int i = 0; i < sightingCount; i++) {
            var s = sightings[i];
            if (!s.seen()) continue;
            var weight = (float) Math.pow(1f - s.distance / range, exponent);
            out.add(-s.dirX * weight, -s.dirY * weight);
        }
        return out;
    }
}
