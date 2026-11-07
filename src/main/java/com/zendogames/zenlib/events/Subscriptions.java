package com.zendogames.zenlib.events;

import com.badlogic.gdx.utils.Array;

import java.util.function.Consumer;

/**
 * The subscriptions of one owner with a lifecycle, so they end together: a screen subscribes in its
 * constructor with {@link #on} and {@link #clear}s in {@code dispose()} ({@code ZenScreen} owns one as
 * {@code subscriptions} and does that). Two screens are alive during a transition, so each one's
 * listeners stop with that screen and not with the other.
 * <pre>{@code
 * subscriptions.on(CombatEvent.SIGNAL, this::onCombat)
 *              .on(ShakeEvent.SIGNAL, e -> shake.addTrauma(e.trauma()));
 * }</pre>
 */
public final class Subscriptions {

    private final Array<Runnable> removers = new Array<>();

    /** Registers {@code listener} on {@code signal} until {@link #clear}. */
    public <T> Subscriptions on(Signal<T> signal, Consumer<? super T> listener) {
        signal.register(listener);
        removers.add(() -> signal.remove(listener));
        return this;
    }

    /** Removes every listener registered through this object. */
    public void clear() {
        for (var remover : removers) {
            remover.run();
        }
        removers.clear();
    }

    public int size() {
        return removers.size;
    }
}
