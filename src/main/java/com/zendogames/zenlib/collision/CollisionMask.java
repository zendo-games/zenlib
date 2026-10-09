package com.zendogames.zenlib.collision;

/**
 * A collider's category, the {@code Flag} pattern: the game's enum implements it and every
 * constant owns one bit. Sets of masks ({@code blockedBy}, {@code detects}, query filters) are
 * plain {@code int} bit sets built with {@link #of}, so there is nothing to allocate and nothing
 * for TeaVM to reflect on. At most 32 constants per game.
 * <pre>{@code
 * public enum GameMask implements CollisionMask {
 *     SOLID, PLAYER, PROJECTILE, TRIGGER;
 *     @Override public int bit() { return 1 << ordinal(); }
 * }
 * }</pre>
 */
public interface CollisionMask {

    /** One bit per constant: {@code 1 << ordinal()}. */
    int bit();

    /** True when this mask's bit is in {@code bits}. */
    default boolean in(int bits) {
        return (bits & bit()) != 0;
    }

    static int of(CollisionMask... masks) {
        int bits = 0;
        for (var mask : masks) {
            bits |= mask.bit();
        }
        return bits;
    }
}
