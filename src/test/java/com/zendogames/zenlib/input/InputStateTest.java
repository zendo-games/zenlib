package com.zendogames.zenlib.input;

import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The frame protocol: events land between frames and are stamped with the clock as it was, {@code step}
 * advances the clock and polls gamepads, bindings read during the frame, {@code endFrame} rolls
 * {@code now} into {@code last} and clears the edges.
 */
public class InputStateTest {

    static final float DT = 1f / 60f;

    final InputState input = new InputState();
    final FakeController pad = new FakeController();

    @After
    public void tearDown() {
        HeadlessApp.uninstall();
    }

    @Test
    public void keyDownIsAPressEdgeThenALevel() {
        input.keyDown(Keys.A);
        input.step(DT);
        assertTrue(input.now.keyboard.pressed(Keys.A));
        assertTrue(input.now.keyboard.down(Keys.A));
        assertFalse(input.now.keyboard.released(Keys.A));

        input.endFrame();
        input.step(DT);
        assertFalse("pressed is an edge", input.now.keyboard.pressed(Keys.A));
        assertTrue(input.now.keyboard.down(Keys.A));
        assertTrue("last frame's edge is readable", input.last.keyboard.pressed(Keys.A));

        input.endFrame();
        input.keyUp(Keys.A);
        input.step(DT);
        assertTrue(input.now.keyboard.released(Keys.A));
        assertFalse(input.now.keyboard.down(Keys.A));
    }

    @Test
    public void pressTimeIsTheStepClock() {
        input.step(0.5f);
        input.step(0.5f);
        input.keyDown(Keys.A);   // between frames: stamped with the clock as it stands, 1.0
        input.step(0.5f);
        assertEquals(1.5, input.time, 1e-9);
        assertEquals(1.0, input.now.keyboard.pressTime(Keys.A), 1e-9);
    }

    @Test
    public void mouseButtonsPositionAndWheel() {
        input.mouseDown(Buttons.LEFT);
        input.mouseMoved(10, 20);
        input.scrolled(0f, 1f);
        input.step(DT);
        assertTrue(input.now.mouse.pressed(Buttons.LEFT));
        assertTrue(input.now.mouse.down(Buttons.LEFT));
        assertEquals(10, input.now.mouseX);
        assertEquals(20, input.now.mouseY);
        assertEquals(1f, input.now.wheelY, 0f);

        input.endFrame();
        assertEquals("the wheel is per frame", 0f, input.now.wheelY, 0f);
        assertEquals(1f, input.last.wheelY, 0f);
        assertEquals("position carries", 10, input.now.mouseX);

        input.mouseUp(Buttons.LEFT);
        input.step(DT);
        assertTrue(input.now.mouse.released(Buttons.LEFT));
        assertFalse(input.now.mouse.down(Buttons.LEFT));
    }

    @Test
    public void outOfRangeCodesReadFalse() {
        input.keyDown(-1);
        input.keyDown(999);
        input.mouseDown(99);
        input.step(DT);
        assertFalse(input.now.keyboard.down(999));
        assertFalse(input.now.keyboard.down(-1));
        assertFalse(input.now.keyboard.pressed(999));
        assertFalse(input.now.mouse.down(99));
        assertEquals(0.0, input.now.keyboard.pressTime(999), 0.0);
    }

    @Test
    public void stepPollsGamepadButtonsAndAxesWithEdgesAgainstLastFrame() {
        input.controllerSource = FakeController.source(pad);
        pad.press(Pad.A).axis(PadAxis.LEFT_X, 0.7f);
        input.step(DT);
        var gamepad = input.now.gamepads[0];
        assertTrue(gamepad.connected);
        assertEquals("fake pad", gamepad.name);
        assertTrue(gamepad.buttons.pressed(Pad.A.ordinal()));
        assertTrue(gamepad.buttons.down(Pad.A.ordinal()));
        assertEquals(0.7f, gamepad.axes[PadAxis.LEFT_X.ordinal()], 0f);
        assertFalse("L2 is UNDEFINED in the mapping", gamepad.buttons.down(Pad.L2.ordinal()));

        input.endFrame();
        input.step(DT);
        assertFalse(input.now.gamepads[0].buttons.pressed(Pad.A.ordinal()));
        assertTrue(input.now.gamepads[0].buttons.down(Pad.A.ordinal()));

        pad.release(Pad.A);
        input.endFrame();
        input.step(DT);
        assertTrue(input.now.gamepads[0].buttons.released(Pad.A.ordinal()));
        assertFalse(input.now.gamepads[0].buttons.down(Pad.A.ordinal()));
    }

    @Test
    public void aDisconnectedGamepadReleasesWhatItHeld() {
        var pads = new Array<Controller>();
        pads.add(pad);
        input.controllerSource = () -> pads;
        pad.press(Pad.A).axis(PadAxis.LEFT_X, 0.7f);
        input.step(DT);
        assertTrue(input.now.gamepads[0].buttons.down(Pad.A.ordinal()));

        pads.clear(); // yanked
        input.endFrame();
        input.step(DT);
        var gamepad = input.now.gamepads[0];
        assertFalse(gamepad.connected);
        assertTrue("the held button is released", gamepad.buttons.released(Pad.A.ordinal()));
        assertFalse(gamepad.buttons.down(Pad.A.ordinal()));
        assertEquals(0f, gamepad.axes[PadAxis.LEFT_X.ordinal()], 0f);
    }

    @Test
    public void aThrowingControllerSourceIsDroppedAfterOneError() {
        HeadlessApp.install(); // the library reports it with Log.error
        input.controllerSource = () -> { throw new GdxRuntimeException("Error creating controller manager"); };
        input.step(DT);
        assertNull("dropped after the first failure", input.controllerSource);
        assertFalse(input.now.gamepads[0].connected);

        input.endFrame();
        input.step(DT); // and never consulted again
    }

    @Test
    public void aNullSourceMeansNoGamepads() {
        assertNull(input.controllerSource);
        input.step(DT);
        for (var gamepad : input.now.gamepads) {
            assertFalse(gamepad.connected);
        }
    }

    @Test
    public void anyPressedSeesEveryDevice() {
        input.controllerSource = FakeController.source(pad);
        input.step(DT);
        assertFalse(input.anyPressed());

        input.keyDown(Keys.X);
        input.step(DT);
        assertTrue(input.anyPressed());

        input.endFrame();
        input.step(DT);
        assertFalse("edges are gone, the held key does not count", input.anyPressed());

        input.mouseDown(Buttons.LEFT);
        input.step(DT);
        assertTrue(input.anyPressed());

        input.endFrame();
        pad.press(Pad.B);
        input.step(DT);
        assertTrue(input.anyPressed());
    }

    @Test
    public void zeroOutDropsEverythingWithoutEdges() {
        input.controllerSource = FakeController.source(pad);
        input.keyDown(Keys.A);
        pad.press(Pad.A).axis(PadAxis.LEFT_X, 1f);
        input.step(DT);
        input.endFrame();

        input.zeroOut();
        assertFalse(input.now.keyboard.down(Keys.A));
        assertFalse(input.now.keyboard.released(Keys.A));
        assertFalse(input.now.gamepads[0].buttons.down(Pad.A.ordinal()));
        assertEquals(0f, input.now.gamepads[0].axes[PadAxis.LEFT_X.ordinal()], 0f);
        assertFalse(input.last.keyboard.down(Keys.A));
        assertFalse(input.last.gamepads[0].buttons.down(Pad.A.ordinal()));
    }
}
