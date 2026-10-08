package com.zendogames.zenlib.ecs;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Bits;

import java.util.function.Consumer;

/**
 * The live set of entities in a {@link World} whose mask matches a filter: every key in
 * {@code all}, at least one of {@code one} (when any are given), none of {@code exclude}.
 * Built from a {@link Builder} by {@link World#family}, which interns equal filters so two
 * systems asking for the same shape share one instance.
 * <pre>{@code
 * var movers = world.family(Family.all(Position.TYPE, Movement.TYPE).exclude(Frozen.TAG));
 * var entities = movers.entities();
 * for (int i = 0, n = entities.size; i < n; i++) step(entities.get(i));
 * movers.onAdd(e -> grid.insert(e));
 * }</pre>
 * Membership follows composition changes at the end of the current system while the world is
 * updating, immediately otherwise, so {@link #entities()} is stable inside a system's update.
 * Prefer the index loop above to for-each: a gdx {@code Array} reuses one iterator, which
 * breaks when loops nest.
 */
public final class Family {

    public sealed interface Key permits ComponentType, Tag {
        int index();
    }

    public static Builder all(Key... keys)     { return new Builder().all(keys); }
    public static Builder one(Key... keys)     { return new Builder().one(keys); }
    public static Builder exclude(Key... keys) { return new Builder().exclude(keys); }

    public static final class Builder {
        final Bits all = new Bits();
        final Bits one = new Bits();
        final Bits exclude = new Bits();

        private Builder() {}

        public Builder all(Key... keys)     { set(all, keys);     return this; }
        public Builder one(Key... keys)     { set(one, keys);     return this; }
        public Builder exclude(Key... keys) { set(exclude, keys); return this; }

        private static void set(Bits bits, Key[] keys) {
            for (var key : keys) {
                bits.set(key.index());
            }
        }
    }

    final Bits all;
    final Bits one;
    final Bits exclude;

    private final Array<Entity> entities = new Array<>();

    /** Membership by entity id, so refresh doesn't scan the list. */
    private final Bits members = new Bits();

    private final Array<Consumer<Entity>> addListeners = new Array<>();
    private final Array<Consumer<Entity>> removeListeners = new Array<>();

    Family(Builder filter) {
        this.all = new Bits(filter.all);
        this.one = new Bits(filter.one);
        this.exclude = new Bits(filter.exclude);
    }

    /** The members, in join order. A live view: read it, don't modify it. */
    public Array<Entity> entities() {
        return entities;
    }

    public int size() {
        return entities.size;
    }

    public boolean contains(Entity e) {
        return e.alive && members.get(e.id);
    }

    /** Whether the entity's current mask fits the filter, regardless of whether membership has caught up. */
    public boolean matches(Entity e) {
        return e.mask.containsAll(all)
            && (one.isEmpty() || e.mask.intersects(one))
            && !e.mask.intersects(exclude);
    }

    /** Runs when an entity joins, after it is in {@link #entities()}. */
    public void onAdd(Consumer<Entity> listener) {
        addListeners.add(listener);
    }

    /** Runs when an entity leaves, after it is out of {@link #entities()} and while its components are still attached. */
    public void onRemove(Consumer<Entity> listener) {
        removeListeners.add(listener);
    }

    boolean sameAs(Builder filter) {
        return same(all, filter.all) && same(one, filter.one) && same(exclude, filter.exclude);
    }

    private static boolean same(Bits a, Bits b) {
        return a.containsAll(b) && b.containsAll(a);
    }

    /** Called by the world when an entity's composition settles; joins or leaves as needed. */
    void refresh(Entity e) {
        boolean member = members.get(e.id);
        boolean matches = e.alive && matches(e);
        if (matches && !member) {
            members.set(e.id);
            entities.add(e);
            for (int i = 0; i < addListeners.size; i++) {
                addListeners.get(i).accept(e);
            }
        } else if (!matches && member) {
            remove(e);
        }
    }

    /** Called by the world on despawn, before the components come off. */
    void remove(Entity e) {
        if (!members.get(e.id)) return;
        members.clear(e.id);
        entities.removeValue(e, true);
        for (int i = 0; i < removeListeners.size; i++) {
            removeListeners.get(i).accept(e);
        }
    }
}
