package com.zendogames.zenlib.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Pool;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.Prefs;
import com.zendogames.zenlib.assets.MusicType;
import com.zendogames.zenlib.assets.SoundType;
import com.zendogames.zenlib.utils.Calc;

import java.util.Arrays;

import static com.zendogames.zenlib.ZenFlag.LOG_AUDIO;

/**
 * Static audio service over libGDX {@code Sound} and {@code Music}, set up by {@code Zen.init} and
 * stepped by {@code ZenGame} every frame with the frame delta, so fades run through a hit-stop. The
 * call sites are the sugar on {@link SoundType}, {@link SoundGroup} and {@link MusicType}; this
 * class holds the rules:
 * <ul>
 *   <li><b>Buses.</b> Every play is scaled by its bus's {@link AudioBus#gain()}. Changing a bus volume
 *       or mute re-applies to the voices playing and to the music; volumes persist through {@code Prefs}, written
 *       {@value #PERSIST_DELAY} s after the last change and on dispose.</li>
 *   <li><b>Voices.</b> Each start is remembered as a {@link Voice} until its {@link SoundType#duration()}
 *       or {@link SoundType#cooldown()}, whichever is longer, has passed; that table is what the cooldown,
 *       the {@link SoundType#maxVoices()} cap, {@link #stop(long)} and the debug view read. Looping voices
 *       stay until stopped. The backend has its own limit (16 OpenAL sources on desktop, the 17th play
 *       silently stops the oldest sound), so caps on the noisy sounds keep that limit for the ones that matter.</li>
 *   <li><b>Refusals.</b> A play that does not happen returns -1 and counts under a {@link Refusal}: zero
 *       gain (a muted bus or volume), cooldown, the voice cap, the bus paused, or the backend gave no id.</li>
 *   <li><b>Pause.</b> {@link #pause()} freezes the voices on {@link AudioBus#pausable()} buses and refuses
 *       new ones there until {@link #resume()}; UI sounds and music keep going. A pause menu calls both.
 *       {@code Time.pauseFor} (hit-stop) does not touch audio.</li>
 *   <li><b>Music.</b> One track at a time. {@link #music(MusicType, float)} crossfades from the current
 *       one over the given seconds, {@link #duck} dips the music under dialogue or a stinger.</li>
 *   <li><b>Listener.</b> {@link #listener(float, float)}, set each frame from the camera by a screen that
 *       pans, makes {@link #pan(float)} and {@link #attenuation(float)} turn a world x into a stereo
 *       position and a falloff for {@code playAt}.</li>
 * </ul>
 * {@code ZenFlag.LOG_AUDIO} logs every start, refusal and music change. Everything runs on the render thread.
 */
public final class Audio {

    private Audio() {}

    private static final String TAG = "Audio";

    /** Voice lifetime assumed for a {@link SoundType} that does not say ({@link SoundType#duration()}). */
    public static final float DEFAULT_DURATION = 1f;
    /** libGDX's documented pitch range; out-of-range requests are clamped. */
    public static final float MIN_PITCH = 0.5f, MAX_PITCH = 2f;
    /** Crossfade used by {@link #music(MusicType)} and {@link #stopMusic()}; 0 for a hard cut. */
    public static float defaultMusicFade = 0.5f;

    static final int MAX_TRACKED_VOICES = 64;
    static final String PREFS_PREFIX = "audio.";
    /** Seconds after the last bus change before the volumes are written to Prefs: a slider drag is one write, not sixty a second. */
    static final float PERSIST_DELAY = 0.5f;
    /** Updates a listener stays valid for without being set again. */
    static final int LISTENER_FRAMES = 2;

    /** Why a play returned -1. */
    public enum Refusal { SILENT, COOLDOWN, VOICES, PAUSED, BACKEND }

    /** One started sound: what the rules count and the debug view lists. Read-only outside the service. */
    public static final class Voice {
        public SoundType sound;
        public long id;
        public float start;   // service clock seconds, see Audio.now()
        public float volume;  // as requested, before the trim and the bus
        public float pan;
        public float pitch;
        public boolean looping;
        public boolean paused;

