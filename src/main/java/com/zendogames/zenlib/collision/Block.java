package com.zendogames.zenlib.collision;

/**
 * Result of {@link CollisionWorld#probe}: whether a one-pixel move is stopped.
 * {@link #ONE_WAY} means the blocker is a {@code CollisionAASeg} that the mover
 * is moving against from its support side; a mover that wants to drop through
 * treats it as {@link #NONE}.
 */
public enum Block {
    NONE, SOLID, ONE_WAY;

    public boolean isBlocked() { return this != NONE; }
}
