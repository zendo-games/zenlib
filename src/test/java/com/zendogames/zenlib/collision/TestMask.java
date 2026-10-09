package com.zendogames.zenlib.collision;

/** The masks a game would declare, for the collision tests. */
enum TestMask implements CollisionMask {
    SOLID, PLATFORM, PLAYER, TRIGGER;

    @Override public int bit() { return 1 << ordinal(); }
}
