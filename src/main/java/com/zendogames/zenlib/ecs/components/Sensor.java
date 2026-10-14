package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointF2;
import com.zendogames.zenlib.collision.RayHit;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;

/**
 * Local perception: whiskers (rays) cast from the collider's center every tick by the
 * {@code SensorSystem}, each keeping its latest {@link RayHit}. {@link #terrain} and {@link #threats}
 * say what the whiskers see; {@link #facing} mirrors the layout so a platformer's "ahead" stays
 * ahead. The read helpers are computed on demand from the readings, nothing aggregate is stored: a
 * consumer with a different steering model loops over {@link #whiskers} itself.
 * <pre>{@code
 * entity.add(Sensor.rays(10 * UNIT, of(SOLID), of(PLAYER), 0, -45, -90));   // ahead, ahead-down, down
 * var threat = Sensor.get(entity).nearest(of(PLAYER));
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

    public final Whisker[] whiskers;

    /** Cast length, pixels. */
    public float range;

    /** Mask bits read as terrain (SOLID, one-ways, ...). */
    public int terrain;

    /** Mask bits read as threats (PLAYER, PROJECTILE, ...). */
    public int threats;

    /** +1 or -1, set by the game: -1 mirrors every whisker about the y axis. */
    public int facing = 1;

    public Sensor(float range, int terrain, int threats, Whisker... whiskers) {
        if (range <= 0) throw new GdxRuntimeException(TAG + ": range must be positive");
        if (whiskers.length == 0) throw new GdxRuntimeException(TAG + ": at least one whisker is needed");
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
        var whiskers = new Whisker[anglesDeg.length];
        for (int i = 0; i < anglesDeg.length; i++) {
            whiskers[i] = new Whisker(anglesDeg[i]);
        }
        return new Sensor(range, terrain, threats, whiskers);
    }

    /** Every mask the whiskers are cast against. */
    public int masks() {
        return terrain | threats;
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
}
