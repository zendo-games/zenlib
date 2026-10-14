package com.zendogames.zenlib.collision.shapes;

import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Shape2D;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.FramePool;

/**
 * {@link Circle} {@code CollisionShape}, entity-local.
 * @param circle local center offset and radius
 */
public record CollisionCirc(Circle circle) implements CollisionShape {

    public CollisionCirc(float x, float y, float radius) {
        this(new Circle(x, y, radius));
    }

    public CollisionCirc {
        if (circle.radius <= 0) {
            throw new GdxRuntimeException("CollisionCirc: radius must be positive");
        }
    }

    @Override
    public Shape2D shape2d() {
        return circle;
    }

    /** (pooled) The circle at the entity's world position. */
    public Circle circle(PointI2 position) {
        return FramePool.circle(position.x + circle.x, position.y + circle.y, circle.radius);
    }

    @Override public float left(PointI2 position)   { return position.x + circle.x - circle.radius; }
    @Override public float right(PointI2 position)  { return position.x + circle.x + circle.radius; }
    @Override public float top(PointI2 position)    { return position.y + circle.y + circle.radius; }
    @Override public float bottom(PointI2 position) { return position.y + circle.y - circle.radius; }

    /** The whole shape is one capsule 'cap', so the inner extent collapses to the center. */
    @Override public float innerTop(PointI2 position)    { return position.y + circle.y; }

    /** The whole shape is one capsule 'cap', so the inner extent collapses to the center. */
    @Override public float innerBottom(PointI2 position) { return position.y + circle.y; }

    @Override
    public boolean overlaps(CollisionShape that, PointI2 thisPos, PointI2 thatPos, PointI2 offset) {
        // Apply the offset so 'thisCirc' is at the candidate position before checking
        var thisCirc = circle(thisPos);
        thisCirc.x += offset.x;
        thisCirc.y += offset.y;

        return switch (that) {
            case CollisionRect rect -> overlapsRect(thisCirc, rect, thatPos);
            case CollisionCirc circ -> overlapsCirc(thisCirc, circ, thatPos);
            case CollisionCaps caps -> CollisionCaps.overlapsCapsCirc(caps, thatPos, thisCirc);
            case CollisionAASeg seg -> CollisionAASeg.overlapsCircAASeg(thisCirc, seg, thatPos);
            case CollisionGrid grid -> overlapsGrid(thisCirc, grid, thatPos);
        };
    }

    @Override
    public float raycast(float ox, float oy, float dirX, float dirY, float range, PointI2 pos) {
        return raycastCircle(ox, oy, dirX, dirY, range, pos.x + circle.x, pos.y + circle.y, circle.radius);
    }

    /**
     * Unit ray against a circle: the nearer root of the quadratic (with {@code |dir| = 1} the leading
     * coefficient is one), {@code 0} from inside or on the circle, {@code -1} for a miss within
     * {@code range}. Shared by the capsule's caps.
     */
    public static float raycastCircle(float ox, float oy, float dirX, float dirY, float range, float cx, float cy, float radius) {
        float fx = ox - cx;
        float fy = oy - cy;
        float c = fx * fx + fy * fy - radius * radius;
        if (c <= 0f) return 0f;                       // origin inside or on the circle

        float b = fx * dirX + fy * dirY;              // half the linear term
        if (b > 0f) return -1f;                       // the circle is behind the origin

        float disc = b * b - c;
        if (disc < 0f) return -1f;                    // passes beside it

        float t = -b - (float) Math.sqrt(disc);
        return (t <= range) ? t : -1f;
    }

    // ------------------------------------------------------------------------
    // Static overlap helpers: 'thisCirc' is already in world coords at its candidate position
    // ------------------------------------------------------------------------

    public static boolean overlapsRect(Circle thisCirc, CollisionRect rect, PointI2 thatPos) {
        return Intersector.overlaps(thisCirc, rect.rectangle(thatPos));
    }

    public static boolean overlapsCirc(Circle thisCirc, CollisionCirc circ, PointI2 thatPos) {
        return Intersector.overlaps(thisCirc, circ.circle(thatPos));
    }

    /** Circle-vs-grid treats every solid cell as a full cell; see {@link CollisionRect#overlapsGrid}. */
    public static boolean overlapsGrid(Circle thisCirc, CollisionGrid grid, PointI2 gridPos) {
        var aabb  = FramePool.rect(thisCirc.x - thisCirc.radius, thisCirc.y - thisCirc.radius, 2 * thisCirc.radius, 2 * thisCirc.radius);
        var range = grid.cellRange(aabb, gridPos);
        for (int y = range.y; y < range.w; y++) {
            for (int x = range.x; x < range.z; x++) {
                if (grid.get(x, y).isSolid() && Intersector.overlaps(thisCirc, grid.cellRect(x, y, gridPos))) {
                    return true;
                }
            }
        }
        return false;
    }
}
