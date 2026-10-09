package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.collision.CollisionMask;
import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.collision.shapes.*;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.systems.ContactSystem;

/**
 * An entity's collision shape and how it interacts with other colliders. Immutable;
 * the {@link #blockedBy(CollisionMask...)} and {@link #detects(CollisionMask...)}
 * copies finish a factory call at spawn time. One instance per entity, since the
 * shape inside is mutable.
 * <pre>{@code
 * entity.add(Collider.rect(GameMask.PLAYER, -4, 0, 8, 16)
 *         .blockedBy(GameMask.SOLID, GameMask.PLAYER)
 *         .detects(GameMask.TRIGGER));
 * }</pre>
 *
 * @param shape     the entity-local geometry
 * @param mask      what this collider is, the single category that others filter on
 * @param blockedBy bit set of masks that stop this collider's movement, read by {@link CollisionWorld#probe}
 * @param detects   bit set of masks reported as {@code OVERLAP} contacts, read by {@link ContactSystem}
 *
 */
public record Collider(
        CollisionShape shape,
        CollisionMask mask,
        int blockedBy,
        int detects
) implements Component {

    public static final ComponentType<Collider> TYPE = ComponentType.register(Collider.class);
    public static Collider get(Entity e) { return TYPE.get(e); }
    public static boolean  has(Entity e) { return TYPE.has(e); }

    private static final String TAG = Collider.class.getSimpleName();

    public Collider {
        if (shape == null || mask == null) {
            throw new GdxRuntimeException(TAG + ": shape and mask must be non-null");
        }
    }

    /** Blocks nothing, detects nothing: a static obstacle or pure target. */
    public Collider(CollisionShape shape, CollisionMask mask) {
        this(shape, mask, 0, 0);
    }

    public Collider blockedBy(CollisionMask... masks) { return new Collider(shape, mask, CollisionMask.of(masks), detects); }
    public Collider detects(CollisionMask... masks)   { return new Collider(shape, mask, blockedBy, CollisionMask.of(masks)); }

    public boolean isBlockedBy(CollisionMask m) { return m.in(blockedBy); }
    public boolean canDetect(CollisionMask m)   { return m.in(detects); }

    /** The shape as a specific type, throwing with both names when it is another. */
    public <T extends CollisionShape> T shape(Class<T> shapeClass) {
        if (!shapeClass.isInstance(shape)) {
            throw new GdxRuntimeException(TAG + ": shape is a %s, not a %s".formatted(shape.getClass().getSimpleName(), shapeClass.getSimpleName()));
        }
        return shapeClass.cast(shape);
    }

    // ------------------------------------------------------------------------
    // Factories: shape + mask, then chain blockedBy / detects
    // ------------------------------------------------------------------------

    public static Collider rect(CollisionMask mask, Rectangle r)                           { return rect(mask, r.x, r.y, r.width, r.height); }
    public static Collider rect(CollisionMask mask, float x, float y, float w, float h)    { return new Collider(new CollisionRect(x, y, w, h), mask); }
    public static Collider circ(CollisionMask mask, float x, float y, float r)             { return new Collider(new CollisionCirc(x, y, r), mask); }
    public static Collider grid(CollisionMask mask, int cellSize, int cols, int rows)      { return new Collider(new CollisionGrid(cellSize, cols, rows), mask); }
    public static Collider capsule(CollisionMask mask, float x, float y, float w, float h) { return capsule(mask, new Rectangle(x, y, w, h), CollisionCaps.Axis.VERTICAL); }
    public static Collider capsule(CollisionMask mask, Rectangle b, CollisionCaps.Axis a)  { return new Collider(new CollisionCaps(a, b), mask); }

    /** A one-way segment; see {@link CollisionAASeg}. */
    public static Collider segment(CollisionMask mask, CollisionAASeg.Axis axis, float coord, float start, float end, CollisionAASeg.Facing facing) {
        return new Collider(new CollisionAASeg(axis, coord, start, end, facing), mask);
    }

    /** The common one-way: a horizontal platform from local {@code (0, 0)} to {@code (width, 0)}, can be stood on from above. */
    public static Collider platform(CollisionMask mask, float width) {
        return segment(mask, CollisionAASeg.Axis.HORIZONTAL, 0, 0, width, CollisionAASeg.Facing.POSITIVE);
    }
}
