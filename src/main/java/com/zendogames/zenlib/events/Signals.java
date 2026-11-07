package com.zendogames.zenlib.events;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ZenFlag;

import java.util.ArrayDeque;

import static com.zendogames.zenlib.ZenFlag.LOG_EVENTS;

/**
 * The registry of every {@link Signal} in the process, the static service behind them: {@code Zen.init}
 * calls {@link #init()} so a recreated game starts with no listeners from the previous one (the signals
 * themselves are static finals and stay), {@code LOG_EVENTS} logs one line per dispatch and
 * {@link #record} keeps the last dispatches as text for {@code DebugViews.events()}. Nothing here
 * formats or allocates unless the flag is on or a recording is running.
 */
public final class Signals {

    private Signals() {}

    private static final String TAG = "Signals";

    private static final Array<Signal<?>> all = new Array<>();

    /** The last {@link #record}ed dispatches as {@code "#sequence name: event"}, oldest first; empty unless recording. */
    public static final ArrayDeque<String> history = new ArrayDeque<>();
    private static int historyCapacity;
    private static long sequence;

    /**
     * Called by {@code Zen.init}: every signal loses its listeners, the counters and history are cleared and
     * recording stops. The signals stay registered: they are static finals, nobody would construct them again.
     */
    public static void init() {
        for (var signal : all) {
            signal.reset();
        }
        history.clear();
        historyCapacity = 0;
        sequence = 0;
    }

    static void add(Signal<?> signal) {
        all.add(signal);
    }

    /** Every signal constructed in this process, in construction order. */
    public static Array<Signal<?>> all() {
        return all;
    }

    /** Dispatches across every signal since the last {@link #init()}. */
    public static long dispatches() {
        return sequence;
    }

    public static boolean isRecording() {
        return historyCapacity > 0;
    }

    /** Keeps the last {@code capacity} dispatch lines in {@link #history}, for a debug view; 0 stops recording and clears it. */
    public static void record(int capacity) {
        historyCapacity = Math.max(0, capacity);
        while (history.size() > historyCapacity) {
            history.pollFirst();
        }
    }

    /** Counts, logs on {@code LOG_EVENTS} and records when {@link #isRecording}; formats nothing when neither is on. */
    static void dispatched(Signal<?> signal, Object event) {
        sequence++;
        if (LOG_EVENTS.off() && historyCapacity == 0) return;
        Log.info(ZenFlag.LOG_EVENTS, TAG, "%s: %s", signal.name, event);
        if (historyCapacity > 0) {
            if (history.size() == historyCapacity) history.pollFirst();
            history.addLast("#%d %s: %s".formatted(sequence, signal.name, event));
        }
    }
}
