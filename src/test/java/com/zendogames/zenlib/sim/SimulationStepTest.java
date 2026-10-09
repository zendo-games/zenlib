package com.zendogames.zenlib.sim;

import org.junit.Test;

import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

public class SimulationStepTest {

    @Test
    public void ticksAndSecondsRoundTrip() {
        assertEquals(60, SimulationStep.ticks(1f));
        assertEquals(1, SimulationStep.ticks(0f));
        assertEquals(1, SimulationStep.ticks(0.001f));
        assertEquals(0.5f, SimulationStep.seconds(30), 0.0001f);
    }

    @Test
    public void countsTicks() {
        var step = new SimulationStep();
        int[] calls = {0};
        step.update(FIXED_DT * 3, dt -> calls[0]++);
        assertEquals(3, calls[0]);
        assertEquals(3, step.tick);
        step.update(FIXED_DT / 2, dt -> calls[0]++);
        assertEquals(3, step.tick); // not enough accumulated for another tick
        assertEquals(0.5f, step.alpha, 0.001f);
    }
}
