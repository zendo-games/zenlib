package com.zendogames.zenlib.sim;

import com.zendogames.zenlib.ZenFlag;

public class SimulationStep {

    public interface Tick { void step(float dt); }

    /** Fixed timestep interval. */
    public static final float FIXED_DT = 1f / 60f;

    /** Bound for when a frame takes far longer than {@link #FIXED_DT}. */
    private static final int MAX_SUBSTEPS = 5;

    /** Fraction of a tick left in the accumulator, for render interpolation. */
    public float alpha = 1f;

    /** Ticks stepped so far; one per {@link Tick#step} call. */
    public int tick = 0;

    private float accumulator = 0f;
    private boolean stepRequested = false;

    public void requestStep() {
        stepRequested = true;
    }

    /** Ticks for a duration authored in seconds, at least one so a positive duration is never zero. */
    public static int ticks(float seconds) {
        return Math.max(1, Math.round(seconds / FIXED_DT));
    }

    /** Seconds for a tick count, for display. */
    public static float seconds(int ticks) {
        return ticks * FIXED_DT;
    }

    public void update(float dt, Tick tick) {
        if (ZenFlag.FRAME_STEP.on()) {
            if (stepRequested) {
                tick.step(FIXED_DT);
                this.tick++;
            }
            stepRequested = false;
            alpha = 1f;
            return;
        }

        if (ZenFlag.FIXED_TIMESTEP.off()) {
            tick.step(dt);
            this.tick++;
            alpha = 1f;
            return;
        }

        int steps = 0;
        accumulator += dt;
        while (accumulator >= FIXED_DT && steps < MAX_SUBSTEPS) {
            tick.step(FIXED_DT);
            this.tick++;
            accumulator -= FIXED_DT;
            steps++;
        }

        if (steps >= MAX_SUBSTEPS) {
            accumulator = 0f;   // drop the backlog instead of spiraling
        }

        alpha = accumulator / FIXED_DT; // set render interp step (alpha) within [0, 1)
    }
}
