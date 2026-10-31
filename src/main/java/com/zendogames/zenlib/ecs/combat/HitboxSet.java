package com.zendogames.zenlib.ecs.combat;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.zendogames.zenlib.assets.AnimType;

/**
 * A fighter type's hitboxes over its animations: the {@link #base} boxes it always has, and per
 * animation ("clip", the Spine animation name, or the sprite {@code AnimType}'s {@code name()})
 * the {@link Window}s of time in which more boxes are out. One instance is shared by every entity
 * of the type; swap {@code Hitboxes.set} to change the whole table (a weapon pickup).
 * <p>
 * Each tick a fighter's boxes are resolved in order: base, then the windows of the playing clip
 * that contain its time. <b>A box replaces an earlier one with the same name</b>, so a crouch clip
 * can shrink "body" for its length and a window can swap "fist" for a bigger one late in a swing.
 * <pre>{@code
 * new HitboxSet()
 *     .base(Hitbox.hurt("body", rect(-8, 0, 16, 44)))
 *     .clip("crouch", Hitbox.hurt("body", rect(-8, 0, 16, 24)))
 *     .window(GameAnim.PUNCH, 0.10f, 0.22f, Hitbox.hit("fist", rect(10, 30, 14, 8), JAB));
 * }</pre>
 * Plain data, built in code for now; the shape a future editor reads and writes.
 */
public class HitboxSet {

    /** Windows per clip: {@code HitSystem} keeps the open ones as the bits of an {@code int}. */
    public static final int MAX_WINDOWS = Integer.SIZE;

    /**
     * Boxes out from {@code start} (inclusive) to {@code end} (exclusive) seconds into a clip. Each
     * window is one activation: it strikes a target at most once while it stays open (see
     * {@code HitSystem}), so a two-hit move is two windows.
     */
    public record Window(float start, float end, Hitbox... boxes) {
        public Window {
            if (end <= start) throw new GdxRuntimeException("Window: end %.3f must be after start %.3f".formatted(end, start));
        }
        public boolean contains(float time) { return time >= start && time < end; }
    }

    /** Out in every clip unless a window box of the same name replaces it. */
    public final Array<Hitbox> base = new Array<>();

    /** Clip name to its windows, in authoring order. */
    public final ObjectMap<String, Array<Window>> clips = new ObjectMap<>();

    public HitboxSet base(Hitbox... boxes) {
        base.addAll(boxes);
        return this;
    }

    /** Boxes out from {@code start} to {@code end} seconds into {@code clip}. */
    public HitboxSet window(String clip, float start, float end, Hitbox... boxes) {
        var windows = clips.get(clip);
        if (windows == null) {
            windows = new Array<>();
            clips.put(clip, windows);
        }
        if (windows.size == MAX_WINDOWS) {
            throw new GdxRuntimeException("HitboxSet: clip '%s' already has %d windows".formatted(clip, MAX_WINDOWS));
        }
        windows.add(new Window(start, end, boxes));
        return this;
    }

    /** Boxes out from {@code start} to {@code end} seconds into the sprite clip {@code clip}. */
    public HitboxSet window(AnimType clip, float start, float end, Hitbox... boxes) {
        return window(clip.name(), start, end, boxes);
    }

    /** Boxes out for the whole of {@code clip}, however long it plays. */
    public HitboxSet clip(String clip, Hitbox... boxes) {
        return window(clip, 0f, Float.POSITIVE_INFINITY, boxes);
    }

    /** Boxes out for the whole of the sprite clip {@code clip}. */
    public HitboxSet clip(AnimType clip, Hitbox... boxes) {
        return clip(clip.name(), boxes);
    }

    /** The windows authored for {@code clip}; null when it has none. */
    public Array<Window> windows(String clip) {
        return (clip == null) ? null : clips.get(clip);
    }
}
