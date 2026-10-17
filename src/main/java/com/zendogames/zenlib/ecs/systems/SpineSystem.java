package com.zendogames.zenlib.ecs.systems;

import com.esotericsoftware.spine.Physics;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.components.SpineAnimator;
import com.zendogames.zenlib.utils.Calc;

/**
 * Advances every {@link SpineAnimator} by the tick: the animation state (which fires events), the
 * skeleton's clock (physics), then the pose at the entity's position with physics stepped. Like
 * {@link AnimationSystem} it eases the scale back toward the default with facing applied, and runs
 * at {@code PRESENT} so the pose follows this tick's movement; bones game code reads (a muzzle,
 * a hand) are current from here until the next tick.
 */
public class SpineSystem extends IteratingSystem {

    public SpineSystem() {
        super(Family.all(Position.TYPE, SpineAnimator.TYPE), SystemOrder.PRESENT);
    }

    @Override
    protected void process(Entity e, float dt) {
        var position = Position.get(e);
        var animator = SpineAnimator.get(e);

        animator.state.update(dt);
        animator.state.apply(animator.skeleton);
        animator.skeleton.update(dt);

        // Return any squash or stretch toward the default, then apply facing to x
        var sx = Calc.approach(Calc.abs(animator.scale.x), animator.defaultScale.x, dt * animator.scaleReturnSpeed);
        var sy = Calc.approach(Calc.abs(animator.scale.y), animator.defaultScale.y, dt * animator.scaleReturnSpeed);
        animator.scale.set(animator.facing * sx, sy);

        animator.pose(position.x, position.y, Physics.update);
    }
}
