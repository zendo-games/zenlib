package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ZenFlag;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.systems.TimerSystem;
import com.zendogames.zenlib.sim.SimulationStep;

import java.util.function.Consumer;

/**
 * Named countdowns on one entity, ticked by {@link TimerSystem}. The lambda tier of behavior:
 * an enemy that acts every few seconds is a {@link Timers} plus a few lines in the factory,
 * no component class and no system of its own.
 * <pre>{@code
 * var timers = new Timers();
 * entity.add(timers);
 * timers.every("shoot", 3f, shoot -> {
 *     var entity = shoot.entity();
 *     factory.bullet(entity);
 *     Animator.get(entity).play(AnimTurret.SHOOT);
 *     timers.after("idle", 0.4f, idle ->
 *             Animator.get(idle.entity()).play(AnimTurret.IDLE));
 * });
 * }</pre>
 * Rules, all enforced here:
 * <ul>
 *     <li>Seconds are authored, ticks are stored ({@link SimulationStep#ticks}), so timers freeze
 *         with the simulation during {@code Time.pauseFor} and step with {@code ZenFlag.FRAME_STEP}.</li>
 *     <li>A timer never fires in the tick it was added: the earliest is the next tick.</li>
 *     <li>Adding a name that exists already replaces the old timer, which never fires again.</li>
 *     <li>Inside its own action a timer may {@link Timer#restart()} to run again (a one-shot that
 *         re-arms itself is the "repeat with varying delay" idiom), add or cancel other timers, or
 *         despawn the entity. Once a one-shot's action has returned without a restart, the handle
 *         is dead and {@code restart} throws.</li>
 *     <li>Per-entity state the actions share (a health counter, a phase, etc...) belongs in a
 *         component, not in the lambda closure.</li>
 * </ul>
 */
public class Timers implements Component {

    public static final ComponentType<Timers> TYPE = ComponentType.register(Timers.class);
    public static Timers  get(Entity e) { return TYPE.get(e); }
    public static boolean has(Entity e) { return TYPE.has(e); }

    private static final String TAG = Timers.class.getSimpleName();

    /** One countdown. Handles are valid until the timer fires (one-shot) or is cancelled or replaced. */
    public final class Timer {

        public final String name;

        private final Consumer<Timer> action;
        private final boolean repeating;

        private int duration;   // ticks that a (re)start counts down from
        private int remaining;
        private boolean canceled;
        private boolean removed;

        private Timer(String name, int duration, boolean repeating, Consumer<Timer> action) {
            this.name = name;
            this.action = action;
            this.repeating = repeating;
            this.duration = duration;
            this.remaining = duration;
            this.canceled = false;
            this.removed = false;
        }

        /** The entity these timers are attached to; null before {@link #onAdd}. */
        public Entity entity() { return entity; }

        /** The component this timer lives in, for adding siblings from an action. */
        public Timers owner() { return Timers.this; }

        public boolean isRepeating()      { return repeating; }
        public boolean isActive()         { return !canceled && !removed; }
        public int     remainingTicks()   { return remaining; }
        public float   remainingSeconds() { return SimulationStep.seconds(remaining); }
        public float   durationSeconds()  { return SimulationStep.seconds(duration); }

        /** Counts down again from the original duration. Inside the action this re-arms a one-shot. */
        public void restart() {
            if (removed) {
                throw new IllegalStateException("timer '" + name + "' is gone, add a new one with after/every");
            }
            canceled = false;
            remaining = duration;
            Log.info(ZenFlag.LOG_TIMERS, TAG, "%s '%s' restarted (%.2fs)", entity, name, durationSeconds());
        }

        /** Counts down again from a new duration, which also becomes the repeat interval. */
        public void restart(float seconds) {
            duration = SimulationStep.ticks(seconds);
            restart();
        }

