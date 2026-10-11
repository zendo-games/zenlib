package com.zendogames.zenlib.ecs.movement;

import com.badlogic.gdx.utils.Null;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.collision.shapes.CollisionShape;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Contacts;

/**
 * {@link MoveWorld} over a {@link CollisionWorld} for the entity currently being stepped:
 * probes with its {@code blockedBy} bits and exclude it from the candidates, and each block
 * is written straight into its {@link Contacts} (when it has one) from the world's last contact.
 * One instance per {@code MovementSystem}, re-pointed with {@link #set} before every step.
 */
public final class MoverWorld implements MoveWorld {

    private final CollisionWorld collision;

    private Entity mover;
    private int blockedBy;
    private Contacts contacts;

    public MoverWorld(CollisionWorld collision) {
        this.collision = collision;
        this.blockedBy = 0;
        this.contacts = null;
    }

    /** Points this adapter at {@code mover} for the next step. */
    public MoverWorld set(Entity mover, Collider collider, @Null Contacts contacts) {
        this.mover = mover;
        this.blockedBy = collider.blockedBy();
        this.contacts = contacts;
        return this;
    }

    @Override
    public Block probe(CollisionShape shape, int x, int y, int dx, int dy) {
        return collision.probe(shape, x, y, dx, dy, blockedBy, mover);
    }

    /** Records the block in the mover's contacts: the entity behind the most recent probe, on the axis of the non-zero component. */
    @Override
    public void onBlocked(int dx, int dy) {
        if (contacts == null) return;
        var other = collision.lastContact();
        if (other != null) {
            contacts.blocked(dx, dy, other);
        }
    }
}
