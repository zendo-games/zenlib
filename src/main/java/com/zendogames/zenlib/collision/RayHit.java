package com.zendogames.zenlib.collision;

import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.components.Collider;

/**
 * Result of a {@link CollisionWorld#raycast}, owned by the caller and filled in place so a cast
 * allocates nothing: sensor readings live across ticks, which rules out the frame pool. A clear
 * cast reads as the ray end at full range with no entity.
 */
public class RayHit {

    /** Distance along the ray to the hit, in pixels; the range when clear. */
    public float distance;

    /** The hit point; the ray end when clear. */
    public float x, y;

    /** What was hit, null when clear. */
    public Entity entity;

    /** SOLID, or ONE_WAY when the entity is a {@code CollisionAASeg} (seen from either side); NONE when clear. */
    public Block block = Block.NONE;

    public boolean isHit() {
        return entity != null;
    }

    /** The hit entity's collider mask; null when clear, or when the entity has since been despawned. */
    public CollisionMask mask() {
        return (isHit() && entity.isAlive()) ? Collider.get(entity).mask() : null;
    }

    /** True when the hit entity's mask is in {@code masks}; false when clear. */
    public boolean hits(int masks) {
        var mask = mask();
        return mask != null && mask.in(masks);
    }

    /** A clear reading: full {@code range}, ending at {@code (endX, endY)}. */
    public RayHit clear(float range, float endX, float endY) {
        this.distance = range;
        this.x = endX;
        this.y = endY;
        this.entity = null;
        this.block = Block.NONE;
        return this;
    }

    public RayHit hit(float distance, float x, float y, Entity entity, Block block) {
        this.distance = distance;
        this.x = x;
        this.y = y;
        this.entity = entity;
        this.block = block;
        return this;
    }

    @Override
    public String toString() {
        return isHit()
                ? "%s %s at %.1f (%.1f, %.1f)".formatted(block, entity, distance, x, y)
                : "clear to %.1f".formatted(distance);
    }
}
