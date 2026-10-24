package com.zendogames.zenlib.input;

import com.badlogic.gdx.Input.Keys;
import com.zendogames.zenlib.input.AxisBinding.Overlap;
import org.junit.Test;

import static org.junit.Assert.*;

public class AxisBindingTest {

    final Frames frames = new Frames();
    final AxisBinding axis = new AxisBinding();

    public AxisBindingTest() {
        frames.on(() -> axis.update(frames.input));
    }

    @Test
    public void oneSideGivesItsSign() {
        axis.keys(Keys.A, Keys.D);
        frames.frame(() -> frames.input.keyDown(Keys.A));
        assertEquals(-1, axis.sign());
        assertEquals(-1f, axis.value(), 0f);

        frames.frame(() -> { frames.input.keyUp(Keys.A); frames.input.keyDown(Keys.D); });
        assertEquals(+1, axis.sign());
        assertEquals(+1f, axis.value(), 0f);

        frames.frame(() -> frames.input.keyUp(Keys.D));
        assertEquals(0, axis.sign());
        assertEquals(0f, axis.value(), 0f);
    }

    @Test
    public void bothHeldNewerWins() {
        axis.keys(Keys.A, Keys.D);
        assertEquals(Overlap.NEWER, axis.overlap);
        frames.frame(() -> frames.input.keyDown(Keys.A));
        frames.frame(() -> frames.input.keyDown(Keys.D));
        assertEquals("the later press wins", +1, axis.sign());

        frames.frame(() -> frames.input.keyUp(Keys.D));
        assertEquals("the older one is still down", -1, axis.sign());
    }

    @Test
    public void olderWins() {
        axis.keys(Keys.A, Keys.D);
        axis.overlap = Overlap.OLDER;
        frames.frame(() -> frames.input.keyDown(Keys.A));
        frames.frame(() -> frames.input.keyDown(Keys.D));
        assertEquals(-1, axis.sign());
    }

    @Test
    public void cancelGivesZero() {
        axis.keys(Keys.A, Keys.D);
        axis.overlap = Overlap.CANCEL;
        frames.frame(() -> frames.input.keyDown(Keys.A));
        frames.frame(() -> frames.input.keyDown(Keys.D));
        assertEquals(0, axis.sign());
        assertEquals(0f, axis.value(), 0f);
        assertTrue(axis.negative.down() && axis.positive.down());
    }

    @Test
    public void aTieGoesToPositive() {
        axis.keys(Keys.A, Keys.D);
        frames.frame(() -> { frames.input.keyDown(Keys.A); frames.input.keyDown(Keys.D); });
        assertEquals(+1, axis.sign());
    }

    @Test
    public void axisHelperSplitsTheSignsWithOneThreshold() {
        axis.axis(0, PadAxis.LEFT_X, 0.5f);

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, -0.7f));
        assertEquals(-1, axis.sign());
        assertEquals(-0.4f, axis.value(), 1e-4f);

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, 0.7f));
        assertEquals(+1, axis.sign());
        assertEquals(+0.4f, axis.value(), 1e-4f);

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, 0.3f));
        assertEquals("inside the threshold", 0, axis.sign());
    }

    @Test
    public void padHelperBindsTwoButtons() {
        axis.pad(0, Pad.DPAD_LEFT, Pad.DPAD_RIGHT);
        frames.frame(() -> frames.pad.press(Pad.DPAD_LEFT));
        assertEquals(-1, axis.sign());
        frames.frame(() -> { frames.pad.release(Pad.DPAD_LEFT); frames.pad.press(Pad.DPAD_RIGHT); });
        assertEquals(+1, axis.sign());
    }

    @Test
    public void pressedIsEitherSidesEdge() {
        axis.keys(Keys.A, Keys.D);
        frames.frame(() -> frames.input.keyDown(Keys.A));
        assertTrue(axis.pressed());
        frames.frame(() -> frames.input.keyDown(Keys.D));
        assertTrue(axis.pressed());
        frames.idle(1);
        assertFalse(axis.pressed());

        axis.consumePress(); // harmless when nothing is buffered
        frames.frame(() -> { frames.input.keyUp(Keys.A); frames.input.keyUp(Keys.D); });
        assertFalse(axis.pressed());
    }

    @Test
    public void gamepadRetargetsBothSides() {
        axis.pad(0, Pad.DPAD_LEFT, Pad.DPAD_RIGHT).gamepad(1);
        frames.frame(() -> frames.pad.press(Pad.DPAD_LEFT));
        assertEquals(0, axis.sign());
        frames.frame(() -> frames.pad2.press(Pad.DPAD_RIGHT));
        assertEquals(+1, axis.sign());
    }

    @Test
    public void zeroOutAndClear() {
        axis.keys(Keys.A, Keys.D);
        frames.frame(() -> frames.input.keyDown(Keys.A));
        axis.zeroOut();
        assertEquals(0, axis.sign());

        axis.clear();
        frames.frame(() -> frames.input.keyDown(Keys.D));
        assertEquals("no sources left", 0, axis.sign());
    }
}
