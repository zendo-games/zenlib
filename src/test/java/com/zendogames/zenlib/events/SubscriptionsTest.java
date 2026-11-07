package com.zendogames.zenlib.events;

import com.badlogic.gdx.utils.Array;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Pins {@code Subscriptions}: one owner's listeners across several signals end together with {@code clear}. */
public class SubscriptionsTest {

    record Ping(int n) {}
    record Pong(int n) {}

    static final Signal<Ping> PING = new Signal<>("SubscriptionsTest.Ping");
    static final Signal<Pong> PONG = new Signal<>("SubscriptionsTest.Pong");

    Array<String> log;

    @Before
    public void reset() {
        Signals.init();
        log = new Array<>();
    }

    @Test
    public void onRegistersAndClearRemovesEverything_otherOwnersAreUntouched() {
        var screen = new Subscriptions()
                .on(PING, p -> log.add("screen ping " + p.n()))
                .on(PONG, p -> log.add("screen pong " + p.n()));
        var other = new Subscriptions()
                .on(PING, p -> log.add("other ping " + p.n()));
        assertEquals(2, screen.size());
        assertEquals(2, PING.size());
        assertEquals(1, PONG.size());

        PING.dispatch(new Ping(1));
        PONG.dispatch(new Pong(2));
        assertEquals(Array.with("screen ping 1", "other ping 1", "screen pong 2"), log);

        screen.clear();
        assertEquals(0, screen.size());
        assertEquals(1, PING.size());
        assertEquals(0, PONG.size());

        log.clear();
        PING.dispatch(new Ping(3));
        PONG.dispatch(new Pong(4));
        assertEquals(Array.with("other ping 3"), log);

        other.clear();
        assertEquals(0, PING.size());
    }

    @Test
    public void aBroaderListenerTypeIsAccepted() {
        var subs = new Subscriptions().on(PING, (Object o) -> log.add("any " + o));
        PING.dispatch(new Ping(1));
        assertEquals(Array.with("any Ping[n=1]"), log);
        subs.clear();
        assertEquals(0, PING.size());
    }

    @Test
    public void clearTwiceIsHarmless() {
        var subs = new Subscriptions().on(PING, p -> {});
        subs.clear();
        subs.clear();
        assertEquals(0, PING.size());
    }
}
