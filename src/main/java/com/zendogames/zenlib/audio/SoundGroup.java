package com.zendogames.zenlib.audio;

import com.badlogic.gdx.math.MathUtils;
import com.zendogames.zenlib.assets.SoundType;
import com.zendogames.zenlib.utils.Weighted;

/**
 * Variations of one event (footsteps, grunts, impacts): a {@link Weighted} pick over sound types
 * with the same play sugar as a single {@link SoundType}. Declared once beside the sounds it draws
 * from, as a static field of the enum:
 * <pre>
 *   public enum GameSound implements SoundType {
 *       STEP1(...), STEP2(...), STEP3(...);
 *       public static final SoundGroup STEPS = SoundGroup.of(STEP1, STEP2, STEP3).noRepeat().cooldown(0.1f);
 *   }
 *   ... GameSound.STEPS.play(0.8f);
 * </pre>
 * The group's {@link #cooldown} is checked before the pick (a per-constant cooldown would let the
 * pick dodge it); {@link #noRepeat()} picks from the other members when there is more than one.
 */
public final class SoundGroup {

    public final Weighted<SoundType> sounds = new Weighted<>();
    float cooldown;
    boolean noRepeat;
    SoundType last;
    float lastStart = Float.NEGATIVE_INFINITY;

    public static SoundGroup of(SoundType... equallyLikely) {
        var group = new SoundGroup();
        for (var sound : equallyLikely) group.add(sound, 1f);
        return group;
    }

    public SoundGroup add(SoundType sound, float weight) { sounds.add(sound, weight); return this; }
    public SoundGroup cooldown(float seconds)            { this.cooldown = seconds; return this; }
    public SoundGroup noRepeat()                         { this.noRepeat = true; return this; }

    public float cooldown()     { return cooldown; }
    public boolean isNoRepeat() { return noRepeat; }
    /** The member picked last, null before the first pick. */
    public SoundType last()     { return last; }

    /** A weighted pick; under {@link #noRepeat()} the previous member is left out of the roll. */
    public SoundType pick() {
        var pick = (noRepeat && last != null && sounds.size() > 1) ? pickExcluding(last) : sounds.pick();
        last = pick;
        return pick;
    }

    private SoundType pickExcluding(SoundType excluded) {
        var excludedWeight = 0f;
        for (int i = 0; i < sounds.size(); i++) {
            if (sounds.get(i) == excluded) excludedWeight += sounds.weight(i);
        }
        var roll = MathUtils.random(sounds.total() - excludedWeight);
        var cumulative = 0f;
        SoundType candidate = null;
        for (int i = 0; i < sounds.size(); i++) {
            if (sounds.get(i) == excluded) continue;
            candidate = sounds.get(i);
            cumulative += sounds.weight(i);
            if (roll <= cumulative) return candidate;
        }
        return candidate; // float rounding, as in Weighted.pick
    }

    public long play()                             { return Audio.play(this, 1f, 0f); }
    public long play(float volume)                 { return Audio.play(this, volume, 0f); }
    public long play(float volume, float pan)      { return Audio.play(this, volume, pan); }
    public long playAt(float worldX)               { return Audio.play(this, Audio.attenuation(worldX), Audio.pan(worldX)); }
    public long playAt(float volume, float worldX) { return Audio.play(this, volume * Audio.attenuation(worldX), Audio.pan(worldX)); }
}
