package com.zendogames.zenlib.ecs.movement.topdown;

/**
 * Tuning for an overhead mover, in designer units ({@link #unit} pixels, typically one tile) and
 * seconds. Like {@code PlatformerTuning}, derived values are computed on demand so a game swaps
 * the whole record on its {@link TopDownMotion#tuning} to change how a character moves.
 *
 * @param unit      pixels per tuning unit, typically the tile size
 * @param speed     max speed, units per second, the same on every heading
 * @param accelTime seconds from rest to {@code speed}
 * @param stopTime  seconds from {@code speed} to rest with no input
 */
public record TopDownTuning(
        float unit,
        float speed,
        float accelTime,
        float stopTime
) {

    /** A brisk walker, {@code unit} pixels per tile: six tiles a second, snappy starts and stops. */
    public static TopDownTuning walker(float unit) {
        return new TopDownTuning(unit, 6, 0.08f, 0.08f);
    }

    public float maxSpeed() { return unit * speed; }
    public float accel()    { return maxSpeed() / accelTime; }
    public float friction() { return maxSpeed() / stopTime; }
}
