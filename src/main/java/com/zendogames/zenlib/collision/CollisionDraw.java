package com.zendogames.zenlib.collision;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.shapes.*;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Position;
import space.earlygrey.shapedrawer.JoinType;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Debug outlines for collision shapes, drawn with the game's {@link ShapeDrawer} between the
 * batch's begin and end. Grids draw each solid cell's region, so the overlay matches the
 * collision math; segments draw the line plus a short tick on the support side.
 */
public final class CollisionDraw {

    private static final float HALF_TURN = MathUtils.PI;

    private CollisionDraw() {}

    /** Every collidable entity in {@code collision}'s family. */
    public static void all(ShapeDrawer shapes, CollisionWorld collision, Color color, float lineWidth) {
        var entities = collision.family().entities();
        for (int i = 0; i < entities.size; i++) {
            entity(shapes, entities.get(i), color, lineWidth);
        }
    }

    public static void entity(ShapeDrawer shapes, Entity e, Color color, float lineWidth) {
        shape(shapes, Collider.get(e).shape(), Position.get(e), color, lineWidth);
    }

    public static void shape(ShapeDrawer shapes, CollisionShape shape, PointI2 pos, Color color, float lineWidth) {
        var prevColor = shapes.getPackedColor();
        shapes.setColor(color);
        switch (shape) {
            case CollisionRect  rect -> shapes.rectangle(rect.rectangle(pos), color, lineWidth);
            case CollisionCirc circ -> {
                var c = circ.circle(pos);
                shapes.circle(c.x, c.y, c.radius, lineWidth, JoinType.SMOOTH);
            }
            case CollisionCaps  caps -> capsule(shapes, caps, pos, lineWidth);
            case CollisionAASeg seg  -> segment(shapes, seg, pos, lineWidth);
            case CollisionGrid  grid -> grid(shapes, grid, pos, color, lineWidth);
        }
        shapes.setColor(prevColor);
    }

    /** Two long edges plus the two cap arcs. */
    private static void capsule(ShapeDrawer shapes, CollisionCaps caps, PointI2 pos, float lineWidth) {
        var inner = caps.innerRect(pos);
        var bot   = caps.bottomCircle(pos);
        var top   = caps.topCircle(pos);
        switch (caps.axis()) {
            case VERTICAL -> {
                shapes.line(inner.x,               inner.y, inner.x,               inner.y + inner.height, lineWidth);
                shapes.line(inner.x + inner.width, inner.y, inner.x + inner.width, inner.y + inner.height, lineWidth);
                shapes.arc(bot.x, bot.y, bot.radius, HALF_TURN, HALF_TURN, lineWidth, true);
                shapes.arc(top.x, top.y, top.radius, 0,         HALF_TURN, lineWidth, true);
            }
            case HORIZONTAL -> {
                shapes.line(inner.x, inner.y,                inner.x + inner.width, inner.y,                lineWidth);
                shapes.line(inner.x, inner.y + inner.height, inner.x + inner.width, inner.y + inner.height, lineWidth);
                shapes.arc(bot.x, bot.y, bot.radius, HALF_TURN / 2,  HALF_TURN, lineWidth, true);
                shapes.arc(top.x, top.y, top.radius, -HALF_TURN / 2, HALF_TURN, lineWidth, true);
            }
        }
    }

    /** The line, with a tick at its midpoint pointing to the support side. */
    private static void segment(ShapeDrawer shapes, CollisionAASeg seg, PointI2 pos, float lineWidth) {
        float x0 = seg.x0(pos), y0 = seg.y0(pos), x1 = seg.x1(pos), y1 = seg.y1(pos);
        shapes.line(x0, y0, x1, y1, lineWidth);

        float tick = 4f * ((seg.facing() == CollisionAASeg.Facing.POSITIVE) ? 1 : -1);
        float mx = (x0 + x1) * 0.5f, my = (y0 + y1) * 0.5f;
        if (seg.isHorizontal()) shapes.line(mx, my, mx, my + tick, lineWidth);
        else                    shapes.line(mx, my, mx + tick, my, lineWidth);
    }

    /** Full cells as squares, ramps as their triangle, empty cells nothing. */
    private static void grid(ShapeDrawer shapes, CollisionGrid grid, PointI2 pos, Color color, float lineWidth) {
        int s = grid.cellSize();
        for (int y = 0; y < grid.rows(); y++) {
            for (int x = 0; x < grid.cols(); x++) {
                var shape = grid.get(x, y);
                if (shape == CollisionGrid.CellShape.EMPTY) continue;
                float x0 = pos.x + x * s, y0 = pos.y + y * s, x1 = x0 + s, y1 = y0 + s;
                switch (shape) {
                    case FULL    -> shapes.rectangle(x0, y0, s, s, color, lineWidth);
                    case RAMP_NE -> triangle(shapes, lineWidth, x0, y0, x1, y0, x1, y1);
                    case RAMP_NW -> triangle(shapes, lineWidth, x0, y0, x1, y0, x0, y1);
                    case RAMP_SE -> triangle(shapes, lineWidth, x0, y1, x1, y1, x1, y0);
                    case RAMP_SW -> triangle(shapes, lineWidth, x0, y1, x1, y1, x0, y0);
                    case EMPTY   -> {}
                }
            }
        }
    }

    private static void triangle(ShapeDrawer shapes, float lineWidth, float ax, float ay, float bx, float by, float cx, float cy) {
        shapes.line(ax, ay, bx, by, lineWidth);
        shapes.line(bx, by, cx, cy, lineWidth);
        shapes.line(cx, cy, ax, ay, lineWidth);
    }
}
