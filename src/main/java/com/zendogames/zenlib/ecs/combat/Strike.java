package com.zendogames.zenlib.ecs.combat;

import com.zendogames.zenlib.ecs.Entity;

/**
 * One landed hit for one tick: {@code attacker}'s {@code hit} box reached {@code target}'s
 * {@code hurt} box (the first of the target's boxes it overlaps, in the order they resolved).
 * {@code facing} is the attacker's; {@code dx} is the target's x minus the attacker's and
 * {@code dy} the attacker's y minus the target's, from the positions (in a brawler's space, the
 * floor lines). {@link #away()} is the direction to throw the target.
 */
public record Strike(Entity attacker, Hitbox hit, Entity target, Hitbox hurt, int facing, int dx, int dy) {

    public Hitbox.Attack attack() { return hit.attack(); }

    /** -1/+1 away from the attacker: the side the target stands on, the attacker's facing when they share an x. */
    public int away() {
        return (dx != 0) ? Integer.signum(dx) : facing;
    }

    /**
     * One entity's own reaction to a strike, authored where it is spawned like {@code Mover.onHitX}:
     * the attacker's ({@code Hitboxes.onStrike}: a weapon that wears, a projectile that pops) or the
     * target's ({@code Hitboxes.onStruck}: a flash, a crate that breaks). {@code e} is the entity the
     * callback belongs to.
     */
    @FunctionalInterface
    public interface Callback {
        void struck(Entity e, Strike strike);
    }

    /**
     * The game's rule book for strikes (damage, knockback, hurt states, hit-stop), the
     * {@code ContactResponder} of combat: called once per {@link Strike} after {@code HitSystem}
     * has found them all for the tick, before the two entities' own {@link Callback}s.
     */
    @FunctionalInterface
    public interface Responder {

        void strike(Strike strike);

        Responder NONE = strike -> {};
    }
}
