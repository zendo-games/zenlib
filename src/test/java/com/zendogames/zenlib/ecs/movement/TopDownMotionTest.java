package com.zendogames.zenlib.ecs.movement;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.shapes.CollisionRect;
import com.zendogames.zenlib.ecs.components.Mover;
import com.zendogames.zenlib.ecs.movement.topdown.TopDownMotion;
import com.zendogames.zenlib.ecs.movement.topdown.TopDownTuning;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static org.junit.Assert.*;

public class TopDownMotionTest {

    static final float DT   = SimulationStep.FIXED_DT;
    static final int   UNIT = 16;

    static final class Walker {
        final TopDownMotion t = new TopDownMotion(TopDownTuning.walker(UNIT));
        final Mover mover = new Mover(t);
        final PointI2 pos = new PointI2(100, 100);
        final CollisionRect shape = new CollisionRect(0, 0, 8, 8);
        final MoveWorld world;

        Walker(MoveWorld world) { this.world = world; }

        void ticks(int n, int moveX, int moveY) {
            for (int i = 0; i < n; i++) MoveStep.step(mover, pos, shape, MoveIntent.move(moveX, moveY), world, DT);
        }

        float speed() { return (float) Math.hypot(mover.xVel, mover.yVel); }
    }

    @Test
    public void acceleratesToMaxSpeedOnEveryHeading() {
        var w = new Walker(MoveWorld.OPEN);
        var max = w.t.tuning.maxSpeed();

        w.ticks(30, 1, 0);
        assertEquals(max, w.mover.xVel, 0.001f);
        assertEquals(0f, w.mover.yVel, 0f);

        w.ticks(30, 1, 1);
        assertEquals("diagonals are normalized to the same speed", max, w.speed(), 0.01f);
        assertEquals(w.mover.xVel, w.mover.yVel, 0.001f);

        w.ticks(30, 0, -1);
        assertEquals(-max, w.mover.yVel, 0.001f);
        assertEquals(0f, w.mover.xVel, 0.001f);
    }

    @Test
    public void stopsWithFriction() {
        var w = new Walker(MoveWorld.OPEN);
        w.ticks(30, 1, 1);
        int x = w.pos.x;
        w.ticks(30, 0, 0);
        assertEquals(0f, w.mover.xVel, 0f);
        assertEquals(0f, w.mover.yVel, 0f);
        assertTrue("coasted a little", w.pos.x > x);
        x = w.pos.x;
        w.ticks(30, 0, 0);
        assertEquals("no drift at rest", x, w.pos.x);
    }

    @Test
    public void noGravityOrAcceleration() {
        var w = new Walker(MoveWorld.OPEN);
        w.ticks(60, 0, 0);
        assertEquals(100, w.pos.x);
        assertEquals(100, w.pos.y);
        assertEquals(0f, w.mover.yAccel, 0f);
    }

    @Test
    public void facingIsCardinalHorizontalWinsATieAndPersists() {
        var w = new Walker(MoveWorld.OPEN);
        assertEquals(0,  w.t.faceX);
        assertEquals(-1, w.t.faceY);

        w.ticks(1, 1, 1);
        assertEquals(1, w.t.faceX);
        assertEquals(0, w.t.faceY);
        assertTrue(w.t.isFacingHorizontal());

        w.ticks(1, 0, 1);
        assertEquals(0, w.t.faceX);
        assertEquals(1, w.t.faceY);
        assertTrue(w.t.isFacingVertical());

        w.ticks(1, -1, 0);
        w.ticks(10, 0, 0);
        assertEquals("kept while idle", -1, w.t.faceX);
        assertEquals(0, w.t.faceY);
    }

    @Test
    public void wallsBlockAndReportHits() {
        var world = new ScriptedWorld().solid(120, 0, 10, 200);
        var w = new Walker(world);
        // Start one pixel short at full speed so the first tick's walk is guaranteed to try the
        // blocked pixel: a walker that lands flush on a sub-pixel boundary is held by wallX before
        // it ever probes the wall and reports nothing (see pushingIntoAWallNeverMovesOrRampsVelocity)
        w.pos.x = 111;
        w.mover.xVel = w.t.tuning.maxSpeed(); // 1.6 px per tick
        w.mover.xRem = 0.5f;                   // so this tick walks two pixels: 112, then the blocked 113
        w.ticks(1, 1, 0);
        assertEquals("flush against the wall", 112, w.pos.x);
        assertEquals(+1, w.mover.xHit);
        assertEquals(+1, world.lastDx);
        assertEquals(1, world.blocks);
        assertEquals(+1, w.t.wallX);
        assertEquals(0,  w.t.wallY);

        w.ticks(1, 1, 0);
        assertEquals("steering into the adjacent wall holds the axis at rest", 0f, w.mover.xVel, 0f);
        assertEquals("the held axis never walks, so nothing is reported while pushing", 1, world.blocks);

        // Sliding along the wall still works on the other axis
        int y = w.pos.y;
        w.ticks(30, 1, 1);
        assertEquals(112, w.pos.x);
        assertTrue(w.pos.y > y);
    }

    /** The flicker fix: without the hold, control re-accelerates from zero every tick and an isMoving() check toggles. */
    @Test
    public void pushingIntoAWallNeverMovesOrRampsVelocity() {
        var world = new ScriptedWorld().solid(120, 0, 10, 200).solid(0, 50, 200, 10);
        var w = new Walker(world);
        w.ticks(60, 1, 0);
        assertEquals(112, w.pos.x);
        for (int i = 0; i < 10; i++) {
            w.ticks(1, 1, 0);
            assertEquals("tick " + i, 0f, w.mover.xVel, 0f);
            assertEquals("tick " + i, 0f, w.mover.xRem, 0f);
            assertEquals(112, w.pos.x);
        }

        // Each axis holds independently: down into the floor while still steering right
        w.ticks(60, 0, -1);
        assertEquals(60, w.pos.y);
        assertEquals(-1, w.t.wallY);
        w.ticks(5, 1, -1);
        assertEquals(0f, w.mover.xVel, 0f);
        assertEquals(0f, w.mover.yVel, 0f);

        // Steering away releases at once
        w.ticks(1, -1, 0);
        assertTrue(w.mover.xVel < 0);
    }

    @Test
    public void tuningIsSwappable() {
        var w = new Walker(MoveWorld.OPEN);
        w.t.tuning = new TopDownTuning(UNIT, 2, 0.05f, 0.05f);
        w.ticks(30, 1, 0);
        assertEquals(2 * UNIT, w.mover.xVel, 0.001f);
    }
}
