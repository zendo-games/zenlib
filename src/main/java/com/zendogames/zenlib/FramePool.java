package com.zendogames.zenlib;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.DefaultPool;
import com.badlogic.gdx.utils.DefaultPool.PoolSupplier;
import com.badlogic.gdx.utils.Pool;
import com.github.tommyettinger.gdcrux.PointF2;
import com.github.tommyettinger.gdcrux.PointI2;
import com.github.tommyettinger.gdcrux.PointI3;
import com.github.tommyettinger.gdcrux.PointI4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

// @formatter:off
/**
 * Scratch objects that live until the start of the next frame, the closest Java gets to
 * stack-allocated structs. Obtain with the static accessors, usually via a static import:
 * {@code var dir = vec2(target).sub(pos).nor();}. Never store what you obtain; {@code ZenGame}
 * frees everything each update. With {@link ZenFlag#DEBUG_FRAME_POOL} on, freed objects are
 * poisoned with {@code NaN} so a reference held across a frame fails loudly.
 * <p>
 * Games add their own types with {@link #register(PoolSupplier, Consumer)} and obtain them
 * with {@link #obtain(Type)}; see the library's own types below for the pattern.
 */
public final class FramePool {

    /** A handle to a registered type, index into each instance's slot list. */
    public static final class Type<T> {
        final int index;
        final PoolSupplier<T> supplier;
        final Consumer<T> poison;
        final String name;

        Type(int index, PoolSupplier<T> supplier, Consumer<T> poison) {
            this.index = index;
            this.supplier = supplier;
            this.poison = poison;
            // Type erasure hides T, but one throwaway instance knows its own class
            this.name = supplier.get().getClass().getSimpleName();
        }

        public String name() { return name; }
    }

    /** A pool and the objects it has handed out since the last reset. */
    private static final class Slot<T> {
        final Type<T> type;
        final Pool<T> pool;
        final Array<T> active;

        Slot(Type<T> type) {
            this.type = type;
            this.pool = new DefaultPool<>(type.supplier, 64) {
                @Override
                protected void reset(T object) {
                    super.reset(object);
                    if (type.poison != null && ZenFlag.DEBUG_FRAME_POOL.on()) {
                        type.poison.accept(object);
                    }
                }
            };
            this.active = new Array<>(false, 64);
        }

        T obtain() {
            var obj = pool.obtain();
            active.add(obj);
            return obj;
        }

        void reset() {
            pool.freeAll(active);
            active.clear();
        }

        void clear() {
            reset();
            pool.clear();
        }
    }

    // ------------------------------------------------------------------------
    // Registry, shared by every instance
    // ------------------------------------------------------------------------

    private static final Array<Type<?>> types = new Array<>();
    private static List<Type<?>> typesView;

    public static List<Type<?>> types() {
        if (typesView == null || typesView.size() != types.size) {
            // Use explicit generic typed list instead Arrays.asList or similar so type info is valid
            var list = new ArrayList<Type<?>>(types.size);
            for (int i = 0; i < types.size; i++) {
                list.add(types.get(i));
            }
            list.sort(Comparator.comparing(Type::name));
            typesView = Collections.unmodifiableList(list);
        }
        return typesView;
    }

    /** Registers a type with no debug poisoning. */
    public static <T> Type<T> register(PoolSupplier<T> supplier) {
        return register(supplier, null);
    }

    /**
     * Registers a type. {@code poison} runs on every freed object while
     * {@link ZenFlag#DEBUG_FRAME_POOL} is on; set fields to {@code NaN}
     * or an impossible value so a reference held across a frame fails loudly.
     */
    public static <T> Type<T> register(PoolSupplier<T> supplier, Consumer<T> poison) {
        var type = new Type<>(types.size, supplier, poison);
        types.add(type);
        return type;
    }

    // ------------------------------------------------------------------------
    // Instance
    // One singleton instance per process for now, swap get() for a ThreadLocal
    // if workers running on separate threads need their own in the future.
    // ------------------------------------------------------------------------

    private static final FramePool instance = new FramePool();

    private static FramePool get() { return instance; }

    private final Array<Slot<?>> slots = new Array<>();

    private FramePool() {}

    @SuppressWarnings("unchecked")
    private <T> Slot<T> slot(Type<T> type) {
        // Types may be registered after this instance exists, grow on demand
        while (slots.size <= type.index) {
            slots.add(new Slot<>(types.get(slots.size)));
        }
        return (Slot<T>) slots.get(type.index);
    }

    /** Returns a scratch object of a registered type, valid until the next frame's update. */
    public static <T> T obtain(Type<T> type) {
        return get().slot(type).obtain();
    }

