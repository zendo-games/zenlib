package com.zendogames.zenlib.audio;

import com.zendogames.zenlib.Time;
import com.zendogames.zenlib.TimeReset;
import com.zendogames.zenlib.audio.Audio.Refusal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The review's audio fixes: a loop on a silent bus is kept and comes back on unmute, the music swap-back
 * clears the pause, the listener holds through a hit-stop, out of earshot is {@code DISTANT}, a group's
 * {@code playAt} goes through the one path, and a bus registers once.
 */
public class AudioFixesTest {

    static final float EPS = 1e-5f;

    @Before
    public void setUp() {
        Audio.init(false);
        TestSound.reset();
        TestMusic.reset();
    }

    @After
    public void unpauseTime() {
        TimeReset.reset();
    }

    @Test
    public void aLoopOnAMutedBusStartsSilentAndComesBackOnUnmute() {
        ZenBus.SFX.mute(true);
        var hum = TestSound.HUM.loop(0.8f);
        var fake = TestSound.HUM.fake;
        assertNotEquals(-1, hum);
        assertEquals(1, fake.loops);
        assertEquals(0f, fake.lastVolume, EPS);
        assertEquals(0f, fake.lastSetPanVolume(), EPS);
        assertEquals(0, Audio.refusals(Refusal.SILENT));
        assertEquals(1, Audio.voices().size);

        ZenBus.SFX.mute(false);
        assertEquals(hum, fake.volumeIds.peek());
        assertEquals(0.4f, fake.lastSetVolume(), EPS);      // 0.8 * SFX 0.5

        ZenBus.MASTER.volume(0f);                            // a one-shot is still refused
        assertEquals(-1, TestSound.BELL.play());
        assertEquals(1, Audio.refusals(Refusal.SILENT));
    }

    @Test
    public void switchingBackToTheFadingTrackClearsThePause() {
        TestMusic.A.play(0f);
        TestMusic.B.play(1f);
        Audio.pauseMusic();
        assertEquals(1, TestMusic.B.fake.pauses);

        TestMusic.A.play(1f);                                // the swap-back branch
        assertSame(TestMusic.A, Audio.currentMusic());
        assertFalse(Audio.isMusicPaused());
        Audio.pauseMusic();
        assertTrue(Audio.isMusicPaused());
        assertEquals(1, TestMusic.A.fake.pauses);
        Audio.resumeMusic();
        assertEquals(2, TestMusic.A.fake.plays);
    }

    @Test
    public void theListenerHoldsThroughAHitStop() {
        Audio.listener(100f, 160f);
        Time.pauseFor(1f);
        for (int i = 0; i < 10; i++) Audio.update(0.016f);
        assertTrue(Audio.hasListener());
        assertEquals(1f, Audio.panAt(260f), EPS);

        TimeReset.reset();                                   // the hit-stop ends: the usual two updates
        Audio.update(0.016f);
        assertTrue(Audio.hasListener());
        Audio.update(0.016f);
        assertFalse(Audio.hasListener());
    }

    @Test
    public void outOfEarshotIsRefusedDistantBeforeAnyOtherRule() {
        Audio.listener(100f, 160f);
        ZenBus.SFX.mute(true);
        assertEquals(-1, TestSound.BELL.playAt(1000f));
        assertEquals(1, Audio.refusals(Refusal.DISTANT));
        assertEquals(0, Audio.refusals(Refusal.SILENT));
        assertSame(TestSound.BELL, Audio.lastRefused(Refusal.DISTANT));

        ZenBus.SFX.mute(false);
        assertEquals(-1, TestSound.BELL.playAt(580f));       // attenuation exactly 0
        assertNotEquals(-1, TestSound.BELL.playAt(579f));
        assertEquals(2, Audio.refusals(Refusal.DISTANT));
        assertEquals(0, Audio.refusals(Refusal.SILENT));

        Audio.init(false);                                   // no listener: nothing is distant
        assertNotEquals(-1, TestSound.BELL.playAt(1000f));
    }

    @Test
    public void aGroupPlayAtPansAndAttenuatesLikeASound() {
        var group = SoundGroup.of(TestSound.BELL).cooldown(0.1f);
        Audio.listener(100f, 160f);
        assertNotEquals(-1, group.playAt(420f));
        var fake = TestSound.BELL.fake;
        assertEquals(1f, fake.lastPan, EPS);
        assertEquals(0.25f, fake.lastVolume, EPS);           // 0.5 attenuation * SFX 0.5
        assertSame(TestSound.BELL, group.last());

        assertEquals(-1, group.playAt(1000f));               // distant: the group's cooldown is not even asked
        assertEquals(1, Audio.refusals(Refusal.DISTANT));
        assertEquals(0, Audio.refusals(Refusal.COOLDOWN));
        assertSame(TestSound.BELL, Audio.lastRefused(Refusal.DISTANT));

        Audio.update(0.11f);
        assertNotEquals(-1, group.playAt(0.5f, 180f));
        assertEquals(0.5f, fake.lastPan, EPS);
        assertEquals(0.25f, fake.lastVolume, EPS);           // 0.5 * attenuation 1 * SFX 0.5
    }

    @Test
    public void aBusRegistersOnce() {
        Audio.register(TestBus.values());
        Audio.register(TestBus.values());
        Audio.register(ZenBus.SFX);
        assertEquals(ZenBus.values().length + TestBus.values().length, Audio.buses().size);
    }

    @Test
    public void duckLevelReadsTheDuckInEffect() {
        assertEquals(1f, Audio.duckLevel(), EPS);
        Audio.duck(0.3f, 0f);
        assertEquals(0.3f, Audio.duckLevel(), EPS);
    }
}
