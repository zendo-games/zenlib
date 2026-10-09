package com.zendogames.zenlib.collision.shapes;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Shape2D;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.FramePool;

/**
 * Axis-aligned line segment in entity-local space: the one-way collider. The segment has no
 * interior, so a mover is only ever blocked when it moves against {@link #facing} with its near
 * edge starting on the support side of the line; see {@link #blocks}. A horizontal segment facing
 * {@link Facing#POSITIVE} is a jump-through platform, facing {@link Facing#NEGATIVE} a ceiling you
 * can rise through but not fall through; vertical segments are the same for sideways movement.
 * <p>
 * A capsule rests with its cap tangent to the line and slides off an endpoint along the cap's arc:
 * the endpoint stays in contact until the cap center is more than one radius past it.
 *
 * @param axis   the axis the segment runs along
 * @param coord  local offset on the other axis: y for a horizontal segment, x for a vertical one
 * @param start  local start along {@code axis}, {@code start <= end}
 * @param end    local end along {@code axis}
 * @param facing which side supports: {@code POSITIVE} is up for horizontal, right for vertical
 */
public record CollisionAASeg(
        Axis axis,
        float coord,
        float start,
        float end,
        Facing facing
) implements CollisionShape, Shape2D {

    public enum Axis   { HORIZONTAL, VERTICAL }
    public enum Facing { POSITIVE, NEGATIVE }

    public CollisionAASeg {
        if (axis == null || facing == null) throw new GdxRuntimeException("CollisionAASeg: axis and facing must be non-null");
        if (end < start) throw new GdxRuntimeException("CollisionAASeg: start must be <= end");
    }

    public boolean isHorizontal() { return axis == Axis.HORIZONTAL; }
    public boolean isVertical()   { return axis == Axis.VERTICAL; }
    public float length()         { return end - start; }

    /** The constant-axis world value: world-Y for a horizontal seg, world-X for a vertical seg. */
    public float worldCoord(PointI2 p) { return isHorizontal() ? p.y + coord : p.x + coord; }
    public float worldStart(PointI2 p) { return isHorizontal() ? p.x + start : p.y + start; }
    public float worldEnd(PointI2 p)   { return isHorizontal() ? p.x + end   : p.y + end; }

    // World endpoints, so the overlap math below is axis-agnostic
    public float x0(PointI2 p) { return isHorizontal() ? p.x + start : p.x + coord; }
    public float x1(PointI2 p) { return isHorizontal() ? p.x + end   : p.x + coord; }
    public float y0(PointI2 p) { return isHorizontal() ? p.y + coord : p.y + start; }
    public float y1(PointI2 p) { return isHorizontal() ? p.y + coord : p.y + end; }

    // No interior: top == bottom for HORIZONTAL, left == right for VERTICAL
    @Override public float left(PointI2 p)   { return x0(p); }
    @Override public float right(PointI2 p)  { return x1(p); }
    @Override public float top(PointI2 p)    { return y1(p); }
    @Override public float bottom(PointI2 p) { return y0(p); }

    @Override public Shape2D shape2d() { return this; }

    // Shape2D contract: a segment has zero area, so it contains no points
    @Override public boolean contains(Vector2 v)        { return false; }
    @Override public boolean contains(float x, float y) { return false; }

    /**
     * The one-way rule, geometry only: true when a mover of {@code shape} at {@code moverPos},
     * about to move by {@code (dx, dy)}, is moving against the segment's facing and its near
     * edge is still on the support side of the line (at or past it). The caller has already
     * established that the moved shape overlaps the segment. Whether the mover may drop through
     * anyway is intent, and stays with the mover.
     */
    public boolean blocks(CollisionShape shape, PointI2 moverPos, PointI2 segPos, int dx, int dy) {
        float line = worldCoord(segPos);
        var positive = (facing == Facing.POSITIVE);
        if (isHorizontal()) {
            return positive ? (dy < 0 && shape.bottom(moverPos) >= line)
                            : (dy > 0 && shape.top(moverPos)    <= line);
        } else {
            return positive ? (dx < 0 && shape.left(moverPos)   >= line)
                            : (dx > 0 && shape.right(moverPos)  <= line);
        }
    }

    @Override
    public boolean overlaps(CollisionShape that, PointI2 thisPos, PointI2 thatPos, PointI2 offset) {
        // Reverse-dispatch: the math lives in the static helpers below, which the other shapes
        // call directly from their own switch arms
        var offsetPos = FramePool.pi2(thisPos.x + offset.x, thisPos.y + offset.y);
        return switch (that) {
            case CollisionRect rect -> overlapsRectAASeg(rect.rectangle(thatPos), this, offsetPos);
            case CollisionCirc circ -> overlapsCircAASeg(circ.circle(thatPos), this, offsetPos);
            case CollisionCaps caps -> overlapsCapsAASeg(caps, thatPos, this, offsetPos);
            // No interior on either side, nothing to overlap; a moving segment is not a use case
            case CollisionGrid grid -> false;
            case CollisionAASeg seg -> false;
        };
    }

    // ------------------------------------------------------------------------
    // Static overlap helpers: the other shape is already in world coords at its candidate position
    // ------------------------------------------------------------------------

    /** Capsule-vs-segment: the capsule's axis is within one radius of the segment. */
    public static boolean overlapsCapsAASeg(CollisionCaps caps, PointI2 capsPos, CollisionAASeg seg, PointI2 segPos) {
        var bot = caps.bottomCircle(capsPos);
        var top = caps.topCircle(capsPos);
        var d = CollisionCaps.segmentSegmentDistance(bot.x, bot.y, top.x, top.y, seg.x0(segPos), seg.y0(segPos), seg.x1(segPos), seg.y1(segPos));
        return d < caps.radius();
    }

    /** Rect-vs-segment: the line is strictly inside the rect's span across it and the extents along it intersect. */
    public static boolean overlapsRectAASeg(Rectangle rect, CollisionAASeg seg, PointI2 segPos) {
        float line = seg.worldCoord(segPos);
        float s0   = seg.worldStart(segPos);
        float s1   = seg.worldEnd(segPos);
        if (seg.isHorizontal()) {
            return line > rect.y && line < rect.y + rect.height
                && s1   > rect.x && s0   < rect.x + rect.width;
        } else {
            return line > rect.x && line < rect.x + rect.width
                && s1   > rect.y && s0   < rect.y + rect.height;
        }
    }

    /** Circle-vs-segment: the center is within one radius of the segment. */
    public static boolean overlapsCircAASeg(Circle circ, CollisionAASeg seg, PointI2 segPos) {
        var d = CollisionCaps.distSegPoint(seg.x0(segPos), seg.y0(segPos), seg.x1(segPos), seg.y1(segPos), circ.x, circ.y);
        return d < circ.radius;
    }
}
