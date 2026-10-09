package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Animator;
import com.zendogames.zenlib.utils.Calc;

/**
 * Advances every {@link Animator} by the tick, so animations freeze with the simulation under
 * {@code ZenFlag.FRAME_STEP}, and eases its scale back toward the default with facing applied.
 */
public class AnimationSystem extends IteratingSystem {

    public AnimationSystem() {
        super(Family.all(Animator.TYPE), SystemOrder.PRESENT);
    }

    @Override
    protected void process(Entity e, float dt) {
        var anim = Animator.get(e);
        if (!anim.hasAnimation()) return;

        anim.time += dt;
        anim.keyframe = anim.animation.getKeyFrame(anim.time);

        // Return any squash or stretch toward the default, then apply facing to x
        var sx = Calc.approach(Calc.abs(anim.scale.x), anim.defaultScale.x, dt * anim.scaleReturnSpeed);
        var sy = Calc.approach(Calc.abs(anim.scale.y), anim.defaultScale.y, dt * anim.scaleReturnSpeed);
        anim.scale.set(anim.facing * sx, sy);
    }
}
