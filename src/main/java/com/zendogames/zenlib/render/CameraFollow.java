package com.zendogames.zenlib.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.zendogames.zenlib.ZenFlag;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.utils.Calc;

/**
 * Moves a world camera toward a {@link Target} with exponential smoothing, keeping the view inside
 * {@link #bounds} when one is set. Not an entity system: the camera must be in place before the
 * world view sets the batch projection, so the screen calls {@link #update} from its
 * {@code update(dt)} with the frame delta, before {@code super.update(dt)}.
 * <pre>{@code
 * cameraFollow = new CameraFollow(worldCamera);
 * cameraFollow.bounds = new Rectangle(0, 0, mapWidth, mapHeight);
 * cameraFollow.follow(player);
 * }</pre>
 */
public class CameraFollow {

    /** Default smoothing; higher is faster, see {@link Calc#smoothDamp}. */
    public static final float PAN_SMOOTHING = 10f;

    public final OrthographicCamera camera;

    /** World rectangle the view stays inside; null for unconstrained. An axis where the view is larger centres instead. */
    public Rectangle bounds;

    public float smoothing = PAN_SMOOTHING;

    private final Vector2 pos = new Vector2();
    private Target target;
    private boolean initialized;

    /** Whole-pixel part of the smoothing lag, moved with a full pixel of hysteresis; see {@link #settle}. */
    private int lagX, lagY;

    public CameraFollow(OrthographicCamera camera) {
        this.camera = camera;
    }

    /** Follows the entity's {@link Position}, interpolated like the render pass while {@code RENDER_INTERP} is on. */
    public void follow(Entity entity) {
        follow(new Target.Follow(entity));
    }

    public void follow(Vector2 point) {
        follow(new Target.Point(point));
    }

    public void follow(Entity entity, boolean snap) {
        follow(new Target.Follow(entity), snap);
    }

    /** Sets the target and jumps to it on the next update. */
    public void follow(Target target) {
        follow(target, true);
    }

    /** Sets the target; {@code snap} false pans there from the current position instead of jumping. */
    public void follow(Target target, boolean snap) {
        this.target = target;
        if (snap) {
            this.initialized = false;
        }
    }

    public Target target() {
        return target;
    }

    /** Snap to the target on the next update instead of panning. */
    public void snap() {
        initialized = false;
    }

    public void update(float dt) {
        if (target == null) return;

        float tx = target.x();
        float ty = target.y();
        if (!initialized) {
            initialized = true;
            pos.set(tx, ty);
        } else {
            pos.set(Calc.smoothDamp(pos.x, tx, smoothing, dt),
                    Calc.smoothDamp(pos.y, ty, smoothing, dt));
        }

        // Whole pixels, in step with the target: the render pass rounds the target's position,
        // so the camera is that rounded position plus a whole-pixel lag. The lag can't simply
        // be rounded: an integer position with a sub-pixel velocity moves stop-go per tick,
        // so the smoothed lag wobbles about half a pixel every tick and a plain round would
        // flip the followed sprite constantly.
        lagX = settle(lagX, pos.x - tx);
        lagY = settle(lagY, pos.y - ty);
        float cx = MathUtils.round(tx) + lagX;
        float cy = MathUtils.round(ty) + lagY;

        float halfW = camera.viewportWidth  * camera.zoom / 2f;
        float halfH = camera.viewportHeight * camera.zoom / 2f;
        if (bounds != null) {
            cx = constrain(cx, bounds.x, bounds.width,  halfW);
            cy = constrain(cy, bounds.y, bounds.height, halfH);
        }

        // Round the view's edges, not its center, so an odd view size can't land on a half pixel
        camera.position.set(
                MathUtils.round(cx - halfW) + halfW,
                MathUtils.round(cy - halfH) + halfH, 0);
        camera.update();
    }

    /** Moves the whole--pixel lag only once the continuous lag is a full pixel past it, so a wobble around half pixel never flips it. */
    private static int settle(int lag, float continuous) {
        float diff = continuous - lag;
        return (diff >= 1f || diff <= -1f) ? lag + (int) diff : lag; // (int) truncates toward zero
    }

    /** Clamps the view centre inside {@code [min, min + extent]}; centres when the view is larger than the extent. */
    private static float constrain(float value, float min, float extent, float half) {
        if (2f * half >= extent) {
            return min + extent / 2f;
        }
        return Calc.clampf(value, min + half, min + extent - half);
    }

    /** Where the camera wants to be, re-read every update. */
    public sealed interface Target {
        float x();
        float y();

        /** An entity's position; holds its last known position once the entity is dead. */
        record Follow(Entity entity, Position position) implements Target {
            public Follow(Entity entity) {
                this(entity, Position.get(entity));
            }

            @Override
            public float x() {
                return ZenFlag.RENDER_INTERP.on() ? position.renderX(alpha()) : position.x;
            }

            @Override
            public float y() {
                return ZenFlag.RENDER_INTERP.on() ? position.renderY(alpha()) : position.y;
            }

            private float alpha() {
                return entity.isAlive() ? entity.world().alpha() : 1f;
            }
        }

        record Point(Vector2 point) implements Target {
            @Override public float x() { return point.x; }
            @Override public float y() { return point.y; }
        }
    }
}
