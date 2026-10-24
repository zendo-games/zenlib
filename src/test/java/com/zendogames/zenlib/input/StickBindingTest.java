package com.zendogames.zenlib.input;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;
import org.junit.Test;

import static org.junit.Assert.*;

/** The one rule that differs from blah: y is up-positive, like {@code Input.moveY} and the world. */
public class StickBindingTest {

    final Frames frames = new Frames();
    final StickBinding stick = new StickBinding();
    final Vector2 value = new Vector2();

    public StickBindingTest() {
        frames.on(() -> stick.update(frames.input));
    }

    @Test
    public void keysUpIsPositiveY() {
        stick.keys(Keys.A, Keys.D, Keys.W, Keys.S);

        frames.frame(() -> frames.input.keyDown(Keys.W));
        assertEquals(+1, stick.signY());
        assertEquals(0, stick.signX());

        frames.frame(() -> { frames.input.keyUp(Keys.W); frames.input.keyDown(Keys.S); });
        assertEquals(-1, stick.signY());

        frames.frame(() -> { frames.input.keyUp(Keys.S); frames.input.keyDown(Keys.D); frames.input.keyDown(Keys.W); });
        assertEquals(+1, stick.signX());
        assertEquals(+1, stick.signY());
        assertEquals(new Vector2(1, 1), stick.value(value));

        frames.frame(() -> { frames.input.keyUp(Keys.D); frames.input.keyDown(Keys.A); });
        assertEquals(-1, stick.signX());
    }

    @Test
    public void dpadUpIsPositiveY() {
        stick.dpad(0);
        frames.frame(() -> frames.pad.press(Pad.DPAD_UP));
        assertEquals(+1, stick.signY());
        frames.frame(() -> { frames.pad.release(Pad.DPAD_UP); frames.pad.press(Pad.DPAD_DOWN); });
        assertEquals(-1, stick.signY());
        frames.frame(() -> { frames.pad.release(Pad.DPAD_DOWN); frames.pad.press(Pad.DPAD_LEFT); });
        assertEquals(-1, stick.signX());
        frames.frame(() -> { frames.pad.release(Pad.DPAD_LEFT); frames.pad.press(Pad.DPAD_RIGHT); });
        assertEquals(+1, stick.signX());
    }

    @Test
    public void leftStickUpIsPositiveY() {
        stick.leftStick(0, 0.5f);

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_Y, -1f)); // libGDX: up is negative
        assertEquals(+1, stick.signY());
        assertEquals(+1f, stick.value(value).y, 1e-4f);

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_Y, +1f));
        assertEquals(-1, stick.signY());

        frames.frame(() -> { frames.pad.axis(PadAxis.LEFT_Y, 0f); frames.pad.axis(PadAxis.LEFT_X, -1f); });
        assertEquals(-1, stick.signX());
        assertEquals(0, stick.signY());

        frames.frame(() -> frames.pad.axis(PadAxis.LEFT_X, 0.4f));
        assertEquals("inside the threshold", 0, stick.signX());
    }

    @Test
    public void rightStickBindsTheOtherAxes() {
        stick.rightStick(0, 0.5f);
        frames.frame(() -> { frames.pad.axis(PadAxis.LEFT_X, 1f); frames.pad.axis(PadAxis.RIGHT_Y, -1f); });
        assertEquals("the left stick is not bound", 0, stick.signX());
        assertEquals(+1, stick.signY());
    }

    @Test
    public void roundThresholdZeroesASmallDeflection() {
        stick.leftStick(0, 0.01f);
        stick.roundThreshold = 0.5f;

        frames.frame(() -> { frames.pad.axis(PadAxis.LEFT_X, 0.3f); frames.pad.axis(PadAxis.LEFT_Y, 0.3f); });
        assertEquals(new Vector2(0, 0), stick.value(value));
        assertEquals(0, stick.signX());
        assertEquals(0, stick.signY());

        frames.frame(() -> { frames.pad.axis(PadAxis.LEFT_X, 0.8f); frames.pad.axis(PadAxis.LEFT_Y, 0f); });
        assertEquals(+1, stick.signX());
        assertTrue(stick.value(value).x > 0.7f);
    }

    @Test
    public void pressedIsAnyAxisEdge() {
        stick.keys(Keys.A, Keys.D, Keys.W, Keys.S);
        frames.frame(() -> frames.input.keyDown(Keys.W));
        assertTrue(stick.pressed());
        frames.idle(1);
        assertFalse(stick.pressed());
    }

    @Test
    public void gamepadRetargetsBothAxes() {
        stick.dpad(0).gamepad(1);
        frames.frame(() -> { frames.pad.press(Pad.DPAD_UP); frames.pad.press(Pad.DPAD_RIGHT); });
        assertEquals(0, stick.signX());
        assertEquals(0, stick.signY());
        frames.frame(() -> { frames.pad2.press(Pad.DPAD_UP); frames.pad2.press(Pad.DPAD_RIGHT); });
        assertEquals(+1, stick.signX());
        assertEquals(+1, stick.signY());
    }

    @Test
    public void zeroOutAndClear() {
        stick.keys(Keys.A, Keys.D, Keys.W, Keys.S);
        frames.frame(() -> frames.input.keyDown(Keys.W));
        stick.zeroOut();
        assertEquals(0, stick.signY());

        stick.clear();
        frames.frame(() -> frames.input.keyDown(Keys.D));
        assertEquals(0, stick.signX());
    }
}
