package com.zendogames.zenlib.ecs;

/**
 * Data attached to an {@link Entity}. Implemented by any class (a record works too) that
 * registers a static {@link ComponentType} for itself; that one line is the whole contract.
 * <pre>{@code
 * public class Foo implements Component {
 *     public static final ComponentType<Foo> TYPE = ComponentType.register(Foo.class);
 *     // Optional sugar, convenience for:
 *     //  - var pos = entity.get(Foo.TYPE)  -> var pos = Foo.get(entity)
 *     //  - if (entity.has(Foo.TYPE)) ...   -> if (Foo.has(entity)) ...
 *     public static Foo     get(Entity e) { return TYPE.get(e); }
 *     public static boolean has(Entity e) { return TYPE.has(e); }
 *
 *     // Data, ctor, methods...
 * }
 * }</pre>
 * A component instance belongs to at most one entity at a time; the hooks receive that entity,
 * there is no back-reference. {@code ZenFlag.DEBUG_ECS} makes {@link Entity#add} check it.
 */
public interface Component {

    /**
     * The registered type of this instance's exact class, looked up once per {@link Entity#add};
     * throws for an unregistered class, so an anonymous or unregistered subclass fails at add
     * instead of silently joining no family. Override to return {@code TYPE} directly, or to
     * store a subclass under its parent's type.
     */
    default ComponentType<?> type() { return ComponentType.of(getClass()); }

    /** Runs at the end of {@link Entity#add}, after the component is stored and the mask bit is set. */
    default void onAdd(Entity e) {}

    /** Runs in {@link Entity#remove} and despawn before the slot is cleared, so the data is still readable. */
    default void onRemove(Entity e) {}
}
