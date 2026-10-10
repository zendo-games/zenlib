package com.zendogames.zenlib.ecs;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.ecs.Fixtures.Pos;
import com.zendogames.zenlib.ecs.components.Timers;
import com.zendogames.zenlib.ecs.systems.TimerSystem;
import org.junit.Before;
import org.junit.Test;

import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

/** {@link Timers} + {@link TimerSystem}: tick counting, replacement, and what an action may do to its own component. */
public class TimerSystemTest {

    private World world;
    private Entity entity;
    private Timers timers;
    private final Array<String> log = new Array<>();

    @Before
    public void setUp() {
        world = new World();
        world.addSystem(new TimerSystem());
        timers = new Timers();
        entity = world.createEntity().add(new Pos(0, 0)).add(timers);
        log.clear();
    }

    private void tick(int n) {
        for (int i = 0; i < n; i++) world.update(FIXED_DT);
    }

    /** Ticks for {@code seconds}, matching what the component stores. */
    private static int ticks(float seconds) {
        return com.zendogames.zenlib.sim.SimulationStep.ticks(seconds);
    }

    @Test
    public void oneShot_firesOnceAfterDurationAndIsRemoved() {
        timers.after("a", 0.5f, t -> log.add("a"));
        int n = ticks(0.5f);

        tick(n - 1);
        assertEquals(0, log.size);
        assertTrue(timers.has("a"));

        tick(1);
        assertEquals(Array.with("a"), log);
        assertFalse(timers.has("a"));
        assertEquals(0, timers.size());

        tick(n);
        assertEquals(1, log.size);
    }

    @Test
    public void zeroSeconds_firesNextTickNeverTheSameTick() {
        timers.after("now", 0f, t -> log.add("now"));
        assertEquals(0, log.size);
        tick(1);
        assertEquals(1, log.size);
    }

    @Test
    public void every_repeatsAtTheInterval() {
        timers.every("beat", 0.1f, t -> log.add("beat"));
        tick(ticks(0.1f) * 3);
        assertEquals(3, log.size);
        assertTrue(timers.has("beat"));
    }

    @Test
    public void restartInsideAction_rearmsOneShotLikeARepeat() {
        // the blob idiom: a one-shot that decides its own next delay
        timers.after("jump", 0.1f, t -> {
            log.add("jump");
            if (log.size < 3) t.restart(0.2f);
        });
        tick(ticks(0.1f));
        assertEquals(1, log.size);
        tick(ticks(0.2f));
        assertEquals(2, log.size);
        tick(ticks(0.2f));
        assertEquals(3, log.size);
        assertFalse("not restarted the third time, so it is gone", timers.has("jump"));
        tick(ticks(0.2f) * 2);
        assertEquals(3, log.size);
    }

    @Test
    public void restartInsideRepeatingAction_overridesTheInterval() {
        timers.every("beat", 0.1f, t -> {
            log.add("beat");
            t.restart(0.3f);
        });
        tick(ticks(0.1f));
        assertEquals(1, log.size);
        tick(ticks(0.1f));
        assertEquals("the restart replaced the 0.1s interval", 1, log.size);
        tick(ticks(0.3f) - ticks(0.1f));
        assertEquals(2, log.size);
    }

    @Test
    public void sameName_replacesAndTheOldOneNeverFires() {
        timers.after("x", 0.1f, t -> log.add("old"));
        var replaced = timers.after("x", 0.2f, t -> log.add("new"));
        assertSame(replaced, timers.get("x"));
        assertEquals(1, timers.size());

        tick(ticks(0.2f));
        assertEquals(Array.with("new"), log);
    }

    @Test
    public void actionAddsATimer_itWaitsForTheNextTick() {
        // the spitter idiom: shoot, then a short timer puts the animation back
        timers.after("shoot", 0f, t -> {
            log.add("shoot");
            t.owner().after("idle", 0f, u -> log.add("idle"));
        });
        tick(1);
        assertEquals(Array.with("shoot"), log);
        tick(1);
        assertEquals(Array.with("shoot", "idle"), log);
    }

    @Test
    public void actionReplacesItself_underTheSameName() {
        timers.after("loop", 0f, t -> {
            log.add("first");
            t.owner().after("loop", 0f, u -> log.add("second"));
        });
        tick(1);
        tick(1);
        assertEquals(Array.with("first", "second"), log);
        assertFalse(timers.has("loop"));
    }

    @Test
    public void actionCancelsALaterTimer_itDoesNotFireThatTick() {
        timers.after("a", 0f, t -> t.owner().cancel("b"));
        timers.after("b", 0f, t -> log.add("b"));
        tick(1);
        assertEquals(0, log.size);
        assertEquals(0, timers.size());
    }

    @Test
    public void actionDespawnsTheEntity_isDeferredAndSafe() {
        timers.after("die", 0f, t -> t.entity().world().despawn(t.entity()));
        timers.after("after", 0f, t -> log.add("still ran this tick"));
        tick(1);
        assertFalse(entity.isAlive());
        assertEquals(1, log.size);
        tick(1);
    }

    @Test
    public void actionRemovesTheComponent_stopsTheRestOfTheTick() {
        timers.after("a", 0f, t -> t.entity().remove(Timers.TYPE));
        timers.after("b", 0f, t -> log.add("b"));
        tick(1);
        assertEquals(0, log.size);
        assertFalse(Timers.has(entity));
        assertEquals(0, timers.size());
        assertNull(timers.get("b"));
    }

    @Test
    public void restartAfterAOneShotFired_throws() {
        var t = timers.after("a", 0f, x -> {});
        tick(1);
        assertFalse(t.isActive());
        assertThrows(IllegalStateException.class, t::restart);
    }

    @Test
    public void cancelOutsideASystem_removesImmediately() {
        var t = timers.after("a", 1f, x -> log.add("a"));
        t.cancel();
        assertEquals(0, timers.size());
        assertFalse(t.isActive());
        tick(ticks(1f));
        assertEquals(0, log.size);
    }

    @Test
    public void clearInsideAnAction_cancelsEverything() {
        timers.every("a", 0f, t -> { log.add("a"); t.owner().clear(); });
        timers.every("b", 0f, t -> log.add("b"));
        tick(1);
        assertEquals(Array.with("a"), log);
        assertEquals(0, timers.size());
    }

    @Test
    public void zeroDelta_doesNotTick() {
        timers.after("a", 0f, t -> log.add("a"));
        world.update(0f);
        world.update(0f);
        assertEquals(0, log.size);
    }

    @Test
    public void componentRemoved_handlesGoDead() {
        var t = timers.after("a", 1f, x -> log.add("a"));
        entity.remove(Timers.TYPE);
        assertFalse(t.isActive());
        assertNull(t.entity());
        assertEquals(0, timers.size());
    }

    @Test
    public void addedBeforeAttach_isFine() {
        var loose = new Timers();
        var t = loose.after("a", 0f, x -> log.add("a:" + x.entity().id));
        assertNull(t.entity());
        var e = world.createEntity().add(loose);
        tick(1);
        assertEquals(Array.with("a:" + e.id), log);
    }

    @Test
    public void nullNameOrAction_rejected() {
        assertThrows(IllegalArgumentException.class, () -> timers.after(null, 1f, t -> {}));
        assertThrows(IllegalArgumentException.class, () -> timers.after("a", 1f, null));
    }
}
