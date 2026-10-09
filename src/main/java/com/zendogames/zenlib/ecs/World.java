package com.zendogames.zenlib.ecs;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntArray;
import com.zendogames.zenlib.ZenFlag;
import com.zendogames.zenlib.sim.SimulationStep;

import java.util.Arrays;

/**
 * Owns the entities, their component storage, the families and the systems of one simulation,
 * and the {@link SimulationStep} that drives it. {@link #update} runs the fixed-step loop and
 * calls every tick system in priority order once per tick; {@link #render} runs the render-rate
 * systems ({@link EntitySystem#runsOnTick()} false) once, with {@link #alpha()} as that frame's
 * interpolation fraction.
 * <p>
 * One rule for structural changes: while a system is running, {@link #despawn} and family
 * membership changes are queued and applied when that system returns, so the list a system
 * iterates never changes under it and a despawned entity stays readable until then. Outside a
 * system (setup code, screen construction) they apply immediately. Component storage changes
 * ({@link Entity#add}, {@link Entity#remove}) are always immediate.
 * <p>
 * Storage is per world ({@code components[type.index][entity.id]}), so two worlds can be alive
 * at once, each with its own ids, as during a transition between two game screens.
 */
public class World implements Disposable {

    public final SimulationStep step = new SimulationStep();

    private final Array<Entity> entities = new Array<>();
    private final IntArray freeIds = new IntArray();
    private int nextId = 0;

    /** Indexed {@code [type.index][entity.id]}; both dimensions grow on demand. */
    private Component[][] components = new Component[0][];

    private final Array<Family> families = new Array<>();
    private final Array<EntitySystem> systems = new Array<>();

    /** True while a system runs, when despawns and membership changes are deferred. */
    private boolean updating = false;
    private final Array<Entity> changed = new Array<>();
    private final Array<Entity> despawning = new Array<>();

    // ------------------------------------------------------------------------
    // Entities
    // ------------------------------------------------------------------------

    /** A new live entity with the lowest free id. */
    public Entity createEntity() {
        int id = (freeIds.size > 0) ? freeIds.pop() : nextId++;
        var entity = new Entity(this, id);
        entities.add(entity);
        changed(entity); // a family with no required keys matches an empty entity
        return entity;
    }

    /**
     * Removes the entity: families drop it (their remove hooks see it intact), then each
     * component's {@code onRemove} runs, then the id is freed. Deferred to the end of the
     * current system while updating, immediate otherwise. A second call is a no-op.
     */
    public void despawn(Entity e) {
        if (!e.alive || e.removing) {
            return;
        }
        e.removing = true;
        if (updating) {
            despawning.add(e);
        } else {
            destroy(e);
        }
    }

    /** Every live entity, in spawn order. A live view: read it, don't modify it. */
    public Array<Entity> entities() {
        return entities;
    }

    /** True while a system is running, when structural changes are deferred. */
    public boolean isUpdating() {
        return updating;
    }

    // ------------------------------------------------------------------------
    // Families
    // ------------------------------------------------------------------------

    /** The family for {@code filter}, created on first request and shared by every later one with the same keys. */
    public Family family(Family.Builder filter) {
        for (int i = 0; i < families.size; i++) {
            var family = families.get(i);
            if (family.sameAs(filter)) {
                return family;
            }
        }
        var family = new Family(filter);
        families.add(family);
        for (int i = 0; i < entities.size; i++) {
            family.refresh(entities.get(i));
        }
        return family;
    }

    // ------------------------------------------------------------------------
    // Systems
    // ------------------------------------------------------------------------

    /** Inserts by priority, after any system with the same priority. One instance per class. */
    public void addSystem(EntitySystem system) {
        var name = system.getClass().getSimpleName();
        if (system.priority == 0) {
            throw new IllegalArgumentException(name + " priority is 0, every system needs an explicit SystemOrder slot");
        }
        for (int i = 0; i < systems.size; i++) {
            if (systems.get(i).getClass() == system.getClass()) {
                throw new IllegalStateException(name + " is already in this world");
            }
        }
        int at = 0;
        while (at < systems.size && systems.get(at).priority <= system.priority) {
            at++;
        }
        systems.insert(at, system);
        system.world = this;
        system.addedToWorld(this);
    }

    public void removeSystem(EntitySystem system) {
        if (systems.removeValue(system, true)) {
            system.removedFromWorld(this);
            system.world = null;
        }
    }

    /** The system of exactly {@code clazz} or a subclass, null when there is none. */
    public <T extends EntitySystem> T getSystem(Class<T> clazz) {
        for (int i = 0; i < systems.size; i++) {
            var system = systems.get(i);
            if (clazz.isInstance(system)) {
                return clazz.cast(system);
            }
        }
        return null;
    }

