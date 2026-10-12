package com.zendogames.zenlib.state;

import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ZenFlag;

import java.util.ArrayDeque;

/**
 * The per-owner half of a state machine: the current {@link State}, how long it has been
 * current, the one before it and at most one pending change request. Stateless {@code State}s
 * carry the logic, so this class never needs subclassing; a game wraps it in a component
 * ({@code ecs.components.Behavior}), a screen or other usages hold on directly.
 * <p>
 * <b>Timing:</b> {@link #update} runs the initial state's {@code enter} on the first call,
 * then each call: applies a pending {@link #request} if the current state is {@code interruptible},
 * counts the tick, runs {@code current.update} and applies the state it returns. So a change
 * happens at most twice per update (one requested, one returned), {@code enter} and {@code exit}
 * run inside {@code update}, never from {@code request}, and a new state gets its first
 * {@code update} on the next tick. {@link #force} is the exception for setup and respawn code.
 * <p>
 * Transitions and refused requests are logged on {@code ZenFlag.LOG_STATE}.
 */
public class StateMachine<C> {
    
    private static final String TAG = StateMachine.class.getSimpleName();
    
    private State<C> current;
    private State<C> previous;
    private State<C> pending;
    
    private boolean started;
    private boolean changing;

    /** The last {@link #record}ed transitions and refusals as text, oldest first; empty unless recording. */
    public final ArrayDeque<String> history = new ArrayDeque<>();
    private int historyCapacity;

    /** Updates since {@link #current} was entered, counting the one in progress. */
    private int ticks;
    
    /** Seconds since {@link #current} was entered: the sum of {@code dt} passed to {@link #update}, so it follows the caller's clock. */
    private float elapsed;

    public StateMachine(State<C> initial) {
        if (initial == null) throw new IllegalArgumentException("initial state is null");
        this.current = initial;
    }
    
    public State<C> current()  { return current; }
    public State<C> previous() { return previous; }
    public State<C> pending()  { return pending; }

    public boolean is(State<C> state)    { return current == state; }
    public boolean isNot(State<C> state) { return current != state; }

    /** Whether the initial state's {@code enter} has run (first {@link #update} or {@link #force}). */
    public boolean isStarted() { return started; }

    public int   ticks()   { return ticks; }
    public float seconds() { return elapsed; }

    public boolean isRecording() { return historyCapacity > 0; }

    /** Keeps the last {@code capacity} transition and refusal lines in {@link #history}, for a debug view; 0 stops recording and clears it. */
    public void record(int capacity) {
        historyCapacity = Math.max(0, capacity);
        while (history.size() > historyCapacity) {
            history.pollFirst();
        }
    }

    /**
     * Asks to change to {@code to} at the top of the next {@link #update}, if the current state
     * is {@link State#interruptible} by it then. The last request before that update wins;
     * requesting the current state cancels a pending request instead.
     */
    public void request(State<C> to) {
        if (to == null) {
            throw new IllegalArgumentException("requested state is null");
        }
        pending = (to == current) ? null : to;
    }

    /**
     * Changes to {@code to} right now, ignoring {@code interruptible} and dropping any pending
     * request: setup, respawn, debug. Runs the hooks like a normal change (and the initial
     * {@code enter} first if the machine has not started). Re-entering the current state is a no-op.
     */
    public void force(C ctx, State<C> to) {
        if (to == null) {
            throw new IllegalArgumentException("forced state is null");
        }
        start(ctx);
        pending = null;
        if (to != current) {
            change(ctx, to);
        }
    }

    /** One tick: start if needed, apply a pending request, then run the current state and apply what it returns. */
    public void update(C ctx, float dt) {
       start(ctx);

       if (pending != null) {
           var to = pending;
           pending = null;
           if (current.interruptible(this, ctx, to)) {
               change(ctx, to);
           } else {
               trace(ctx, "%s refused %s", current, to);
           }
       }

       ticks++;
       elapsed += dt;

       var next = current.update(this, ctx, dt);
       if (next == null) {
           throw new IllegalStateException(current + ".update returned null; return this to stay");
       }
       if (next != current) {
           change(ctx, next);
       }
    }

    private void start(C ctx) {
        if (started) return;
        started = true;
        trace(ctx, "start %s", current);
        guarded(() -> current.enter(this, ctx), ctx);
    }

    private void change(C ctx, State<C> to) {
        trace(ctx, "%s -> %s after %d ticks", current, to, ticks);
        guarded(() -> {
            current.exit(this, ctx);
            previous = current;
            current  = to;
            ticks    = 0;
            elapsed  = 0;
            current.enter(this, ctx);
        }, ctx);
    }

    /** Runs the hooks with re-entry blocked: a {@link #force} from inside {@code enter/exit} throws instead. */
    private void guarded(Runnable hooks, C ctx) {
        if (changing) {
            throw new IllegalStateException(ctx + ": change from inside enter/exit of " + current + "; use request");
        }
        changing = true;
        try {
            hooks.run();
        } finally {
            changing = false;
        }
    }

    /** Logs on {@code LOG_STATE} and records when {@link #isRecording}; formats nothing when neither is on. */
    private void trace(C ctx, String what, Object... args) {
        if (ZenFlag.LOG_STATE.off() && historyCapacity == 0) return;
        var line = what.formatted(args);
        Log.info(ZenFlag.LOG_STATE, TAG, "%s: %s", ctx, line);
        if (historyCapacity > 0) {
            if (history.size() == historyCapacity) history.pollFirst();
            history.addLast(line);
        }
    }

    @Override
    public String toString() {
        return "%s{%s, %d ticks}".formatted(TAG, current, ticks);
    }
}
