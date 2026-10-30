package com.zendogames.zenlib.audio;

import com.zendogames.zenlib.audio.Audio.Refusal;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The service's rules against recording fakes. Bus defaults: MASTER 1, SFX / MUSIC / UI 0.5, so a
 * plain play at volume 1 reaches the backend at 0.5. {@code Audio.init(false)} keeps Prefs out.
 */
public class AudioTest {

    static final float EPS = 1e-5f;

    @Before
    public void setUp() {
        Audio.init(false);
        TestSound.reset();
        TestMusic.reset();
    }

    // ------------------------------------------------------------------------
    // Plays
    // ------------------------------------------------------------------------

    @Test
    public void playScalesByTheTrimAndTheBusChainAndReturnsTheBackendId() {
        var id = TestSound.QUIET.play(0.8f);               // trim 0.5 * SFX 0.5 * MASTER 1
        var fake = TestSound.QUIET.fake;
        assertEquals(1, fake.plays);
        assertEquals(fake.lastId, id);
        assertEquals(0.2f, fake.lastVolume, EPS);
        assertEquals(1f, fake.lastPitch, EPS);
        assertEquals(0f, fake.lastPan, EPS);
        assertEquals(1, Audio.voices().size);
        assertTrue(Audio.isAlive(id));
    }

    @Test
    public void pitchComesFromTheConstantOrTheCallAndVarianceStaysInRange() {
        TestSound.BELL_HIGH.play();
        assertEquals(1.2f, TestSound.BELL_HIGH.fake.lastPitch, EPS);

        Audio.play(TestSound.BELL, 1f, 0f, 3f);             // an explicit pitch, clamped to libGDX's range
        assertEquals(Audio.MAX_PITCH, TestSound.BELL.fake.lastPitch, EPS);

        for (int i = 0; i < 50; i++) {
            TestSound.VARIED.play();
            var pitch = TestSound.VARIED.fake.lastPitch;
            assertTrue("pitch " + pitch, pitch >= 0.9f - EPS && pitch <= 1.1f + EPS);
        }
    }

    @Test
    public void panIsClamped() {
        TestSound.BELL.play(1f, 2f);
        assertEquals(1f, TestSound.BELL.fake.lastPan, EPS);
        TestSound.BELL.play(1f, -2f);
        assertEquals(-1f, TestSound.BELL.fake.lastPan, EPS);
    }

    @Test
    public void aMutedOrSilentBusRefusesWithoutSpendingAVoice() {
        ZenBus.SFX.mute(true);
        assertEquals(-1, TestSound.BELL.play());
        assertEquals(0, TestSound.BELL.fake.plays);
        assertEquals(1, Audio.refusals(Refusal.SILENT));
        assertSame(TestSound.BELL, Audio.lastRefused(Refusal.SILENT));
        assertEquals(0, Audio.voices().size);

        ZenBus.SFX.mute(false);
        ZenBus.MASTER.volume(0f);
        assertEquals(-1, TestSound.BELL.play());
        assertEquals(2, Audio.refusals(Refusal.SILENT));

        ZenBus.MASTER.volume(1f);
        assertNotEquals(-1, TestSound.BELL.play());

        Audio.resetRefusals();
        assertEquals(0, Audio.refusals(Refusal.SILENT));
        assertNull(Audio.lastRefused(Refusal.SILENT));
    }

    @Test
    public void busVolumesAreClampedAndReachTheVoicesPlaying() {
        ZenBus.SFX.volume(1.5f);
        assertEquals(1f, ZenBus.SFX.volume(), EPS);
        var fake = TestSound.BELL.fake;
        TestSound.BELL.play(0.8f);
        assertEquals(0.8f, fake.lastVolume, EPS);

        ZenBus.SFX.volume(0.25f);
        assertEquals(0.2f, fake.lastSetVolume(), EPS);
        ZenBus.MASTER.volume(-1f);
        assertEquals(0f, ZenBus.MASTER.volume(), EPS);
        assertEquals(0f, fake.lastSetVolume(), EPS);
        ZenBus.MASTER.volume(1f);
        assertEquals(0.2f, fake.lastSetVolume(), EPS);
        ZenBus.MASTER.mute(true);
        assertEquals(0f, fake.lastSetVolume(), EPS);
    }

