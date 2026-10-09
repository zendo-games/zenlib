package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.collision.Contact;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.systems.ContactSystem;

/**
 * A mover's contact memory, for entities that want {@link Contact}s reported: who it touched
 * last tick and this tick, so {@link ContactSystem} can phase them ENTER / STAY / EXIT, plus the
 * block report that the mover writes each tick that it is stopped.
 */
public class Contacts implements Component {

    public static final ComponentType<Contacts> TYPE = ComponentType.register(Contacts.class);
    public static Contacts get(Entity e) { return TYPE.get(e); }
    public static boolean  has(Entity e) { return TYPE.has(e); }

    public static final class Entry {
        public final Entity entity;
        public final Contact.Kind kind;

        /** Responder-owned: number of ticks the responder's own condition has held for this pair. Carried while the contact lasts. */
        public int held;

        public Entry(Entity entity, Contact.Kind kind, int held) {
            this.entity = entity;
            this.kind = kind;
            this.held = held;
        }
    }

    /** Last tick's contacts; swapped with {@link #curr} by the system. */
    public Array<Entry> prev = new Array<>(8);

    /** This tick's contacts, in detection order. */
    public Array<Entry> curr = new Array<>(8);

    /** Entity that stopped movement on the x axis this tick, or null. Written by the mover, consumed by the system. */
    public Entity blockerX;

    /** Entity that stopped movement on the y axis this tick, or null. Written by the mover, consumed by the system. */
    public Entity blockerY;

    /** Direction of the block on each axis, -1/0/+1. */
    public int hitX, hitY;

    /** Records a block on the axis of the non-zero component. Exactly one of {@code dx, dy} is non-zero. */
    public  void blocked(int dx, int dy, Entity other) {
        if (dx != 0) { blockerX = other; hitX = Integer.signum(dx); }
        if (dy != 0) { blockerY = other; hitY = Integer.signum(dy); }
    }

    public void clearBlocked() {
        blockerX = null;
        blockerY = null;
        hitX = 0;
        hitY = 0;
    }

    /** This tick's entry for {@code other}, null when not in contact. */
    public Entry current(Entity other) {
        return find(curr, other);
    }

    public static Entry find(Array<Entry> list, Entity e) {
        for (int i = 0; i < list.size; i++) {
            var entry = list.get(i);
            if (entry.entity == e) {
                return entry;
            }
        }
        return null;
    }
}
