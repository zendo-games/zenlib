package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.ControllerPowerLevel;
import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Input;
import com.zendogames.zenlib.ecs.movement.MoveIntent;
import com.zendogames.zenlib.input.AxisBinding.Overlap;
import com.zendogames.zenlib.input.InputControls;
import com.zendogames.zenlib.input.InputState;
import com.zendogames.zenlib.input.Pad;
import com.zendogames.zenlib.input.PadAxis;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

/**
 * The system reads one {@link InputState} that steps per frame; a frame here is: events, {@code step},
 * one tick, {@code endFrame}. Edges on {@link Input} are per tick, derived by the system from the
 * bindings' levels and the frame's edge, so a tap inside one frame and two ticks in one frame both read right.
 */
public class InputSystemTest {

    final InputState input = new InputState();
    final Pad1 pad = new Pad1();
    final World world = new World();
    final InputControls wasd = InputControls.wasd(Keys.SPACE);
    final Entity player;

    public InputSystemTest() {
        input.controllerSource = () -> Array.with((Controller) pad);
        world.addSystem(new InputSystem(input));
        player = world.createEntity().add(new Input(wasd));
    }

    void tick() {
        world.update(FIXED_DT);
    }

    /** Events land, the frame steps, one tick runs, the frame ends. */
    void frame(Runnable events) {
        events.run();
        input.step(FIXED_DT);
        tick();
        input.endFrame();
    }

    void idle() {
        frame(() -> {});
    }

    Input input() {
        return Input.get(player);
    }

    @Test
    public void steersFromHeldKeysUpIsPositive() {
        frame(() -> { input.keyDown(Keys.D); input.keyDown(Keys.W); });
        assertEquals(+1, input().moveX);
        assertEquals(+1, input().moveY);
        assertTrue(input().rightHeld);
        assertTrue(input().upHeld);
        assertFalse(input().leftHeld);

        frame(() -> { input.keyUp(Keys.D); input.keyUp(Keys.W); input.keyDown(Keys.A); input.keyDown(Keys.S); });
        assertEquals(-1, input().moveX);
        assertEquals(-1, input().moveY);
        assertTrue(input().leftHeld);
        assertTrue(input().downHeld);
    }

    @Test
    public void oppositeKeysNewerWinsUnlessCancel() {
        frame(() -> input.keyDown(Keys.A));
        assertEquals(-1, input().moveX);

        frame(() -> input.keyDown(Keys.D));
        assertEquals("the later press wins", +1, input().moveX);
        assertTrue(input().leftHeld && input().rightHeld);

        frame(() -> input.keyUp(Keys.D));
        assertEquals(-1, input().moveX);

        wasd.move.x.overlap = Overlap.CANCEL;
        wasd.move.y.overlap = Overlap.CANCEL;
        frame(() -> { input.keyDown(Keys.D); input.keyDown(Keys.W); input.keyDown(Keys.S); });
        assertEquals(0, input().moveX);
        assertEquals(0, input().moveY);
        assertTrue(input().upHeld && input().downHeld);
    }

    @Test
    public void actionEdgesLastOneTick() {
        frame(() -> input.keyDown(Keys.SPACE));
        assertTrue(input().actionPressed);
        assertTrue(input().actionHeld);
        assertFalse(input().actionReleased);

        idle();
        assertFalse("pressed is an edge", input().actionPressed);
        assertTrue(input().actionHeld);

        frame(() -> input.keyUp(Keys.SPACE));
        assertTrue(input().actionReleased);
        assertFalse(input().actionHeld);

        idle();
        assertFalse(input().actionReleased);
    }

    @Test
    public void aTapWithinOneFrameGivesOnePressedAndOneReleasedTick() {
        frame(() -> { input.keyDown(Keys.SPACE); input.keyUp(Keys.SPACE); });
        assertTrue("the frame's press edge counts as held for this tick", input().actionPressed);
        assertTrue(input().actionHeld);
        assertFalse(input().actionReleased);

        idle();
        assertTrue(input().actionReleased);
        assertFalse(input().actionHeld);
        assertFalse(input().actionPressed);
    }

    @Test
    public void twoTicksInOneFrameGiveOnePressedEdge() {
        input.keyDown(Keys.SPACE);
        input.keyDown(Keys.D);
        input.step(FIXED_DT);

        tick();
        assertTrue(input().actionPressed);
        assertTrue(input().actionHeld);
        assertEquals(+1, input().moveX);

        tick();
        assertFalse("the second tick of the frame sees a level, not an edge", input().actionPressed);
        assertTrue(input().actionHeld);
        assertEquals(+1, input().moveX);

        input.endFrame();
    }

    @Test
    public void aGamepadSteersLikeTheKeys() {
        wasd.gamepad(0);

        frame(() -> pad.buttons[Pad.DPAD_RIGHT.ordinal()] = true);
        assertEquals(+1, input().moveX);
        assertTrue(input().rightHeld);

        frame(() -> { pad.buttons[Pad.DPAD_RIGHT.ordinal()] = false; pad.axes[PadAxis.LEFT_Y.ordinal()] = -1f; });
        assertEquals(0, input().moveX);
        assertEquals("stick up is positive y", +1, input().moveY);
        assertTrue(input().upHeld);

        frame(() -> { pad.axes[PadAxis.LEFT_Y.ordinal()] = 0f; pad.buttons[Pad.A.ordinal()] = true; });
        assertEquals(0, input().moveY);
        assertTrue(input().actionPressed);
        assertTrue(input().actionHeld);
    }

