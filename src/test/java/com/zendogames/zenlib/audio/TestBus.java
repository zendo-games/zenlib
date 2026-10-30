package com.zendogames.zenlib.audio;

/** A game's buses as the javadoc shows them: both under SFX, AMBIENT keeps playing through a pause. */
// @formatter:off
enum TestBus implements AudioBus {
      VOICE   (ZenBus.SFX, 1f)
    , AMBIENT (ZenBus.SFX, 0.5f) { @Override public boolean pausable() { return false; } }
    ;
    private final AudioBus parent;
    private final State state;

    TestBus(AudioBus parent, float initialVolume) {
        this.parent = parent;
        this.state = new State(initialVolume);
    }

    @Override public AudioBus parent() { return parent; }
    @Override public State state()     { return state; }
}
// @formatter:on
