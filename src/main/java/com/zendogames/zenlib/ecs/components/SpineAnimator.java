package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.spine.*;
import com.zendogames.zenlib.FramePool;
import com.zendogames.zenlib.assets.SpineData;
import com.zendogames.zenlib.assets.SpineType;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;

import java.util.function.Consumer;

/**
 * A Spine skeleton the entity animates and draws: its own {@link #skeleton} (the pose) and
 * {@link #state} (which animations play on which track) over the shared data of a {@link SpineType}.
 * {@code SpineSystem} advances the state and poses the skeleton at the entity position once per
 * tick, so animations freeze under frame step and events fire on the tick; the {@code RenderSystem}
 * draws it in z-order with every other {@link Renderable}, re-posing at the interpolated position
 * without stepping physics. The Spine API is the API: the methods here only cover the common calls.
 * <p>
 * {@link #origin} is the offset from the entity position to the skeleton root, usually the feet
 * and so {@code (0, 0)}; {@link #scale} scales the skeleton about the root (a negative x flips it,
 * which {@link #facing} does); {@link #size} and {@link #rect} are the setup-pose bounds from the export.
 */
public class SpineAnimator extends Renderable {

    public static final ComponentType<SpineAnimator> TYPE = ComponentType.register(SpineAnimator.class);
    public static SpineAnimator get(Entity e) { return TYPE.get(e); }
    public static boolean       has(Entity e) { return TYPE.has(e); }

    /** Shared by every skeleton: scratch arrays and a clipper, no state between draws and no GPU resources. */
    private static final SkeletonRenderer renderer = new SkeletonRenderer();

    public final Skeleton skeleton;
    public final AnimationState state;
    public final boolean premultipliedAlpha;

    /** -1 or +1; flips the skeleton by negating {@code scale.x}. */
    public int facing = 1;

    /** Where {@link #scale} returns to, at {@link #scaleReturnSpeed} per second; set {@code scale} for a squash or stretch. */
    public final Vector2 defaultScale = new Vector2(1, 1);
    public float scaleReturnSpeed = 2f;

    public SpineAnimator(SpineType type) {
        this(type.get());
    }

    public SpineAnimator(SpineType type, String animation) {
        this(type);
        play(animation);
    }

    public SpineAnimator(SpineData data, String animation) {
        this(data);
        play(animation);
    }

    public SpineAnimator(SpineData data) {
        this.skeleton = new Skeleton(data.skeleton());
        this.state = new AnimationState(data.mixes());
        this.premultipliedAlpha = data.premultipliedAlpha();
        this.size.set(data.skeleton().getWidth(), data.skeleton().getHeight());
    }

    @Override
    public void render(Batch batch, float x, float y) {
        pose(x, y, Physics.pose);
        skeleton.getColor().set(tint);

        // The renderer leaves the blend function wherever the last slot put it; put it back for the next sprite
        int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
        int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
        renderer.setPremultipliedAlpha(premultipliedAlpha);
        renderer.draw(batch, skeleton);
        batch.setBlendFunctionSeparate(src, dst, srcAlpha, dstAlpha);
    }

    /** Moves the root to the entity position {@code (x, y)}, applies {@link #scale}, and computes the bones' world transforms. */
    public void pose(float x, float y, Physics physics) {
        skeleton.setPosition(x - origin.x * scale.x, y - origin.y * scale.y);
        skeleton.setScale(scale.x, scale.y);
        skeleton.updateWorldTransform(physics);
    }

    /** The setup-pose bounds from the export, placed and scaled like the skeleton; a frame-pooled scratch value. */
    @Override
    public Rectangle rect(float x, float y) {
        var data = skeleton.getData();
        float rootX = x - origin.x * scale.x, rootY = y - origin.y * scale.y;
        float w = size.x * scale.x, h = size.y * scale.y;
        float left   = rootX + data.getX() * scale.x;
        float bottom = rootY + data.getY() * scale.y;
        return FramePool.rect(Math.min(left, left + w), Math.min(bottom, bottom + h), Math.abs(w), Math.abs(h));
    }

    // ------------------------------------------------------------------------
    // Animations: track 0 unless given; start restarts, play switches only when different (as Animator)
    // ------------------------------------------------------------------------

    /** Switches track 0 to {@code animation}, looping, unless it is already playing; returns the entry either way. */
    public AnimationState.TrackEntry play(String animation) { return play(0, animation, true); }
    public AnimationState.TrackEntry play(String animation, boolean loop) { return play(0, animation, loop); }

    public AnimationState.TrackEntry play(int track, String animation, boolean loop) {
        var current = state.getTrack(track);
        if (current != null && current.getAnimation().getName().equals(animation)) return current;
        return state.setAnimation(track, animation, loop);
    }

    /** Switches track 0 to {@code animation} and restarts it even if it is already playing. */
    public AnimationState.TrackEntry start(String animation) { return start(0, animation, true); }
    public AnimationState.TrackEntry start(String animation, boolean loop) { return start(0, animation, loop); }

    public AnimationState.TrackEntry start(int track, String animation, boolean loop) {
        return state.setAnimation(track, animation, loop);
    }

    /** Queues {@code animation} on track 0 after the current one ends, or after {@code delay} seconds when positive. */
    public AnimationState.TrackEntry queue(String animation, boolean loop, float delay) { return queue(0, animation, loop, delay); }

    public AnimationState.TrackEntry queue(int track, String animation, boolean loop, float delay) {
        return state.addAnimation(track, animation, loop, delay);
    }

    /** Clears track 0 by crossfading to the setup pose over {@code mix} seconds. */
    public void stop(float mix) { state.setEmptyAnimation(0, mix); }

    public boolean is(String animation)    { return is(0, animation); }
    public boolean isNot(String animation) { return !is(0, animation); }

    public boolean is(int track, String animation) {
        var current = state.getTrack(track);
        return current != null && current.getAnimation().getName().equals(animation);
    }

    /** True when track 0 is empty or its animation has played through once; a looping animation never completes. */
    public boolean isComplete()   { return isComplete(0); }
    public boolean isIncomplete() { return !isComplete(0); }

    public boolean isComplete(int track) {
        var current = state.getTrack(track);
        return current == null || current.isComplete();
    }

    /** Runs {@code listener} for every animation event (authored keys like "footstep"), on the tick the animation reaches it. */
    public void onEvent(Consumer<Event> listener) {
        state.addListener(new AnimationState.AnimationStateAdapter() {
            @Override public void event(AnimationState.TrackEntry entry, Event event) { listener.accept(event); }
        });
    }

    // ------------------------------------------------------------------------
    // Skeleton
    // ------------------------------------------------------------------------

    /** Switches skin by name and resets the slots to the setup pose so the new skin's attachments show. */
    public void skin(String name) {
        skeleton.setSkin(name);
        skeleton.setupPoseSlots();
    }

    /** A bone's applied pose: its world position and rotation after the last pose (a muzzle, a hand); null when there is no such bone. */
    public BonePose bonePose(String name) {
        var bone = skeleton.findBone(name);
        return bone == null ? null : bone.getAppliedPose();
    }
}
