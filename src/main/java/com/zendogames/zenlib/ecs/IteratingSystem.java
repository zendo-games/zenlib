package com.zendogames.zenlib.ecs;

/**
 * A system that calls {@link #process} once per member of a {@link Family} each update. The
 * family is resolved when the system joins a world, so subclasses that override
 * {@link #addedToWorld} or {@link #removedFromWorld} must call super. Despawning or changing
 * the entity being processed is safe: membership only changes after this system returns.
 */
public abstract class IteratingSystem extends EntitySystem {

    private final Family.Builder filter;
    private Family family;

    protected IteratingSystem(Family.Builder filter, int priority) {
        super(priority);
        this.filter = filter;
    }

    /** The resolved family, null while not in a {@link World}. */
    protected Family family() {
        return family;
    }

    @Override
    public void addedToWorld(World world) {
        family = world.family(filter);
    }

    @Override
    public void removedFromWorld(World world) {
        family = null;
    }

    @Override
    public void update(float dt) {
        var entities = family.entities();
        for (int i = 0; i < entities.size; i++) {
            process(entities.get(i), dt);
        }
    }

    protected abstract void process(Entity e, float dt);
}
