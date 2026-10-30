package com.zendogames.zenlib.audio;

import com.badlogic.gdx.audio.Music;
import com.zendogames.zenlib.assets.Asset;
import com.zendogames.zenlib.assets.MusicType;

/** Music types over {@link FakeMusic}s; {@code get()} returns the fake. {@link #reset()} per test. */
// @formatter:off
enum TestMusic implements MusicType {
      A      ("a.ogg")
    , B      ("b.ogg")
    , C      ("c.ogg")
    , JINGLE ("jingle.ogg") { @Override public boolean looping() { return false; } }
    ;
    private final Asset<Music> asset = new Asset<>(this);
    private final String file;
    FakeMusic fake = new FakeMusic();

    TestMusic(String file) {
        this.file = "audio/music/" + file;
    }

    @Override public Asset<Music> asset() { return asset; }
    @Override public String file()         { return file; }
    @Override public Music get()           { return fake; }

    static void reset() {
        for (var music : values()) music.fake = new FakeMusic();
    }
}
// @formatter:on