    @Test
    public void cooldownRefusesASecondStartUntilItHasPassed() {
        assertNotEquals(-1, TestSound.STEP.play());
        assertEquals(-1, TestSound.STEP.play());
        assertEquals(1, Audio.refusals(Refusal.COOLDOWN));

        Audio.update(0.15f);                                 // the sound (0.1 s) is over, the cooldown (0.2 s) is not
        assertEquals(-1, TestSound.STEP.play());
        assertEquals(1, Audio.voices().size);                // tabled for the cooldown
        assertFalse(Audio.voices().first().alive());

        Audio.update(0.06f);
        assertNotEquals(-1, TestSound.STEP.play());
        assertEquals(2, TestSound.STEP.fake.plays);
        assertEquals(1, Audio.voices().size);                // the expired one was swept
    }

    @Test
    public void maxVoicesCountsTheVoicesYoungerThanTheDuration() {
        assertNotEquals(-1, TestSound.POP.play());
        assertNotEquals(-1, TestSound.POP.play());
        assertEquals(-1, TestSound.POP.play());
        assertEquals(1, Audio.refusals(Refusal.VOICES));
        assertSame(TestSound.POP, Audio.lastRefused(Refusal.VOICES));

        Audio.update(0.31f);
        assertNotEquals(-1, TestSound.POP.play());
        assertEquals(3, TestSound.POP.fake.plays);
    }

    @Test
    public void oneShotsAgeOutAndLoopsStayUntilStopped() {
        TestSound.BELL.play();
        var hum = TestSound.HUM.loop();
        assertEquals(2, Audio.voices().size);

        Audio.update(1.01f);
        assertEquals(1, Audio.voices().size);
        assertTrue(Audio.isAlive(hum));

        Audio.stop(hum);
        assertEquals(0, Audio.voices().size);
        assertEquals(hum, TestSound.HUM.fake.stopped.first());
        assertFalse(Audio.isAlive(hum));

        Audio.stop(hum);                                     // already gone: ignored
        Audio.stop(12345L);                                  // never existed: ignored
        assertEquals(1, TestSound.HUM.fake.stopped.size);
    }

    @Test
    public void loopUsesTheSettersNotTheThreeArgumentOverload() {
        var id = TestSound.HUM.loop(0.5f, 0.25f);
        var fake = TestSound.HUM.fake;
        assertEquals(1, fake.loops);
        assertEquals(0, fake.plays);
        assertEquals(0.25f, fake.lastVolume, EPS);           // 0.5 * SFX 0.5
        assertEquals(id, fake.pitchIds.first());
        assertEquals(1f, fake.lastSetPitch(), EPS);
        assertEquals(0.25f, fake.lastSetPan(), EPS);
        assertEquals(0.25f, fake.lastSetPanVolume(), EPS);
    }

    @Test
    public void voiceVolumePitchAndPanApplyWithTheBusGain() {
        var id = TestSound.HUM.loop(0.5f);
        var fake = TestSound.HUM.fake;
        Audio.volume(id, 1f);
        assertEquals(0.5f, fake.lastSetVolume(), EPS);
        Audio.pitch(id, 3f);
        assertEquals(Audio.MAX_PITCH, fake.lastSetPitch(), EPS);
        Audio.pan(id, -2f);
        assertEquals(-1f, fake.lastSetPan(), EPS);
        assertEquals(0.5f, fake.lastSetPanVolume(), EPS);

        var calls = fake.volumes.size;
        Audio.volume(999L, 1f);                              // unknown id: nothing
        assertEquals(calls, fake.volumes.size);
    }

    @Test
    public void stopBySoundAndStopAll() {
        TestSound.BELL.play();
        TestSound.BELL.play();
        TestSound.HUM.loop();
        Audio.stop(TestSound.BELL);
        assertEquals(1, Audio.voices().size);
        assertEquals(2, TestSound.BELL.fake.stopped.size);
        Audio.stopAll();
        assertEquals(0, Audio.voices().size);
        assertEquals(1, TestSound.HUM.fake.stopped.size);
    }

    @Test
    public void aBackendFailureIsRefusedAndNotTracked() {
        TestSound.BELL.fake.failNext = true;
        assertEquals(-1, TestSound.BELL.play());
        assertEquals(1, Audio.refusals(Refusal.BACKEND));
        assertEquals(0, Audio.voices().size);
        TestSound.HUM.fake.failNext = true;
        assertEquals(-1, TestSound.HUM.loop());
        assertEquals(0, TestSound.HUM.fake.pitchIds.size);   // no setters on a failed loop
    }