        public float age() { return now - start; }
        /** Alive by the estimate: looping, or younger than the sound's duration. */
        public boolean alive() { return looping || age() < sound.duration(); }
        /** Tabled for the longer of duration and cooldown, so a cooldown outlives a short sound. */
        boolean expired() { return !looping && age() >= Math.max(sound.duration(), sound.cooldown()); }
        float gain() { return volume * sound.volume() * sound.bus().gain(); }
    }

    private static final Array<AudioBus> buses = new Array<>();
    private static final Array<Voice> voices = new Array<>(true, MAX_TRACKED_VOICES);
    private static final Pool<Voice> voicePool = new Pool<>(MAX_TRACKED_VOICES) {
        @Override protected Voice newObject() { return new Voice(); }
    };
    private static final int[] refusals = new int[Refusal.values().length];
    private static final SoundType[] lastRefused = new SoundType[Refusal.values().length];

    private static boolean persist;
    private static boolean dirty;      // a bus changed since the last Prefs write
    private static float dirtyFor;     // seconds since that change
    private static boolean paused;
    private static float now;

    // Music: the current track fading in (level 0..1 at fadeRate per second) and the previous one fading out
    private static MusicType currentType, previousType;
    private static Music current, previous;
    private static float currentLevel, previousLevel, fadeRate;
    private static boolean musicPaused;
    private static float duck = 1f, duckTarget = 1f, duckRate;
    private static final ObjectSet<Music> hooked = new ObjectSet<>(); // Music objects that have our completion listener

    private static int listenerFrames; // > 0 while the listener is valid, see listener()
    private static float listenerX, listenerHalfWidth;

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    /** {@code Zen.init}: library buses registered with their saved volumes, nothing playing. */
    public static void init() {
        init(true);
    }

    /**
     * {@code persist} false keeps {@code Prefs} out of it (tests, or a game that saves volumes itself);
     * every bus starts at its default. Forgets voices and music without touching them: a previous
     * game's sounds are already disposed, {@link #dispose()} is what stops them.
     */
    public static void init(boolean persist) {
        for (var voice : voices) voicePool.free(voice);
        voices.clear();
        current = previous = null;
        currentType = previousType = null;
        currentLevel = previousLevel = fadeRate = 0f;
        musicPaused = false;
        hooked.clear();
        duck = duckTarget = 1f;
        duckRate = 0f;
        listenerFrames = 0;
        paused = false;
        dirty = false;
        dirtyFor = 0f;
        now = 0f;
        Arrays.fill(refusals, 0);
        Arrays.fill(lastRefused, null);
        for (var bus : buses) bus.state().reset();
        buses.clear();
        Audio.persist = persist;
        register(ZenBus.values());
    }

    /** {@code ZenGame.dispose()}, before the assets go: stops everything so no disposed handle is touched later. */
    public static void dispose() {
        if (dirty) persistBuses();
        stopAll();
        if (current  != null) current.stop();
        if (previous != null) previous.stop();
        current = previous = null;
        currentType = previousType = null;
    }

    /**
     * Adds buses, parents first; the library's are registered by {@link #init()}, a game's in
     * {@code ZenGame.init()}. Each bus comes up at its saved volume and mute when persisting.
     */
    public static void register(AudioBus... newBuses) {
        for (var bus : newBuses) {
            var state = bus.state();
            state.reset();
            if (persist) {
                state.volume = Prefs.get(volumeKey(bus), state.initial);
                state.muted  = Prefs.get(muteKey(bus), false);
            }
            buses.add(bus);
        }
    }

    /** Registered buses in registration order, for a settings menu or the debug view. */
    public static Array<AudioBus> buses() {
        return buses;
    }

    /** Every frame with the frame delta: ages voices out, runs the music fades and the duck. */
    public static void update(float dt) {
        now += dt;
        if (listenerFrames > 0) listenerFrames--;
        if (dirty) {
            dirtyFor += dt;
            if (dirtyFor >= PERSIST_DELAY) persistBuses();
        }
        for (int i = voices.size - 1; i >= 0; i--) {
            var voice = voices.get(i);
            if (voice.paused) {
                voice.start += dt; // a paused voice does not age
            } else if (voice.expired()) {
                free(i);
            }
        }

        if (previous != null) {
            previousLevel -= dt * fadeRate;
            if (previousLevel <= 0f) {
                previous.stop();
                previous = null;
                previousType = null;
            } else {
                applyMusicVolume(previous, previousType, previousLevel);
            }
        }

        var ducking = (duck != duckTarget);
        if (ducking) duck = Calc.approach(duck, duckTarget, dt * duckRate);
        if (current != null) {
            var fading = (currentLevel < 1f);
            if (fading) currentLevel = Math.min(1f, currentLevel + dt * fadeRate);
            if (fading || ducking) applyMusicVolume(current, currentType, currentLevel);
        }
    }

