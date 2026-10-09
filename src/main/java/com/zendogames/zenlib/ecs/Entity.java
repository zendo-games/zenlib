package com.zendogames.zenlib.ecs;

import com.badlogic.gdx.utils.Bits;
import com.zendogames.zenlib.ZenFlag;

/**
 * A handle to one entity in a {@link World}: an id into every component storage plus the mask
 * of what it has. Allocated once per spawn and never reused; after despawn the object stays a
 * dead handle ({@link #isAlive()} false, {@link #get} and every mutator throw) while the id goes
 * back to the world for the next spawn, so a reference held too long fails loudly instead of
 * aliasing the new occupant.
 * <p>
 * Component and tag changes take effect in storage immediately. {@link Family} membership follows
 * at the end of the current system while the world is updating, immediately otherwise; see
 * {@link World}.
 */
public final class Entity {

    /** Index into every {@link ComponentType}'s storage; recycled by {@link World}. */
    public final int id;

    final World world;

    /** One bit per {@link ComponentType} or {@link Tag} index. */
    final Bits mask;

    /** False after removal; the object is never reused, only the id is. */
    boolean alive;

    /** Despawn scheduled, applied at the end of the current system. */
    boolean removing;

    /** Sitting in the world's changed queue, so a second change doesn't queue it twice. */
    boolean queued;

    Entity(World world, int id) {
        this.id = id;
        this.world = world;
        this.mask = new Bits(ComponentType.count());
        this.alive = true;
        this.removing = false;
        this.queued = false;
    }

    public World world() { return world; }

    public boolean isAlive() { return alive; }

    /** True once {@link World#despawn}  has been called; the entity stays alive until the end of the current system. */
    public boolean isRemoving() { return removing; }

    /** The component of {@code type}, null when the entity has none. Throws on a dead handle. */
    public <T extends Component> T get(ComponentType<T> type) {
        requireAlive();
        return world.component(type, id);
    }

    /** False on a dead handle, whose mask is cleared. */
    public <T extends Component> boolean has(ComponentType<T> type) {
        return mask.get(type.index);
    }

    /**
     * Attaches {@code component}, replacing (and removing, hooks included) one of the same type
     * if present. An instance belongs to one entity at a time; attaching it twice is only
     * detected while {@link ZenFlag#DEBUG_ECS} is on.
     */
    public <T extends Component> Entity add(T component) {
        requireAlive();
        var type = component.type();
        if (ZenFlag.DEBUG_ECS.on()) {
            world.requireUnattached(type, component);
        }
        detach(type); // replacing: the old one leaves first, one membership refresh for both
        world.store(type, id, component);
        mask.set(type.index);
        component.onAdd(this);
        world.changed(this);
        return this;
    }

    /** Detaches and returns the component of {@code type}, running its remove hook first; null when absent. */
    public <T extends Component> T remove(ComponentType<T> type) {
        requireAlive();
        var component = detach(type);
        if (component != null) {
            world.changed(this);
        }
        return component;
    }

    private <T extends Component> T detach(ComponentType<T> type) {
        var component = world.component(type, id);
        if (component == null) {
            return null;
        }
        component.onRemove(this);
        world.store(type, id, null);
        mask.clear(type.index);
        return component;
    }

    public Entity tag(Tag tag) {
        requireAlive();
        if (!mask.get(tag.index)) {
            mask.set(tag.index);
            world.changed(this);
        }
        return this;
    }

    public Entity untag(Tag tag) {
        requireAlive();
        if (mask.get(tag.index)) {
            mask.clear(tag.index);
            world.changed(this);
        }
        return this;
    }

    /** False on a dead handle, whose mask is cleared. */
    public boolean hasTag(Tag tag) {
        return mask.get(tag.index);
    }

    private void requireAlive() {
        if (!alive) throw new IllegalStateException(this + " is not alive");
    }

    @Override
    public String toString() {
        return "Entity#" + id;
    }
}
