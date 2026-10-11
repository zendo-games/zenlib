package com.zendogames.zenlib.ecs.movement;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.shapes.CollisionRect;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.components.Mover;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerMode;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerMotion;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerTuning;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The platformer physics against a {@link ScriptedWorld}: no ECS, one {@link MoveStep#step} per
 * tick. The hero is an 8x16 rect; the unit is 16 px, so {@code PlatformerTuning.player(16)} jumps
 * 80 px and runs at 192 px/s.
 */
public class MoveStepTest {

    static final float DT   = SimulationStep.FIXED_DT;
    static final int   UNIT = 16;
    static final int   FLOOR_TOP = 16;

    static final MoveIntent NONE  = MoveIntent.NONE;
    static final MoveIntent RIGHT = MoveIntent.move(1, 0);
    static final MoveIntent LEFT  = MoveIntent.move(-1, 0);
    static final MoveIntent DOWN  = MoveIntent.move(0, -1);
    static final MoveIntent JUMP_PRESS = new MoveIntent(0, 0, true,  true,  false);
    static final MoveIntent JUMP_HOLD  = new MoveIntent(0, 0, false, true,  false);
    static final MoveIntent DASH_RIGHT = new MoveIntent(1, 0, false, false, true);

    /** One mover with its position and shape, stepped against a world. */
    static final class Hero {
        final Mover mover;
        final PlatformerMotion p;
        final PointI2 pos;
        final CollisionShape shape = new CollisionRect(0, 0, 8, 16);
        final ScriptedWorld world;

        Hero(ScriptedWorld world, int x, int y) {
            this(world, x, y, new PlatformerMotion(PlatformerTuning.player(UNIT)));
        }

        Hero(ScriptedWorld world, int x, int y, PlatformerMotion motion) {
            this.world = world;
            this.p = motion;
            this.mover = new Mover(motion);
            this.pos = new PointI2(x, y);
        }

        void tick(MoveIntent intent) {
            MoveStep.step(mover, pos, shape, intent, world, DT);
        }

        void ticks(int n, MoveIntent intent) {
            for (int i = 0; i < n; i++) tick(intent);
        }

        /** Ticks until {@code landed} fires or {@code max} ticks pass; returns the tick count. */
        int tickUntilLanded(MoveIntent intent, int max) {
            for (int i = 1; i <= max; i++) {
                tick(intent);
                if (p.landed) return i;
            }
            return -1;
        }

        /** Holds the jump through the whole arc and returns the highest y reached, relative to the start. */
        int jumpAndMeasureRise() {
            int startY = pos.y;
            int maxY = startY;
            tick(JUMP_PRESS);
            for (int i = 0; i < 200 && !(p.landed); i++) {
                tick(JUMP_HOLD);
                maxY = Math.max(maxY, pos.y);
            }
            return maxY - startY;
        }
    }

    /** A wide floor whose top is {@link #FLOOR_TOP}. */
    static ScriptedWorld floorWorld() {
        return new ScriptedWorld().solid(-1000, 0, 2000, FLOOR_TOP);
    }

    static Hero standingHero(ScriptedWorld world) {
        var hero = new Hero(world, 100, FLOOR_TOP);
        hero.tick(NONE); // settle: grounded, the first-tick landing consumed
        assertTrue(hero.p.grounded);
        return hero;
    }

    // ------------------------------------------------------------------------
    // Core sweep
    // ------------------------------------------------------------------------

    @Test
    public void motionNoneIntegratesVelocityAndStopsOnBlock() {
        var world = new ScriptedWorld().solid(130, 0, 10, 100);
        var mover = new Mover(Motion.NONE, 60f, 0f);
        var pos   = new PointI2(100, 10);
        var shape = new CollisionRect(0, 0, 8, 8);

        for (int i = 0; i < 20; i++) MoveStep.step(mover, pos, shape, NONE, world, DT);
        assertEquals("one pixel per tick at 60 px/s", 120, pos.x, 1);
        assertEquals(0, mover.xHit);

        for (int i = 0; i < 20; i++) MoveStep.step(mover, pos, shape, NONE, world, DT);
        assertEquals("flush against the wall", 122, pos.x);
        assertEquals(0f, mover.xVel, 0f);
        assertEquals(0f, mover.xRem, 0f);
        assertEquals(1, world.lastDx);
        assertTrue(world.blocks >= 1);
    }

    @Test
    public void hitsAreEventsForOneTick() {
        var world = new ScriptedWorld().solid(110, 0, 10, 100);
        var mover = new Mover(Motion.NONE, 600f, 0f);
        var pos   = new PointI2(100, 10);
        var shape = new CollisionRect(0, 0, 8, 8);

        MoveStep.step(mover, pos, shape, NONE, world, DT);
        assertEquals(+1, mover.xHit);
        MoveStep.step(mover, pos, shape, NONE, world, DT);
        assertEquals("velocity is zero, no probe, no hit", 0, mover.xHit);
    }

    @Test
    public void openWorldNeverBlocks() {
        var mover = new Mover(Motion.NONE, 600f, -600f);
        var pos   = new PointI2(0, 0);
        for (int i = 0; i < 60; i++) MoveStep.step(mover, pos, null, NONE, MoveWorld.OPEN, DT);
        assertEquals(600, pos.x, 1);
        assertEquals(-600, pos.y, 1);
    }

    @Test
    public void terminalSpeedCapsAcceleration() {
        var mover = new Mover(Motion.NONE);
        mover.yAccel    = -1000f;
        mover.terminalY = 100f;
        var pos = new PointI2(0, 0);
        for (int i = 0; i < 60; i++) MoveStep.step(mover, pos, null, NONE, MoveWorld.OPEN, DT);
        assertEquals(-100f, mover.yVel, 0.001f);

        // A limit only applies in the direction of the acceleration
        mover.yVel = 500f;
        MoveStep.step(mover, pos, null, NONE, MoveWorld.OPEN, DT);
        assertTrue(mover.yVel > 100f);
    }

    @Test
    public void moverRejectsNullMotionAndCastsByClass() {
        assertThrows(com.badlogic.gdx.utils.GdxRuntimeException.class, () -> new Mover(null));
        var mover = new Mover(new PlatformerMotion(PlatformerTuning.ballistic(UNIT)));
        assertNotNull(mover.motion(PlatformerMotion.class));
        assertTrue(mover.is(PlatformerMotion.class));
        assertFalse(mover.is(com.zendogames.zenlib.ecs.movement.topdown.TopDownMotion.class));
        assertThrows(com.badlogic.gdx.utils.GdxRuntimeException.class, () -> mover.motion(com.zendogames.zenlib.ecs.movement.topdown.TopDownMotion.class));
    }

    // ------------------------------------------------------------------------
    // Ground and gravity
    // ------------------------------------------------------------------------

    @Test
    public void restingOnTheFloorIsGroundedAndLandsOnce() {
        var world = floorWorld();
        var hero  = new Hero(world, 100, FLOOR_TOP);

        hero.tick(NONE);
        assertTrue(hero.p.grounded);
        assertTrue("first settle on the ground is a landing", hero.p.landed);
        assertEquals(-1, hero.mover.yHit);
        assertEquals("the landing is reported as a block below", -1, world.lastDy);
        assertEquals(1, world.blocks);
        assertEquals(FLOOR_TOP, hero.pos.y);

        hero.tick(NONE);
        assertTrue(hero.p.grounded);
        assertFalse(hero.p.landed);
        assertEquals(0, hero.mover.yHit);
        assertEquals(0f, hero.mover.yVel, 0f);
    }

    @Test
    public void fallsUnderGravityAndLandsFlush() {
        var world = floorWorld();
        var hero  = new Hero(world, 100, FLOOR_TOP + 50);

        hero.tick(NONE);
        assertFalse(hero.p.grounded);
        assertTrue(hero.mover.yVel < 0);

        int ticks = hero.tickUntilLanded(NONE, 120);
        assertTrue(ticks > 0);
        assertEquals(FLOOR_TOP, hero.pos.y);
        assertEquals(0f, hero.mover.yVel, 0f);
        assertEquals(-1, hero.mover.yHit);
    }

    @Test
    public void fallSpeedIsCappedAtTerminal() {
        var hero = new Hero(new ScriptedWorld(), 0, 5000);
        hero.ticks(300, NONE);
        assertEquals(-hero.p.tuning.maxFallVel(), hero.mover.yVel, 0.001f);
    }

    @Test
    public void ballisticIgnoresIntentAndKeepsHorizontalVelocity() {
        var world = floorWorld();
        var hero  = new Hero(world, 100, FLOOR_TOP + 40, new PlatformerMotion(PlatformerTuning.ballistic(UNIT), PlatformerMode.BALLISTIC));
        hero.mover.xVel = 48f;

        hero.ticks(10, JUMP_PRESS);
        assertEquals(48f, hero.mover.xVel, 0f);
        assertFalse(hero.p.jumped);
        assertSame(PlatformerMode.BALLISTIC, hero.p.mode);
        hero.tickUntilLanded(RIGHT, 120);
        assertEquals(FLOOR_TOP, hero.pos.y);
        assertEquals("no friction in ballistic", 48f, hero.mover.xVel, 0f);
    }

    // ------------------------------------------------------------------------
    // Jump
    // ------------------------------------------------------------------------

    @Test
    public void jumpReachesExactlyTheDesignHeight() {
        var hero = standingHero(floorWorld());
        int rise = hero.jumpAndMeasureRise();
        assertEquals("jumpHeight 5 units of 16 px: a ledge at 80 is reachable, a wall of 81 is not", 5 * UNIT, rise);
        assertTrue(hero.p.landed);
        assertEquals(FLOOR_TOP, hero.pos.y);
    }

    @Test
    public void jumpHeightFollowsTheTuning() {
        var hero = standingHero(floorWorld());
        hero.p.tuning = hero.p.tuning.withJumpHeight(3);
        assertEquals(3 * UNIT, hero.jumpAndMeasureRise());

        hero.p.tuning = new PlatformerTuning(8, 12, 0.12f, 0.1f, 11, 0.25f, 0.3f, 22, 5, 0.38f, 2.5f, 2f, 0.1f, 0.12f, PlatformerTuning.Abilities.NONE);
        assertEquals("unit is per tuning, nothing is fixed to 16", 5 * 8, hero.jumpAndMeasureRise());
    }

    @Test
    public void releasingEarlyCutsTheJump() {
        var hero = standingHero(floorWorld());
        int startY = hero.pos.y;
        hero.tick(JUMP_PRESS);
        assertTrue(hero.p.jumped);
        int maxY = startY;
        for (int i = 0; i < 200 && !hero.p.landed; i++) {
            hero.tick(NONE); // action released on the second tick
            maxY = Math.max(maxY, hero.pos.y);
        }
        assertTrue(hero.p.jumpCut);
        assertTrue("a cut jump stays well under the full height", maxY - startY < 3 * UNIT);
    }

    @Test
    public void noDoubleJumpInTheAir() {
        var hero = standingHero(floorWorld());
        hero.tick(JUMP_PRESS);
        hero.ticks(5, JUMP_HOLD);
        float yVel = hero.mover.yVel;
        hero.tick(JUMP_PRESS);
        assertFalse(hero.p.jumped);
        assertTrue(hero.mover.yVel < yVel);
    }

    @Test
    public void coyoteTimeAllowsAJumpJustAfterLeavingTheGround() {
        var hero = standingHero(floorWorld());
        hero.pos.x = 5000; // off the end of the floor
        hero.tick(NONE);   // settle: not grounded, wasGrounded
        hero.tick(NONE);   // control: coyote starts at 0.12 s = 7.2 ticks
        hero.ticks(6, NONE);
        hero.tick(JUMP_PRESS);
        assertTrue("within the coyote window", hero.p.jumped);
        assertTrue(hero.mover.yVel > 0);
    }

    @Test
    public void coyoteTimeExpires() {
        var hero = standingHero(floorWorld());
        hero.pos.x = 5000;
        hero.tick(NONE);
        hero.tick(NONE);
        hero.ticks(7, NONE);
        hero.tick(JUMP_PRESS);
        assertFalse("one tick past the window", hero.p.jumped);
    }

    @Test
    public void bufferedJumpFiresOnLanding() {
        var world = floorWorld();
        var hero  = new Hero(world, 100, FLOOR_TOP + 6);
        hero.tick(JUMP_PRESS); // in the air: no jump, but remembered for 0.1 s
        assertFalse(hero.p.jumped);
        assertTrue(hero.p.jumpBuffer > 0);

        int landedAt = hero.tickUntilLanded(NONE, 10);
        assertTrue(landedAt > 0);
        hero.tick(NONE);
        assertTrue("the buffered press jumps on the first grounded tick", hero.p.jumped);
    }

    @Test
    public void bufferExpiresBeforeALateLanding() {
        var world = floorWorld();
        var hero  = new Hero(world, 100, FLOOR_TOP + 100);
        hero.tick(JUMP_PRESS);
        hero.tickUntilLanded(NONE, 200);
        hero.tick(NONE);
        assertFalse(hero.p.jumped);
    }

    // ------------------------------------------------------------------------
    // Running
    // ------------------------------------------------------------------------

    @Test
    public void runsUpToMaxSpeedAndStopsWithFriction() {
        var hero = standingHero(floorWorld());
        hero.ticks(60, RIGHT);
        assertEquals(hero.p.tuning.maxSpeed(true), hero.mover.xVel, 0.001f);
        assertEquals(1, hero.p.facing);
        assertTrue(hero.pos.x > 100);

        hero.ticks(60, NONE);
        assertEquals(0f, hero.mover.xVel, 0f);
        assertEquals("facing is kept while idle", 1, hero.p.facing);

        hero.ticks(5, LEFT);
        assertEquals(-1, hero.p.facing);
        assertTrue(hero.mover.xVel < 0);
    }

    @Test
    public void runningIntoAWallStopsAndReportsTheHit() {
        var world = floorWorld().solid(140, FLOOR_TOP, 20, 100);
        var hero  = standingHero(world);
        hero.ticks(60, RIGHT);
        assertEquals(132, hero.pos.x);
        assertEquals(+1, hero.p.wallDir);
        assertTrue(hero.mover.xHit == +1 || hero.mover.xVel == 0f);
    }

    // ------------------------------------------------------------------------
    // Walls
    // ------------------------------------------------------------------------

    /**
     * Pushing into a wall is standing still: the velocity must not ramp up between the ticks
     * that actually try a pixel, or a {@code |xVel| > 0} "running" check flickers every few ticks.
     */
    @Test
    public void pushingIntoAWallHoldsVelocityAtZero() {
        var world = floorWorld().solid(140, FLOOR_TOP, 20, 100);
        var hero  = standingHero(world);
        hero.ticks(60, RIGHT);
        assertEquals("flush against the wall", 132, hero.pos.x);
        assertEquals(+1, hero.p.wallDir);

        for (int i = 0; i < 20; i++) {
            hero.tick(RIGHT);
            assertEquals("tick " + i + ": xVel while pushing", 0f, hero.mover.xVel, 0f);
            assertEquals("tick " + i + ": xRem while pushing", 0f, hero.mover.xRem, 0f);
            assertEquals(132, hero.pos.x);
            assertTrue(hero.p.grounded);
        }

        // Steering away leaves immediately
        hero.tick(LEFT);
        assertTrue(hero.mover.xVel < 0f);
    }

    /** A tall wall whose left face is at x = 108, the hero flush against it at x = 100. */
    static ScriptedWorld wallWorld() {
        return floorWorld().solid(108, 0, 40, 400);
    }

    @Test
    public void wallSlideSettlesOnTheSlideSpeed() {
        var hero = new Hero(wallWorld(), 100, 300);
        hero.ticks(40, RIGHT); // pushing into the wall while falling
        assertEquals(+1, hero.p.wallDir);
        assertFalse(hero.p.grounded);
        var slideVel = -hero.p.tuning.abilities().wallSlideSpeed() * UNIT;
        assertEquals(slideVel, hero.mover.yVel, 0.01f);
    }

    @Test
    public void wallSlideNeedsThePush() {
        var hero = new Hero(wallWorld(), 100, 300);
        hero.ticks(40, NONE);
        assertEquals(+1, hero.p.wallDir);
        assertTrue("free fall is faster than the slide", hero.mover.yVel < -hero.p.tuning.abilities().wallSlideSpeed() * UNIT);
    }

    @Test
    public void wallJumpKicksAwayAndLocksSteering() {
        var hero = new Hero(wallWorld(), 100, 300);
        hero.ticks(10, RIGHT);
        hero.tick(new MoveIntent(1, 0, true, true, false));
        assertTrue(hero.p.jumped);
        assertEquals(-1, hero.p.facing);
        var kick = -hero.p.tuning.abilities().wallJumpSpeedX() * UNIT;
        assertEquals(kick, hero.mover.xVel, 0.001f);
        assertTrue(hero.p.steerLock > 0);

        hero.tick(new MoveIntent(1, 0, false, true, false)); // steering back into the wall is ignored
        assertEquals("the kick is carried exactly through the steer lock", kick, hero.mover.xVel, 0.001f);
        assertTrue(hero.pos.x < 100);
    }

    // ------------------------------------------------------------------------
    // One-way platforms
    // ------------------------------------------------------------------------

    static final int PLATFORM_Y = 64;

    static ScriptedWorld platformWorld() {
        return floorWorld().platform(50, PLATFORM_Y, 100);
    }

    @Test
    public void standsOnAPlatformAndDropsThroughWithDownAndJump() {
        var hero = new Hero(platformWorld(), 100, PLATFORM_Y);
        hero.tick(NONE);
        assertTrue(hero.p.grounded);
        assertTrue(hero.p.onOneWay);

        hero.tick(new MoveIntent(0, -1, true, true, false)); // down + jump: drop, not jump
        assertTrue(hero.p.dropping);
        assertFalse(hero.p.jumped);
        hero.ticks(10, DOWN);
        assertTrue("fell through", hero.pos.y < PLATFORM_Y);
        hero.tickUntilLanded(NONE, 120);
        assertEquals(FLOOR_TOP, hero.pos.y);
        assertFalse(hero.p.dropping);
    }

    @Test
    public void jumpsOffAPlatformWithoutDown() {
        var hero = new Hero(platformWorld(), 100, PLATFORM_Y);
        hero.tick(NONE);
        hero.tick(JUMP_PRESS);
        assertTrue(hero.p.jumped);
        assertFalse(hero.p.dropping);
        hero.ticks(5, JUMP_HOLD);
        assertTrue(hero.pos.y > PLATFORM_Y);
    }

    @Test
    public void risesThroughAPlatformFromBelowAndLandsOnIt() {
        var hero = standingHero(platformWorld()); // on the floor, under the platform (top at 16, platform at 64: 48 px up, jump is 80)
        hero.tick(JUMP_PRESS);
        hero.ticks(25, JUMP_HOLD); // around the apex, 96 px up
        assertEquals("never blocked from below", 0, hero.mover.yHit);
        assertTrue(hero.pos.y > PLATFORM_Y);
        hero.tickUntilLanded(JUMP_HOLD, 120);
        assertEquals(PLATFORM_Y, hero.pos.y);
        assertTrue(hero.p.onOneWay);
    }

    // ------------------------------------------------------------------------
    // Dash
    // ------------------------------------------------------------------------

    @Test
    public void dashRunsAtDashSpeedThenReturnsToPlayerAtRunSpeed() {
        var hero = standingHero(floorWorld());
        var abilities = hero.p.tuning.abilities();
        hero.tick(DASH_RIGHT);
        assertSame(PlatformerMode.DASH, hero.p.mode);
        assertEquals(abilities.dashSpeed() * UNIT, hero.mover.xVel, 0.001f);
        assertEquals(1, hero.p.facing);

        int dashTicks = Math.round(abilities.dashTime() / DT);
        int ticks = 1; // the dash tick itself
        while (hero.p.mode == PlatformerMode.DASH && ticks < 30) {
            hero.tick(NONE);
            ticks++;
        }
        assertSame(PlatformerMode.PLAYER, hero.p.mode);
        assertEquals("ends on the timer", dashTicks, ticks, 1);
        assertEquals("left at run speed, friction has not run yet", hero.p.tuning.maxSpeed(true), hero.mover.xVel, 0.001f);
        assertTrue(hero.p.dashCooldown > 0);

        hero.tick(DASH_RIGHT);
        assertSame("cooling down", PlatformerMode.PLAYER, hero.p.mode);
    }

    @Test
    public void dashEndsOnAWall() {
        var hero = standingHero(floorWorld().solid(140, FLOOR_TOP, 20, 100));
        hero.tick(DASH_RIGHT);
        hero.ticks(3, NONE);
        assertSame(PlatformerMode.PLAYER, hero.p.mode);
        assertEquals(0f, hero.mover.xVel, 0f);
        assertEquals(132, hero.pos.x);
    }

    @Test
    public void noDashWithoutTheAbility() {
        var hero = new Hero(floorWorld(), 100, FLOOR_TOP, new PlatformerMotion(PlatformerTuning.player(UNIT).withAbilities(PlatformerTuning.Abilities.NONE)));
        hero.tick(NONE);
        hero.tick(DASH_RIGHT);
        assertSame(PlatformerMode.PLAYER, hero.p.mode);
    }

    // ------------------------------------------------------------------------
    // Ledge grab
    // ------------------------------------------------------------------------

    static final int LEDGE_Y = 64;

    /** A block to the right of the hero whose top is {@link #LEDGE_Y}; the hero at x = 100 is flush with its left face. */
    static ScriptedWorld ledgeWorld() {
        return new ScriptedWorld().solid(108, 0, 40, LEDGE_Y);
    }

    @Test
    public void grabsALedgeWhileFallingAndPushingIntoIt() {
        var hero = new Hero(ledgeWorld(), 100, LEDGE_Y - 16 + 2); // top of the body 2 px above the ledge, within the 4 px reach
        hero.tick(RIGHT);
        assertSame(PlatformerMode.LEDGE, hero.p.mode);
        assertEquals("snapped so the top of the body is level with the ledge", LEDGE_Y - 16, hero.pos.y);
        assertEquals(0f, hero.mover.yVel, 0f);

        hero.ticks(10, RIGHT);
        assertEquals("hanging still", LEDGE_Y - 16, hero.pos.y);
    }

    @Test
    public void ledgeNeedsThePushUnderThePushRule() {
        var hero = new Hero(ledgeWorld(), 100, LEDGE_Y - 16 + 2);
        hero.tick(NONE);
        assertSame(PlatformerMode.PLAYER, hero.p.mode);
    }

    @Test
    public void ledgeReleaseFallsAndLocksRegrab() {
        var hero = new Hero(ledgeWorld(), 100, LEDGE_Y - 16 + 2);
        hero.tick(RIGHT);
        assertSame(PlatformerMode.LEDGE, hero.p.mode);

        hero.tick(DOWN);
        assertSame(PlatformerMode.PLAYER, hero.p.mode);
        assertTrue(hero.p.ledgeRegrabLock > 0);
        hero.ticks(3, RIGHT);
        assertSame("no regrab on the way past", PlatformerMode.PLAYER, hero.p.mode);
        assertTrue(hero.pos.y < LEDGE_Y - 16);
    }

    @Test
    public void ledgeJumpGoesUp() {
        var hero = new Hero(ledgeWorld(), 100, LEDGE_Y - 16 + 2);
        hero.tick(RIGHT);
        hero.tick(new MoveIntent(0, 0, true, true, false));
        assertSame(PlatformerMode.PLAYER, hero.p.mode);
        assertTrue(hero.p.jumped);
        hero.ticks(5, JUMP_HOLD);
        assertTrue(hero.pos.y > LEDGE_Y - 16);
    }

    // ------------------------------------------------------------------------
    // Launched
    // ------------------------------------------------------------------------

    @Test
    public void launchFliesTheDesignedArc() {
        var hero = standingHero(floorWorld());
        int startX = hero.pos.x;
        hero.p.launch(hero.mover, +1, 4, 3, 1f);
        assertSame(PlatformerMode.LAUNCHED, hero.p.mode);

        int startY = hero.pos.y;
        int maxY = startY;
        int ticks = 0;
        do {
            hero.tick(LEFT); // steering is ignored while launched
            maxY = Math.max(maxY, hero.pos.y);
        } while (!hero.p.landed && ++ticks < 200);
        assertTrue(hero.p.landed);
        assertSame(PlatformerMode.PLAYER, hero.p.mode);
        assertEquals("height 3 units", 3 * UNIT, maxY - startY, 1);
        assertEquals("distance 4 units", 4 * UNIT, hero.pos.x - startX, 2);
    }
}
