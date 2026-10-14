package com.zendogames.zenlib.collision.shapes;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Shape2D;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.FramePool;

/**
 * Geometry in entity-local space; every query takes the entity's world position. Shapes are
 * records around mutable gdx math types (a {@code CollisionRect}'s {@code Rectangle} can be
 * resized in place), so one instance belongs to one {@code Collider}.
 * <p>
 * Overlap dispatch is pairwise: each shape's {@link #overlaps} switches over the other's type and
 * the math for a pair lives in one static helper, called from both sides. Touching never counts
 * as overlapping, so a mover resting flush against a surface is not blocked until it probes one
 * pixel further into it. A {@link CollisionGrid} is only ever the {@code that} side.
 * <p>
 * Returned {@code Rectangle}s and {@code Circle}s come from the {@link FramePool}.
 */
public sealed interface CollisionShape permits CollisionCaps, CollisionRect, CollisionCirc, CollisionGrid, CollisionAASeg {

    float left(PointI2 position);
    float right(PointI2 position);
    float top(PointI2 position);
    float bottom(PointI2 position);

    /** The local-space gdx shape; subtract the entity position before a {@code contains} test. */
    Shape2D shape2d();

    /** True when this shape, moved from {@code thisPos} by {@code offset}, overlaps {@code that} at {@code thatPos}. */
    boolean overlaps(CollisionShape that, PointI2 thisPos, PointI2 thatPos, PointI2 offset);

    /** Distance along the unit ray at which it first enters this shape placed at {@code pos}, or {@code -1} for a miss within {@code range}. */
    float raycast(float ox, float oy, float dirX, float dirY, float range, PointI2 pos);

    default float centerX(PointI2 position) { return (left(position) + right(position)) * 0.5f; }
    default float centerY(PointI2 position) { return (bottom(position) + top(position)) * 0.5f; }

    /** (pooled) Axis--aligned bounds at the entity's world position. */
    default Rectangle aabb(PointI2 position) {
        var left   = left(position);
        var bottom = bottom(position);
        return FramePool.rect(left, bottom, right(position) - left, top(position) - bottom);
    }

    /** Capsule cap-center anchor for the "head" cap; for non-capsule shapes this is the same as {@link #top}. */
    default float innerTop(PointI2 position) { return top(position); }

    /** Capsule cap-center anchor for the "foot" cap; for non-capsule shapes this is the same as {@link #bottom}. */
    default float innerBottom(PointI2 position) { return bottom(position); }
}
