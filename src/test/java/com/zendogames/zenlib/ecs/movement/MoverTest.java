package com.zendogames.zenlib.ecs.movement;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.ecs.components.Mover;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerMotion;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerTuning;
import com.zendogames.zenlib.ecs.movement.topdown.TopDownMotion;
import com.zendogames.zenlib.ecs.movement.topdown.TopDownTuning;
import org.junit.Test;

import static org.junit.Assert.*;

/** The {@link Mover} component on its own: construction, the typed motion accessor, the small helpers. */
public class MoverTest {

    @Test
    public void isMovingOnEitherAxis() {
        var mover = new Mover(Motion.NONE);
        assertFalse("at rest", mover.isMoving());

        mover.xVel = 40f;
        assertTrue("moving horizontally only", mover.isMoving());

        mover.xVel = 0f;
        mover.yVel = -40f;
        assertTrue("moving vertically only", mover.isMoving());

        mover.xVel = 40f;
        assertTrue("moving on both axes", mover.isMoving());
    }

    @Test
    public void stopZeroesVelocityAndRemainders() {
        var mover = new Mover(Motion.NONE, 40f, -20f);
        mover.xRem = 0.5f;
        mover.yRem = -0.25f;
        mover.stop();
        assertEquals(0f, mover.xVel, 0f);
        assertEquals(0f, mover.yVel, 0f);
        assertEquals(0f, mover.xRem, 0f);
        assertEquals(0f, mover.yRem, 0f);
        assertFalse(mover.isMoving());
    }

    @Test
    public void motionAccessorCastsOrThrows() {
        var platformer = new PlatformerMotion(PlatformerTuning.player(16));
        var mover = new Mover(platformer);
        assertSame(platformer, mover.motion(PlatformerMotion.class));
        assertSame(platformer, mover.motion(Motion.class));
        assertTrue(mover.is(PlatformerMotion.class));
        assertFalse(mover.is(TopDownMotion.class));

        try {
            mover.motion(TopDownMotion.class);
            fail("expected a GdxRuntimeException naming both motion types");
        } catch (GdxRuntimeException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("PlatformerMotion"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("TopDownMotion"));
        }
    }

    @Test(expected = GdxRuntimeException.class)
    public void nullMotionIsRejected() {
        new Mover(null);
    }

    @Test
    public void topDownMoverStartsAtRest() {
        var mover = new Mover(new TopDownMotion(TopDownTuning.walker(16)));
        assertFalse(mover.isMoving());
        assertEquals(0f, mover.xVel, 0f);
        assertEquals(0f, mover.yVel, 0f);
    }
}
