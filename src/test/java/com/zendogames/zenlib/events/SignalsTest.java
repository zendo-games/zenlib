package com.zendogames.zenlib.events;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins the registry: every signal is known from construction, {@code init} clears listeners and
 * counters but keeps the signals, and {@code record} keeps a bounded history across all signals.
 * {@code LOG_EVENTS} stays off (tests must not call {@code Flag.set}).
 */
public class SignalsTest {

    record Ping(int n) {}

    static final Signal<Ping> A = new Signal<>("SignalsTest.A");
    static final Signal<Ping> B = new Signal<>("SignalsTest.B");

    @Before
    public void reset() {
        Signals.init();
    }

    @Test
    public void everySignalIsRegisteredFromConstructionAndSurvivesInit() {
        var all = Signals.all();
        assertTrue(all.contains(A, true));
        assertTrue(all.contains(B, true));
        assertTrue(all.indexOf(A, true) < all.indexOf(B, true));
    }

    @Test
    public void initClearsListenersCountersAndHistoryOfEverySignal() {
        A.register(p -> {});
        B.register(p -> {});
        Signals.record(8);
        A.dispatch(new Ping(1));
        assertEquals(1, A.size());
        assertEquals(1, A.dispatches());
        assertEquals(1, Signals.dispatches());
        assertEquals(1, Signals.history.size());

        Signals.init();
        assertEquals(0, A.size());
        assertEquals(0, B.size());
        assertEquals(0, A.dispatches());
        assertNull(A.last());
        assertEquals(0, Signals.dispatches());
        assertTrue(Signals.history.isEmpty());
        assertFalse(Signals.isRecording());
    }

    @Test
    public void recordKeepsTheLastDispatchesAcrossSignals_offByDefault() {
        A.dispatch(new Ping(0));
        assertFalse(Signals.isRecording());
        assertTrue(Signals.history.isEmpty());

        Signals.record(3);
        A.dispatch(new Ping(1));
        B.dispatch(new Ping(2));
        A.dispatch(new Ping(3));
        B.dispatch(new Ping(4));
        assertEquals(3, Signals.history.size());
        assertEquals("#3 SignalsTest.B: Ping[n=2]", Signals.history.peekFirst());
        assertEquals("#5 SignalsTest.B: Ping[n=4]", Signals.history.peekLast());

        Signals.record(1);
        assertEquals(1, Signals.history.size());
        assertEquals("#5 SignalsTest.B: Ping[n=4]", Signals.history.peekFirst());

        Signals.record(0);
        assertFalse(Signals.isRecording());
        assertTrue(Signals.history.isEmpty());
    }

    @Test
    public void theSequenceCountsEveryDispatchWithOrWithoutListeners() {
        A.dispatch(new Ping(1));
        B.register(p -> {});
        B.dispatch(new Ping(2));
        assertEquals(2, Signals.dispatches());
        assertEquals(1, A.dispatches());
        assertEquals(1, B.dispatches());
    }
}
