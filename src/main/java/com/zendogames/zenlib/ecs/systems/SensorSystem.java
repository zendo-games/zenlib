package com.zendogames.zenlib.ecs.systems;

import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.components.Collider;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.components.Sensor;

/**
 * {@code SENSE}: casts every {@link Sensor}'s rays into the collision world as it stands at the start
 * of the tick, before {@code DECIDE} and the movers, so a decision system reads perception of last
 * tick's settled positions. Whiskers start at the collider's center plus their offset, mirrored by the
 * sensor's {@code facing}, skip the entity's own collider, and read the terrain and threat masks in one
 * cast each. Sight rays go from the same center to the center of every threat within range (nearest
 * first, as many as the sensor has room for), against the same masks, so a sighting is the target or
 * whatever stands in front of it.
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

        if (sensor.sightings != null) {
            gather(e, sensor, cx, cy);
            for (int i = 0; i < sensor.sightingCount; i++) {
                cast(e, sensor, sensor.sightings[i], cx, cy, masks);
            }
        }
    }

    /** Every threat whose collider center is within range becomes a sighting, nearest first, up to the sensor's room. */
    private void gather(Entity e, Sensor sensor, float centerX, float centerY) {
        sensor.clearSightings();
        var entities = collision.family().entities();
        for (int i = 0; i < entities.size; i++) {
            var other = entities.get(i);
            if (other == e) continue;

            var otherCol = Collider.get(other);
            if (!otherCol.mask().in(sensor.threats)) continue;

            var otherPos = Position.get(other);
            var targetX  = otherCol.shape().centerX(otherPos);
            var targetY  = otherCol.shape().centerY(otherPos);
            var dx = targetX - centerX;
            var dy = targetY - centerY;
            var distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance > sensor.range) continue;

            sensor.addSighting(other, targetX, targetY, distance);
        }
    }

    /** One ray at the target's center, over any one-way platform; two bodies sharing a center see each other at zero without a cast. */
    private void cast(Entity e, Sensor sensor, Sensor.Sighting sighting, float centerX, float centerY, int masks) {
        sighting.startX = centerX;
        sighting.startY = centerY;
        if (sighting.targetDistance == 0f) {
            sighting.dirX = 1f;
            sighting.dirY = 0f;
            sighting.hit(0f, centerX, centerY, sighting.target, Block.SOLID);
            return;
        }
        sighting.dirX = (sighting.targetX - centerX) / sighting.targetDistance;
        sighting.dirY = (sighting.targetY - centerY) / sighting.targetDistance;
        collision.raycast(sighting.startX, sighting.startY, sighting.dirX, sighting.dirY, sensor.range, masks, e, /*throughOneWays*/ true, sighting);
    }
}
