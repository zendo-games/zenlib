package com.zendogames.zenlib.assets;

import com.badlogic.gdx.audio.Sound;
import com.zendogames.zenlib.audio.Audio;
import com.zendogames.zenlib.audio.AudioBus;
import com.zendogames.zenlib.audio.ZenBus;

/**
 * A short, fully decoded sound effect, played fire-and-forget through {@link Audio}. The enum
 * declares the file and, optionally, how the sound behaves: the {@link #bus()} it plays on, an
 * authored {@link #volume()} trim, a {@link #pitch()} (several constants may share one file at
 * different pitches), a random {@link #pitchVariance()}, a {@link #cooldown()} between starts and
 * a {@link #maxVoices()} cap so a burst of the same event does not stack. The play sugar here is
 * the everyday call, {@code GameSound.JUMP.play()} from any enum body or state, no service
 * reference needed.
 * <pre>
 *   public enum GameSound implements SoundType {
 *         JUMP  ("audio/sounds/jump.ogg")
 *       , STEP  ("audio/sounds/step.ogg")  { public float cooldown() { return 0.12f; } public float pitchVariance() { return 0.1f; } }
 *       , CLICK ("audio/sounds/click.ogg") { public AudioBus bus() { return ZenBus.UI; } }
 *       ;
 *       private final Asset&lt;Sound&gt; asset = new Asset&lt;&gt;(this);
 *       private final String file;
 *       GameSound(String file) { this.file = file; }
 *       public Asset&lt;Sound&gt; asset() { return asset; }
 *       public String file()         { return file; }
 *   }
 * </pre>
 * The stock {@code AssetManager} {@code SoundLoader} decodes the whole file on load (desktop: an
 * OpenAL buffer; web: a Howl over the bytes), so keep sounds short and use {@link MusicType} for tracks.
 */
public interface SoundType extends AssetType<Sound> {

    /** Assets-relative path, eg. {@code "audio/sounds/jump.ogg"}. */
    String file();

    default AudioBus bus() { return ZenBus.SFX; }

    /** Authored gain trim, multiplied into every play. */
    default float volume() { return 1f; }

    /** Playback rate in libGDX's 0.5..2 range. */
    default float pitch() { return 1f; }

    /** Random +- offset added to the pitch on every play; 0.05 to 0.15 takes the machine-gun feel out of repeats. */
    default float pitchVariance() { return 0f; }

    /** Seconds after a start during which another start of this constant is refused. */
    default float cooldown() { return 0f; }

    /** Most voices of this constant alive at once (alive by {@link #duration()}); 0 for no cap. */
    default int maxVoices() { return 0; }

    /**
     * Seconds the sound plays for. libGDX gives no way to ask the backend, so {@link Audio} ages voices
     * out by this estimate; it matters for {@link #maxVoices()}, {@code Audio.pause()} and the debug view.
     * Set it for sounds longer than the default, and short rather than long when unsure.
     */
    default float duration() { return Audio.DEFAULT_DURATION; }

    @Override default void  load(Assets assets) { assets.mgr.load(file(), Sound.class); }
    @Override default Sound init(Assets assets) { return assets.mgr.get(file(), Sound.class); }

    // Play sugar; the rules (bus gain, cooldown, voice cap, pause) are Audio's.

    default long play()                        { return Audio.play(this, 1f, 0f); }
    default long play(float volume)            { return Audio.play(this, volume, 0f); }
    default long play(float volume, float pan) { return Audio.play(this, volume, pan); }

    /** Panned and attenuated by a world x against {@code Audio.listener}. */
    default long playAt(float worldX)               { return Audio.playAt(this, 1f, worldX); }
    default long playAt(float volume, float worldX) { return Audio.playAt(this, volume, worldX); }

    /** A looping voice: keep the id for {@code Audio.stop(id)}, {@code Audio.volume(id, v)} and friends. */
    default long loop()                        { return Audio.loop(this, 1f, 0f); }
    default long loop(float volume)            { return Audio.loop(this, volume, 0f); }
    default long loop(float volume, float pan) { return Audio.loop(this, volume, pan); }
}
