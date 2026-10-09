package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.collision.Contact;
import com.zendogames.zenlib.collision.ContactResponder;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Contacts;
import com.zendogames.zenlib.ecs.components.Name;
import com.zendogames.zenlib.ecs.components.Position;

import java.util.ArrayDeque;

/**
 * Turns this tick's blocks and overlaps into phased {@link Contact}s and hands them to the
 * {@link ContactResponder}. Runs after the movers ({@code PHYSICS + 10}) over every entity with
 * {@link Position}, {@link Collider} and {@link Contacts}: the block report the mover left in
 * its {@code Contacts} becomes BLOCKED contacts, a {@link CollisionWorld#detects} query becomes
 * OVERLAP contacts, and anything in last tick's memory that is absent now becomes an EXIT.
 * All contacts are detected first, then responded to in detection order, so a responder sees a
 * settled tick. Despawns from a responder are deferred by the world as usual.
 * <p>
 * {@link #contacts} and {@link #log} are for a debug window: the former is this tick's list,
 * the latter one line per ENTER / EXIT, capped. STAY is never logged, BLOCKED EXITs only while
 * {@link #logBlockedExits} is set (a mover leaves the ground every jump).
 */
public class ContactSystem extends IteratingSystem {

    public static final int PRIORITY = SystemOrder.PHYSICS + 10;
    private static final int LOG_CAPACITY = 256;

    private final CollisionWorld collision;
    private final Array<Entity> scratch = new Array<>(16);

    public ContactResponder responder;

    /** This tick's contacts, in detection order; valid until the next update. */
    public final Array<Contact> contacts = new Array<>(32);

    /** Transition log, oldest first. */
    public final ArrayDeque<String> log = new ArrayDeque<>(LOG_CAPACITY);

    public boolean logPaused = false;
    public boolean logBlockedExits = false;

    public ContactSystem(CollisionWorld collision, ContactResponder responder) {
        super(Family.all(Position.TYPE, Collider.TYPE, Contacts.TYPE), PRIORITY);
        this.collision = collision;
        this.responder = (responder != null) ? responder : ContactResponder.NONE;
    }

    @Override
    public void update(float dt) {
        contacts.clear();
        super.update(dt); // detect, one entity at a time
        for (int i = 0; i < contacts.size; i++) {
            responder.respond(contacts.get(i));
        }
    }

    @Override
    protected void process(Entity mover, float dt) {
        var mem = Contacts.get(mover);

        // Rotate memory: prev <- curr, curr <- empty
        var tmp = mem.prev;
        mem.prev = mem.curr;
        mem.curr = tmp;
        mem.curr.clear();

        // Blocks reported by the mover's pixel walk this tick
        if (mem.blockerX != null) add(mover, mem.blockerX, Contact.Kind.BLOCKED, mem, mem.hitX, 0);
        if (mem.blockerY != null) add(mover, mem.blockerY, Contact.Kind.BLOCKED, mem, 0, mem.hitY);
        mem.clearBlocked();

        // Overlaps with whatever the mover detects, at its settled position
        if (Collider.get(mover).detects() != 0) {
            scratch.clear();
            collision.detects(mover, scratch);
            for (int i = 0; i < scratch.size; i++) {
                add(mover, scratch.get(i), Contact.Kind.OVERLAP, mem, 0, 0);
            }
        }

        // Exits: in memory last tick, absent this tick
        for (int i = 0; i < mem.prev.size; i++) {
            var was = mem.prev.get(i);
            if (Contacts.find(mem.curr, was.entity) != null) continue;
            record(new Contact(mover, was.entity, was.kind, Contact.Phase.EXIT, 0, 0));
        }
    }

    private void add(Entity mover, Entity other, Contact.Kind kind, Contacts mem, int dx, int dy) {
        // Phase from last tick's memory; the first kind seen this tick owns the entry
        var prevEntry = Contacts.find(mem.prev, other);
        var phase = (prevEntry != null) ? Contact.Phase.STAY : Contact.Phase.ENTER;
        if (Contacts.find(mem.curr, other) == null) {
            mem.curr.add(new Contacts.Entry(other, kind, (prevEntry != null) ? prevEntry.held : 0));
        }
        record(new Contact(mover, other, kind, phase, dx, dy));
    }

    private void record(Contact contact) {
        contacts.add(contact);
        if (contact.isStay() || logPaused) return;
        if (contact.isExit() && contact.kind() == Contact.Kind.BLOCKED && !logBlockedExits) return;

        if (log.size() >= LOG_CAPACITY) log.pollFirst();
        log.addLast(format(contact));
    }

    public String format(Contact contact) {
        return "%6d  %-7s %-5s  %s -> %s  (%+d,%+d)".formatted(
                world.step.tick, contact.kind(), contact.phase(),
                Name.of(contact.mover()), Name.of(contact.other()),
                contact.dx(), contact.dy());
    }
}
