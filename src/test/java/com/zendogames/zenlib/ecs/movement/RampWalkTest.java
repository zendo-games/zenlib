package com.zendogames.zenlib.ecs.movement;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.shapes.CollisionCaps;
import com.zendogames.zenlib.collision.shapes.CollisionGrid;
import com.zendogames.zenlib.collision.shapes.CollisionRect;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.components.Mover;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerMotion;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerTuning;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static com.zendogames.zenlib.collision.shapes.CollisionGrid.CellShape.FULL;
import static com.zendogames.zenlib.collision.shapes.CollisionGrid.CellShape.RAMP_NE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A capsule hero against ramp cells of a {@link CollisionGrid}: the x sweep steps up a slope, the
 * platformer snaps down a slope, a slope is not a wall, a wall is still a wall, a rect stays ramp-blind.
 */
public class RampWalkTest {

    static final int   UNIT = 16;
    static final float DT   = SimulationStep.FIXED_DT;
    static final MoveIntent RIGHT = MoveIntent.move(+1, 0);
    static final MoveIntent LEFT  = MoveIntent.move(-1, 0);

    /** Floor at row 0, a RAMP_NE at cell 4 row 1 rising right onto a FULL platform (top y = 32). */
    static CollisionGrid rampWorld() {
        var grid = new CollisionGrid(UNIT, 12, 6);
        for (int x = 0; x < 12; x++) grid.set(x, 0, FULL);
        grid.set(4, 1, RAMP_NE);
        for (int x = 5; x < 12; x++) grid.set(x, 1, FULL);
        return grid;
    }

    /** Floor at row 0, one FULL cell at cell 4 row 1: a 16 px wall. */
    static CollisionGrid wallWorld() {
        var grid = new CollisionGrid(UNIT, 12, 6);
        for (int x = 0; x < 12; x++) grid.set(x, 0, FULL);
        grid.set(4, 1, FULL);
        return grid;
    }

    static MoveWorld over(CollisionGrid grid) {
        var origin = new PointI2(0, 0);
        return (shape, x, y, dx, dy) -> shape.overlaps(grid, new PointI2(x, y), origin, new PointI2(dx, dy)) ? Block.SOLID : Block.NONE;
    }

    static final class Hero {
        final PlatformerMotion p = new PlatformerMotion(PlatformerTuning.player(UNIT));
        final Mover mover = new Mover(p);
        final PointI2 pos;
        final CollisionShape shape;
        final MoveWorld world;
        int airborneTicks, xHitTicks, wallTicks, landings;

        Hero(MoveWorld world, CollisionShape shape, int x, int y) {
            this.world = world;
            this.shape = shape;
            this.pos   = new PointI2(x, y);
        }

        void walk(MoveIntent intent, int ticks) {
            for (int t = 1; t <= ticks; t++) {
                MoveStep.step(mover, pos, shape, intent, world, DT);
                if (t == 1) continue; // the first tick settles onto the floor
                if (!p.grounded)      airborneTicks++;
                if (mover.xHit != 0)  xHitTicks++;
                if (p.wallDir != 0)   wallTicks++;
                if (p.landed)         landings++;
            }
        }
    }

    static CollisionShape capsule() { return new CollisionCaps(CollisionCaps.Axis.VERTICAL, -4, 0, 8, 16); }
    static CollisionShape rect()    { return new CollisionRect(-4, 0, 8, 16); }

    @Test
    public void capsuleWalksUpARampAndStaysGrounded() {
        var hero = new Hero(over(rampWorld()), capsule(), 24, 16);
        hero.walk(RIGHT, 40);
        assertEquals("on the platform", 32, hero.pos.y);
        assertTrue("past the ramp", hero.pos.x > 80);
        assertEquals("never airborne", 0, hero.airborneTicks);
        assertEquals("never blocked", 0, hero.xHitTicks);
        assertEquals("a slope is not a wall", 0, hero.wallTicks);
    }

    @Test
    public void capsuleWalksDownARampWithoutTakingOff() {
        var hero = new Hero(over(rampWorld()), capsule(), 152, 32);
        hero.walk(LEFT, 50);
        assertEquals("on the floor", 16, hero.pos.y);
        assertTrue("past the ramp", hero.pos.x < 64);
        assertEquals("never airborne", 0, hero.airborneTicks);
        assertEquals("no landing: it never left the ground", 0, hero.landings);
    }

    @Test
    public void aWallIsStillAWall() {
        var hero = new Hero(over(wallWorld()), capsule(), 24, 16);
        hero.walk(RIGHT, 30);
        assertEquals("stopped at the wall", 60, hero.pos.x);
        assertEquals(16, hero.pos.y);
        assertEquals(1, hero.mover.xHit);
        assertEquals(1, hero.p.wallDir);
    }

    @Test
    public void rectMoversStayRampBlind() {
        var hero = new Hero(over(rampWorld()), rect(), 24, 16);
        hero.walk(RIGHT, 30);
        assertEquals("blocked at the ramp cell like a wall", 60, hero.pos.x);
        assertEquals(1, hero.mover.xHit);
    }
}
