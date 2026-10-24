package com.zendogames.zenlib.input;

import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import org.junit.Test;

import static org.junit.Assert.*;

public class ButtonBindingTest {

    final Frames frames = new Frames();
    final ButtonBinding button = new ButtonBinding();

    public ButtonBindingTest() {
        frames.on(() -> button.update(frames.input));
    }

    @Test
    public void pressedIsAnEdgeAndDownIsALevel() {
        button.key(Keys.X);
        frames.frame(() -> frames.input.keyDown(Keys.X));
        assertTrue(button.pressed());
        assertTrue(button.down());
        assertFalse(button.released());
        assertEquals(1, button.sign());

        frames.idle(1);
        assertFalse("pressed is an edge", button.pressed());
        assertTrue(button.down());

        frames.frame(() -> frames.input.keyUp(Keys.X));
        assertTrue(button.released());
        assertFalse(button.down());
        assertEquals(0, button.sign());

        frames.idle(1);
        assertFalse("released is an edge", button.released());
    }

    @Test
    public void aTapInsideOneFrameReadsPressedAndReleased() {
        button.key(Keys.X);
        frames.frame(() -> { frames.input.keyDown(Keys.X); frames.input.keyUp(Keys.X); });
        assertTrue(button.pressed());
        assertTrue(button.released());
        assertFalse(button.down());
    }

    @Test
    public void anySourceCounts() {
        button.key(Keys.X).mouse(Buttons.LEFT).pad(0, Pad.A);

        frames.frame(() -> frames.input.keyDown(Keys.X));
        assertTrue(button.down());
        assertEquals(1f, button.value(), 0f);
        frames.frame(() -> frames.input.keyUp(Keys.X));
        assertFalse(button.down());

        frames.frame(() -> frames.input.mouseDown(Buttons.LEFT));
        assertTrue(button.down());
        assertTrue(button.pressed());
        frames.frame(() -> frames.input.mouseUp(Buttons.LEFT));
        assertFalse(button.down());

        frames.frame(() -> frames.pad.press(Pad.A));
        assertTrue(button.down());
        assertTrue(button.pressed());
        assertEquals(1f, button.value(), 0f);
        frames.frame(() -> frames.pad.release(Pad.A));
        assertTrue(button.released());
    }

    @Test
    public void pressBufferKeepsPressedForItsWindow() {
        button.key(Keys.X);
        button.pressBuffer = 0.15f;
        frames.frame(() -> { frames.input.keyDown(Keys.X); frames.input.keyUp(Keys.X); });
        assertTrue(button.pressed());

        frames.idle(14); // 0.14 s later: inside the window
        assertTrue(button.pressed());
        assertFalse(button.down());

        frames.idle(2);  // 0.16 s: outside
        assertFalse(button.pressed());
    }

    @Test
    public void releaseBufferKeepsReleasedForItsWindow() {
        button.key(Keys.X);
        button.releaseBuffer = 0.05f;
        frames.frame(() -> frames.input.keyDown(Keys.X));
        frames.frame(() -> frames.input.keyUp(Keys.X));
        frames.idle(4); // 0.04 s later
        assertTrue(button.released());
        frames.idle(2); // 0.06 s
        assertFalse(button.released());
    }

    @Test
    public void consumePressEatsTheBuffer() {
        button.key(Keys.X);
        button.pressBuffer = 0.15f;
        frames.frame(() -> frames.input.keyDown(Keys.X));
        assertTrue(button.pressed());

        button.consumePress();
        assertFalse(button.pressed());
        assertTrue("down is untouched", button.down());

        frames.idle(5);
        assertFalse("the buffer is gone too", button.pressed());
    }

    @Test
    public void aNewPressAfterConsumeReadsAgain() {
        button.key(Keys.X);
        button.pressBuffer = 0.15f;
        frames.frame(() -> frames.input.keyDown(Keys.X));
        button.consumePress();
        frames.frame(() -> frames.input.keyUp(Keys.X));
        frames.frame(() -> frames.input.keyDown(Keys.X));
        assertTrue(button.pressed());
    }

