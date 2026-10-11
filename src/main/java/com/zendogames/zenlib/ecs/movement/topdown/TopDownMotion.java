package com.zendogames.zenlib.ecs.movement.topdown;

import com.zendogames.zenlib.ecs.components.Mover;
import com.zendogames.zenlib.ecs.movement.Motion;
import com.zendogames.zenlib.ecs.movement.MoveIntent;
import com.zendogames.zenlib.utils.Calc;

/**
 * Overhead 8-way movement with no gravity: accelerate toward the steered direction, slow to rest
 * without input, the same top speed on every heading (diagonals are normalized). Facing is one
 * of the four cardinal directions, taken from the dominant axis of the last non-zero steer
 * (horizontal wins a tie), so a four-sprite character never faces a diagonal. Blocks are left
 * where the sweep reports them, {@code xHit}/{@code yHit}; there is nothing to settle.
 */
public final class TopDownMotion implements Motion {

    private static final float DIAGONAL = (float) (1.0 / Math.sqrt(2.0));

    public TopDownTuning tuning;

    /** Cardinal facing: exactly one of the two is non-zero, -1/+1. Starts facing down. */
    public int faceX = 0, faceY = -1;

    public TopDownMotion(TopDownTuning tuning) {
        this.tuning = tuning;
    }

    @Override
    public void control(Mover mover, MoveIntent intent, float dt) {
        int ix = intent.moveX();
        int iy = intent.moveY();

        // Target velocity: the steered heading at top speed, diagonals scaled to the same speed
        var scale = (ix != 0 && iy != 0) ? DIAGONAL : 1f;
        var targetX = ix * tuning.maxSpeed() * scale;
        var targetY = iy * tuning.maxSpeed() * scale;

        // Accelerate toward the target on a steered axis, decelerate to rest on an unsteered one
        mover.xVel = Calc.approach(mover.xVel, targetX, ((ix != 0) ? tuning.accel() : tuning.friction()) * dt);
        mover.yVel = Calc.approach(mover.yVel, targetY, ((iy != 0) ? tuning.accel() : tuning.friction()) * dt);
        mover.xAccel = 0;
        mover.yAccel = 0;

        // Facing follows the dominant steered axis; unchanged while idle
        if (ix != 0 || iy != 0) {
            if (Math.abs(ix) >= Math.abs(iy)) { faceX = ix; faceY = 0;  }
            else                              { faceX = 0;  faceY = iy; }
        }
    }

    public boolean isFacingHorizontal() { return faceX != 0; }
    public boolean isFacingVertical()   { return faceY != 0; }
}
