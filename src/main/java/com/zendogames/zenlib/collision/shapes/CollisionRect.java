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
