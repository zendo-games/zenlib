package com.zendogames.zenlib.ecs;

/**
 * A data-less marker that takes a bit in the entity mask like a {@link ComponentType} but
 * stores nothing, for {@code Hurt}-style flags. Families match on tags the same way.
 * Registered once as a static field: {@code public static final Tag HURT = Tag.register("Hurt");}
 * then {@code entity.tag(HURT)}, {@code entity.hasTag(HURT)}, {@code entity.untag(HURT)}.
 */
public final class Tag implements Family.Key {

    public final int index;
    final String name;

    private Tag(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public static Tag register(String name) {
        return new Tag(ComponentType.nextIndex(), name);
    }

    @Override public int index() { return index; }
    @Override public String toString() { return name; }
}
