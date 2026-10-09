package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Interp;
import com.zendogames.zenlib.ecs.components.Name;

/** Advances every unpaused {@link Interp} by the tick. */
public class InterpSystem extends IteratingSystem {

    private static final String TAG = "InterpSystem";

    public InterpSystem() {
        super(Family.all(Interp.TYPE), SystemOrder.TIMERS);
    }

    @Override
    protected void process(Entity e, float dt) {
        var interp = Interp.get(e);
        if (interp.isPaused()) return;

        if (interp.speed <= 0) {
            Log.warn(TAG, "%s has an invalid interp speed %.2f <= 0, reset to 1", Name.of(e), interp.speed);
            interp.speed = 1;
        }

        interp.update(dt);
    }
}
