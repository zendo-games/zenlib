package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.math.Interpolation;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Interp;
import org.junit.Test;

import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

public class InterpSystemTest {

    @Test
    public void advancesLinearlyAndClampsAtOne() {
        var world = new World();
        world.addSystem(new InterpSystem());
        var interp = new Interp(FIXED_DT * 4);
        world.createEntity().add(interp);

        world.update(FIXED_DT);
        assertEquals(0.25f, interp.value(), 0.001f);
        assertEquals(10f + 0.25f * 30f, interp.apply(10f, 40f), 0.001f);
        assertFalse(interp.isComplete());

        world.update(FIXED_DT * 3);
        assertEquals(1f, interp.value(), 0.001f);
        assertTrue(interp.isComplete());

        world.update(FIXED_DT);
        assertEquals(1f, interp.value(), 0.001f); // stays clamped
        assertEquals(0f, interp.inverseValue(), 0.001f);
    }

    @Test
    public void pausedInterpHolds() {
        var world = new World();
        world.addSystem(new InterpSystem());
        var interp = new Interp(1f);
        world.createEntity().add(interp);

        interp.pause();
        world.update(FIXED_DT);
        assertEquals(0f, interp.elapsed(), 0f);

        interp.resume();
        world.update(FIXED_DT);
        assertEquals(FIXED_DT, interp.elapsed(), 0.0001f);
    }

    @Test
    public void speedScalesElapsedAndEasingApplies() {
        var world = new World();
        world.addSystem(new InterpSystem());
        var interp = new Interp(FIXED_DT * 4, Interpolation.pow2In);
        interp.speed = 2f;
        world.createEntity().add(interp);

        world.update(FIXED_DT); // elapsed = 2 ticks of 4 -> alpha 0.5 -> pow2In 0.25
        assertEquals(0.25f, interp.value(), 0.001f);

        interp.reset();
        assertEquals(0f, interp.value(), 0f);
        assertEquals(0f, interp.elapsed(), 0f);
    }
}