    /** In run order. A live view: read it, don't modify it. */
    public Array<EntitySystem> systems() {
        return systems;
    }

    // ------------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------------

    /** Feeds the frame delta to the fixed-step loop: each tick runs the tick systems once. */
    public void update(float dt) {
        step.update(dt, this::tick);
    }

    /** Runs the render-rate systems once; call per frame after {@link #update}. */
    public void render(float dt) {
        run(dt, false);
    }

    /** Fraction of a tick left over after the last {@link #update}, for render interpolation. */
    public float alpha() {
        return step.alpha;
    }

    void tick(float dt) {
        run(dt, true);
    }

    private void run(float dt, boolean onTick) {
        if (updating) throw new IllegalStateException("World.update/render called from inside a system");
        updating = true;
        try {
            for (int i = 0; i < systems.size; i++) {
                var system = systems.get(i);
                if (system.enabled && system.runsOnTick() == onTick) {
                    system.update(dt);
                    flush();
                }
            }
        } finally {
            updating = false;
        }
    }

    /** Applies queued despawns and membership changes; hooks may queue more, so loop until settled. */
    private void flush() {
        while (despawning.size > 0 || changed.size > 0) {
            for (int i = 0; i < despawning.size; i++) {
                destroy(despawning.get(i));
            }
            despawning.clear();
            for (int i = 0; i < changed.size; i++) {
                refresh(changed.get(i));
            }
            changed.clear();
        }
    }

    // ------------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------------

    /** The reload path: every entity out (hooks run), ids start over, families and systems stay. */
    public void clear() {
        if (updating) throw new IllegalStateException("World.clear() called from inside a system");
        while (entities.size > 0) {
            destroy(entities.peek());
        }
        despawning.clear();
        changed.clear();
        freeIds.clear();
        nextId = 0;
    }

    /** {@link #clear()}, then removes every system in reverse order. */
    @Override
    public void dispose() {
        clear();
        for (int i = systems.size - 1; i >= 0; i--) {
            removeSystem(systems.get(i));
        }
    }

    // ------------------------------------------------------------------------
    // Package internals, called by Entity
    // ------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    <T extends Component> T component(ComponentType<T> type, int id) {
        if (type.index >= components.length) {
            return null;
        }
        var slot = components[type.index];
        return (slot != null && id < slot.length) ? (T) slot[id] : null;
    }

    /**
     * Debug check behind {@link ZenFlag#DEBUG_ECS}: the instance must not already sit in this
     * world's storage under any entity. A scan of one type's slot, so linear in entities.
     * Does not see an instance attached in another world.
     */
    void requireUnattached(ComponentType<?> type, Component component) {
        var slot = (type.index < components.length) ? components[type.index] : null;
        if (slot == null) {
            return;
        }
        for (int id = 0; id < slot.length; id++) {
            if (slot[id] == component) {
                throw new IllegalStateException(type + " is already attached to Entity#" + id);
            }
        }
    }

    void store(ComponentType<?> type, int id, Component component) {
        if (type.index >= components.length) {
            components = Arrays.copyOf(components, Math.max(ComponentType.count(), type.index + 1));
        }
        var slot = components[type.index];
        if (slot == null) {
            slot = components[type.index] = new Component[Math.max(64, id + 1)];
        } else if (id >= slot.length) {
            slot = components[type.index] = Arrays.copyOf(slot, Math.max(slot.length * 2, id + 1));
        }
        slot[id] = component;
    }

    /** The entity's composition changed: refresh families now, or at the end of the current system. */
    void changed(Entity e) {
        if (!updating) {
            refresh(e);
        } else if (!e.queued) {
            e.queued = true;
            changed.add(e);
        }
    }

    private void refresh(Entity e) {
        e.queued = false;
        if (!e.alive) {
            return;
        }
        for (int i = 0; i < families.size; i++) {
            families.get(i).refresh(e);
        }
    }

    private void destroy(Entity e) {
        if (!e.alive) {
            return;
        }
        for (int i = 0; i < families.size; i++) {
            families.get(i).remove(e);
        }
        for (int i = e.mask.nextSetBit(0); i >=0; i = e.mask.nextSetBit(i + 1)) {
            var slot = (i < components.length) ? components[i] : null;
            var component = (slot != null && e.id < slot.length) ? slot[e.id] : null;
            if (component != null) {
                component.onRemove(e);
                slot[e.id] = null;
            }
        }
        e.mask.clear();
        e.alive = false;
        entities.removeValue(e, true);
        freeIds.add(e.id);
    }
}
