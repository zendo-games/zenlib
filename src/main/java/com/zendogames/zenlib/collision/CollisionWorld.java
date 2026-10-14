package com.zendogames.zenlib.collision;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.FramePool;
import com.zendogames.zenlib.collision.shapes.CollisionAASeg;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Position;

/**
 * Overlap queries over every entity with a {@link Position} and a {@link Collider}, one per
 * {@link World}. Stateless apart from {@link #lastContact()}: there is no current mover, every
 * call says what to test, where, against which masks, and which entity to skip (itself).
 * <p>
 * {@link #probe} is the per-pixel question a mover asks before each step; the one-way rule is
 * {@link CollisionAASeg#blocks}, applied here. The rest are plain overlap queries for sensors,
 * triggers and spawn checks. Every candidate is tested, there is no broadphase yet.
 * <pre>{@code
 * var block = collision.probe(e, dx, 0);          // this entity's shape, position and blockedBy
 * if (block.isBlocked()) contacts.blocked(dx, 0, collision.lastContact());
 * }</pre>
 */
public class CollisionWorld {

    private static final String TAG = CollisionWorld.class.getSimpleName();

    private final Family family;

    /** Entity behind the most recent SOLID / ONE_WAY probe result, null after a NONE. */
    private Entity lastContact;

    // Scratch for probe; the pool would work, but a pixel walk can probe hundreds of times per tick. */
    private final PointI2 probePos    = new PointI2();
    private final PointI2 probeOffset = new PointI2();

    public CollisionWorld(World world) {
        this.family = world.family(Family.all(Position.TYPE, Collider.TYPE));
    }

    /** Every collidable entity, a live view. */
    public Family family() {
        return family;
    }

    // ------------------------------------------------------------------------
    // Probe: would a move be blocked
    // ------------------------------------------------------------------------

    /**
     * Whether moving {@code shape} from {@code (x, y)} by {@code (dx, dy)} is stopped by an entity
     * whose mask is in {@code blockedBy}, skipping {@code excluded}. A SOLID blocker wins over a
     * ONE_WAY one; the entity behind the result is {@link #lastContact()} until the next probe.
     */
    public Block probe(CollisionShape shape, int x, int y, int dx, int dy, int blockedBy, Entity excluded) {
        lastContact = null;
        if (blockedBy == 0) return Block.NONE;

        var pos    = probePos.set(x, y);
        var offset = probeOffset.set(dx, dy);
        var result = Block.NONE;
        Entity resultEntity = null;

        var entities = family.entities();
        for (int i = 0; i < entities.size; i++) {
            var other = entities.get(i);
            if (other == excluded) continue;

            var otherCol = Collider.get(other);
            if (!otherCol.mask().in(blockedBy)) continue;

            var otherPos = Position.get(other);
            if (!shape.overlaps(otherCol.shape(), pos, otherPos, offset)) continue;

            if (otherCol.shape() instanceof CollisionAASeg seg) {
                if (seg.blocks(shape, pos, otherPos, dx, dy)) {
                    result = Block.ONE_WAY;
                    resultEntity = other;
                }
            } else {
                lastContact = other;
                return Block.SOLID;
            }
        }

        lastContact = resultEntity;
        return result;
    }

    /** {@link #probe} for an entity with a {@link Position} and a {@link Collider}: its shape, position and {@code blockedBy}, excluding itself. */
    public Block probe(Entity mover, int dx, int dy) {
        var pos = Position.get(mover);
        var col = Collider.get(mover);
        return probe(col.shape(), pos.x, pos.y, dx, dy, col.blockedBy(), mover);
    }

    public Entity lastContact() {
        return lastContact;
    }

    // ------------------------------------------------------------------------
    // Overlap queries
    // ------------------------------------------------------------------------

    /** True when {@code shape} moved by {@code (dx, dy)} overlaps any entity whose mask is in {@code masks}. */
    public boolean any(CollisionShape shape, int x, int y, int dx, int dy, int masks, Entity excluded) {
        return (first(shape, x, y, dx, dy, masks, excluded) != null);
    }

