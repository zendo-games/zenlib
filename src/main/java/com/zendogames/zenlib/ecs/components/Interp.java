package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.math.Interpolation;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.utils.Calc;

/**
 * One eased parameter in {@code [0, 1]} that {@code InterpSystem} advances over {@link #duration}
 * seconds. Presentation state, so it stays in float seconds. An entity that needs several values
 * with different timings wants tweens instead.
 */
public class Interp implements Component {

    public static final ComponentType<Interp> TYPE = ComponentType.register(Interp.class);
    public static Interp  get(Entity e) { return TYPE.get(e); }
    public static boolean has(Entity e) { return TYPE.has(e); }

    private final float duration;

    private float elapsed;
    private float value;
    private boolean paused;

    public Interpolation interpolation;

    /** Multiplier for elapsed time: must be greater than zero, {@code InterpSystem} resets it to 1 otherwise. */
    public float speed;

    public Interp(float duration) {
        this(duration, Interpolation.linear);
    }

    public Interp(float duration, Interpolation interpolation) {
        this.duration = duration;
        this.interpolation = interpolation;
        this.elapsed = 0;
        this.value = 0;
        this.paused = false;
        this.speed = 1;
    }
    
    /** {@code start} to {@code end} at the current eased value. */
    public float apply(float start, float end) {
        return start + (end - start) * value;
    }

    public void update(float dt) {
        elapsed += speed * dt;
        var alpha = Calc.clampf(elapsed / duration, 0f, 1f);
        value = interpolation.apply(alpha);
    }

    public void pause()  { paused = true; }
    public void resume() { paused = false; }

    public void reset() {
        elapsed = 0;
        value = 0;
    }

    public float duration()     { return duration; }
    public float elapsed()      { return elapsed; }
    public float value()        { return value; }
    public float inverseValue() { return 1f - value; }
    public boolean isPaused()   { return paused; }
    public boolean isComplete() { return elapsed >= duration; }
}
