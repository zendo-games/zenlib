package com.zendogames.zenlib.sim;

import com.badlogic.gdx.utils.Array;

public class Scheduler {

    public static class Timer {
        private final Runnable action;
        private final float interval;  // zero for a one-shot timer

        private float remaining;
        private boolean cancelled = false;

        private Timer(float seconds, boolean repeats, Runnable action) {
            this.action = action;
            this.interval = repeats ? seconds : 0;
            this.remaining = seconds;
        }

        public void cancel() {
            cancelled = true;
        }
    }

    private final Array<Timer> timers = new Array<>();

    /** Runs {@code action} once, after {@code seconds} have passed. */
    public Timer after(float seconds, Runnable action) {
        var timer = new Timer(seconds, false, action);
        timers.add(timer);
        return timer;
    }

    /** Runs {@code action} repeatedly, every {@code seconds}. */
    public Timer every(float seconds, Runnable action) {
        var timer = new Timer(seconds, true, action);
        timers.add(timer);
        return timer;
    }

    public void update(float dt) {
        // Iterating backwards lets an action add new timers without disturbing the loop
        for (int i = timers.size - 1; i >= 0; i--) {
            var timer = timers.get(i);
            if (!timer.cancelled) {
                timer.remaining -= dt;
                if (timer.remaining <= 0) {
                    timer.action.run();
                    if (timer.interval > 0) timer.remaining += timer.interval;
                    else                    timer.cancelled = true;
                }
            }
            if (timer.cancelled) {
                timers.removeIndex(i);
            }
        }
    }

    /** Cancels every timer. They are removed by the next {@link #update}, so an action can safely call this. */
    public void clear() {
        for (int i = 0; i < timers.size; i++) {
            timers.get(i).cancelled = true;
        }
    }
}
