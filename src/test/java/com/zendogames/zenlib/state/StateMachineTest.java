package com.zendogames.zenlib.state;

import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pins the timing rules of {@code StateMachine} against a recording context: start on the first
 * update, one returned change per update, requests applied at the top of the next update and
 * refused by {@code interruptible}, {@code force} for setup code, hook order and counters.
 */
public class StateMachineTest {

    /** Each state writes "name:hook" to the log; TIMED leaves after two ticks, STUBBORN refuses every request. */
    enum S implements State<Array<String>> {
        A, B, C,
        TIMED {
            @Override public State<Array<String>> update(StateMachine<Array<String>> m, Array<String> log, float dt) {
                log.add(name() + ":update" + m.ticks());
                return (m.ticks() >= 2) ? A : this;
            }
        },
        STUBBORN {
            @Override public boolean interruptible(StateMachine<Array<String>> m, Array<String> log, State<Array<String>> by) {
                log.add(name() + ":refuse:" + by);
                return false;
            }
        },
        NULLER {
            @Override public State<Array<String>> update(StateMachine<Array<String>> m, Array<String> log, float dt) {
                return null;
            }
        },
        FORCER {
            @Override public void enter(StateMachine<Array<String>> m, Array<String> log) {
                m.force(log, A); // illegal: a change from inside a hook
            }
        };

        @Override public void enter(StateMachine<Array<String>> m, Array<String> log) { log.add(name() + ":enter(from " + m.previous() + ")"); }
        @Override public void exit(StateMachine<Array<String>> m, Array<String> log)  { log.add(name() + ":exit"); }
        @Override public State<Array<String>> update(StateMachine<Array<String>> m, Array<String> log, float dt) {
            log.add(name() + ":update" + m.ticks());
            return this;
        }
    }

    private Array<String> log;

    @Before
    public void setUp() {
        log = new Array<>();
    }

    private static String joined(Array<String> log) {
        return String.join(" ", log);
    }

    // ------------------------------------------------------------------------
    // Start and stay
    // ------------------------------------------------------------------------

    @Test
    public void constructorDoesNotEnter_firstUpdateDoes() {
        var m = new StateMachine<>(S.A);
        assertFalse(m.isStarted());
        assertEquals(S.A, m.current());
        assertNull(m.previous());
        assertEquals(0, log.size);

        m.update(log, 1f);
        assertTrue(m.isStarted());
        assertEquals("A:enter(from null) A:update1", joined(log));
    }

    @Test
    public void stayingCountsTicksAndSeconds() {
        var m = new StateMachine<>(S.A);
        m.update(log, 0.5f);
        m.update(log, 0.25f);
        m.update(log, 0.25f);
        assertEquals(3, m.ticks());
        assertEquals(1f, m.seconds(), 1e-6f);
        assertEquals(S.A, m.current());
        assertNull(m.previous());
    }

    @Test
    public void nullConstructorArgumentThrows() {
        assertThrows(IllegalArgumentException.class, () -> new StateMachine<>(null));
    }

    // ------------------------------------------------------------------------
    // Returned changes
    // ------------------------------------------------------------------------

    @Test
    public void returnedStateChangesInsideUpdate_exitThenEnter_countersReset() {
        var m = new StateMachine<>(S.TIMED);
        m.update(log, 1f);
        assertEquals(S.TIMED, m.current());
        assertEquals(1, m.ticks());

        m.update(log, 1f);
        assertEquals(S.A, m.current());
        assertEquals(S.TIMED, m.previous());
        assertEquals(0, m.ticks());
        assertEquals(0f, m.seconds(), 0f);
        assertEquals("TIMED:enter(from null) TIMED:update1 TIMED:update2 TIMED:exit A:enter(from TIMED)", joined(log));

        // The new state gets its first update on the next tick, not the same one
        m.update(log, 1f);
        assertEquals("A:update1", log.get(log.size - 1));
    }

    @Test
    public void returningNullThrows() {
        var m = new StateMachine<>(S.NULLER);
        assertThrows(IllegalStateException.class, () -> m.update(log, 1f));
    }

    // ------------------------------------------------------------------------
    // Requests
    // ------------------------------------------------------------------------

    @Test
    public void requestIsAppliedAtTopOfNextUpdate_notImmediately() {
        var m = new StateMachine<>(S.A);
        m.update(log, 1f);
        log.clear();

        m.request(S.B);
        assertEquals(S.A, m.current());
        assertEquals(S.B, m.pending());
        assertEquals(0, log.size);

        m.update(log, 1f);
        assertEquals(S.B, m.current());
        assertNull(m.pending());
        assertEquals(1, m.ticks()); // B ran this update
        assertEquals("A:exit B:enter(from A) B:update1", joined(log));
    }

    @Test
    public void lastRequestWins_requestingCurrentCancels() {
        var m = new StateMachine<>(S.A);
        m.request(S.B);
        m.request(S.C);
        assertEquals(S.C, m.pending());

        m.request(S.A);
        assertNull(m.pending());

        m.update(log, 1f);
        assertEquals(S.A, m.current());
    }