    /** Seconds of {@link #update} time since init; the clock {@link Voice#start} is on. */
    public static float now() {
        return now;
    }

    // ------------------------------------------------------------------------
    // Buses
    // ------------------------------------------------------------------------

    /** Sets a bus volume (clamped 0..1), persists it and re-applies to the voices playing and the music. */
    public static void volume(AudioBus bus, float volume) {
        volume = MathUtils.clamp(volume, 0f, 1f);
        bus.state().volume = volume;
        markDirty();
        applyGains();
        Log.debug(TAG, "%s volume %.2f", bus.name(), volume);
    }

    public static void mute(AudioBus bus, boolean muted) {
        bus.state().muted = muted;
        markDirty();
        applyGains();
        Log.debug(TAG, "%s %s", bus.name(), muted ? "muted" : "unmuted");
    }

    private static void markDirty() {
        if (!persist) return;
        dirty = true;
        dirtyFor = 0f;
    }

    /** Every bus's volume and mute to Prefs; debounced from {@link #update}, immediate from {@link #dispose}. */
    private static void persistBuses() {
        for (int i = 0; i < buses.size; i++) {
            var bus = buses.get(i);
            Prefs.put(volumeKey(bus), bus.state().volume);
            Prefs.put(muteKey(bus), bus.state().muted);
        }
        dirty = false;
        dirtyFor = 0f;
    }

    private static void applyGains() {
        for (int i = 0; i < voices.size; i++) {
            var voice = voices.get(i);
            voice.sound.get().setVolume(voice.id, voice.gain());
        }
        if (current  != null) applyMusicVolume(current,  currentType,  currentLevel);
        if (previous != null) applyMusicVolume(previous, previousType, previousLevel);
    }

    // ------------------------------------------------------------------------
    // Sounds
    // ------------------------------------------------------------------------

    public static long play(SoundType sound, float volume, float pan) {
        return start(sound, volume, pan, sound.pitch(), false);
    }

    /** With an explicit pitch in place of the constant's; the variance still applies. */
    public static long play(SoundType sound, float volume, float pan, float pitch) {
        return start(sound, volume, pan, pitch, false);
    }

    /** Panned and attenuated by the world x against the {@link #listener}. */
    public static long playAt(SoundType sound, float volume, float worldX) {
        return play(sound, volume * attenuation(worldX), pan(worldX));
    }

    /** A looping voice; it never ages out, so keep the id and {@link #stop(long)} it. */
    public static long loop(SoundType sound, float volume, float pan) {
        return start(sound, volume, pan, sound.pitch(), true);
    }

    /** The group's cooldown, then one member picked and played by the member's own rules. */
    public static long play(SoundGroup group, float volume, float pan) {
        if (group.lastStart > now) group.lastStart = Float.NEGATIVE_INFINITY; // a clock ahead of ours is a previous init's
        if (now - group.lastStart < group.cooldown) return refuse(Refusal.COOLDOWN, group.last);
        var last = group.last;
        var id = play(group.pick(), volume, pan);
        if (id == -1) group.last = last; // the refused pick never played, so noRepeat keeps excluding the one that did
        else group.lastStart = now;
        return id;
    }

    /** The one start path: pause, bus gain, cooldown and cap, pitch variance, then the backend. */
    private static long start(SoundType sound, float volume, float pan, float pitch, boolean looping) {
        var bus = sound.bus();
        if (paused && bus.pausable()) return refuse(Refusal.PAUSED, sound);

        var gain = volume * sound.volume() * bus.gain();
        if (gain <= 0f) return refuse(Refusal.SILENT, sound);

        if (sound.cooldown() > 0f || sound.maxVoices() > 0) {
            var alive = 0;
            var lastStart = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < voices.size; i++) {
                var voice = voices.get(i);
                if (voice.sound != sound) continue;
                if (voice.alive()) alive++;
                lastStart = Math.max(lastStart, voice.start);
            }
            if (now - lastStart < sound.cooldown())                  return refuse(Refusal.COOLDOWN, sound);
            if (sound.maxVoices() > 0 && alive >= sound.maxVoices()) return refuse(Refusal.VOICES, sound);
        }

