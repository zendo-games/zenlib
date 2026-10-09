package com.zendogames.zenlib.collision;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Contacts;
import com.zendogames.zenlib.ecs.components.Name;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.systems.ContactSystem;
import org.junit.Test;

import static com.zendogames.zenlib.collision.Contact.Kind.*;
import static com.zendogames.zenlib.collision.Contact.Phase.*;
import static com.zendogames.zenlib.collision.TestMask.*;
import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

public class ContactSystemTest {

    /** Records every contact handed to it. */
    static final class Recorder implements ContactResponder {
        final Array<Contact> seen = new Array<>();
        int sizeAtFirstCall = -1;

        @Override
        public void respond(Contact contact) {
            seen.add(contact);
        }
    }

    private static Entity player(World world, String name, int x, int y) {
        return world.createEntity()
                .add(new Name(name))
                .add(new Position(x, y))
                .add(Collider.rect(PLAYER, 0, 0, 10, 10).blockedBy(SOLID).detects(TRIGGER, PLAYER))
                .add(new Contacts());
    }

    private static Entity trigger(World world, int x, int y, int w, int h) {
        return world.createEntity().add(new Name("trigger")).add(new Position(x, y)).add(Collider.rect(TRIGGER, 0, 0, w, h));
    }

    private static Contact only(ContactSystem system) {
        assertEquals(1, system.contacts.size);
        return system.contacts.first();
    }

    private static void assertContact(Contact c, Entity mover, Entity other, Contact.Kind kind, Contact.Phase phase, int dx, int dy) {
        assertSame(mover, c.mover());
        assertSame(other, c.other());
        assertEquals(kind, c.kind());
        assertEquals(phase, c.phase());
        assertEquals(dx, c.dx());
        assertEquals(dy, c.dy());
    }

    @Test
    public void overlapPhasesEnterStayExit() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var recorder = new Recorder();
        var system = new ContactSystem(collision, recorder);
        world.addSystem(system);
        var trigger = trigger(world, 0, 0, 50, 50);
        var mover = player(world, 0, 0);

        world.update(FIXED_DT);
        assertContact(only(system), mover, trigger, OVERLAP, ENTER, 0, 0);
        world.update(FIXED_DT);
        assertContact(only(system), mover, trigger, OVERLAP, STAY, 0, 0);

        Position.get(mover).set(100, 100);
        world.update(FIXED_DT);
        assertContact(only(system), mover, trigger, OVERLAP, EXIT, 0, 0);
        world.update(FIXED_DT);
        assertEquals(0, system.contacts.size);

        assertEquals(3, recorder.seen.size);   // ENTER, STAY, EXIT; the fourth tick had nothing
        assertEquals(2, system.log.size());    // STAY is not logged
        assertTrue(system.log.peekFirst().contains("OVERLAP ENTER  player -> trigger"));
        assertTrue(system.log.peekLast().contains("OVERLAP EXIT   player -> trigger"));
    }

    @Test
    public void blockReportBecomesABlockedContactAndIsConsumed() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var system = new ContactSystem(collision, null);   // null responder: NONE
        world.addSystem(system);
        var wall = world.createEntity().add(new Name("wall")).add(new Position(20, 0)).add(Collider.rect(SOLID, 0, 0, 10, 10));
        var mover = player(world, 10, 0);
        var mem = Contacts.get(mover);

        mem.blocked(1, 0, wall);
        world.update(FIXED_DT);
        assertContact(only(system), mover, wall, BLOCKED, ENTER, 1, 0);
        assertNull(mem.blockerX);                          // consumed
        assertEquals(0, mem.hitX);

        mem.blocked(1, 0, wall);
        world.update(FIXED_DT);
        assertContact(only(system), mover, wall, BLOCKED, STAY, 1, 0);

        world.update(FIXED_DT);                            // no report this tick: the block ended
        assertContact(only(system), mover, wall, BLOCKED, EXIT, 0, 0);
        assertEquals(1, system.log.size());                // BLOCKED EXIT is not logged by default

        system.logBlockedExits = true;
        mem.blocked(0, -1, wall);
        world.update(FIXED_DT);
        assertContact(only(system), mover, wall, BLOCKED, ENTER, 0, -1);
        world.update(FIXED_DT);
        assertEquals(3, system.log.size());
    }

    @Test
    public void heldCarriesWhileTheContactLasts() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var system = new ContactSystem(collision, contact -> {
            var entry = Contacts.get(contact.mover()).current(contact.other());
            if (entry != null) entry.held++;
        });
        world.addSystem(system);
        var trigger = trigger(world, 0, 0, 50, 50);
        var mover = player(world, 0, 0);

        world.update(FIXED_DT);
        world.update(FIXED_DT);
        world.update(FIXED_DT);
        assertEquals(3, Contacts.get(mover).current(trigger).held);

        Position.get(mover).set(100, 100);
        world.update(FIXED_DT);                            // EXIT: no current entry, the responder's lookup is null
        assertNull(Contacts.get(mover).current(trigger));
        Position.get(mover).set(0, 0);
        world.update(FIXED_DT);                            // fresh ENTER starts over
        assertEquals(1, Contacts.get(mover).current(trigger).held);
    }

    @Test
    public void bothSidesOfAMutualOverlapReportAndRespondersSeeTheWholeTick() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var sizes = new Array<Integer>();
        var system = new ContactSystem(collision, null);
        system.responder = contact -> sizes.add(system.contacts.size);
        world.addSystem(system);
        var a = player(world, "a", 0, 0);
        var b = player(world, "b", 5, 0);

        world.update(FIXED_DT);
        assertEquals(2, system.contacts.size);
        assertContact(system.contacts.get(0), a, b, OVERLAP, ENTER, 0, 0);
        assertContact(system.contacts.get(1), b, a, OVERLAP, ENTER, 0, 0);
        assertEquals(2, sizes.size);
        assertEquals(2, (int) sizes.get(0));               // all detection done before the first respond
    }

    @Test
    public void despawnFromAResponderIsDeferredAndTheExitNamesTheDeadEntity() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var recorder = new Recorder();
        var system = new ContactSystem(collision, contact -> {
            recorder.respond(contact);
            if (contact.isEnter()) world.despawn(contact.other());
        });
        world.addSystem(system);
        var trigger = trigger(world, 0, 0, 50, 50);
        var mover = player(world, 0, 0);

        world.update(FIXED_DT);
        assertFalse(trigger.isAlive());
        world.update(FIXED_DT);
        var exit = only(system);
        assertContact(exit, mover, trigger, OVERLAP, EXIT, 0, 0);
        assertFalse(exit.other().isAlive());
        assertTrue(system.log.peekLast().contains("-> Entity#0"));   // Name.of on a dead handle
    }

    @Test
    public void aMoverWithoutDetectsOnlyReportsBlocks() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var system = new ContactSystem(collision, null);
        world.addSystem(system);
        trigger(world, 0, 0, 50, 50);
        var mover = world.createEntity()
                .add(new Position(0, 0))
                .add(Collider.rect(PLAYER, 0, 0, 10, 10).blockedBy(SOLID))
                .add(new Contacts());

        world.update(FIXED_DT);
        assertEquals(0, system.contacts.size);
        assertEquals(0, Contacts.get(mover).curr.size);
    }

    private static Entity player(World world, int x, int y) {
        return player(world, "player", x, y);
    }
}