    @Test
    public void requestRefusedByInterruptible_isDropped() {
        var m = new StateMachine<>(S.STUBBORN);
        m.update(log, 1f);
        log.clear();

        m.request(S.A);
        m.update(log, 1f);
        assertEquals(S.STUBBORN, m.current());
        assertNull(m.pending());
        assertEquals("STUBBORN:refuse:A STUBBORN:update2", joined(log));

        // Refused means gone: the next update does not ask again
        log.clear();
        m.update(log, 1f);
        assertEquals("STUBBORN:update3", joined(log));
    }

    @Test
    public void requestBeforeStart_startsThenChanges() {
        var m = new StateMachine<>(S.A);
        m.request(S.B);
        m.update(log, 1f);
        assertEquals("A:enter(from null) A:exit B:enter(from A) B:update1", joined(log));
    }

    @Test
    public void requestThenReturnedChange_bothApplyInOneUpdate() {
        var m = new StateMachine<>(S.A);
        m.update(log, 1f);
        log.clear();

        m.request(S.TIMED);
        m.update(log, 1f); // -> TIMED, update1
        m.update(log, 1f); // TIMED update2 -> A
        assertEquals(S.A, m.current());
        assertEquals("A:exit TIMED:enter(from A) TIMED:update1 TIMED:update2 TIMED:exit A:enter(from TIMED)", joined(log));
    }

    @Test
    public void nullRequestThrows() {
        var m = new StateMachine<>(S.A);
        assertThrows(IllegalArgumentException.class, () -> m.request(null));
    }

    // ------------------------------------------------------------------------
    // Force
    // ------------------------------------------------------------------------

    @Test
    public void forceBeforeStart_runsInitialEnterThenChanges() {
        var m = new StateMachine<>(S.A);
        m.force(log, S.B);
        assertTrue(m.isStarted());
        assertEquals(S.B, m.current());
        assertEquals(S.A, m.previous());
        assertEquals("A:enter(from null) A:exit B:enter(from A)", joined(log));
    }

    @Test
    public void forceIgnoresInterruptible_andDropsPending() {
        var m = new StateMachine<>(S.STUBBORN);
        m.update(log, 1f);
        m.request(S.C);
        log.clear();

        m.force(log, S.A);
        assertEquals(S.A, m.current());
        assertNull(m.pending());
        assertEquals("STUBBORN:exit A:enter(from STUBBORN)", joined(log));
    }

    @Test
    public void forceToCurrentIsNoOp() {
        var m = new StateMachine<>(S.A);
        m.update(log, 1f);
        log.clear();

        m.force(log, S.A);
        assertEquals(0, log.size);
        assertEquals(1, m.ticks());
    }

    @Test
    public void changeFromInsideHookThrows() {
        var m = new StateMachine<>(S.FORCER);
        assertThrows(IllegalStateException.class, () -> m.update(log, 1f));
    }

    // ------------------------------------------------------------------------
    // Hooks see the right machine state
    // ------------------------------------------------------------------------

    @Test
    public void exitSeesOldCurrent_enterSeesNewCurrentAndPrevious() {
        var seen = new Array<String>();
        var observer = new State<Array<String>>() {
            @Override public State<Array<String>> update(StateMachine<Array<String>> m, Array<String> ctx, float dt) { return this; }
            @Override public void enter(StateMachine<Array<String>> m, Array<String> ctx) { seen.add("enter current=" + (m.current() == this) + " previous=" + m.previous()); }
        };
        var leaver = new State<Array<String>>() {
            @Override public State<Array<String>> update(StateMachine<Array<String>> m, Array<String> ctx, float dt) { return observer; }
            @Override public void exit(StateMachine<Array<String>> m, Array<String> ctx) { seen.add("exit current=" + (m.current() == this) + " ticks=" + m.ticks()); }
        };
        var m = new StateMachine<>(leaver);
        m.update(log, 1f);
        assertEquals("exit current=true ticks=1", seen.get(0));
        assertEquals("enter current=true previous=" + leaver, seen.get(1));
    }

    // ------------------------------------------------------------------------
    // Optional transition history (proposal section 3.6): delete this test if record()/history are not applied
    // ------------------------------------------------------------------------

    @Test
    public void recordKeepsTheLastTransitionsAndRefusals_offByDefault() {
        var m = new StateMachine<>(S.STUBBORN);
        m.update(log, 1f);
        m.request(S.A);
        m.update(log, 1f);
        assertFalse(m.isRecording());
        assertTrue(m.history.isEmpty());

        m.record(2);
        m.force(log, S.TIMED);     // TIMED -> A after two updates
        m.update(log, 1f);
        m.update(log, 1f);
        m.request(S.B);
        m.update(log, 1f);         // applied at the top, before A's tick is counted
        assertEquals(2, m.history.size());
        assertEquals("TIMED -> A after 2 ticks", m.history.peekFirst());
        assertEquals("A -> B after 0 ticks", m.history.peekLast());

        m.record(0);
        assertFalse(m.isRecording());
        assertTrue(m.history.isEmpty());
    }
}
