package com.zendogames.zenlib.ecs;

// @formatter:off
/**
 * Data attached to an {@link Entity}. Implemented by plain classes that own a static
 * {@link ComponentType} handle and a {@link Link}, the same key + slot shape as {@code Flag}
 * and {@code Flag.State}:
 * <pre>{@code
 * public class Position extends PointI2 implements Component {
 *     public static final ComponentType<Position> TYPE = ComponentType.register(Position.class);
 *     public static Position get(Entity e) { return TYPE.get(e); }
 *     public static boolean  has(Entity e) { return TYPE.has(e); }
 *
 *     private final Link link = new Link(TYPE);
 *     @Override public Link link() { return link; }
 * }
 * }</pre>
 * A component instance belongs to at most one entity at a time: {@link Entity#add} sets the
 * back-reference, {@link Entity#remove} and despawn clear it.
 */
public interface Component {

    /** The slot holding this component's type and owning entity. */
    Link link();

    default ComponentType<?> type() { return link().type; }

    /** The owning entity, null while not attached. */
    default Entity entity() { return link().entity; }

    /** Runs at the end of {@link Entity#add}, after the component is stored and the mask bit is set. */
    default void onAdd(Entity e)    {}

    /** Runs in {@link Entity#remove} and despawn before the slot is cleared, so the data is still readable. */
    default void onRemove(Entity e) {}

    /** Type handle plus entity back-reference, owned by the component that declares it. */
    final class Link {
        final ComponentType<?> type;
        Entity entity;

        public Link(ComponentType<?> type) {
            this.type = type;
        }
    }
}
// @formatter:on
