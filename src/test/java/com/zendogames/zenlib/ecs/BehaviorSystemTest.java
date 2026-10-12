package com.zendogames.zenlib.ecs;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.ecs.Fixtures.Pos;
import com.zendogames.zenlib.ecs.components.Behavior;
import com.zendogames.zenlib.ecs.systems.BehaviorSystem;
import com.zendogames.zenlib.state.State;
import com.zendogames.zenlib.state.StateMachine;
import org.junit.Before;
import org.junit.Test;

import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

/** {@code Behavior} + {@code BehaviorSystem}: the machine ticks with the entity, and states may change components or despawn. */
public class BehaviorSystemTest {

    /** Each hook logs "entityId:name:hook". ARMED removes Pos on enter; DOOMED despawns on its first update. */
    enum S implements State<Entity> {
        IDLE,
        ARMED {
            @Override public void enter(StateMachine<Entity> m, Entity e) {
                super.enter(m, e);
                e.remove(Pos.TYPE);
            }
        },
        DOOMED {
            @Override public State<Entity> update(StateMachine<Entity> m, Entity e, float dt) {
                e.world().despawn(e);
                return this;
            }
        };

        static final Array<String> log = new Array<>();

        @Override public void enter(StateMachine<Entity> m, Entity e) { log.add(e.id + ":" + name() + ":enter"); }
        @Override public void exit(StateMachine<Entity> m, Entity e)  { log.add(e.id + ":" + name() + ":exit"); }
        @Override public State<Entity> update(StateMachine<Entity> m, Entity e, float dt) {
            log.add(e.id + ":" + name() + ":update");
            return this;
        }
    }

    private World world;

    @Before
    public void setUp() {
        world = new World();
        world.addSystem(new BehaviorSystem());
        S.log.clear();
    }

    @Test
    public void componentBeforeDependencies_entersOnFirstTickNotOnAdd() {
        var e = world.createEntity().add(new Behavior(S.IDLE));
        assertFalse(Behavior.get(e).machine.isStarted());
        assertEquals(0, S.log.size);

        world.update(FIXED_DT);
        assertTrue(Behavior.get(e).machine.isStarted());
        assertEquals(e.id + ":IDLE:enter", S.log.get(0));
        assertEquals(e.id + ":IDLE:update", S.log.get(1));
        assertEquals(1, Behavior.get(e).ticks());
    }

    @Test
    public void requestFromOutsideAppliesOnNextTick_andMayChangeComponents() {
        var e = world.createEntity().add(new Pos(0, 0)).add(new Behavior(S.IDLE));
        var positioned = world.family(Family.all(Pos.TYPE));
        world.update(FIXED_DT);

        Behavior.get(e).request(S.ARMED);
        assertTrue(Behavior.get(e).is(S.IDLE));
        assertEquals(1, positioned.size());

        world.update(FIXED_DT);
        assertTrue(Behavior.get(e).is(S.ARMED));
        assertFalse(Pos.has(e));
        assertEquals(0, positioned.size()); // membership applied after the system returned
    }

    @Test
    public void despawnFromInsideAStateIsDeferredAndSafe() {
        var e = world.createEntity().add(new Behavior(S.DOOMED));
        world.update(FIXED_DT);
        assertFalse(e.isAlive());
        assertEquals(0, world.entities().size);
    }

    @Test
    public void defaultPriorityIsControl_customPriorityHonored() {
        assertEquals(SystemOrder.CONTROL, new BehaviorSystem().priority);
        assertEquals(SystemOrder.PHYSICS + 5, new BehaviorSystem(SystemOrder.PHYSICS + 5).priority);
    }
}
