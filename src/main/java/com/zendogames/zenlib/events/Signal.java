package com.zendogames.zenlib.events;

import com.badlogic.gdx.utils.SnapshotArray;

import java.util.function.Consumer;

/**
 * One event type's subscriber list: {@link #register} a {@code Consumer} of the event, {@link #dispatch}
 * an event to every listener in registration order, on the caller's stack, before {@code dispatch}
 * returns. The convention is one {@code public static final Signal<X> SIGNAL} on the event type with
 * static helpers that build and dispatch the event, so a call site reads {@code CombatEvent.struck(strike)}
 * and never sees the signal:
 * <pre>{@code
 * public sealed interface CombatEvent {
 *     Signal<CombatEvent> SIGNAL = new Signal<>("CombatEvent");
 *     record Struck(Strike strike) implements CombatEvent {}
 *     record Died(Entity entity, Strike strike) implements CombatEvent {}
 *     static void struck(Strike s) { SIGNAL.dispatch(new Struck(s)); }
 * }
 * // a listener switches on the sealed subtypes; the compiler checks the switch is complete
 * subscriptions.on(CombatEvent.SIGNAL, e -> { switch (e) { case Struck s -> ...; case Died d -> ...; } });
 * }</pre>
 * <ul>
 *   <li><b>Granularity is the hierarchy's.</b> A signal delivers exactly the type it is declared for; a
 *       listener for a family of events subscribes to the family's signal and switches, a listener for one
 *       kind subscribes to that kind's own signal if the event type declares one. There is no class walk.</li>
 *   <li><b>Identity.</b> {@code register} returns the listener it was given so the owner can keep it for
 *       {@link #remove}; a method reference evaluates to a new object each time, so {@code remove(this::onHit)}
 *       removes nothing. {@link Subscriptions} keeps the pairs for an owner with a lifecycle (a screen).</li>
 *   <li><b>During a dispatch</b> a listener may register, remove (itself included) or dispatch again: the list is
 *       a {@code SnapshotArray}. A listener removed mid-dispatch still receives the event in flight, one added
 *       does not. A nested dispatch is delivered immediately, before the outer one continues.</li>
 *   <li><b>Every dispatch allocates the event</b> (a record, usually) and nothing else. Listeners may keep it.
 *       An exception in a listener propagates to the dispatcher and stops the delivery.</li>
 *   <li><b>Inside an ECS system</b> the usual rule holds for whatever a listener does: despawns and membership
 *       changes are deferred until the system returns, storage changes are immediate.</li>
 * </ul>
 * Every signal is known to {@link Signals} from construction, which is what {@code Zen.init} clears, what
 * {@code ZenFlag.LOG_EVENTS} logs and what {@code DebugViews.events()} lists, so signals are created as static
 * finals (a game's event types, the library's) and never per entity or per frame.
 *
 * @param <T> the event type delivered
 */
public final class Signal<T> {

    /** For logs and the debug view, usually the event type's simple name. */
    public final String name;

    private final SnapshotArray<Consumer<? super T>> listeners = new SnapshotArray<>(false, 8);

    private int dispatches;
    private T last;

    public Signal(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("signal name is blank");
        this.name = name;
        Signals.add(this);
    }

    /** Adds {@code listener} unless the same object is already registered; returns it for a later {@link #remove}. */
    public <L extends Consumer<? super T>> L register(L listener) {
        if (listener == null) throw new IllegalArgumentException(name + ": listener is null");
        if (!listeners.contains(listener, true)) {
            listeners.add(listener);
        }
        return listener;
    }

    /** Removes by identity; unknown listeners are ignored. */
    public void remove(Consumer<? super T> listener) {
        listeners.removeValue(listener, true);
    }

    public void removeAll() {
        listeners.clear();
    }

    /** {@link Signals#init()}: listeners and counters gone, the signal itself stays registered. */
    void reset() {
        listeners.clear();
        dispatches = 0;
        last = null;
    }

    /** Registered listeners. */
    public int size() {
        return listeners.size;
    }

    /** Dispatches since the last {@link Signals#init()}. */
    public int dispatches() {
        return dispatches;
    }

    /** The most recent event dispatched, null before the first. */
    public T last() {
        return last;
    }

    /** Delivers {@code event} to every listener registered now, in registration order, then returns. */
    public void dispatch(T event) {
        if (event == null) throw new IllegalArgumentException(name + ": event is null");
        dispatches++;
        last = event;
        Signals.dispatched(this, event);

        Object[] items = listeners.begin(); // the backing array is Object[], never cast it as a whole
        for (int i = 0, n = listeners.size; i < n; i++) {
            @SuppressWarnings("unchecked")
            var listener = (Consumer<? super T>) items[i];
            listener.accept(event);
        }
        listeners.end();
    }

    @Override
    public String toString() {
        return "Signal{%s, %d listeners, %d dispatched}".formatted(name, listeners.size, dispatches);
    }
}