    @Test
    public void theVoiceTableEvictsTheOldestOneShotWhenFull() {
        var hum = TestSound.HUM.loop();
        var first = TestSound.BELL.play();
        for (int i = 2; i < Audio.MAX_TRACKED_VOICES; i++) TestSound.BELL.play();
        assertEquals(Audio.MAX_TRACKED_VOICES, Audio.voices().size);

        TestSound.BELL.play();
        assertEquals(Audio.MAX_TRACKED_VOICES, Audio.voices().size);
        assertFalse(Audio.isAlive(first));
        assertTrue(Audio.isAlive(hum));
        assertEquals(0, TestSound.BELL.fake.stopped.size);   // evicted from the table, not stopped
    }

    // ------------------------------------------------------------------------
    // Groups
    // ------------------------------------------------------------------------

    @Test
    public void aGroupChecksItsCooldownBeforeThePick() {
        var group = SoundGroup.of(TestSound.BELL, TestSound.BELL_HIGH).cooldown(0.1f);
        assertNotEquals(-1, group.play());
        assertEquals(1, TestSound.BELL.fake.plays + TestSound.BELL_HIGH.fake.plays);
        assertNotNull(group.last());

        assertEquals(-1, group.play());
        assertEquals(1, Audio.refusals(Refusal.COOLDOWN));
        assertSame(group.last(), Audio.lastRefused(Refusal.COOLDOWN));

        Audio.update(0.11f);
        assertNotEquals(-1, group.play(0.5f, 0.5f));
        assertEquals(2, TestSound.BELL.fake.plays + TestSound.BELL_HIGH.fake.plays);
        assertEquals(0.5f, group.last().get() == TestSound.BELL.fake ? TestSound.BELL.fake.lastPan : TestSound.BELL_HIGH.fake.lastPan, EPS);
    }

    @Test
    public void aMembersRefusalDoesNotStartTheGroupsCooldown() {
        var group = SoundGroup.of(TestSound.STEP).cooldown(0.5f);
        TestSound.STEP.play();                               // the member's own 0.2 s cooldown starts
        assertEquals(-1, group.play());                      // refused by the member
        assertEquals(1, Audio.refusals(Refusal.COOLDOWN));
        assertNull(group.last());                            // the refused pick is not remembered as the last played
        Audio.update(0.21f);
        assertNotEquals(-1, group.play());                   // the group's 0.5 s never started
    }

    // ------------------------------------------------------------------------
    // Pause
    // ------------------------------------------------------------------------

    @Test
    public void pauseFreezesPausableBusesAndRefusesNewStartsThere() {
        var hum = TestSound.HUM.loop();
        TestSound.CLICK.play();                              // UI: not pausable
        Audio.pause();
        assertTrue(Audio.isPaused());
        assertEquals(hum, TestSound.HUM.fake.paused.first());
        assertEquals(0, TestSound.CLICK.fake.paused.size);

        assertEquals(-1, TestSound.BELL.play());
        assertEquals(1, Audio.refusals(Refusal.PAUSED));
        assertNotEquals(-1, TestSound.CLICK.play());

        var age = voice(hum).age();
        Audio.update(0.5f);
        assertEquals(age, voice(hum).age(), EPS);            // a paused voice does not age
        Audio.pause();                                       // idempotent
        assertEquals(1, TestSound.HUM.fake.paused.size);

        Audio.resume();
        assertFalse(Audio.isPaused());
        assertEquals(hum, TestSound.HUM.fake.resumed.first());
        assertNotEquals(-1, TestSound.BELL.play());
        Audio.resume();
        assertEquals(1, TestSound.HUM.fake.resumed.size);
    }

    @Test
    public void pauseLeavesVoicesDeadByTheEstimateAlone() {
        TestSound.STEP.play();                               // 0.1 s long, tabled 0.2 s for its cooldown
        Audio.update(0.15f);
        Audio.pause();
        assertEquals(0, TestSound.STEP.fake.paused.size);
        Audio.resume();
        assertEquals(0, TestSound.STEP.fake.resumed.size);
    }

