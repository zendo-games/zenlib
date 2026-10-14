package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.components.Sensor;

/**
 * {@code SENSE}: casts every {@link Sensor}'s whiskers into the collision world as it stands at
 * the start of the tick, before {@code DECIDE} and the movers, so a decision system reads
 * perception of last tick's settled positions. Whiskers start at the collider's center plus their
 * offset, mirrored by the sensor's {@code facing}, skip the entity's own collider, and read the
 * terrain and threat masks in one cast each.
 */
public class SensorSystem extends IteratingSystem {

    public static final int PRIORITY = SystemOrder.SENSE;

    private final CollisionWorld collision;

    public SensorSystem(CollisionWorld collision) {
        super(Family.all(Position.TYPE, Collider.TYPE, Sensor.TYPE), PRIORITY);
        this.collision = collision;
    }

    @Override
    protected void process(Entity e, float dt) {
        var sensor = Sensor.get(e);
        var pos    = Position.get(e);
        var shape  = Collider.get(e).shape();
        var cx     = shape.centerX(pos);
        var cy     = shape.centerY(pos);
        var facing = (sensor.facing < 0) ? -1 : 1;
        var masks  = sensor.masks();

        for (var w : sensor.whiskers) {
            w.startX = cx + w.originX * facing;
            w.startY = cy + w.originY;
            w.dirX   = w.cos * facing;
            w.dirY   = w.sin;
            collision.raycast(w.startX, w.startY, w.dirX, w.dirY, sensor.range, masks, e, w);
        }
    }
}
