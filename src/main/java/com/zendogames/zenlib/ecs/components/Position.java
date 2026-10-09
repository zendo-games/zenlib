package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.math.MathUtils;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;

/**
 * Integer world position, plus where the entity was at the start of the last tick so a render pass
 * can interpolate between the two. The rule: anything that moves an entity during a tick calls
 * {@link #savePrev()} before it moves, once per tick. {@link #teleport} moves without a streak.
 */
public class Position extends PointI2 implements Component {

    public static final ComponentType<Position> TYPE = ComponentType.register(Position.class);
    public static Position get(Entity e) { return TYPE.get(e); }
    public static boolean  has(Entity e) { return TYPE.has(e); }

    /** Position at the start of the last tick; see {@link #savePrev()}. */
    public int prevX, prevY;

    public Position(int x, int y) {
        super(x, y);
        this.prevX = x;
        this.prevY = y;
    }

    public Position(float x, float y) {
        this(MathUtils.round(x), MathUtils.round(y));
    }

    /** Records the current position as last tick's. Call once at the start of the tick, before moving. */
    public void savePrev() {
        prevX = x;
        prevY = y;
    }

    /** Sets current and previous together, so the next render pass doesn't interpolate across the jump. */
    public Position teleport(int x, int y) {
        set(x, y);
        savePrev();
        return this;
    }

    /** Interpolated x for rendering, {@code alpha} in {@code [0, 1)} from {@code World.alpha()}. */
    public float renderX(float alpha) { return MathUtils.lerp(prevX, x, alpha); }
    public float renderY(float alpha) { return MathUtils.lerp(prevY, y, alpha); }

    public boolean is(int x, int y) {
        return this.x == x && this.y == y;
    }
}