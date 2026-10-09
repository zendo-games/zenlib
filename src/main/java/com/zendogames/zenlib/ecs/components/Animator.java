package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.zendogames.zenlib.assets.AnimType;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.utils.Draw;

/**
 * The animation an entity is playing and how far into it. {@code AnimationSystem} advances
 * {@link #time} per tick, picks {@link #keyframe}, eases {@link #scale} back to
 * {@link #defaultScale} and applies {@link #facing} to its x. Time is float seconds:
 * presentation state.
 */
public class Animator extends Renderable {

    public static final ComponentType<Animator> TYPE = ComponentType.register(Animator.class);
    public static Animator get(Entity e) { return TYPE.get(e); }
    public static boolean  has(Entity e) { return TYPE.has(e); }

    /** The asset key when the animation came from one, for {@link #is}; null for a raw animation or frame. */
    public AnimType type;
    public Animation<TextureRegion> animation;
    public TextureRegion keyframe;

    public float time;

    /** -1 or +1; flips the frame by negating {@code scale.x}. */
    public int facing = 1;

    /** Where {@link #scale} returns to, at {@link #scaleReturnSpeed} per second; set {@code scale} for a squash or stretch. */
    public final Vector2 defaultScale = new Vector2(1, 1);
    public float scaleReturnSpeed = 2f;

    public Animator(AnimType type) {
        this(type.get());
        this.type = type;
    }

    public Animator(AnimType type, float originX, float originY) {
        this(type);
        this.origin.set(originX, originY);
    }

    public Animator(AnimType type, float originX, float originY, float width, float height) {
        this(type, originX, originY);
        this.size.set(width, height);
    }

    public Animator(Animation<TextureRegion> animation) {
        this(animation.getKeyFrame(0));
        this.animation = animation;
    }

    public Animator(TextureRegion keyframe) {
        this.keyframe = keyframe;
        this.size.set(keyframe.getRegionWidth(), keyframe.getRegionHeight());
    }

    @Override
    public void render(SpriteBatch batch, float x, float y) {
        if (keyframe == null) return;
        Draw.region(batch, keyframe, rect(x, y), tint);
    }

    /** Switches animation and restarts it; returns its duration. */
    public float start(AnimType type) {
        time = 0;
        return play(type);
    }

    /** Switches animation keeping the current time, so a per-tick {@code play} of the same type is harmless; returns its duration. */
    public float play(AnimType type) {
        this.type = type;
        return play(type.get());
    }

    public float play(Animation<TextureRegion> animation) {
        if (animation == null) return 0;
        this.animation = animation;
        return animation.getAnimationDuration();
    }

    public boolean is(AnimType type)    { return this.type == type; }
    public boolean isNot(AnimType type) { return this.type != type; }

    public boolean hasAnimation() { return animation != null; }
    public boolean isComplete()   { return hasAnimation() && animation.isAnimationFinished(time); }
    public boolean isIncomplete() { return !isComplete(); }
}
