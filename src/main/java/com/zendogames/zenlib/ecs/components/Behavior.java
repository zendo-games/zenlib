package com.zendogames.zenlib.ecs.components;

import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.state.State;
import com.zendogames.zenlib.state.StateMachine;

/**
 * An entity's state machine over itself: the {@link State}s receive the {@link Entity} and read
 * its components. {@code BehaviorSystem} ticks it; the states are a game enum ({@code HeroState.IDLE}, ...).
 * The initial state's {@code enter} runs on the first tick, not in the constructor, so this
 * component may be added before the ones its states read. The current state's {@code exit}
 * does not run when the entity despawns or this component is removed.
 * <p>
 * One machine per entity. A second, independent machine (say a weapon alongside the hero's
 * body) is a game component holding its own {@code StateMachine<Entity>} and a small system.
 */
public class Behavior implements Component {

    public static final ComponentType<Behavior> TYPE = ComponentType.register(Behavior.class);
    public static Behavior get(Entity e) { return TYPE.get(e); }
    public static boolean  has(Entity e) { return TYPE.has(e); }

    public final StateMachine<Entity> machine;

    public Behavior(State<Entity> initial) {
        this.machine = new StateMachine<>(initial);
    }

    // Shortcuts for the common reads and the one common write; the machine has the rest
    public State<Entity> current()        { return machine.current(); }
    public boolean is(State<Entity> s)    { return machine.is(s); }
    public boolean isNot(State<Entity> s) { return machine.isNot(s); }
    public int     ticks()                { return machine.ticks(); }
    public float   seconds()              { return machine.seconds(); }

    /** Asks for a change on the next tick, see {@link StateMachine#request}. */
    public void request(State<Entity> to) { machine.request(to); }

    /** Sugar for {@code Behavior.get(e).machine.update(e, dt)}. */
    public void update(Entity e, float dt) {
        machine.update(e, dt);
    }

    @Override
    public String toString() {
        return "%s{%s}".formatted(getClass().getSimpleName(), machine.current());
    }
}
