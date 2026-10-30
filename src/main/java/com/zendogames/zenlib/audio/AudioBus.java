package com.zendogames.zenlib.audio;

/**
 * A named volume that sounds and music play through, implemented by enum constants the way
 * {@code Flag} is: the library's are {@link ZenBus}, a game adds its own with
 * {@code Audio.register(GameBus.values())} in {@code ZenGame.init()}. Buses form a tree through
 * {@link #parent()}: a play's gain is its bus's volume times every ancestor's, zero if any of them
 * is muted, so one MASTER slider scales everything and a MUSIC or SFX slider scales its branch.
 * Volume and mute live in a {@link State} owned by the constant and persist through {@code Prefs}
 * under {@code audio.<name>} (lowercase), so a settings menu is one slider per bus and nothing else.
 * <pre>
 *   public enum GameBus implements AudioBus {
 *       VOICE   (ZenBus.SFX, 1f),
 *       AMBIENT (ZenBus.SFX, 0.7f);
 *       private final AudioBus parent;
 *       private final State state;
 *       GameBus(AudioBus parent, float initialVolume) { this.parent = parent; this.state = new State(initialVolume); }
 *       public AudioBus parent() { return parent; }
 *       public State state()     { return state; }
 *   }
 * </pre>
 */
public interface AudioBus {

    String name();

    /** The bus this one's volume multiplies into, null for the root. Register parents before children. */
    AudioBus parent();

    State state();

    /** This bus's own volume, 0..1; the setter clamps, persists and re-applies to what is playing. */
    default float volume()             { return state().volume; }
    default void  volume(float volume) { Audio.volume(this, volume); }
    default boolean muted()            { return state().muted; }
    default void  mute(boolean muted)  { Audio.mute(this, muted); }

    /** Whether voices on this bus freeze under {@code Audio.pause()} (a pause menu). Music and menu buses say no. */
    default boolean pausable() { return true; }

    /** Own volume times every ancestor's; 0 while this or any ancestor is muted. */
    default float gain() {
        var gain = 1f;
        for (AudioBus bus = this; bus != null; bus = bus.parent()) {
            if (bus.muted()) return 0f;
            gain *= bus.volume();
        }
        return gain;
    }

    /** Mutable value of a single bus, owned by the enum constant that declares it. */
    class State {
        final float initial;
        float volume;
        boolean muted;

        public State(float initialVolume) {
            this.initial = initialVolume;
            this.volume = initialVolume;
        }

        /** Back to the default; {@code Audio.register} then reads the saved value over it. */
        void reset() {
            volume = initial;
            muted = false;
        }
    }
}
