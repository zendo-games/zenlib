package com.zendogames.zenlib.collision;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.zendogames.zenlib.ZenFlag;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.components.Sensor;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Debug overlay for {@link Sensor}s, drawn with the game's {@link ShapeDrawer} between the batch's
 * begin and end: every whisker from its cast origin, dim to full range when clear, bright to the
 * hit point with a dot there on a hit (orange for solid terrain, cyan for a one-way, red for a
 * threat), plus a dot at the origin. Sight rays are red to a seen target's edge with a dot there, or
 * grey to whatever hides the target with a ring at the center that was aimed at. Gate it like
 * {@link CollisionDraw}.
 */
public final class SensorDraw {

    public static final Color CLEAR   = new Color(1f, 1f, 1f, 0.25f);
    public static final Color TERRAIN = new Color(Color.ORANGE);
    public static final Color ONE_WAY = new Color(Color.CYAN);
    public static final Color THREAT  = new Color(Color.RED);
    public static final Color HIDDEN  = new Color(0.6f, 0.6f, 0.6f, 0.5f);
    public static final Color ORIGIN  = new Color(1f, 1f, 1f, 0.6f);

    private static final float DOT_RADIUS  = 1.5f;
    private static final float RING_RADIUS = 3f;
    private static final Family.Builder SENSING = Family.all(Position.TYPE, Collider.TYPE, Sensor.TYPE);

    private SensorDraw() {}

    /** Every sensing entity in {@code world}, at its tick position. */
    public static void all(ShapeDrawer shapes, World world, float lineWidth) {
        all(shapes, world, lineWidth, 1f);
    }

    /**
     * Like {@link #all(ShapeDrawer, World, float)}, shifted to each entity's interpolated render
     * position so the whiskers sit on the sprite; {@code alpha} is {@code World.alpha()}. The
     * readings themselves were taken at the tick position (see {@link CollisionDraw}'s note).
     */
    public static void all(ShapeDrawer shapes, World world, float lineWidth, float alpha) {
        var a = ZenFlag.RENDER_INTERP.on() ? alpha : 1f;
        var entities = world.family(SENSING).entities();
        for (int i = 0; i < entities.size; i++) {
            var e = entities.get(i);
            var pos = Position.get(e);
            var dx = MathUtils.round(pos.renderX(a)) - pos.x;
            var dy = MathUtils.round(pos.renderY(a)) - pos.y;
            sensor(shapes, Sensor.get(e), dx, dy, lineWidth);
        }
    }

    /** One sensor's whiskers and sight rays, shifted by {@code (dx, dy)} from where they were cast. */
    public static void sensor(ShapeDrawer shapes, Sensor sensor, float dx, float dy, float lineWidth) {
        var prevColor = shapes.getPackedColor();
        for (var w : sensor.whiskers) {
            float x0 = w.startX + dx, y0 = w.startY + dy;
            float x1 = w.x + dx,      y1 = w.y + dy;
            if (w.isHit()) {
                shapes.setColor(color(sensor, w));
                shapes.line(x0, y0, x1, y1, lineWidth);
                shapes.filledCircle(x1, y1, DOT_RADIUS);
            } else {
                shapes.setColor(CLEAR);
                shapes.line(x0, y0, x1, y1, lineWidth);
            }
            shapes.setColor(ORIGIN);
            shapes.filledCircle(x0, y0, DOT_RADIUS);
        }
        for (int i = 0; i < sensor.sightingCount; i++) {
            var s = sensor.sightings[i];
            float x0 = s.startX + dx, y0 = s.startY + dy;
            float x1 = s.x + dx,      y1 = s.y + dy;
            if (s.seen()) {
                shapes.setColor(THREAT);
                shapes.line(x0, y0, x1, y1, lineWidth);
                shapes.filledCircle(x1, y1, DOT_RADIUS);
            } else {
                shapes.setColor(HIDDEN);
                shapes.line(x0, y0, x1, y1, lineWidth);
                shapes.circle(s.targetX + dx, s.targetY + dy, RING_RADIUS, lineWidth);
            }
        }
        shapes.setColor(prevColor);
    }

    private static Color color(Sensor sensor, Sensor.Whisker whisker) {
        if (whisker.hits(sensor.threats)) return THREAT;
        return (whisker.block == Block.ONE_WAY) ? ONE_WAY : TERRAIN;
    }
}
