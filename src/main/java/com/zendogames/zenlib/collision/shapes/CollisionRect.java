package com.zendogames.zenlib.collision.shapes;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Shape2D;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.FramePool;

/**
 * {@link Rectangle} {@code CollisionShape}, entity-local.
 * @param rectangle local offset and size; resize in place with {@code rectangle().set(...)}
 */
public record CollisionRect(Rectangle rectangle) implements CollisionShape {

    public CollisionRect(float x, float y, float w, float h) {
        this(new Rectangle(x, y, w, h));
    }

    public CollisionRect {
        if (rectangle.width <= 0 || rectangle.height <= 0) {
            throw new GdxRuntimeException("CollisionRect: width and height must be positive");
        }
    }

    @Override
    public Shape2D shape2d() {
        return rectangle;
    }

    /** (pooled) The rectangle at the entity's world position. */
    public Rectangle rectangle(PointI2 position) {
        return FramePool.rect(
                position.x + rectangle.x,
                position.y + rectangle.y,
                rectangle.width, rectangle.height);
    }

    @Override public float left(PointI2 position)   { return position.x + rectangle.x; }
    @Override public float right(PointI2 position)  { return position.x + rectangle.x + rectangle.width; }
    @Override public float top(PointI2 position)    { return position.y + rectangle.y + rectangle.height; }
    @Override public float bottom(PointI2 position) { return position.y + rectangle.y; }

    @Override
    public boolean overlaps(CollisionShape that, PointI2 thisPos, PointI2 thatPos, PointI2 offset) {
        // Apply the offset so 'thisRect' is at the candidate position before checking
        var thisRect = rectangle(thisPos);
        thisRect.x += offset.x;
        thisRect.y += offset.y;

        return switch (that) {
            case CollisionRect rect -> overlapsRect(thisRect, rect, thatPos);
            case CollisionCirc circ -> overlapsCirc(thisRect, circ, thatPos);
            case CollisionCaps caps -> CollisionCaps.overlapsCapsRect(caps, thatPos, thisRect);
            case CollisionAASeg seg -> CollisionAASeg.overlapsRectAASeg(thisRect, seg, thatPos);
            case CollisionGrid grid -> overlapsGrid(thisRect, grid, thatPos);
        };
    }

    @Override
    public float raycast(float ox, float oy, float dirX, float dirY, float range, PointI2 pos) {
        return raycastBox(ox, oy, dirX, dirY, range, left(pos), bottom(pos), right(pos), top(pos));
    }

    /**
     * Slab test of a unit ray against an axis-aligned box, edges inclusive: the distance at which the
     * ray enters the box, {@code 0} when the origin is inside, {@code -1} for a miss within {@code range}.
     * Shared by the capsule's inner rect and the grid's bounds.
     */
    public static float raycastBox(float ox, float oy, float dirX, float dirY, float range,
                                   float left, float bottom, float right, float top) {
        float tMin = 0f;
        float tMax = range;
        if (dirX == 0f) {
            if (ox < left || ox > right) return -1f;
        } else {
            float inv = 1f / dirX;
            float t0 = (left  - ox) * inv;
            float t1 = (right - ox) * inv;
            if (t0 > t1) { float tmp = t0; t0 = t1; t1 = tmp; }
            tMin = Math.max(tMin, t0);
            tMax = Math.min(tMax, t1);
        }
        if (dirY == 0f) {
            if (oy < bottom || oy > top) return -1f;
        } else {
            float inv = 1f / dirY;
            float t0 = (bottom - oy) * inv;
            float t1 = (top    - oy) * inv;
            if (t0 > t1) { float tmp = t0; t0 = t1; t1 = tmp; }
            tMin = Math.max(tMin, t0);
            tMax = Math.min(tMax, t1);
        }
        return (tMin <= tMax) ? tMin : -1f;
    }

    // ------------------------------------------------------------------------
    // Static overlap helpers: 'thisRect' is already in world coords at its candidate position
    // ------------------------------------------------------------------------

    public static boolean overlapsRect(Rectangle thisRect, CollisionRect rect, PointI2 thatPos) {
        return thisRect.overlaps(rect.rectangle(thatPos));
    }

    public static boolean overlapsCirc(Rectangle thisRect, CollisionCirc circ, PointI2 thatPos) {
        return Intersector.overlaps(circ.circle(thatPos), thisRect);
    }

    /**
     * Rect-vs-grid treats every solid cell as a full cell, so a rect entering a ramp cell from its
     * open half is blocked early.
     * <p>
     * TODO: Promote to the shape-aware test in {@link CollisionCaps#overlapsCapsGrid} when a rect mover needs ramps.
     */
    public static boolean overlapsGrid(Rectangle thisRect, CollisionGrid grid, PointI2 gridPos) {
        var range = grid.cellRange(thisRect, gridPos);
        for (int y = range.y; y < range.w; y++) {
            for (int x = range.x; x < range.z; x++) {
                if (grid.get(x, y).isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }
}
