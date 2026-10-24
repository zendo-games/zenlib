package com.zendogames.zenlib.input;

import com.badlogic.gdx.Input.Keys;
import org.junit.Test;

import static org.junit.Assert.*;

public class InputControlsTest {

    final Frames frames = new Frames();

    @Test
    public void wasdSteersAndActs() {
        var controls = InputControls.wasd(Keys.SPACE);
        frames.on(() -> controls.update(frames.input));

        frames.frame(() -> { frames.input.keyDown(Keys.D); frames.input.keyDown(Keys.W); frames.input.keyDown(Keys.SPACE); });
        assertEquals(+1, controls.move.signX());
        assertEquals(+1, controls.move.signY());
        assertTrue(controls.action.down());
        assertTrue(controls.action.pressed());

        frames.frame(() -> { frames.input.keyUp(Keys.D); frames.input.keyUp(Keys.W); frames.input.keyDown(Keys.A); frames.input.keyDown(Keys.S); });
        assertEquals(-1, controls.move.signX());
        assertEquals(-1, controls.move.signY());
        assertFalse(controls.action.pressed());
    }

    @Test
    public void arrowsSteer() {
        var controls = InputControls.arrows(Keys.ENTER);
        frames.on(() -> controls.update(frames.input));

        frames.frame(() -> { frames.input.keyDown(Keys.LEFT); frames.input.keyDown(Keys.UP); frames.input.keyDown(Keys.ENTER); });
        assertEquals(-1, controls.move.signX());
        assertEquals(+1, controls.move.signY());
        assertTrue(controls.action.down());

        frames.frame(() -> frames.input.keyDown(Keys.W));
        assertEquals("WASD is not bound here", +1, controls.move.signY()); // still UP from before
        frames.frame(() -> frames.input.keyUp(Keys.UP));
        assertEquals(0, controls.move.signY());
    }

    @Test
    public void gamepadAddsDpadStickAndA() {
        var controls = InputControls.wasd(Keys.SPACE).gamepad(0);
        frames.on(() -> controls.update(frames.input));

        frames.frame(() -> frames.pad.press(Pad.DPAD_LEFT));
        assertEquals(-1, controls.move.signX());

        frames.frame(() -> { frames.pad.release(Pad.DPAD_LEFT); frames.pad.axis(PadAxis.LEFT_X, 0.6f); });
        assertEquals("past STICK_THRESHOLD", +1, controls.move.signX());

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, 0.4f));
        assertEquals("inside it", 0, controls.move.signX());

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_Y, -1f));
        assertEquals("stick up is positive y", +1, controls.move.signY());

        frames.frame(() -> { frames.pad.axis(PadAxis.LEFT_Y, 0f); frames.pad.press(Pad.A); });
        assertTrue(controls.action.pressed());
        assertTrue(controls.action.down());
    }

    @Test
    public void keyboardAndPadAreOneBinding() {
        var controls = InputControls.wasd(Keys.SPACE).gamepad(0);
        frames.on(() -> controls.update(frames.input));

        frames.frame(() -> { frames.input.keyDown(Keys.A); frames.pad.press(Pad.DPAD_RIGHT); });
        assertEquals("a tie goes to positive", +1, controls.move.signX());

        frames.frame(() -> frames.pad.release(Pad.DPAD_RIGHT));
        assertEquals(-1, controls.move.signX());

        frames.frame(() -> frames.input.keyDown(Keys.SPACE));
        frames.frame(() -> frames.pad.press(Pad.A));
        assertTrue("each source's edge counts, as in blah", controls.action.pressed());
        assertTrue(controls.action.down());

        frames.frame(() -> frames.input.keyUp(Keys.SPACE));
        assertTrue(controls.action.released());
        assertTrue("the pad still holds it", controls.action.down());
    }

    @Test
    public void keysAddsAnotherLayout() {
        var controls = InputControls.wasd(Keys.SPACE).keys(Keys.LEFT, Keys.RIGHT, Keys.UP, Keys.DOWN, Keys.ENTER);
        frames.on(() -> controls.update(frames.input));
        frames.frame(() -> { frames.input.keyDown(Keys.RIGHT); frames.input.keyDown(Keys.ENTER); });
        assertEquals(+1, controls.move.signX());
        assertTrue(controls.action.down());
    }

    @Test
    public void zeroOutDropsBoth() {
        var controls = InputControls.wasd(Keys.SPACE);
        frames.on(() -> controls.update(frames.input));
        frames.frame(() -> { frames.input.keyDown(Keys.D); frames.input.keyDown(Keys.SPACE); });
        controls.zeroOut();
        assertEquals(0, controls.move.signX());
        assertFalse(controls.action.down());
    }

    @Test
    public void controlsBindingsHaveNoBuffers() {
        var controls = InputControls.wasd(Keys.SPACE);
        assertEquals(0f, controls.action.pressBuffer, 0f);
        assertEquals(0f, controls.action.releaseBuffer, 0f);
        assertEquals(0.5f, InputControls.STICK_THRESHOLD, 0f);
    }
}
