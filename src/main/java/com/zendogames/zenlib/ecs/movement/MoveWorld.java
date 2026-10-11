package com.zendogames.zenlib.ecs.movement;

import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.shapes.CollisionShape;

/**
 * What {@code MoveStep} and a {@link Motion} step against: one probe and one block callback,
 * so the physics is testable with a scripted world and no ECS. {@link MoverWorld} is the
 * adapter over {@code CollisionWorld} for one entity.
 */
public interface MoveWorld {

    /** Whether moving {@code shape} from {@code (x, y)} by {@code (dx, dy)} pixels would be blocked. */
    Block probe(CollisionShape shape, int x, int y, int dx, int dy);

    /**
     * Called when the most recent probe's result stopped a pixel walk (or, from a motion, stands
     * fore a block that a flush landing never probed into). Exactly one of {@code dx, dy} is non-zero.
     */
    default void onBlocked(int dx, int dy) {}

    /** No collision at all: every probe is {@link Block#NONE}. For movers without a {@code Collider}. */
    MoveWorld OPEN = (shape, x, y, dx, dy) -> Block.NONE;
}
