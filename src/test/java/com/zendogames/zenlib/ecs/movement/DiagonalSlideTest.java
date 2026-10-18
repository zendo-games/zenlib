package com.zendogames.zenlib.ecs.movement;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.shapes.CollisionCaps;
import com.zendogames.zenlib.collision.shapes.CollisionGrid;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.components.Mover;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static com.zendogames.zenlib.collision.shapes.CollisionGrid.CellShape.FULL;
import static com.zendogames.zenlib.collision.shapes.CollisionGrid.CellShape.RAMP_SE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A floor-plane mover against a diagonal of ramp cells, seen as a 45-degree wall: a motion with a
 * {@link Motion#slide} reach slips along it at full speed, is deflected along it when walking
 * straight into it, and stops when pushing into it on both axes; a motion without the reach stops
 * as before, and a full-cell wall is still a wall.
 */
public class DiagonalSlideTest {

    static final int   UNIT  = 16;
    static final float DT    = SimulationStep.FIXED_DT;
    static final float SPEED = 10 * UNIT;

    /** A top-down walker: velocity straight from the intent, no gravity, {@code reach} pixels of slide. */
    static Motion walker(int reach) {
        return new Motion() {
            @Override public void control(Mover mover, MoveIntent intent, float dt) {
                mover.xVel = intent.moveX() * SPEED;
                mover.yVel = intent.moveY() * SPEED;
            }
            @Override public int slide(CollisionShape shape) { return reach; }
        };
    }

    /** 16 x 8 cells: a FULL row along the top, a RAMP_SE staircase from (6,6) down-right to (9,3), FULL to its right. */
    static CollisionGrid diagonal() {
        var grid = new CollisionGrid(UNIT, 16, 8);
        for (int x = 0; x < 16; x++) grid.set(x, 7, FULL);
        for (int i = 0; i < 4; i++) {
            grid.set(6 + i, 6 - i, RAMP_SE);
            for (int x = 7 + i; x < 16; x++) grid.set(x, 6 - i, FULL);
        }
        return grid;
    }

    /** 16 x 8 cells: a FULL column at x = 8. */
    static CollisionGrid wall() {
        var grid = new CollisionGrid(UNIT, 16, 8);
        for (int y = 0; y < 8; y++) grid.set(8, y, FULL);
        return grid;
    }

    static MoveWorld over(CollisionGrid grid) {
        var origin = new PointI2(0, 0);
        return (shape, x, y, dx, dy) -> shape.overlaps(grid, new PointI2(x, y), origin, new PointI2(dx, dy)) ? Block.SOLID : Block.NONE;
    }

    static CollisionShape feet() { return new CollisionCaps(CollisionCaps.Axis.HORIZONTAL, -18, 0, 36, 12); }

    static final class Walk {
        final Mover mover;
        final PointI2 pos = new PointI2(5 * UNIT, 6 * UNIT + 2); // left of the diagonal, just under the top row
        int xHits, yHits;

        Walk(CollisionGrid grid, int reach, MoveIntent intent, int ticks) {
            mover = new Mover(walker(reach));
            var world = over(grid);
            var shape = feet();
            for (int t = 0; t < ticks; t++) {
                MoveStep.step(mover, pos, shape, intent, world, DT);
                if (mover.xHit != 0) xHits++;
                if (mover.yHit != 0) yHits++;
            }
        }
    }

    @Test
    public void steeringAlongTheDiagonalSlidesAtFullSpeed() {
        var walk = new Walk(diagonal(), 1, MoveIntent.move(+1, -1), 60);
        assertEquals("never blocked", 0, walk.xHits + walk.yHits);
        assertTrue("x advanced along the slope: " + walk.pos.x, walk.pos.x >= 5 * UNIT + 100);
        assertTrue("y dropped along the slope: " + walk.pos.y, walk.pos.y <= 6 * UNIT + 2 - 60);
        assertEquals("speed kept", SPEED, walk.mover.xVel, 0f);
    }

    @Test
    public void walkingIntoTheDiagonalIsDeflectedAlongIt() {
        var walk = new Walk(diagonal(), 1, MoveIntent.move(+1, 0), 60);
        assertEquals("never blocked", 0, walk.xHits);
        assertTrue("x advanced past the slope: " + walk.pos.x, walk.pos.x >= 5 * UNIT + 140);
        assertTrue("y was pushed down the slope: " + walk.pos.y, walk.pos.y < 6 * UNIT + 2);
    }

    @Test
    public void pushingIntoTheDiagonalOnBothAxesStops() {
        var walk = new Walk(diagonal(), 1, MoveIntent.move(+1, +1), 60);
        assertTrue("x stayed: " + walk.pos.x, walk.pos.x < 5 * UNIT + 20);
        assertTrue("blocked on x", walk.xHits > 0);
    }

    @Test
    public void withoutTheReachTheDiagonalStopsTheMover() {
        var walk = new Walk(diagonal(), 0, MoveIntent.move(+1, 0), 60);
        assertTrue("x stayed: " + walk.pos.x, walk.pos.x < 5 * UNIT + 20);
        assertTrue("blocked on x", walk.xHits > 0);
    }

    @Test
    public void aFullCellWallIsStillAWall() {
        var walk = new Walk(wall(), 1, MoveIntent.move(+1, 0), 60);
        assertTrue("stopped at the wall: " + walk.pos.x, walk.pos.x < 8 * UNIT);
        assertTrue("blocked on x", walk.xHits > 0);
        assertEquals("the sweep zeroed the velocity on the block", 0f, walk.mover.xVel, 0f);
    }
}
