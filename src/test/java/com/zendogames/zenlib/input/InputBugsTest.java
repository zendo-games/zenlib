package com.zendogames.zenlib.input;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.IntArray;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The review's verified input bugs: a consume that a second tick in the frame undid, a {@code KeySender} button
 * that swallowed every second tap, and a held time that stayed stale on a tap inside one frame.
 */
public class InputBugsTest {

    final Frames frames = new Frames();
    final ButtonBinding button = new ButtonBinding().key(Keys.X);

    @Test
    public void consumePressSurvivesASecondTickInTheSameFrame() {
        button.pressBuffer = 0.15f;
        frames.input.keyDown(Keys.X);
        frames.input.step(Frames.DT);

        button.update(frames.input);                         // tick 1: the jump reads and is consumed
        assertTrue(button.pressed());
        button.consumePress();
        assertFalse(button.pressed());

        button.update(frames.input);                         // tick 2, same frame, same edge
        assertFalse("still consumed", button.pressed());
        frames.input.endFrame();

        frames.on(() -> button.update(frames.input));
        frames.idle(3);                                      // inside the buffer window
        assertFalse("the consumed press does not come back through the buffer", button.pressed());
        assertTrue(button.down());
    }

    @Test
    public void aTapInsideOneFrameSendsDownThenUp() {
        var target = new KeySenderTest.Target();
        var sender = new KeySender(target).button(button, Keys.ENTER);
        frames.on(() -> sender.update(frames.input, Frames.DT));

        frames.frame(() -> { frames.input.keyDown(Keys.X); frames.input.keyUp(Keys.X); });
        assertEquals(IntArray.with(Keys.ENTER), target.downs);
        assertEquals(IntArray.with(Keys.ENTER), target.ups);

        frames.frame(() -> frames.input.keyDown(Keys.X));    // the next press is not swallowed
        assertEquals(IntArray.with(Keys.ENTER, Keys.ENTER), target.downs);
        frames.frame(() -> frames.input.keyUp(Keys.X));
        assertEquals(IntArray.with(Keys.ENTER, Keys.ENTER), target.ups);
    }

    @Test
    public void heldTimeIsZeroOnATapInsideOneFrame() {
        var dt = 0.1f;
        frames.on(() -> button.update(frames.input));
        frames.frame(dt, () -> frames.input.keyDown(Keys.X));
        frames.idle(3, dt);
        frames.frame(dt, () -> frames.input.keyUp(Keys.X));
        assertEquals(0.3f, button.heldTime(), 1e-5f);

        frames.frame(dt, () -> { frames.input.keyDown(Keys.X); frames.input.keyUp(Keys.X); });
        assertTrue(button.pressed() && button.released() && !button.down());
        assertEquals("a tap is a hold of this frame, not the last one", 0f, button.heldTime(), 1e-5f);
    }
}
