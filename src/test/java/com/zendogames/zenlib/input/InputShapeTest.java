package com.zendogames.zenlib.input;

import com.badlogic.gdx.Input.Keys;
import com.zendogames.zenlib.input.AxisBinding.Overlap;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The review's input API changes: a frame counter tells frames apart, {@code InputControls.addGamepad} adds while
 * every binding's {@code gamepad} retargets (a no-op without pad sources), axes and sticks read {@code released()},
 * and a press never seen counts as the oldest in an overlap.
 */
public class InputShapeTest {

    final Frames frames = new Frames();

    @Test
    public void aZeroDeltaFrameIsStillANewFrame() {
        var button = new ButtonBinding().key(Keys.X);
        button.pressBuffer = 0.15f;
        frames.on(() -> button.update(frames.input));

        frames.frame(() -> frames.input.keyDown(Keys.X));
        button.consumePress();
        frames.frame(0f, () -> frames.input.keyUp(Keys.X));
        frames.frame(0f, () -> frames.input.keyDown(Keys.X));
        assertTrue("a new press in a frame the clock did not move for", button.pressed());
    }

    @Test
    public void addGamepadAddsAndGamepadRetargets() {
        var controls = InputControls.wasd(Keys.SPACE).addGamepad(0);
        frames.on(() -> controls.update(frames.input));

        frames.frame(() -> frames.pad.press(Pad.A));
        assertTrue(controls.action.down());
        frames.frame(() -> { frames.pad.release(Pad.A); frames.input.keyDown(Keys.SPACE); });
        assertTrue("the keys stay", controls.action.down());
        frames.frame(() -> frames.input.keyUp(Keys.SPACE));

        controls.action.gamepad(1);
        controls.move.gamepad(1);
        frames.frame(() -> { frames.pad.press(Pad.A); frames.pad.press(Pad.DPAD_LEFT); });
        assertFalse("slot 0 no longer counts", controls.action.down());
        assertEquals(0, controls.move.signX());
        frames.frame(() -> { frames.pad2.press(Pad.A); frames.pad2.press(Pad.DPAD_LEFT); });
        assertTrue(controls.action.down());
        assertEquals(-1, controls.move.signX());
    }

    @Test
    public void gamepadOnABindingWithoutPadSourcesIsANoOp() {
        var button = new ButtonBinding().key(Keys.X).gamepad(1);
        frames.on(() -> button.update(frames.input));
        frames.frame(() -> frames.input.keyDown(Keys.X));
        assertTrue(button.down());
        frames.frame(() -> frames.pad2.press(Pad.A));
        assertFalse(button.pressed());
    }

    @Test
    public void axisAndStickReleasedAreEitherSidesEdge() {
        var axis = new AxisBinding().keys(Keys.A, Keys.D);
        var stick = new StickBinding().keys(Keys.LEFT, Keys.RIGHT, Keys.UP, Keys.DOWN);
        frames.on(() -> { axis.update(frames.input); stick.update(frames.input); });

        frames.frame(() -> { frames.input.keyDown(Keys.D); frames.input.keyDown(Keys.UP); });
        assertFalse(axis.released());
        assertFalse(stick.released());

        frames.frame(() -> frames.input.keyUp(Keys.D));
        assertTrue(axis.released());
        assertFalse(stick.released());

        frames.frame(() -> frames.input.keyUp(Keys.UP));
        assertFalse("released is an edge", axis.released());
        assertTrue(stick.released());

        frames.frame(() -> frames.input.keyDown(Keys.A));
        frames.frame(() -> frames.input.keyUp(Keys.A));
        assertTrue("the negative side too", axis.released());
        axis.consumeRelease();
        assertFalse(axis.released());
    }

    @Test
    public void anUnseenPressCountsAsTheOldestInAnOverlap() {
        var axis = new AxisBinding().keys(Keys.A, Keys.D);
        frames.on(() -> axis.update(frames.input));

        frames.frame(() -> frames.input.keyDown(Keys.A));
        axis.zeroOut();                                      // a screen change: A stays held in the state
        frames.frame(() -> frames.input.keyDown(Keys.D));
        assertTrue(axis.negative.down() && axis.positive.down());
        assertEquals(-1.0, axis.negative.timestamp(), 0.0);

        assertEquals("NEWER: the press we saw", +1, axis.sign());
        axis.overlap = Overlap.OLDER;
        assertEquals("OLDER: the press we never saw", -1, axis.sign());
    }
}
