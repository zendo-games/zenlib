package com.zendogames.zenlib.audio;

import com.badlogic.gdx.audio.Music;

/** A {@code Music} that records its state; {@link #complete()} fires the completion listener like a track running out. */
final class FakeMusic implements Music {

    int plays, pauses, stops, listenerSets;
    boolean playing, looping;
    float volume = 1f;
    float position;
    OnCompletionListener listener;

    void complete() {
        playing = false;
        if (listener != null) listener.onCompletion(this);
    }

    @Override public void play()  { plays++; playing = true; }
    @Override public void pause() { pauses++; playing = false; }
    @Override public void stop()  { stops++; playing = false; position = 0f; }

    @Override public boolean isPlaying()                 { return playing; }
    @Override public void setLooping(boolean isLooping)  { looping = isLooping; }
    @Override public boolean isLooping()                 { return looping; }
    @Override public void setVolume(float volume)        { this.volume = volume; }
    @Override public float getVolume()                   { return volume; }
    @Override public void setPan(float pan, float volume){ this.volume = volume; }
    @Override public void setPosition(float position)    { this.position = position; }
    @Override public float getPosition()                 { return position; }
    @Override public void dispose()                      {}
    @Override public void setOnCompletionListener(OnCompletionListener listener) { this.listener = listener; listenerSets++; }
}
