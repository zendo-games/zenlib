package com.zendogames.zenlib.collision;

import com.zendogames.zenlib.ecs.Entity;

/**
 * One contact for one tick, from the reporting mover's side: {@code mover} was either stopped
 * by {@code other} ({@link Kind#BLOCKED}, {@code dx, dy} is the travel direction, one of them
 * zero) or overlapped it ({@link Kind#OVERLAP}, {@code dx, dy} zero). Both sides of a mutual
 * overlap report, each with its own record; a responder that neds one action per pair acts
 * from the lower {@code Entity.id}. An {@link Phase#EXIT} may name an {@code other} that has
 * since despawned.
 */
public record Contact(Entity mover, Entity other, Kind kind, Phase phase, int dx, int dy) {
    public enum Kind  { BLOCKED, OVERLAP }
    public enum Phase { ENTER, STAY, EXIT }

    public boolean isEnter() { return phase == Phase.ENTER; }
    public boolean isStay()  { return phase == Phase.STAY; }
    public boolean isExit()  { return phase == Phase.EXIT; }
}
