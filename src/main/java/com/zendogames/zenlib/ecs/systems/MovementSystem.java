package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.*;
import com.zendogames.zenlib.ecs.movement.MoveIntent;
import com.zendogames.zenlib.ecs.movement.MoveStep;
import com.zendogames.zenlib.ecs.movement.MoveWorld;
import com.zendogames.zenlib.ecs.movement.MoverWorld;

/**
 * Steps every {@link Position} + {@link Mover} once per tick: saves the render-interpolation
 * position, builds the intent from an {@link Input} if there is one, runs {@link MoveStep}
 * against the {@link CollisionWorld} when the entity has a {@link Collider} (else an open world),
 * and lets the step write blocks into the entity's {@link Contacts} for {@code ContactSystem}.
 * Without a collision world every mover moves freely.
 */
public class MovementSystem extends IteratingSystem {

    public static final int PRIORITY = SystemOrder.PHYSICS;

    /** Null when constructed without a collision world: every mover is stepped against {@link MoveWorld#OPEN}. */
    private final MoverWorld moverWorld;

    public MovementSystem() {
        this(null);
    }

    public MovementSystem(CollisionWorld collision) {
        super(Family.all(Position.TYPE, Mover.TYPE), PRIORITY);
        this.moverWorld = (collision != null) ? new MoverWorld(collision) : null;
    }

    @Override
    protected void process(Entity e, float dt) {
        var position = Position.get(e);
        var collider = Collider.get(e);
        var input    = Input.get(e);
        var mover    = Mover.get(e);

        position.savePrev(); // once per tick, before moving: the render pass interpolates prev -> current

        var shape  = (CollisionShape) null;
        var world  = MoveWorld.OPEN;
        if (collider != null && moverWorld != null) {
            shape = collider.shape();
            world = moverWorld.set(e, collider, Contacts.get(e));
        }

        var intent = (input != null) ? MoveIntent.of(input) : MoveIntent.NONE;
        MoveStep.step(mover, position, shape, intent, world, dt);

        if (mover.xHit != 0 && mover.onHitX != null) mover.onHitX.hit(e, mover, mover.xHit);
        if (mover.yHit != 0 && mover.onHitY != null) mover.onHitY.hit(e, mover, mover.yHit);
    }
}
