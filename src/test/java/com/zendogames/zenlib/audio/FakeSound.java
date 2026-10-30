package com.zendogames.zenlib.audio;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.LongArray;

/**
 * A {@code Sound} that records every call. Ids come from one counter shared by all fakes, as both
 * real backends hand out globally unique ids. {@code loop(volume, pitch, pan)} throws: the
 * gdx-teavm 1.6.2 backend gets that overload wrong, so the service must never use it.
 */
final class FakeSound implements Sound {

    private static long nextId = 1;

    int plays, loops, stoppedAll;
    long lastId = -1;
    float lastVolume, lastPitch, lastPan;
    boolean failNext;

    final LongArray stopped = new LongArray();
    final LongArray paused = new LongArray();
    final LongArray resumed = new LongArray();
    final LongArray volumeIds = new LongArray();
    final FloatArray volumes = new FloatArray();
    final LongArray pitchIds = new LongArray();
    final FloatArray pitches = new FloatArray();
    final LongArray panIds = new LongArray();
    final FloatArray pans = new FloatArray();
    final FloatArray panVolumes = new FloatArray();

    float lastSetVolume()    { return volumes.isEmpty()    ? Float.NaN : volumes.peek(); }
    float lastSetPitch()     { return pitches.isEmpty()    ? Float.NaN : pitches.peek(); }
    float lastSetPan()       { return pans.isEmpty()       ? Float.NaN : pans.peek(); }
    float lastSetPanVolume() { return panVolumes.isEmpty() ? Float.NaN : panVolumes.peek(); }

    @Override public long play()             { return play(1f, 1f, 0f); }
    @Override public long play(float volume) { return play(volume, 1f, 0f); }
    @Override public long play(float volume, float pitch, float pan) {
        if (failNext) { failNext = false; return -1; }
        plays++;
        lastVolume = volume;
        lastPitch = pitch;
        lastPan = pan;
        return lastId = nextId++;
    }

    @Override public long loop() { return loop(1f); }
    @Override public long loop(float volume) {
        if (failNext) { failNext = false; return -1; }
        loops++;
        lastVolume = volume;
        lastPitch = 1f;
        lastPan = 0f;
        return lastId = nextId++;
    }
    @Override public long loop(float volume, float pitch, float pan) {
        throw new AssertionError("loop(volume, pitch, pan): the gdx-teavm 1.6.2 backend drops the pitch and passes the volume as the pan");
    }

    @Override public void stop()    { stoppedAll++; }
    @Override public void pause()   {}
    @Override public void resume()  {}
    @Override public void dispose() {}

    @Override public void stop(long soundId)   { stopped.add(soundId); }
    @Override public void pause(long soundId)  { paused.add(soundId); }
    @Override public void resume(long soundId) { resumed.add(soundId); }

    @Override public void setLooping(long soundId, boolean looping) {}
    @Override public void setPitch(long soundId, float pitch)   { pitchIds.add(soundId); pitches.add(pitch); }
    @Override public void setVolume(long soundId, float volume) { volumeIds.add(soundId); volumes.add(volume); }
    @Override public void setPan(long soundId, float pan, float volume) {
        panIds.add(soundId);
        pans.add(pan);
        panVolumes.add(volume);
    }
}
