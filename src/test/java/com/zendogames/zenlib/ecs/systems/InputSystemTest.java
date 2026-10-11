package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.IntSet;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Input;
import com.zendogames.zenlib.ecs.movement.MoveIntent;
import com.zendogames.zenlib.input.InputControls;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static org.junit.Assert.*;

public class InputSystemTest {

    static final InputControls WASD = InputControls.wasd(Keys.SPACE);

    final IntSet held = new IntSet();
    final World world = new World();
    final Entity player;

    public InputSystemTest() {
        world.addSystem(new InputSystem(held::contains));
        player = world.createEntity().add(new Input(WASD));
    }

    void tick() {
        world.update(SimulationStep.FIXED_DT);
    }

    Input input() {
        return Input.get(player);
    }

    @Test
    public void steersFromHeldKeysUpIsPositive() {
        held.add(Keys.D);
        held.add(Keys.W);
        tick();
        assertEquals(+1, input().moveX);
        assertEquals(+1, input().moveY);
        assertTrue(input().rightHeld);
        assertTrue(input().upHeld);
        assertFalse(input().leftHeld);

        held.clear();
        held.add(Keys.A);
        held.add(Keys.S);
        tick();
        assertEquals(-1, input().moveX);
        assertEquals(-1, input().moveY);
    }

    @Test
    public void oppositeKeysCancel() {
        held.add(Keys.A);
        held.add(Keys.D);
        held.add(Keys.W);
        held.add(Keys.S);
        tick();
        assertEquals(0, input().moveX);
        assertEquals(0, input().moveY);
        assertTrue(input().leftHeld && input().rightHeld);
    }

    @Test
    public void actionEdgesLastOneTick() {
        held.add(Keys.SPACE);
        tick();
        assertTrue(input().actionPressed);
        assertTrue(input().actionHeld);
        assertFalse(input().actionReleased);

        tick();
        assertFalse("pressed is an edge", input().actionPressed);
        assertTrue(input().actionHeld);

        held.clear();
        tick();
        assertTrue(input().actionReleased);
        assertFalse(input().actionHeld);

        tick();
        assertFalse(input().actionReleased);
    }

    @Test
    public void doubleTapIsADash() {
        held.add(Keys.D);
        tick();                 // tap one
        assertFalse(input().dashPressed);
        held.clear();
        tick();                 // released
        held.add(Keys.D);
        tick();                 // tap two, well inside the window
        assertTrue(input().dashPressed);
        tick();
        assertFalse("dash is an edge", input().dashPressed);

        // A third quick tap does not trigger again
        held.clear();
        tick();
        held.add(Keys.D);
        tick();
        assertFalse(input().dashPressed);
    }

    @Test
    public void doubleTapWindowExpires() {
        held.add(Keys.D);
        tick();
        held.clear();
        int gap = Math.round(Input.DOUBLE_TAP_WINDOW / SimulationStep.FIXED_DT) + 2;
        for (int i = 0; i < gap; i++) tick();
        held.add(Keys.D);
        tick();
        assertFalse(input().dashPressed);
    }

    @Test
    public void reverseTapDoesNotDash() {
        held.add(Keys.D);
        tick();
        held.clear();
        held.add(Keys.A);
        tick();
        assertFalse(input().dashPressed);
    }

    @Test
    public void nullControlsAreLeftToOthersButEventsClear() {
        var ai = world.createEntity().add(new Input());
        var in = Input.get(ai);
        in.moveX = -1;
        in.actionPressed = true;
        in.dashPressed = true;
        held.add(Keys.D);
        tick();
        assertEquals("steer kept, keyboard ignored", -1, in.moveX);
        assertFalse(in.actionPressed);
        assertFalse(in.dashPressed);
    }

    @Test
    public void intentMirrorsInput() {
        held.add(Keys.A);
        held.add(Keys.S);
        held.add(Keys.SPACE);
        tick();
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
        held.add(Keys.D);
        tick();
        assertEquals(0, in.moveX);
    }
}
