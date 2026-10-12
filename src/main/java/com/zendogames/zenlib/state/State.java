package com.zendogames.zenlib.state;

/**
 * One state of a {@link StateMachine}: stateless logic run against a context {@code C}, usually
 * an enum constant with per-constant bodies, or a singleton class for a state with a lot of code.
 * Everything that varies per owner (which state is current, for how long, what came before) lives
 * on the machine, so one {@code State} instance serves every machine of that kind.
 * <pre>{@code
 * public enum HeroState implements State<Entity> {
 *     IDLE {
 *         @Override
 *         public State<Entity> update(StateMachine<Entity> machine, Entity entity, float dt) {
 *             return (Input.get(entity).moveX != 0) ? HeroState.RUN : this;
 *         }
 *     },
 *     RUN { ... };
 * }
 * }</pre>
 * {@link #update} returns the state to be in next; {@code this} stays. A change runs
 * {@link #exit} on the old state then {@link #enter} on the new one, inside the same
 * {@code StateMachine.update}. Code outside the state (a contact responder, a system)
 * asks for a change through {@link StateMachine#request}, which the current state can
 * refuse via {@link #interruptible}.
 */
public interface State<C> {

    /** Runs when this state becomes current; {@code machine.previous()} is the state left, null on start. */
    default void enter(StateMachine<C> machine, C ctx) {}

    /**
     * One tick in this state. Returns the state to be in next: {@code this} to stay, another
     * state to change (its {@code enter} runs before this call returns), never null.
     * {@code machine.ticks()} and {@code machine.seconds()} already include this update.
     */
    State<C> update(StateMachine<C> machine, C ctx, float dt);

    /** Runs when this state stops being current; {@code machine.current()} is still this state. */
    default void exit(StateMachine<C> machine, C ctx) {}

    /** Whether a {@link StateMachine#request} to change to {@code by} is honored; false for a state that must run its course. */
    default boolean interruptible(StateMachine<C> machine, C ctx, State<C> by) { return true; }
}
