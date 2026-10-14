package com.zendogames.zenlib.collision.shapes;

import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.FramePool;

/**
 * Capsule {@code CollisionShape}: an axis-aligned rectangle with semicircular end caps, the usual
 * character body. For a {@link Axis#VERTICAL} capsule {@code radius = bounds.width / 2} and the
 * caps sit at the top and bottom of {@code bounds}; for {@link Axis#HORIZONTAL}
 * {@code radius = bounds.height / 2} and the caps sit at the left and right. The overlap math
 * works on the capsule's axis segment (cap center to cap center) and its radius, so it is the
 * same for both orientations.
 *
 * @param axis   which way the capsule is long
 * @param bounds local outer bounds, caps included
 */
public record CollisionCaps(Axis axis, Rectangle bounds) implements CollisionShape, Shape2D {

    public enum Axis { VERTICAL, HORIZONTAL }

    public CollisionCaps(float x, float y, float w, float h) {
        this(Axis.VERTICAL, x, y, w, h);
    }

    public CollisionCaps(Axis axis, float x, float y, float w, float h) {
        this(axis, new Rectangle(x, y, w, h));
    }

    public CollisionCaps {
        if (bounds.width <= 0 || bounds.height <= 0) throw new GdxRuntimeException("CollisionCaps: width and height must be positive");
        if (bounds.width == bounds.height) throw new GdxRuntimeException("CollisionCaps: width==height is a circle, use CollisionCirc");
        if (axis == Axis.VERTICAL   && bounds.width  > bounds.height) throw new GdxRuntimeException("CollisionCaps: a vertical capsule needs width < height");
        if (axis == Axis.HORIZONTAL && bounds.height > bounds.width)  throw new GdxRuntimeException("CollisionCaps: a horizontal capsule needs height < width");
    }

    public float radius() {
        return switch (axis) {
            case VERTICAL   -> bounds.width  / 2f;
            case HORIZONTAL -> bounds.height / 2f;
        };
    }

    @Override
    public Shape2D shape2d() {
        return this;
    }

    @Override public float left(PointI2 position)   { return position.x + bounds.x; }
    @Override public float right(PointI2 position)  { return position.x + bounds.x + bounds.width; }
    @Override public float top(PointI2 position)    { return position.y + bounds.y + bounds.height; }
    @Override public float bottom(PointI2 position) { return position.y + bounds.y; }

    /** Pixels a vertical capsule may step up to clear a low obstruction while moving sideways: its cap radius. */
    public int slideUpHeight() {
        return axis == Axis.VERTICAL ? (int) radius() : 0;
    }

    @Override
    public float innerTop(PointI2 position) {
        return switch (axis) {
            case VERTICAL   -> top(position) - radius();
            case HORIZONTAL -> top(position);
        };
    }

    @Override
    public float innerBottom(PointI2 position) {
        return switch (axis) {
            case VERTICAL   -> bottom(position) + radius();
            case HORIZONTAL -> bottom(position);
        };
    }

    /** (pooled) Outer bounds at the entity's world position. */
    public Rectangle bounds(PointI2 position) {
        return FramePool.rect(
                position.x + bounds.x,
                position.y + bounds.y,
                bounds.width, bounds.height);
    }

    /**
     * (pooled) The rectangle between the two cap centers: {@code bounds} shrunk by {@code radius}
     * along the axis. A point inside it is inside the capsule.
     */
    public Rectangle innerRect(PointI2 position) {
        var r = radius();
        return switch (axis) {
            case VERTICAL   -> FramePool.rect(position.x + bounds.x,     position.y + bounds.y + r, bounds.width,          bounds.height - 2f * r);
            case HORIZONTAL -> FramePool.rect(position.x + bounds.x + r, position.y + bounds.y,     bounds.width - 2f * r, bounds.height);
        };
    }

    /** (pooled) The upper cap for a vertical capsule, the right cap for a horizontal one. */
    public Circle topCircle(PointI2 position) {
        var r = radius();
        return switch (axis) {
            case VERTICAL   -> FramePool.circle(position.x + bounds.x + r,                position.y + bounds.y + bounds.height - r, r);
            case HORIZONTAL -> FramePool.circle(position.x + bounds.x + bounds.width - r, position.y + bounds.y + r,                 r);
        };
    }

    /** (pooled) The lower cap for a vertical capsule, the left cap for a horizontal one; one radius in from the bottom-left corner either way. */
    public Circle bottomCircle(PointI2 position) {
        var r = radius();
        return FramePool.circle(position.x + bounds.x + r, position.y + bounds.y + r, r);
    }

    @Override
    public boolean overlaps(CollisionShape that, PointI2 thisPos, PointI2 thatPos, PointI2 offset) {
        // Bake the offset into a pooled position so every accessor sees the capsule at its candidate position
        var offsetPos = FramePool.pi2(thisPos.x + offset.x, thisPos.y + offset.y);

        return switch (that) {
            case CollisionRect rect -> overlapsCapsRect(this, offsetPos, rect.rectangle(thatPos));
            case CollisionCirc circ -> overlapsCapsCirc(this, offsetPos, circ.circle(thatPos));
            case CollisionCaps caps -> overlapsCapsCaps(this, offsetPos, caps, thatPos);
            case CollisionAASeg seg -> CollisionAASeg.overlapsCapsAASeg(this, offsetPos, seg, thatPos);
            case CollisionGrid grid -> overlapsCapsGrid(this, offsetPos, grid, thatPos);
        };
    }