        /** Stops this timer; it is removed at the end of the current (or next) tick. */
        public void cancel() {
            if (canceled || removed) return;
            canceled = true;
            Log.info(ZenFlag.LOG_TIMERS, TAG, "%s '%s' cancelled", entity, name);
            if (!updating) {
                sweep();
            }
        }

        @Override
        public String toString() {
            return "Timer(" + name + ", " + remaining + "/" + duration + (repeating ? ", every " : "") + ")";
        }
    }

    private final Array<Timer> timers;
    private Entity entity;
    private boolean updating;

    public Timers() {
        this.timers = new Array<>(4);
        this.entity = null;
        this.updating = false;
    }

    @Override
    public void onAdd(Entity e) {
        entity = e;
    }

    @Override
    public void onRemove(Entity e) {
        for (int i = 0; i < timers.size; i++) {
            timers.get(i).canceled = true;
        }
        if (!updating) {
            sweep();
        }
        entity = null;
    }

    /** Fires {@code action} once after {@code seconds}, replacing any timer with the same name. */
    public Timer after(String name, float seconds, Consumer<Timer> action) {
        return add(name, seconds, /*repeating*/ false, action);
    }

    /** Fires {@code action} every {@code seconds} until canceled, replacing any timer with the same name. */
    public Timer every(String name, float seconds, Consumer<Timer> action) {
        return add(name, seconds, /*repeating*/ true, action);
    }

    private Timer add(String name, float seconds, boolean repeating, Consumer<Timer> action) {
        if (name   == null) throw new IllegalArgumentException("timer name is null");
        if (action == null) throw new IllegalArgumentException("timer '" + name + "' has no action");
        cancel(name);
        var timer = new Timer(name, SimulationStep.ticks(seconds), repeating, action);
        timers.add(timer);
        Log.info(ZenFlag.LOG_TIMERS, TAG, "%s '%s' %s %.2fs", entity, name, repeating ? "every" : "after", seconds);
        return timer;
    }

    /** The active timer named {@code name}, or null. */
    public Timer get(String name) {
        for (int i = 0; i < timers.size; i++) {
            var timer = timers.get(i);
            if (!timer.canceled && timer.name.equals(name)) {
                return timer;
            }
        }
        return null;
    }

    public boolean has(String name) {
        return (get(name) != null);
    }

    /** Cancels the timer named {@code name}; false if there was none. */
    public boolean cancel(String name) {
        var timer = get(name);
        if (timer == null) {
            return false;
        }
        timer.cancel();
        return true;
    }

    /** Cancels every timer. */
    public void clear() {
        for (int i = 0; i < timers.size; i++) {
            timers.get(i).cancel();
        }
    }

    /** Active timers, including ones canceled this tick but not yet swept. */
    public int size() {
        return timers.size;
    }

    /** Active timers in add order, for a debug window. Read only. */
    public Array<Timer> timers() {
        return timers;
    }

    // TODO: should this code be hoisted into TimerSystem or just called there? general component/system boundary design question...
    /**
     * One simulation tick: counts every timer down and runs the ones that reach zero. Called by
     * {@code TimerSystem}; timers added by an action wait for the next tick, and timers canceled
     * (or the component removed) by an action are honored before the next one runs.
     */
    public void update() {
        updating = true;
        int count = timers.size;
        for (int i = 0; i < count && (entity != null); i++) {
            var timer = timers.get(i);
            if (timer.canceled) continue;
            if (--timer.remaining > 0) continue;

            // Settle the timer's own fate before the action, so a restart() inside it wins
            if (timer.repeating) timer.remaining = timer.duration;
            else                 timer.canceled  = true;

            Log.info(ZenFlag.LOG_TIMERS, TAG, "%s '%s' fired", entity, timer.name);
            timer.action.accept(timer);
        }
        updating = false;
        sweep();
    }

    private void sweep() {
        for (int i = timers.size - 1; i >= 0; i--) {
            var timer = timers.get(i);
            if (timer.canceled) {
                timer.removed = true;
                timers.removeIndex(i);
            }
        }
    }
}
