package com.zendogames.zenlib.audio;

/**
 * The library's audio buses, registered first by {@code Audio.init()}. A game's buses parent
 * one of these, usually {@link #MASTER} or {@link #SFX}. {@link #MUSIC} and {@link #UI} do not
 * pause with the game: a pause menu keeps its music and its clicks.
 */
// @formatter:off
public enum ZenBus implements AudioBus {
      MASTER (null,   1f)
    , MUSIC  (MASTER, 0.5f) { @Override public boolean pausable() { return false; } }
    , SFX    (MASTER, 0.5f)
    , UI     (MASTER, 0.5f) { @Override public boolean pausable() { return false; } }
    ;

    private final AudioBus parent;
    private final State state;

    ZenBus(AudioBus parent, float initialVolume) {
        this.parent = parent;
        this.state = new State(initialVolume);
    }

    @Override public AudioBus parent() { return parent; }
    @Override public State state()     { return state; }
}
// @formatter:on