    /** The nearer of the inner rect and the two cap circles, after a cheap reject on the outer bounds. */
    @Override
    public float raycast(float ox, float oy, float dirX, float dirY, float range, PointI2 pos) {
        float left = left(pos), bottom = bottom(pos), right = right(pos), top = top(pos);
        if (CollisionRect.raycastBox(ox, oy, dirX, dirY, range, left, bottom, right, top) < 0f) return -1f;

        float r = radius();
        boolean vertical = (axis == Axis.VERTICAL);
        float innerLeft   = vertical ? left       : left + r;
        float innerRight  = vertical ? right      : right - r;
        float innerBottom = vertical ? bottom + r : bottom;
        float innerTop    = vertical ? top - r    : top;
        float ax = left + r,                   ay = bottom + r;   // bottom / left cap, one radius in from the bottom-left corner
        float bx = vertical ? left + r : right - r;
        float by = vertical ? top - r  : bottom + r;              // top / right cap

        float t = CollisionRect.raycastBox(ox, oy, dirX, dirY, range, innerLeft, innerBottom, innerRight, innerTop);
        t = nearest(t, CollisionCirc.raycastCircle(ox, oy, dirX, dirY, range, ax, ay, r));
        t = nearest(t, CollisionCirc.raycastCircle(ox, oy, dirX, dirY, range, bx, by, r));
        return t;
    }

    /** The smaller of two ray distances, where {@code -1} is a miss. */
    static float nearest(float a, float b) {
        if (a < 0f) return b;
        if (b < 0f) return a;
        return Math.min(a, b);
    }

    // ------------------------------------------------------------------------
    // Static overlap helpers, also called from the other shapes' reverse arms.
    // The capsule is at 'capsPos'; the other shape is already in world coords.
    // ------------------------------------------------------------------------

    public static boolean overlapsCapsRect(CollisionCaps caps, PointI2 capsPos, Rectangle rect) {
        return caps.innerRect(capsPos).overlaps(rect)
            || Intersector.overlaps(caps.bottomCircle(capsPos), rect)
            || Intersector.overlaps(caps.topCircle(capsPos),    rect);
    }

    public static boolean overlapsCapsCirc(CollisionCaps caps, PointI2 capsPos, Circle circ) {
        var bot = caps.bottomCircle(capsPos);
        var top = caps.topCircle(capsPos);
        var d = distSegPoint(bot.x, bot.y, top.x, top.y, circ.x, circ.y);
        return d < (caps.radius() + circ.radius);
    }

    public static boolean overlapsCapsCaps(CollisionCaps a, PointI2 aPos, CollisionCaps b, PointI2 bPos) {
        var aBot = a.bottomCircle(aPos);
        var aTop = a.topCircle(aPos);
        var bBot = b.bottomCircle(bPos);
        var bTop = b.topCircle(bPos);
        var d = segmentSegmentDistance(aBot.x, aBot.y, aTop.x, aTop.y, bBot.x, bBot.y, bTop.x, bTop.y);
        return d < (a.radius() + b.radius());
    }

