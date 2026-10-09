package com.zendogames.zenlib.ecs;

import java.util.HashMap;

/**
 * Handle for one component class: its bit in every entity's mask and its index into each
 * world's storage. Registered once per class as a static field, see {@link Component}.
 * No reflection: the class is only read for its debug name.
 */
public final class ComponentType<T extends Component> implements Family.Key {

    private static int count = 0;
    private static final HashMap<Class<?>, ComponentType<?>> byClass = new HashMap<>();


    /** Hands out mask bits; shared with {@link Tag} so both live in one mask. */
    static int nextIndex() {
        return count++;
    }

    /** Registered types and tags so far, the upper bound for mask bits and storage slots. */
    static int count() {
        return count;
    }

    public final int index;
    final String name;

    private ComponentType(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public static <T extends Component> ComponentType<T> register(Class<T> clazz) {
        var type = new ComponentType<T>(nextIndex(), clazz.getSimpleName());
        byClass.put(clazz, type);
        return type;
    }

    /** The type registered for exactly this class; throws for an unregistered class, a subclass of a registered one included. */
    static ComponentType<?> of(Class<?> clazz) {
        var type = byClass.get(clazz);
        if (type == null) throw new IllegalStateException(clazz.getSimpleName() + " is not a registered component type");
        return type;
    }

    /** The entity's component of this type, null when it has none. */
    public T get(Entity e) {
        return e.get(this);
    }

    public boolean has(Entity e) {
        return e.has(this);
    }

    @Override public int index() { return index; }
    @Override public String toString() { return name; }
}
