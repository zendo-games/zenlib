package com.zendogames.zenlib.ecs.components;

import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;

/**
 * Hit points. Plain data: nothing in the library reads it, a {@code Strike.Responder} or a hurt
 * state calls {@link #damage} and decides what zero means (a death state, a despawn, a respawn).
 */
public class Health implements Component {

    public static final ComponentType<Health> TYPE = ComponentType.register(Health.class);
    public static Health  get(Entity e) { return TYPE.get(e); }
    public static boolean has(Entity e) { return TYPE.has(e); }

    public int max;
    public int current;

    public Health(int max) {
        this.max = max;
        this.current = max;
    }

    /** Takes {@code amount}, never below zero; returns what is left. */
    public int damage(int amount) {
        current = Math.max(0, current - amount);
        return current;
    }

    public void restore() { current = max; }
    public void kill()    { current = 0; }

    public boolean isAlive() { return current > 0; }
    public boolean isDead()  { return current <= 0; }
}
