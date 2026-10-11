package com.zendogames.zenlib.ecs.movement.topdown;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.components.Mover;
import com.zendogames.zenlib.ecs.movement.Motion;
import com.zendogames.zenlib.ecs.movement.MoveIntent;
import com.zendogames.zenlib.ecs.movement.MoveWorld;
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

    /** -1/+1 when a SOLID is one pixel left/right (below/above), else 0; refreshed after every sweep. */
    public int wallX, wallY;

    public TopDownMotion(TopDownTuning tuning) {
        this.tuning = tuning;
    }

    @Override
    public void control(Mover mover, MoveIntent intent, float dt) {
        int ix = intent.moveX();
        int iy = intent.moveY();

        // Steering into an adjacent wall holds that axis at rest, otherwise velocity ramps between
        // the ticks that try a pixel and get blocked, and an isMoving() animation check flickers
        if (ix != 0 && ix == wallX) { ix = 0; mover.xVel = 0; mover.xRem = 0; }
        if (iy != 0 && iy == wallY) { iy = 0; mover.yVel = 0; mover.yRem = 0; }

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

    @Override
    public void after(Mover mover, MoveIntent intent, PointI2 pos, CollisionShape shape, MoveWorld world) {
        if (shape == null) { wallX = 0; wallY = 0; return; }
        wallX = (world.probe(shape, pos.x, pos.y, -1,  0) == Block.SOLID) ? -1
              : (world.probe(shape, pos.x, pos.y, +1,  0) == Block.SOLID) ? +1 : 0;
        wallY = (world.probe(shape, pos.x, pos.y,  0, -1) == Block.SOLID) ? -1
              : (world.probe(shape, pos.x, pos.y,  0, +1) == Block.SOLID) ? +1 : 0;
    }

    public boolean isFacingHorizontal() { return faceX != 0; }
    public boolean isFacingVertical()   { return faceY != 0; }
}
