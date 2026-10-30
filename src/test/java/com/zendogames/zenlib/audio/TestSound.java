package com.zendogames.zenlib.audio;

import com.badlogic.gdx.audio.Sound;
import com.zendogames.zenlib.assets.Asset;
import com.zendogames.zenlib.assets.SoundType;

/** Sound types over {@link FakeSound}s; {@code get()} returns the fake, so nothing is loaded. {@link #reset()} per test. */
// @formatter:off
enum TestSound implements SoundType {
      BELL      ("bell.ogg")
    , BELL_HIGH ("bell.ogg")   { @Override public float pitch() { return 1.2f; } }
    , VARIED    ("varied.ogg") { @Override public float pitchVariance() { return 0.1f; } }
    , STEP      ("step.ogg")   { @Override public float cooldown() { return 0.2f; } @Override public float duration() { return 0.1f; } }
    , POP       ("pop.ogg")    { @Override public int maxVoices() { return 2; } @Override public float duration() { return 0.3f; } }
    , HUM       ("hum.ogg")
    , QUIET     ("quiet.ogg")  { @Override public float volume() { return 0.5f; } }
    , CLICK     ("click.ogg")  { @Override public AudioBus bus() { return ZenBus.UI; } }
    , VOICE     ("voice.ogg")  { @Override public AudioBus bus() { return TestBus.VOICE; } }
    ;
    private final Asset<Sound> asset = new Asset<>(this);
    private final String file;
    FakeSound fake = new FakeSound();

    TestSound(String file) {
        this.file = "audio/sounds/" + file;
    }

    @Override public Asset<Sound> asset() { return asset; }
    @Override public String file()         { return file; }
    @Override public Sound get()           { return fake; }

    static void reset() {
        for (var sound : values()) sound.fake = new FakeSound();
    }
}
// @formatter:on