    /** Shape-aware: full cells as rectangles, ramp cells as their triangle. */
    public static boolean overlapsCapsGrid(CollisionCaps caps, PointI2 capsPos, CollisionGrid grid, PointI2 gridPos) {
        var range = grid.cellRange(caps.bounds(capsPos), gridPos);
        for (int y = range.y; y < range.w; y++) {
            for (int x = range.x; x < range.z; x++) {
                var shape = grid.get(x, y);
                if (shape == CollisionGrid.CellShape.EMPTY) continue;
                var cell = grid.cellRect(x, y, gridPos);
                var hit = (shape == CollisionGrid.CellShape.FULL)
                        ? overlapsCapsRect(caps, capsPos, cell)
                        : overlapsCapsRamp(caps, capsPos, shape, cell.x, cell.y, cell.width);
                if (hit) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Capsule-vs-ramp triangle at cell origin {@code (cellX, cellY)} with edge length {@code size}:
     * the capsule's axis is within one radius of any of the triangle's three edges, or a cap
     * center is inside the triangle (deep penetration). Vertices are named by cell corner:
     * BL {@code (x0, y0)}, BR {@code (x1, y0)}, TL {@code (x0, y1)}, TR {@code (x1, y1)}.
     */
    public static boolean overlapsCapsRamp(CollisionCaps caps, PointI2 capsPos, CollisionGrid.CellShape shape, float cellX, float cellY, float size) {
        var bot = caps.bottomCircle(capsPos);
        var top = caps.topCircle(capsPos);
        float r  = caps.radius();
        float x0 = cellX;
        float y0 = cellY;
        float x1 = cellX + size;
        float y1 = cellY + size;

        // @formatter:off
        boolean nearEdges = switch (shape) {
            case RAMP_NE -> near(bot, top, r, x0, y0, x1, y0)   // bottom
                         || near(bot, top, r, x1, y0, x1, y1)   // right
                         || near(bot, top, r, x0, y0, x1, y1);  // hypotenuse BL -> TR
            case RAMP_NW -> near(bot, top, r, x0, y0, x1, y0)   // bottom
                         || near(bot, top, r, x0, y0, x0, y1)   // left
                         || near(bot, top, r, x1, y0, x0, y1);  // hypotenuse BR -> TL
            case RAMP_SE -> near(bot, top, r, x0, y1, x1, y1)   // top
                         || near(bot, top, r, x1, y0, x1, y1)   // right
                         || near(bot, top, r, x0, y1, x1, y0);  // hypotenuse TL -> BR
            case RAMP_SW -> near(bot, top, r, x0, y1, x1, y1)   // top
                         || near(bot, top, r, x0, y0, x0, y1)   // left
                         || near(bot, top, r, x1, y1, x0, y0);  // hypotenuse TR -> BL
            case EMPTY, FULL -> throw new GdxRuntimeException("CollisionCaps: " + shape + " is not a ramp");
        };
        if (nearEdges) return true;

        return shape.contains(bot.x - cellX, bot.y - cellY, size)
            || shape.contains(top.x - cellX, top.y - cellY, size);
        // @formatter:on
    }

    /** True when the capsule axis {@code bot -> top} passes within {@code r} of the edge {@code (ax, ay) -> (bx, by)}. */
    private static boolean near(Circle bot, Circle top, float r, float ax, float ay, float bx, float by) {
        return segmentSegmentDistance(bot.x, bot.y, top.x, top.y, ax, ay, bx, by) < r;
    }

    // ------------------------------------------------------------------------
    // Segment distance primitives, shared with CollisionAASeg
    // ------------------------------------------------------------------------

    /**
     * Shortest distance between two segments, zero when they intersect. Non-intersecting
     * segments are closest at one of the four endpoints, so this takes the minimum of the
     * four point-to-segment distances.
     */
    static float segmentSegmentDistance(float ax1, float ay1, float ax2, float ay2,
                                        float bx1, float by1, float bx2, float by2) {
        var s1 = FramePool.vec2(ax1, ay1);
        var e1 = FramePool.vec2(ax2, ay2);
        var s2 = FramePool.vec2(bx1, by1);
        var e2 = FramePool.vec2(bx2, by2);
        if (Intersector.intersectSegments(s1, e1, s2, e2, null)) return 0f;

        var d1 = distSegPoint(ax1, ay1, ax2, ay2, bx1, by1);
        var d2 = distSegPoint(ax1, ay1, ax2, ay2, bx2, by2);
        var d3 = distSegPoint(bx1, by1, bx2, by2, ax1, ay1);
        var d4 = distSegPoint(bx1, by1, bx2, by2, ax2, ay2);
        return Math.min(Math.min(d1, d2), Math.min(d3, d4));
    }

    /**
     * Point-to-segment distance through a pooled vector. gdx's {@code Intersector.distanceSegmentPoint}
     * looks scratch-free but routes the nearest point through a shared static buffer; this keeps the
     * math re-entrant should the pool ever go per-thread.
     */
    static float distSegPoint(float sx, float sy, float ex, float ey, float px, float py) {
        var nearest = FramePool.vec2();
        Intersector.nearestSegmentPoint(sx, sy, ex, ey, px, py, nearest);
        return nearest.dst(px, py);
    }

    // ------------------------------------------------------------------------
    // Shape2D: local-space containment, like the gdx shapes the other records return
    // ------------------------------------------------------------------------

    @Override
    public boolean contains(Vector2 point) {
        return contains(point.x, point.y);
    }

    @Override
    public boolean contains(float x, float y) {
        var r = radius();
        return switch (axis) {
            case VERTICAL -> {
                var cx       = bounds.x + r;
                var bottomCY = bounds.y + r;
                var topCY    = bounds.y + bounds.height - r;
                var inInnerRect = x >= bounds.x && x <= bounds.x + bounds.width
                               && y >= bottomCY && y <= topCY;
                if (inInnerRect) yield true;
                if (Vector2.dst(x, y, cx, bottomCY) <= r) yield true;
                yield Vector2.dst(x, y, cx, topCY) <= r;
            }
            case HORIZONTAL -> {
                var cy      = bounds.y + r;
                var leftCX  = bounds.x + r;
                var rightCX = bounds.x + bounds.width - r;
                var inInnerRect = y >= bounds.y && y <= bounds.y + bounds.height
                               && x >= leftCX && x <= rightCX;
                if (inInnerRect) yield true;
                if (Vector2.dst(x, y, leftCX, cy) <= r) yield true;
                yield Vector2.dst(x, y, rightCX, cy) <= r;
            }
        };
    }
}