    @Test
    public void doubleTapIsADash() {
        frame(() -> input.keyDown(Keys.D));   // tap one
        assertFalse(input().dashPressed);
        frame(() -> input.keyUp(Keys.D));     // released
        frame(() -> input.keyDown(Keys.D));   // tap two, well inside the window
        assertTrue(input().dashPressed);
        idle();
        assertFalse("dash is an edge", input().dashPressed);

        // A third quick tap does not trigger again
        frame(() -> input.keyUp(Keys.D));
        frame(() -> input.keyDown(Keys.D));
        assertFalse(input().dashPressed);
    }

    @Test
    public void doubleTapWindowExpires() {
        frame(() -> input.keyDown(Keys.D));
        frame(() -> input.keyUp(Keys.D));
        int gap = Math.round(Input.DOUBLE_TAP_WINDOW / SimulationStep.FIXED_DT) + 2;
        for (int i = 0; i < gap; i++) idle();
        frame(() -> input.keyDown(Keys.D));
        assertFalse(input().dashPressed);
    }

    @Test
    public void reverseTapDoesNotDash() {
        frame(() -> input.keyDown(Keys.D));
        frame(() -> { input.keyUp(Keys.D); input.keyDown(Keys.A); });
        assertFalse(input().dashPressed);
    }

    @Test
    public void nullControlsAreLeftToOthersButEventsClear() {
        var ai = world.createEntity().add(new Input());
        var in = Input.get(ai);
        in.moveX = -1;
        in.actionPressed = true;
        in.dashPressed = true;
        frame(() -> input.keyDown(Keys.D));
        assertEquals("steer kept, keyboard ignored", -1, in.moveX);
        assertFalse(in.actionPressed);
        assertFalse(in.dashPressed);
    }

    @Test
    public void intentMirrorsInput() {
        frame(() -> { input.keyDown(Keys.A); input.keyDown(Keys.S); input.keyDown(Keys.SPACE); });
        var intent = MoveIntent.of(input());
        assertEquals(-1, intent.moveX());
        assertEquals(-1, intent.moveY());
        assertTrue(intent.downHeld());
        assertFalse(intent.upHeld());
        assertTrue(intent.actionPressed());
        assertTrue(intent.actionHeld());
        assertFalse(intent.dashPressed());
    }

    @Test
    public void resetClearsBindings() {
        var in = input();
        in.reset();
        assertNull(in.controls);
        frame(() -> input.keyDown(Keys.D));
        assertEquals(0, in.moveX);
    }

    /** A one-pad controller source for this package; the input tests have a richer one. */
    static final class Pad1 implements Controller {
        final boolean[] buttons = new boolean[Pad.values().length];
        final float[] axes = new float[PadAxis.values().length];
        static final ControllerMapping MAPPING = new ControllerMapping(
                PadAxis.LEFT_X.ordinal(), PadAxis.LEFT_Y.ordinal(), PadAxis.RIGHT_X.ordinal(), PadAxis.RIGHT_Y.ordinal(),
                Pad.A.ordinal(), Pad.B.ordinal(), Pad.X.ordinal(), Pad.Y.ordinal(), Pad.BACK.ordinal(), Pad.START.ordinal(),
                Pad.L1.ordinal(), ControllerMapping.UNDEFINED, Pad.R1.ordinal(), ControllerMapping.UNDEFINED,
                Pad.LEFT_STICK.ordinal(), Pad.RIGHT_STICK.ordinal(),
                Pad.DPAD_UP.ordinal(), Pad.DPAD_DOWN.ordinal(), Pad.DPAD_LEFT.ordinal(), Pad.DPAD_RIGHT.ordinal()) {};

        @Override public boolean getButton(int code)         { return code >= 0 && code < buttons.length && buttons[code]; }
        @Override public float getAxis(int code)             { return (code >= 0 && code < axes.length) ? axes[code] : 0f; }
        @Override public String getName()                    { return "pad1"; }
        @Override public String getUniqueId()                { return "pad1"; }
        @Override public int getMinButtonIndex()             { return 0; }
        @Override public int getMaxButtonIndex()             { return buttons.length - 1; }
        @Override public int getAxisCount()                  { return axes.length; }
        @Override public boolean isConnected()               { return true; }
        @Override public boolean canVibrate()                { return false; }
        @Override public boolean isVibrating()               { return false; }
        @Override public void startVibration(int d, float s) {}
        @Override public void cancelVibration()              {}
        @Override public boolean supportsPlayerIndex()       { return false; }
        @Override public int getPlayerIndex()                { return PLAYER_IDX_UNSET; }
        @Override public void setPlayerIndex(int index)      {}
        @Override public ControllerMapping getMapping()      { return MAPPING; }
        @Override public ControllerPowerLevel getPowerLevel() { return ControllerPowerLevel.POWER_UNKNOWN; }
        @Override public void addListener(ControllerListener l)    {}
        @Override public void removeListener(ControllerListener l) {}
    }
}