    /** The first overlapping entity in family order, or null. */
    public Entity first(CollisionShape shape, int x, int y, int dx, int dy, int masks, Entity excluded) {
        if (masks == 0) return null;
        var pos    = FramePool.pi2(x, y);
        var offset = FramePool.pi2(dx, dy);
        var entities = family.entities();
        for (int i = 0; i < entities.size; i++) {
            var other = entities.get(i);
            if (hits(other, excluded, shape, pos, offset, masks)) {
                return other;
            }
        }
        return null;
    }

    /** Appends every overlapping entity to {@code out}, in family order. */
    public void all(CollisionShape shape, int x, int y, int dx, int dy, int masks, Entity excluded, Array<Entity> out) {
        if (masks == 0) return;
        var pos    = FramePool.pi2(x, y);
        var offset = FramePool.pi2(dx, dy);
        var entities = family.entities();
        for (int i = 0; i < entities.size; i++) {
            var other = entities.get(i);
            if (hits(other, excluded, shape, pos, offset, masks)) {
                out.add(other);
            }
        }
    }

    /** {@link #all} at the entity's own position against its {@code detects} masks: what it is touching now. */
    public void detects(Entity mover, Array<Entity> out) {
        var pos = Position.get(mover);
        var col = Collider.get(mover);
        all(col.shape(), pos.x, pos.y, 0, 0, col.detects(), mover, out);
    }

    // ------------------------------------------------------------------------
    // Raycasting
    // ------------------------------------------------------------------------

    /**
     * Nearest entity whose mask is in {@code masks} along the ray from {@code (ox, oy)} in the direction
     * {@code (dirX, dirY)} (normalized here), within {@code range} pixels, skipping {@code excluded}.
     * Fills {@code out} and returns true on a hit. Edges count as hits; an origin inside a shape hits at
     * distance zero; a one-way segment is hit from either side and reported as ONE_WAY (perception sees
     * the platform, the mover's one-way rule stays in {@link #probe}). Every candidate is tested, no broadphase.
     */
    public boolean raycast(float ox, float oy, float dirX, float dirY, float range, int masks, Entity excluded, RayHit out) {
        var length = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (length == 0f) throw new GdxRuntimeException(TAG + ": raycast needs a direction");

        dirX /= length;
        dirY /= length;
        out.clear(range, ox + dirX * range, oy + dirY * range);
        if (masks == 0 || range <= 0f) return false;

        var best = range;
        var bestBlock = Block.NONE;
        var bestEntity = (Entity) null;

        var entities = family.entities();
        for (int i = 0; i < entities.size; i++) {
            var other = entities.get(i);
            if (other == excluded) continue;

            var otherCol = Collider.get(other);
            if (!otherCol.mask().in(masks)) continue;

            // Only as far as the best hit so far: a farther shape cannot win
            var dist = otherCol.shape().raycast(ox, oy, dirX, dirY, best, Position.get(other));
            if (dist < 0f) continue;

            var block = (otherCol.shape() instanceof CollisionAASeg) ? Block.ONE_WAY : Block.SOLID;
            var tie = (dist == best) && (block == Block.SOLID) && (bestBlock == Block.ONE_WAY); // SOLID wins a tie, as in probe
            if (bestEntity == null || dist < best || tie) {
                best = dist;
                bestBlock = block;
                bestEntity = other;
            }
        }
        if (bestEntity == null) return false;

        out.hit(best, ox + dirX * best, oy + dirY * best, bestEntity, bestBlock);
        return true;
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static boolean hits(Entity other, Entity excluded, CollisionShape shape, PointI2 pos, PointI2 offset, int masks) {
        if (other == excluded) return false;
        var otherCol = Collider.get(other);
        if (!otherCol.mask().in(masks)) return false;
        return shape.overlaps(otherCol.shape(), pos, Position.get(other), offset);
    }
}