    @Test
    public void aGameBusThatIsNotPausableKeepsPlaying() {
        Audio.register(TestBus.values());
        TestSound.VOICE.play();
        Audio.pause();
        assertEquals(1, TestSound.VOICE.fake.paused.size);  // VOICE pauses
        assertEquals(-1, TestSound.VOICE.play());
    }

    // ------------------------------------------------------------------------
    // Music
    // ------------------------------------------------------------------------

    @Test
    public void musicCrossfadesAndIgnoresARequestForTheCurrentTrack() {
        TestMusic.A.play(1f);
        var a = TestMusic.A.fake;
        assertEquals(1, a.plays);
        assertTrue(a.looping);
        assertEquals(0f, a.volume, EPS);
        assertSame(TestMusic.A, Audio.currentMusic());

        Audio.update(0.5f);
        assertEquals(0.25f, a.volume, EPS);                  // level 0.5 * MUSIC 0.5
        Audio.update(0.6f);
        assertEquals(0.5f, a.volume, EPS);
        assertEquals(1f, Audio.musicLevel(), EPS);

        TestMusic.B.play(0.5f);
        var b = TestMusic.B.fake;
        assertSame(TestMusic.B, Audio.currentMusic());
        assertSame(TestMusic.A, Audio.previousMusic());
        assertEquals(0f, b.volume, EPS);
        Audio.update(0.25f);
        assertEquals(0.25f, a.volume, EPS);
        assertEquals(0.25f, b.volume, EPS);
        assertEquals(0, a.stops);
        Audio.update(0.3f);
        assertEquals(1, a.stops);
        assertNull(Audio.previousMusic());
        assertEquals(0.5f, b.volume, EPS);

        TestMusic.B.play();                                  // the current track: nothing happens
        assertEquals(1, b.plays);
    }

    @Test
    public void aThirdTrackCutsTheFadeOutStillRunning() {
        TestMusic.A.play(0f);
        TestMusic.B.play(1f);
        TestMusic.C.play(1f);
        assertEquals(1, TestMusic.A.fake.stops);
        assertSame(TestMusic.C, Audio.currentMusic());
        assertSame(TestMusic.B, Audio.previousMusic());
        Audio.update(0.1f);
        assertEquals(1, TestMusic.B.fake.stops);             // B was at level 0, so it is gone at once
        assertNull(Audio.previousMusic());
    }

    @Test
    public void switchingBackToTheTrackFadingOutSwapsThemWithoutARestart() {
        TestMusic.A.play(0f);
        TestMusic.B.play(1f);
        Audio.update(0.5f);
        var a = TestMusic.A.fake;
        var b = TestMusic.B.fake;
        assertEquals(0.25f, a.volume, EPS);
        assertEquals(0.25f, b.volume, EPS);

        TestMusic.A.play(1f);
        assertSame(TestMusic.A, Audio.currentMusic());
        assertSame(TestMusic.B, Audio.previousMusic());
        assertEquals(1, a.plays);
        Audio.update(0.5f);
        assertEquals(0.5f, a.volume, EPS);
        assertEquals(1, b.stops);
        assertNull(Audio.previousMusic());

        TestMusic.B.play(1f);
        Audio.update(0.5f);
        TestMusic.A.play(0f);                                // swap back as a hard cut
        assertEquals(0.5f, a.volume, EPS);
        assertEquals(2, b.stops);
        assertNull(Audio.previousMusic());
    }

    @Test
    public void aHardCutStopsThePreviousAtOnce() {
        TestMusic.A.play(0f);
        assertEquals(0.5f, TestMusic.A.fake.volume, EPS);
        TestMusic.B.play(0f);
        assertEquals(1, TestMusic.A.fake.stops);
        assertEquals(0.5f, TestMusic.B.fake.volume, EPS);
        assertNull(Audio.previousMusic());
    }

    @Test
    public void stopMusicFadesOutAndForgetsTheTrack() {
        TestMusic.A.play(0f);
        Audio.stopMusic(0.5f);
        assertNull(Audio.currentMusic());
        assertSame(TestMusic.A, Audio.previousMusic());
        assertEquals(0, TestMusic.A.fake.stops);
        Audio.update(0.6f);
        assertEquals(1, TestMusic.A.fake.stops);
        assertNull(Audio.previousMusic());

        Audio.stopMusic();                                   // nothing to stop
        TestMusic.A.play(0f);                                // forgotten, so it plays again
        assertEquals(2, TestMusic.A.fake.plays);
        Audio.stopMusic(0f);
        assertEquals(2, TestMusic.A.fake.stops);
        assertNull(Audio.previousMusic());
    }

