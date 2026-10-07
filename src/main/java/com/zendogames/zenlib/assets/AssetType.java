package com.zendogames.zenlib.assets;

/**
 * A typed handle to one loaded asset, implemented by enum constants. The constant declares the
 * data (paths, parameters) and owns an {@link Asset} slot that receives the loaded value, the
 * same arrangement as {@code Flag} and {@code Flag.State}. Categories (icons, fonts, ...) are
 * sub-interfaces whose default {@link #load} and {@link #init} do the work for any enum that
 * implements them.
 */
public interface AssetType<T> {

    /** Enum constant name, implemented for free with {@code java.lang.Enum}. */
    String name();

    /** The slot this constant owns: {@code private final Asset<T> asset = new Asset<>(this);} */
    Asset<T> asset();

    /** Queue files on the {@code AssetManager}. Runs before anything is loaded, in registration order. */
    default void load(Assets assets) {}

    /** Build the final value from loaded files. Runs after everything queued has loaded, in registration order. */
    T init(Assets assets);

    /** The loaded value; throws with the constant's name if it isn't loaded yet. */
    default T get() {
        return asset().get();
    }
}
