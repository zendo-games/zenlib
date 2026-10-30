package com.zendogames.zenlib.assets;

import com.badlogic.gdx.audio.Music;
import com.zendogames.zenlib.audio.Audio;
import com.zendogames.zenlib.audio.AudioBus;
import com.zendogames.zenlib.audio.ZenBus;

/**
 * A long track. Desktop streams it from disk; the web backend decodes the whole file into memory
 * (its Howl is built over the bytes), so web builds want compressed tracks and not too many of
 * them. One plays at a time through {@link Audio#music}: {@code GameMusic.LEVEL.play()} crossfades
 * from whatever is playing. The {@link #volume()} trim, {@link #bus()} and {@link #looping()} are
 * the enum's to declare; the stock {@code MusicLoader} does nothing but open the file.
 */
public interface MusicType extends AssetType<Music> {

    /** Assets-relative path, eg. {@code "audio/music/level.ogg"}. */
    String file();

    default AudioBus bus()    { return ZenBus.MUSIC; }
    default float volume()    { return 1f; }
    default boolean looping() { return true; }

    @Override default void  load(Assets assets) { assets.mgr.load(file(), Music.class); }
    @Override default Music init(Assets assets) { return assets.mgr.get(file(), Music.class); }

    default void play()                  { Audio.music(this); }
    default void play(float fadeSeconds) { Audio.music(this, fadeSeconds); }
}