    /** Highest number of objects of this type freed in one frame so far, for debug display. */
    public static int peak(Type<?> type) {
        return get().slot(type).pool.peak;
    }

    /** Objects of this type handed out so far this frame. */
    public static int active(Type<?> type) {
        return get().slot(type).active.size;
    }

    /** Objects of this type parked in the pool, available without allocating. */
    public static int free(Type<?> type) {
        return get().slot(type).pool.getFree();
    }

    /** Frees everything obtained since the last call. {@code ZenGame} calls this once per update. */
    static void resetAll() {
        var slots = get().slots;
        for (int i = 0; i < slots.size; i++) {
            var slot = slots.get(i);
            slot.reset();
        }
    }

    /** Frees everything and drops the pooled objects, so a second {@code Zen.init} starts clean. */
    static void init() {
        var slots = get().slots;
        for (int i = 0; i < slots.size; i++) {
            var slot = slots.get(i);
            slot.clear();
        }
    }

    // ------------------------------------------------------------------------
    // Library types
    // ------------------------------------------------------------------------

    public static final Type<Vector2>   VEC2   = register(Vector2::new,   v -> v.set(Float.NaN, Float.NaN));
    public static final Type<Vector3>   VEC3   = register(Vector3::new,   v -> v.set(Float.NaN, Float.NaN, Float.NaN));
    public static final Type<PointF2>   PF2    = register(PointF2::new,   p -> p.set(Float.NaN, Float.NaN));
    public static final Type<PointI2>   PI2    = register(PointI2::new,   p -> p.set(Integer.MIN_VALUE, Integer.MIN_VALUE));
    public static final Type<PointI3>   PI3    = register(PointI3::new,   p -> p.set(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
    public static final Type<PointI4>   PI4    = register(PointI4::new,   p -> p.set(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
    public static final Type<Circle>    CIRCLE = register(Circle::new,    c -> c.set(Float.NaN, Float.NaN, Float.NaN));
    public static final Type<Color>     COLOR  = register(Color::new,     c -> c.set(Float.NaN, Float.NaN, Float.NaN, Float.NaN));
    public static final Type<Rectangle> RECT   = register(Rectangle::new, r -> r.set(Float.NaN, Float.NaN, Float.NaN, Float.NaN));

    // ------------------------------------------------------------------------
    // Vector types
    // ------------------------------------------------------------------------

    public static Vector2 vec2()                 { return obtain(VEC2); }
    public static Vector2 vec2(float x, float y) { return vec2().set(x, y); }
    public static Vector2 vec2(Vector2 other)    { return vec2().set(other); }

    public static Vector3 vec3()                          { return obtain(VEC3); }
    public static Vector3 vec3(float x, float y)          { return vec3(x, y, 0); }
    public static Vector3 vec3(float x, float y, float z) { return vec3().set(x, y, z); }
    public static Vector3 vec3(Vector2 xy)                { return vec3(xy.x, xy.y); }
    public static Vector3 vec3(Vector3 other)             { return vec3().set(other); }

    // ------------------------------------------------------------------------
    // Point types
    // ------------------------------------------------------------------------

    public static PointF2 pf2()                 { return obtain(PF2); }
    public static PointF2 pf2(float x, float y) { return pf2().set(x, y); }

    public static PointI2 pi2()             { return obtain(PI2); }
    public static PointI2 pi2(int x, int y) { return pi2().set(x, y); }

    public static PointI3 pi3()                    { return obtain(PI3); }
    public static PointI3 pi3(int x, int y, int z) { return pi3().set(x, y, z); }

    public static PointI4 pi4()                           { return obtain(PI4); }
    public static PointI4 pi4(int x, int y, int z, int w) { return pi4().set(x, y, z, w); }

    // ------------------------------------------------------------------------
    // Shapes and color
    // ------------------------------------------------------------------------

    public static Circle circle()             { return obtain(CIRCLE); }
    public static Circle circle(Circle other) { return circle(other.x, other.y, other.radius); }
    public static Circle circle(float x, float y, float r) {
        // NOTE: Circle is the only Shape2D that doesn't return 'this' for chaining
        var c = circle();
        c.set(x, y, r);
        return c;
    }

    public static Rectangle rect()                                     { return obtain(RECT); }
    public static Rectangle rect(float x, float y, float w, float h)   { return rect().set(x, y, w, h); }
    public static Rectangle rect(Rectangle other)                      { return rect().set(other); }

    public static Color color()                                   { return obtain(COLOR); }
    public static Color color(float r, float g, float b)          { return color(r, g, b, 1); }
    public static Color color(float r, float g, float b, float a) { return color().set(r, g, b, a); }
    public static Color color(Color other)                        { return color().set(other); }
}
// @formatter:on
