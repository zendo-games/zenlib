package com.zendogames.zenlib.assets;

import com.badlogic.gdx.utils.GdxRuntimeException;

/** Mutable holder for a single loaded value, owned by the enum constant that declares it. */
public final class Asset<T> {

    private final AssetType<T> owner;
    private T value;

    public Asset(AssetType<T> owner) {
        this.owner = owner;
    }

    public T get() {
        if (value == null) throw new GdxRuntimeException("asset not loaded: " + label());
        return value;
    }

    public boolean isLoaded() {
        return (value != null);
    }

    void set(T value) {
        this.value = value;
    }

    void clear() {
        value = null;
    }

    /** {@code GameIcon.WRENCH}, also for constants with a body (whose class is an anonymous subclass). */
    public String label() {
        var type = (owner instanceof Enum<?> e) ? e.getDeclaringClass() : owner.getClass();
        return type.getSimpleName() + "." + owner.name();
    }
}
