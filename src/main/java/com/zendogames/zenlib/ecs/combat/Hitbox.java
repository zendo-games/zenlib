package com.zendogames.zenlib.ecs.combat;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.FramePool;
import com.zendogames.zenlib.collision.shapes.CollisionCaps;
import com.zendogames.zenlib.collision.shapes.CollisionCirc;
import com.zendogames.zenlib.collision.shapes.CollisionRect;
import com.zendogames.zenlib.collision.shapes.CollisionShape;

/**
 * One hit or hurt shape of a fighter, authored facing right in pixels from the feet (the entity
 * position), or from a Spine bone when {@link #bone} is set. Immutable and shared by every entity
 * whose {@link HitboxSet} lists it, so the mirrored copy for facing left is built once, here.
 * <p>
 * A HIT box touches a HURT box when the shapes {@link #overlaps overlap}; touching edges never
 * count, as for colliders. Where each shape is placed is the {@code HitSystem.Space}'s business:
 * where the art is drawn, unless the game's space says otherwise.
 * <pre>{@code
 * Hitbox.hurt("body", rect(-8, 0, 16, 44));                      // from the feet
 * Hitbox.hurt("head", circ(2, 14, 12)).on("head");               // follows the head bone
 * Hitbox.hit("fist", rect(0, -4, 14, 8), JAB).on("front-fist");  // a HIT box needs an Attack
 * }</pre>
 *
 * @param name   label for the debug view and the responder ("body", "head", "fist"); a later box with the same name replaces it while out (see {@link HitboxSet})
 * @param kind   {@link Kind#HIT} strikes, {@link Kind#HURT} is struck
 * @param right  the shape facing right
 * @param left   the same shape mirrored about the anchor, for facing left
 * @param bone   the Spine bone the shape is placed from, null for the feet
 * @param attack what a HIT box does when it lands; null on a HURT box
 */
public record Hitbox(
        String name,
        Kind kind,
        CollisionShape right,
        CollisionShape left,
        String bone,
        Attack attack
) {

    public enum Kind { HIT, HURT }

    /**
     * What a HIT box carries to the {@link Strike.Responder}, in tiles like the motions' {@code launch}.
     *
     * @param damage    health taken
     * @param knockback how far the target is thrown away from the attacker, tiles
     * @param height    how high it is thrown, tiles; 0 is a shove along the floor
     * @param heavy     a heavy or special attack, for the responder to pick other sounds, particles or a longer hit-stop
     */
    public record Attack(int damage, float knockback, float height, boolean heavy) {
        public Attack(int damage, float knockback, float height) {
            this(damage, knockback, height, /*heavy*/ false);
        }
    }

    private static final String TAG = Hitbox.class.getSimpleName();

    public Hitbox {
        if (name == null || kind == null || right == null || left == null) {
            throw new GdxRuntimeException(TAG + ": name, kind and shape must be non-null");
        }
        var isHit     = kind == Kind.HIT;
        var hasAttack = attack != null;
        if (isHit != hasAttack) {
            throw new GdxRuntimeException(TAG + " '%s': a HIT box needs an attack, a HURT box has none".formatted(name));
        }
    }

    public static Hitbox hurt(String name, CollisionShape shape)                { return new Hitbox(name, Kind.HURT, shape, mirror(shape), null, null); }
    public static Hitbox hit (String name, CollisionShape shape, Attack attack) { return new Hitbox(name, Kind.HIT,  shape, mirror(shape), null, attack); }

    public static Attack attack     (int damage, float knockback, float height) { return new Attack(damage, knockback, height); }
    public static Attack attackHeavy(int damage, float knockback, float height) { return new Attack(damage, knockback, height, /*heavy*/ true); }

    /** A copy placed from the named Spine bone's position instead of the feet; the shape does not rotate with the bone. */
    public Hitbox on(String bone) { return new Hitbox(name, kind, right, left, bone, attack); }

    public CollisionShape shape(int facing) { return (facing < 0) ? left : right; }

    /** Whether this box, facing {@code facing} with its anchor at {@code at}, overlaps {@code other} facing {@code otherFacing} at {@code otherAt}; world pixels. */
    public boolean overlaps(int facing, PointI2 at, Hitbox other, int otherFacing, PointI2 otherAt) {
        return shape(facing).overlaps(other.shape(otherFacing), at, otherAt, FramePool.pi2(0, 0));
    }

    public boolean isHit()  { return kind == Kind.HIT; }
    public boolean isHurt() { return kind == Kind.HURT; }

    // ------------------------------------------------------------------------
    // Shapes: the collider shapes, so the overlap math and the debug outlines are the library's
    // ------------------------------------------------------------------------

    public static CollisionShape rect(float x, float y, float w, float h) { return new CollisionRect(x, y, w, h); }
    public static CollisionShape circ(float x, float y, float radius)     { return new CollisionCirc(x, y, radius); }

    /** A capsule along its longer side: taller than wide is vertical (a limb), wider than tall is horizontal (a swipe). */
    public static CollisionShape capsule(float x, float y, float w, float h) {
        return new CollisionCaps((w < h) ? CollisionCaps.Axis.VERTICAL : CollisionCaps.Axis.HORIZONTAL, x, y, w, h);
    }

    /** The shape reflected about x = 0. */
    static CollisionShape mirror(CollisionShape shape) {
        return switch (shape) {
            case CollisionRect r -> new CollisionRect(-(r.rectangle().x + r.rectangle().width), r.rectangle().y, r.rectangle().width, r.rectangle().height);
            case CollisionCirc c -> new CollisionCirc(-c.circle().x, c.circle().y, c.circle().radius);
            case CollisionCaps c -> new CollisionCaps(c.axis(), -(c.bounds().x + c.bounds().width), c.bounds().y, c.bounds().width, c.bounds().height);
            default -> throw new GdxRuntimeException(TAG + ": a hitbox is a rect, circle or capsule, not a " + shape.getClass().getSimpleName());
        };
    }
}