    @Test
    public void duckDipsTheMusicAndComesBack() {
        TestMusic.A.play(0f);
        var a = TestMusic.A.fake;
        Audio.duck(0.2f, 0.5f);
        Audio.update(0.25f);
        assertEquals(0.6f, Audio.duck(), EPS);
        assertEquals(0.3f, a.volume, EPS);
        Audio.update(0.3f);
        assertEquals(0.2f, Audio.duck(), EPS);
        assertEquals(0.1f, a.volume, EPS);
        Audio.update(1f);                                    // settled: no drift past the target
        assertEquals(0.2f, Audio.duck(), EPS);

        Audio.duck(1f, 0f);
        assertEquals(1f, Audio.duck(), EPS);
        assertEquals(0.5f, a.volume, EPS);
    }

    @Test
    public void musicVolumeFollowsItsBusAndPausesAndResumes() {
        TestMusic.A.play(0f);
        var a = TestMusic.A.fake;
        ZenBus.MUSIC.volume(1f);
        assertEquals(1f, a.volume, EPS);
        ZenBus.MASTER.volume(0.5f);
        assertEquals(0.5f, a.volume, EPS);
        ZenBus.MUSIC.mute(true);
        assertEquals(0f, a.volume, EPS);
        ZenBus.MUSIC.mute(false);

        Audio.pauseMusic();
        assertTrue(Audio.isMusicPaused());
        assertEquals(1, a.pauses);
        assertFalse(a.playing);
        Audio.pauseMusic();
        assertEquals(1, a.pauses);
        Audio.resumeMusic();
        assertFalse(Audio.isMusicPaused());
        assertEquals(2, a.plays);
        assertTrue(a.playing);
    }

    @Test
    public void aNonLoopingTrackIsForgottenWhenItCompletes() {
        TestMusic.JINGLE.play(0f);
        var jingle = TestMusic.JINGLE.fake;
        assertFalse(jingle.looping);
        jingle.complete();
        assertNull(Audio.currentMusic());
        TestMusic.JINGLE.play(0f);
        assertEquals(2, jingle.plays);

        TestMusic.A.play(0f);
        TestMusic.A.fake.complete();                         // the web backend fires this per loop: nothing changes
        assertSame(TestMusic.A, Audio.currentMusic());
    }

    // ------------------------------------------------------------------------
    // Listener
    // ------------------------------------------------------------------------

    @Test
    public void listenerPansAndAttenuatesByWorldX() {
        assertEquals(0f, Audio.pan(500f), EPS);
        assertEquals(1f, Audio.attenuation(500f), EPS);

        Audio.listener(100f, 160f);
        assertEquals(0f, Audio.pan(100f), EPS);
        assertEquals(1f, Audio.pan(260f), EPS);
        assertEquals(-1f, Audio.pan(-60f), EPS);
        assertEquals(1f, Audio.pan(1000f), EPS);
        assertEquals(0.5f, Audio.pan(180f), EPS);
        assertEquals(1f, Audio.attenuation(260f), EPS);
        assertEquals(0.5f, Audio.attenuation(420f), EPS);
        assertEquals(0f, Audio.attenuation(580f), EPS);

        TestSound.BELL.playAt(420f);
        assertEquals(1f, TestSound.BELL.fake.lastPan, EPS);
        assertEquals(0.25f, TestSound.BELL.fake.lastVolume, EPS);   // 0.5 attenuation * SFX 0.5
        assertEquals(-1, TestSound.BELL.playAt(1000f));             // attenuated to nothing: SILENT
        assertEquals(1, Audio.refusals(Refusal.SILENT));
    }

    @Test
    public void theListenerLapsesWhenNotSetAgain() {
        Audio.listener(100f, 160f);
        assertTrue(Audio.hasListener());
        Audio.update(0.016f);
        assertTrue(Audio.hasListener());                     // one missed update is fine (set in update, read in render)
        assertEquals(1f, Audio.pan(260f), EPS);
        Audio.update(0.016f);
        assertFalse(Audio.hasListener());
        assertEquals(0f, Audio.pan(260f), EPS);
        assertEquals(1f, Audio.attenuation(1000f), EPS);
    }