        var variance = sound.pitchVariance();
        if (variance > 0f) pitch += MathUtils.random(-variance, variance);
        pitch = MathUtils.clamp(pitch, MIN_PITCH, MAX_PITCH);
        pan = MathUtils.clamp(pan, -1f, 1f);

        var handle = sound.get();
        long id;
        if (looping) {
            // Not loop(volume, pitch, pan): the gdx-teavm 1.6.2 backend drops the pitch and passes the volume as the pan there
            id = handle.loop(gain);
            if (id != -1) {
                handle.setPitch(id, pitch);
                handle.setPan(id, pan, gain);
            }
        } else {
            id = handle.play(gain, pitch, pan);
        }
        if (id == -1) return refuse(Refusal.BACKEND, sound);

        if (voices.size == MAX_TRACKED_VOICES) free(oldestOneShot());
        var voice = voicePool.obtain();
        voice.sound   = sound;
        voice.id      = id;
        voice.start   = now;
        voice.volume  = volume;
        voice.pan     = pan;
        voice.pitch   = pitch;
        voice.looping = looping;
        voice.paused  = false;
        voices.add(voice);
        Log.info(LOG_AUDIO, TAG, "%s %s id=%d gain=%.2f pitch=%.2f pan=%+.2f",
                looping ? "loop" : "play", label(sound), id, gain, pitch, pan);
        return id;
    }

    private static long refuse(Refusal reason, SoundType sound) {
        refusals[reason.ordinal()]++;
        lastRefused[reason.ordinal()] = sound;
        Log.info(LOG_AUDIO, TAG, "refused %s: %s", label(sound), reason);
        return -1;
    }

    // ------------------------------------------------------------------------
    // Voices
    // ------------------------------------------------------------------------

    /** Stops the voice a play returned; an unknown or finished id is ignored. */
    public static void stop(long id) {
        var index = indexOf(id);
        if (index < 0) return;
        var voice = voices.get(index);
        voice.sound.get().stop(id);
        free(index);
    }

    /** Stops every voice of one sound. */
    public static void stop(SoundType sound) {
        for (int i = voices.size - 1; i >= 0; i--) {
            var voice = voices.get(i);
            if (voice.sound != sound) continue;
            voice.sound.get().stop(voice.id);
            free(i);
        }
    }

    public static void stopAll() {
        for (int i = voices.size - 1; i >= 0; i--) {
            var voice = voices.get(i);
            voice.sound.get().stop(voice.id);
            free(i);
        }
    }

    /** Volume of a voice (a loop that follows an engine's speed); the trim and bus gain still apply. */
    public static void volume(long id, float volume) {
        var voice = voice(id);
        if (voice == null) return;
        voice.volume = volume;
        voice.sound.get().setVolume(id, voice.gain());
    }

    public static void pitch(long id, float pitch) {
        var voice = voice(id);
        if (voice == null) return;
        voice.pitch = MathUtils.clamp(pitch, MIN_PITCH, MAX_PITCH);
        voice.sound.get().setPitch(id, voice.pitch);
    }

    public static void pan(long id, float pan) {
        var voice = voice(id);
        if (voice == null) return;
        voice.pan = MathUtils.clamp(pan, -1f, 1f);
        voice.sound.get().setPan(id, voice.pan, voice.gain());
    }

    /** True while the voice is tabled and alive by its estimate (a loop until stopped). */
    public static boolean isAlive(long id) {
        var voice = voice(id);
        return voice != null && voice.alive();
    }

    /** The voice table in start order, for the debug view. */
    public static Array<Voice> voices() {
        return voices;
    }

    // ------------------------------------------------------------------------
    // Pause
    // ------------------------------------------------------------------------

    /**
     * Pause menu opened: voices alive on pausable buses freeze and new ones there are refused until
     * {@link #resume()}. Only voices alive by their estimate are paused, because the web backend
     * restarts a finished sound when it is resumed; a long {@link SoundType#duration()} on a short
     * sound is what would make that audible.
     */
    public static void pause() {
        if (paused) return;
        paused = true;
        for (int i = 0; i < voices.size; i++) {
            var voice = voices.get(i);
            if (voice.paused || !voice.alive() || !voice.sound.bus().pausable()) continue;
            voice.paused = true;
            voice.sound.get().pause(voice.id);
        }
        Log.info(LOG_AUDIO, TAG, "paused");
    }

    public static void resume() {
        if (!paused) return;
        paused = false;
        for (int i = 0; i < voices.size; i++) {
            var voice = voices.get(i);
            if (!voice.paused) continue;
            voice.paused = false;
            voice.sound.get().resume(voice.id);
        }
        Log.info(LOG_AUDIO, TAG, "resumed");
    }

    public static boolean isPaused() {
        return paused;
    }

    // ------------------------------------------------------------------------
    // Music
    // ------------------------------------------------------------------------

    public static void music(MusicType type) {
        music(type, defaultMusicFade);
    }

    /**
     * Crossfades to {@code type} over {@code fadeSeconds} (0 cuts). The current type is left alone;
     * the type still fading out swaps back in from its level; any other fade-out still running is cut.
     */
    public static void music(MusicType type, float fadeSeconds) {
        if (type == currentType) return;
        if (fadeSeconds > 0f) fadeRate = 1f / fadeSeconds;

        if (type == previousType) {
            var music = current; var musicType = currentType; var level = currentLevel;
            current = previous;  currentType = previousType;  currentLevel = previousLevel;
            previous = music;    previousType = musicType;    previousLevel = level;
            if (fadeSeconds <= 0f) finishFade();
            Log.info(LOG_AUDIO, TAG, "music back to %s fade %.2fs", type.asset().label(), fadeSeconds);
            return;
        }

        if (previous != null) {
            previous.stop();
            previous = null;
            previousType = null;
        }
        if (current != null) {
            if (fadeSeconds <= 0f) {
                current.stop();
            } else {
                previous = current;
                previousType = currentType;
                previousLevel = currentLevel;
            }
        }

        current = type.get();
        currentType = type;
        currentLevel = (fadeSeconds <= 0f) ? 1f : 0f;
        musicPaused = false;
        current.setLooping(type.looping());
        // Once per Music object: the web backend's setOnCompletionListener adds a listener per call and never removes one
        if (hooked.add(current)) current.setOnCompletionListener(Audio::completed);
        applyMusicVolume(current, type, currentLevel);
        current.play();
        Log.info(LOG_AUDIO, TAG, "music %s fade %.2fs", type.asset().label(), fadeSeconds);
    }

    public static void stopMusic() {
        stopMusic(defaultMusicFade);
    }

    /** Fades the current track out over {@code fadeSeconds} (0 cuts) and forgets it. */
    public static void stopMusic(float fadeSeconds) {
        if (current == null) return;
        if (previous != null) previous.stop();
        if (fadeSeconds <= 0f) {
            current.stop();
            previous = null;
            previousType = null;
        } else {
            fadeRate = 1f / fadeSeconds;
            previous = current;
            previousType = currentType;
            previousLevel = currentLevel;
        }
        current = null;
        currentType = null;
        musicPaused = false;
        Log.info(LOG_AUDIO, TAG, "music stop fade %.2fs", fadeSeconds);
    }

    public static void pauseMusic() {
        if (current == null || musicPaused) return;
        musicPaused = true;
        current.pause();
    }

    public static void resumeMusic() {
        if (current == null || !musicPaused) return;
        musicPaused = false;
        current.play();
    }

    /**
     * Dips the music to {@code level} (0..1 of its volume) over {@code seconds}; {@code duck(1, s)}
     * brings it back. Dialogue, a stinger, a pause menu.
     */
    public static void duck(float level, float seconds) {
        duckTarget = MathUtils.clamp(level, 0f, 1f);
        if (seconds <= 0f) {
            duck = duckTarget;
            if (current  != null) applyMusicVolume(current,  currentType,  currentLevel);
            if (previous != null) applyMusicVolume(previous, previousType, previousLevel);
        } else {
            duckRate = Math.abs(duckTarget - duck) / seconds;
        }
    }

    /** The track playing or fading in, null when none. */
    public static MusicType currentMusic()  { return currentType; }
    /** The track fading out, null when none. */
    public static MusicType previousMusic() { return previousType; }
    /** Fade-in level of the current track, 0..1. */
    public static float musicLevel()        { return currentLevel; }
    public static float duck()              { return duck; }
    public static boolean isMusicPaused()   { return musicPaused; }

    private static void applyMusicVolume(Music music, MusicType type, float level) {
        music.setVolume(type.volume() * type.bus().gain() * level * duck);
    }

    /** A hard cut while a swap is in flight: the outgoing one stops, the incoming one is at full level. */
    private static void finishFade() {
        if (previous != null) previous.stop();
        previous = null;
        previousType = null;
        currentLevel = 1f;
        applyMusicVolume(current, currentType, currentLevel);
    }

    /** A non-looping track ran out: forget it, so the same type plays again on the next request. */
    private static void completed(Music music) {
        if (music != current || currentType.looping()) return;
        Log.info(LOG_AUDIO, TAG, "music %s completed", currentType.asset().label());
        current = null;
        currentType = null;
    }

    // ------------------------------------------------------------------------
    // Listener
    // ------------------------------------------------------------------------

    /**
     * Where the player hears from, in world units: the camera's x and half the view's width. Set it every
     * frame from the screen that pans sounds; it lapses after {@value #LISTENER_FRAMES} updates without a set, so
     * a screen that stops panning leaves nothing behind. Without one {@link #pan} is 0 and {@link #attenuation} is 1.
     */
    public static void listener(float worldX, float halfWidth) {
        listenerFrames = LISTENER_FRAMES;
        listenerX = worldX;
        listenerHalfWidth = Math.max(halfWidth, 1f);
    }

    /** Stereo position of a world x: -1 at the view's left edge, +1 at the right, clamped beyond. */
    public static float pan(float worldX) {
        return hasListener() ? MathUtils.clamp((worldX - listenerX) / listenerHalfWidth, -1f, 1f) : 0f;
    }

    /** 1 inside the view, falling linearly to 0 one view width past its edge. */
    public static float attenuation(float worldX) {
        if (!hasListener()) return 1f;
        var beyond = Math.abs(worldX - listenerX) / listenerHalfWidth - 1f; // 0 at the edge, 2 one view width out
        return MathUtils.clamp(1f - beyond / 2f, 0f, 1f);
    }

    public static boolean hasListener() {
        return listenerFrames > 0;
    }

    // ------------------------------------------------------------------------
    // Refusals
    // ------------------------------------------------------------------------

    public static int refusals(Refusal reason)            { return refusals[reason.ordinal()]; }
    public static SoundType lastRefused(Refusal reason)   { return lastRefused[reason.ordinal()]; }
    public static void resetRefusals() {
        Arrays.fill(refusals, 0);
        Arrays.fill(lastRefused, null);
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static void free(int index) {
        var voice = voices.removeIndex(index);
        voice.sound = null;
        voicePool.free(voice);
    }

    /** The oldest non-looping voice, or index 0 when every tabled voice loops (64 loops is a game bug, not a case). */
    private static int oldestOneShot() {
        var oldest = 0;
        for (int i = 0; i < voices.size; i++) {
            var voice = voices.get(i);
            if (voice.looping) continue;
            if (voices.get(oldest).looping || voice.start < voices.get(oldest).start) oldest = i;
        }
        return oldest;
    }

    private static int indexOf(long id) {
        for (int i = 0; i < voices.size; i++) {
            if (voices.get(i).id == id) return i;
        }
        return -1;
    }

    private static Voice voice(long id) {
        var index = indexOf(id);
        return (index < 0) ? null : voices.get(index);
    }

    private static String volumeKey(AudioBus bus) { return PREFS_PREFIX + bus.name().toLowerCase(); }
    private static String muteKey(AudioBus bus)   { return volumeKey(bus) + ".muted"; }
    private static String label(SoundType sound)  { return (sound == null) ? "?" : sound.asset().label(); }
}
