package com.zendogames.zenlib.input;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.IntArray;
import org.junit.Test;

import static org.junit.Assert.*;

public class KeySenderTest {

    /** Records the key events a Stage would receive. */
    static final class Target extends InputAdapter {
        final IntArray downs = new IntArray();
        final IntArray ups   = new IntArray();
        @Override public boolean keyDown(int keycode) { downs.add(keycode); return true; }
        @Override public boolean keyUp(int keycode)   { ups.add(keycode);   return true; }
    }

    static final float DT = 0.04f;

    final Frames frames = new Frames();
    final Target target = new Target();
    final KeySender sender = new KeySender(target);
    final StickBinding stick = new StickBinding().keys(Keys.A, Keys.D, Keys.W, Keys.S);
    final ButtonBinding button = new ButtonBinding().key(Keys.SPACE);

    public KeySenderTest() {
        sender.stick(stick, Keys.LEFT, Keys.RIGHT, Keys.UP, Keys.DOWN)
              .button(button, Keys.ENTER);
        frames.on(() -> sender.update(frames.input, DT));
    }

    @Test
    public void aStickPressSendsKeyDownAndItsReleaseKeyUp() {
        frames.frame(DT, () -> frames.input.keyDown(Keys.W));
        assertEquals(IntArray.with(Keys.UP), target.downs);
        assertEquals(0, target.ups.size);

        frames.frame(DT, () -> frames.input.keyUp(Keys.W));
        assertEquals(IntArray.with(Keys.UP), target.ups);
        assertEquals("no repeat on a short press", 1, target.downs.size);
    }

    @Test
    public void aHeldDirectionRepeatsAfterTheDelay() {
        assertEquals(0.35f, sender.repeatDelay, 0f);
        assertEquals(0.1f, sender.repeatInterval, 0f);

        frames.frame(DT, () -> frames.input.keyDown(Keys.W));
        frames.idle(7, DT); // 8 frames, 0.32 s: before the delay
        assertEquals(1, target.downs.size);

        frames.idle(7, DT); // 15 frames, 0.60 s: the delay and two intervals have passed
        assertEquals("press + repeats at 0.35, 0.45, 0.55", 4, target.downs.size);
        for (int i = 0; i < target.downs.size; i++) assertEquals(Keys.UP, target.downs.get(i));
        assertEquals("repeats are keyDowns only", 0, target.ups.size);

        frames.frame(DT, () -> frames.input.keyUp(Keys.W));
        assertEquals(IntArray.with(Keys.UP), target.ups);
    }

    @Test
    public void changingDirectionReleasesTheOldOne() {
        frames.frame(DT, () -> frames.input.keyDown(Keys.W));
        frames.frame(DT, () -> { frames.input.keyUp(Keys.W); frames.input.keyDown(Keys.S); });
        assertEquals(IntArray.with(Keys.UP), target.ups);
        assertEquals(IntArray.with(Keys.UP, Keys.DOWN), target.downs);

        frames.frame(DT, () -> frames.input.keyDown(Keys.D));
        assertEquals("both axes are independent", IntArray.with(Keys.UP, Keys.DOWN, Keys.RIGHT), target.downs);
    }

    @Test
    public void aButtonSendsItsKeyOnPressAndRelease() {
        frames.frame(DT, () -> frames.input.keyDown(Keys.SPACE));
        assertEquals(IntArray.with(Keys.ENTER), target.downs);
        frames.idle(12, DT);
        assertEquals("buttons do not repeat", 1, target.downs.size);
        frames.frame(DT, () -> frames.input.keyUp(Keys.SPACE));
        assertEquals(IntArray.with(Keys.ENTER), target.ups);
    }

    @Test
    public void clearForgetsWithoutKeyUps() {
        frames.frame(DT, () -> frames.input.keyDown(Keys.W));
        sender.clear();
        frames.frame(DT, () -> frames.input.keyUp(Keys.W));
        assertEquals(0, target.ups.size);

        frames.frame(DT, () -> frames.input.keyDown(Keys.W));
        assertEquals("a fresh press after clear is a new keyDown", 2, target.downs.size);
    }
}