    // ------------------------------------------------------------------------
    // Buses, init, dispose
    // ------------------------------------------------------------------------

    @Test
    public void gameBusesRegisterAfterTheLibrarysAndChainTheirGains() {
        Audio.register(TestBus.values());
        var buses = Audio.buses();
        assertEquals(ZenBus.values().length + TestBus.values().length, buses.size);
        assertSame(ZenBus.MASTER, buses.first());
        assertSame(TestBus.AMBIENT, buses.peek());

        assertEquals(0.5f, TestBus.VOICE.gain(), EPS);
        ZenBus.SFX.volume(0.4f);
        assertEquals(0.4f, TestBus.VOICE.gain(), EPS);
        assertEquals(0.2f, TestBus.AMBIENT.gain(), EPS);
        ZenBus.MASTER.mute(true);
        assertEquals(0f, TestBus.VOICE.gain(), EPS);
        ZenBus.MASTER.mute(false);

        TestSound.VOICE.play();
        assertEquals(0.4f, TestSound.VOICE.fake.lastVolume, EPS);
    }

    @Test
    public void initForgetsEverythingAndResetsTheBuses() {
        Audio.register(TestBus.values());
        ZenBus.SFX.volume(0.1f);
        TestBus.VOICE.volume(0.3f);
        TestBus.VOICE.mute(true);
        TestSound.HUM.loop();
        TestMusic.A.play(0f);
        Audio.listener(10f, 10f);
        Audio.update(2f);
        TestSound.BELL.fake.failNext = true;
        TestSound.BELL.play();

        Audio.init(false);
        assertEquals(0.5f, ZenBus.SFX.volume(), EPS);
        assertEquals(1f, TestBus.VOICE.volume(), EPS);
        assertFalse(TestBus.VOICE.muted());
        assertEquals(ZenBus.values().length, Audio.buses().size);
        assertEquals(0, Audio.voices().size);
        assertNull(Audio.currentMusic());
        assertEquals(0f, Audio.pan(500f), EPS);
        assertEquals(0f, Audio.now(), EPS);
        assertEquals(0, Audio.refusals(Refusal.BACKEND));
        assertEquals(0, TestSound.HUM.fake.stopped.size);    // forgotten, not stopped: dispose() stops
        assertEquals(0, TestMusic.A.fake.stops);
    }

    @Test
    public void aGroupPlayedBeforeASecondInitIsNotStuckInItsCooldown() {
        var group = SoundGroup.of(TestSound.BELL).cooldown(0.5f);
        Audio.update(100f);
        assertNotEquals(-1, group.play());                   // lastStart = 100 on the old clock
        Audio.init(false);
        TestSound.reset();
        assertNotEquals(-1, group.play());                   // the new clock reads 0: a clock behind the group's is a new run
        assertEquals(-1, group.play());                      // and its cooldown works again from here
    }

    @Test
    public void theCompletionListenerIsSetOncePerMusicObject() {
        TestMusic.A.play(0f);
        TestMusic.B.play(0f);
        TestMusic.A.play(0f);
        assertEquals(1, TestMusic.A.fake.listenerSets);
        assertEquals(1, TestMusic.B.fake.listenerSets);
        Audio.init(false);                                   // a new game has new Music objects; the fakes stand in for them
        TestMusic.A.play(0f);
        assertEquals(2, TestMusic.A.fake.listenerSets);
    }

    @Test
    public void disposeStopsEverythingPlaying() {
        TestSound.HUM.loop();
        TestMusic.A.play(0f);
        TestMusic.B.play(1f);
        Audio.dispose();
        assertEquals(1, TestSound.HUM.fake.stopped.size);
        assertEquals(1, TestMusic.A.fake.stops);
        assertEquals(1, TestMusic.B.fake.stops);
        assertNull(Audio.currentMusic());
        assertNull(Audio.previousMusic());
        assertEquals(0, Audio.voices().size);
    }

    private static Audio.Voice voice(long id) {
        for (var voice : Audio.voices()) {
            if (voice.id == id) return voice;
        }
        throw new AssertionError("no voice " + id);
    }
}
