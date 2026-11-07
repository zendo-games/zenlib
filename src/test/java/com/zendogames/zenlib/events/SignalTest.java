package com.zendogames.zenlib.events;

import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 * Pins the delivery contract of {@code Signal}: registration order, identity-based register and
 * remove, snapshot semantics during a dispatch, nested dispatch, the event-hierarchy idiom, and
 * the counters the debug view reads.
 */
public class SignalTest {

    sealed interface Combat {}
    record Struck(int damage) implements Combat {}
    record Died(String who) implements Combat {}

    static final Signal<Combat> COMBAT = new Signal<>("SignalTest.Combat");
    static final Signal<Struck> STRUCK = new Signal<>("SignalTest.Struck");

    Array<String> log;

    @Before
    public void reset() {
        Signals.init();
        log = new Array<>();
    }

    @Test
    public void dispatchReachesEveryListenerInRegistrationOrder() {
        COMBAT.register(e -> log.add("a:" + e));
        COMBAT.register(e -> log.add("b:" + e));
        COMBAT.dispatch(new Struck(3));
        assertEquals(Array.with("a:Struck[damage=3]", "b:Struck[damage=3]"), log);
    }

    @Test
    public void registeringTheSameObjectTwiceListensOnce() {
        Consumer<Combat> once = e -> log.add("once");
        assertSame(once, COMBAT.register(once));
        COMBAT.register(once);
        assertEquals(1, COMBAT.size());
        COMBAT.dispatch(new Died("x"));
        assertEquals(1, log.size);
    }

    @Test
    public void removeIsByIdentityNotEquals() {
        Consumer<Combat> a = new Consumer<>() {
            @Override public void accept(Combat e) { log.add("a"); }
            @Override public boolean equals(Object o) { return true; } // would match anything by equals
            @Override public int hashCode() { return 0; }
        };
        Consumer<Combat> b = e -> log.add("b");
        COMBAT.register(a);
        COMBAT.register(b);
        COMBAT.remove(e -> {}); // a different object, equal to a by a's equals: nothing removed
        assertEquals(2, COMBAT.size());
        COMBAT.remove(a);
        assertEquals(1, COMBAT.size());
        COMBAT.dispatch(new Struck(1));
        assertEquals(Array.with("b"), log);
    }

    @Test
    public void aMethodReferenceIsANewObjectEachTimeSoKeepTheReturnedOne() {
        var kept = COMBAT.register(this::record);
        COMBAT.remove(this::record); // a fresh object: removes nothing
        assertEquals(1, COMBAT.size());
        COMBAT.remove(kept);
        assertEquals(0, COMBAT.size());
    }

    @Test
    public void aSupertypeListenerSwitchesOnTheSealedKinds() {
        COMBAT.register(e -> {
            switch (e) {
                case Struck s -> log.add("struck " + s.damage());
                case Died d   -> log.add("died " + d.who());
            }
        });
        COMBAT.dispatch(new Struck(2));
        COMBAT.dispatch(new Died("hero"));
        assertEquals(Array.with("struck 2", "died hero"), log);
    }

    @Test
    public void aListenerOfABroaderTypeMayRegisterOnANarrowerSignal() {
        Consumer<Object> any = e -> log.add("any:" + e.getClass().getSimpleName());
        STRUCK.register(any);
        STRUCK.dispatch(new Struck(1));
        assertEquals(Array.with("any:Struck"), log);
    }

    @Test
    public void aListenerRemovedDuringDispatchStillGetsTheEventInFlight_oneAddedDoesNot() {
        Consumer<Combat> late = e -> log.add("late");
        var self = new Consumer<Combat>() {
            @Override public void accept(Combat e) { log.add("self"); COMBAT.remove(this); COMBAT.register(late); }
        };
        COMBAT.register(self);
        COMBAT.register(e -> log.add("after"));

        COMBAT.dispatch(new Struck(1));
        assertEquals(Array.with("self", "after"), log);
        assertEquals(2, COMBAT.size()); // after + late

        log.clear();
        COMBAT.dispatch(new Struck(2));
        assertEquals(Array.with("after", "late"), log);
    }

    @Test
    public void aNestedDispatchIsDeliveredBeforeTheOuterOneContinues() {
        COMBAT.register(e -> {
            log.add("first:" + e);
            if (e instanceof Struck) COMBAT.dispatch(new Died("nested"));
        });
        COMBAT.register(e -> log.add("second:" + e));
        COMBAT.dispatch(new Struck(1));
        assertEquals(Array.with(
                "first:Struck[damage=1]",
                "first:Died[who=nested]", "second:Died[who=nested]",
                "second:Struck[damage=1]"), log);
    }

    @Test
    public void aListenerExceptionStopsTheDeliveryAndPropagates() {
        COMBAT.register(e -> { throw new IllegalStateException("boom"); });
        COMBAT.register(e -> log.add("never"));
        var thrown = assertThrows(IllegalStateException.class, () -> COMBAT.dispatch(new Struck(1)));
        assertEquals("boom", thrown.getMessage());
        assertEquals(0, log.size);
    }

    @Test
    public void countersAndLastEventAreKeptForTheDebugView() {
        assertEquals(0, COMBAT.dispatches());
        assertNull(COMBAT.last());
        var died = new Died("x");
        COMBAT.dispatch(new Struck(1));
        COMBAT.dispatch(died);
        assertEquals(2, COMBAT.dispatches());
        assertSame(died, COMBAT.last());
        assertEquals("Signal{SignalTest.Combat, 0 listeners, 2 dispatched}", COMBAT.toString());
    }

    @Test
    public void nullsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> COMBAT.dispatch(null));
        assertThrows(IllegalArgumentException.class, () -> COMBAT.register(null));
        assertThrows(IllegalArgumentException.class, () -> new Signal<>(" "));
    }

    private void record(Combat e) {
        log.add(String.valueOf(e));
    }
}