    @Test
    public void anAxisCrossingItsThresholdIsAnEdgeAndRemapsValue() {
        button.axis(0, PadAxis.LEFT_X, true, 0.5f);

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, 0.3f));
        assertFalse(button.down());
        assertEquals(0f, button.value(), 0f);

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, 0.6f));
        assertTrue(button.pressed());
        assertTrue(button.down());
        assertEquals("(0.6 - 0.5) / (1 - 0.5)", 0.2f, button.value(), 1e-4f);

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, 1f));
        assertFalse(button.pressed());
        assertTrue(button.down());
        assertEquals(1f, button.value(), 1e-4f);

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, 0.2f));
        assertTrue(button.released());
        assertFalse(button.down());

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, -0.9f));
        assertFalse("the wrong sign never counts", button.down());
    }

    @Test
    public void aDigitalSourceDownMakesValueOneDespiteAHalfAxis() {
        button.key(Keys.X).axis(0, PadAxis.LEFT_X, true, 0.5f);
        frames.frame(() -> { frames.input.keyDown(Keys.X); frames.pad.axis(PadAxis.LEFT_X, 0.75f); });
        assertEquals(1f, button.value(), 0f);
    }

    @Test
    public void heldTimeCountsWhileDownAndKeepsItsValueOnRelease() {
        var dt = 0.1f;
        button.key(Keys.X);
        frames.frame(dt, () -> frames.input.keyDown(Keys.X));
        assertEquals(0f, button.heldTime(), 1e-5f);

        frames.idle(3, dt);
        assertEquals(0.3f, button.heldTime(), 1e-5f);

        frames.frame(dt, () -> frames.input.keyUp(Keys.X));
        assertTrue(button.released());
        assertEquals("a hold move reads it on the release", 0.3f, button.heldTime(), 1e-5f);

        frames.idle(2, dt);
        assertEquals("kept after the release too", 0.3f, button.heldTime(), 1e-5f);

        frames.frame(dt, () -> frames.input.keyDown(Keys.X));
        assertEquals("restarts on the next press", 0f, button.heldTime(), 1e-5f);
    }

    @Test
    public void timestampIsTheLastPress() {
        button.key(Keys.X);
        frames.idle(3);
        frames.frame(() -> frames.input.keyDown(Keys.X));
        var pressedAt = button.timestamp();
        assertEquals(frames.input.time, pressedAt, 1e-9);
        frames.frame(() -> frames.input.keyUp(Keys.X));
        frames.idle(2);
        assertEquals("a release does not move it", pressedAt, button.timestamp(), 1e-9);
    }

    @Test
    public void twoUpdatesInOneFrameSeeTheSameEdge() {
        button.key(Keys.X);
        frames.input.keyDown(Keys.X);
        frames.input.step(Frames.DT);

        button.update(frames.input);
        assertTrue(button.pressed());
        var held = button.heldTime();

        button.update(frames.input); // a second tick in the same frame
        assertTrue("the frame's edge is still there", button.pressed());
        assertTrue(button.down());
        assertEquals(held, button.heldTime(), 0f);

        frames.input.endFrame();
    }

    @Test
    public void gamepadRetargetsPadSources() {
        button.pad(0, Pad.A).gamepad(1);
        frames.frame(() -> frames.pad.press(Pad.A));
        assertFalse("pad 0 no longer counts", button.down());
        frames.frame(() -> frames.pad2.press(Pad.A));
        assertTrue(button.down());
    }

    @Test
    public void anUnconnectedOrOutOfRangeGamepadIsIgnored() {
        button.pad(3, Pad.A).axis(9, PadAxis.LEFT_X, true, 0.5f);
        frames.frame(() -> {});
        assertFalse(button.down());
        assertFalse(button.pressed());
        assertEquals(0f, button.value(), 0f);
    }

    @Test
    public void zeroOutDropsDownWithoutARelease() {
        button.key(Keys.X);
        frames.frame(() -> frames.input.keyDown(Keys.X));
        frames.idle(3);
        button.zeroOut();
        assertFalse(button.down());
        assertFalse(button.pressed());
        assertFalse(button.released());
        assertEquals(0f, button.heldTime(), 0f);
        assertEquals(0f, button.value(), 0f);
    }

    @Test
    public void clearRemovesEverySource() {
        button.key(Keys.X).mouse(Buttons.LEFT).pad(0, Pad.A);
        button.clear();
        frames.frame(() -> { frames.input.keyDown(Keys.X); frames.input.mouseDown(Buttons.LEFT); frames.pad.press(Pad.A); });
        assertFalse(button.down());
    }
}
