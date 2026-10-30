package com.zendogames.zenlib.audio;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AudioBusTest {

    static final float EPS = 1e-6f;

    @Before
    public void setUp() {
        Audio.init(false);
    }

    @Test
    public void gainIsTheProductUpTheParentChain() {
        assertEquals(1f, ZenBus.MASTER.gain(), EPS);
        assertEquals(0.5f, ZenBus.SFX.gain(), EPS);
        Audio.register(TestBus.values());
        assertEquals(0.5f, TestBus.VOICE.gain(), EPS);     // 1 * 0.5 * 1
        assertEquals(0.25f, TestBus.AMBIENT.gain(), EPS);  // 0.5 * 0.5 * 1
        ZenBus.MASTER.volume(0.5f);
        assertEquals(0.125f, TestBus.AMBIENT.gain(), EPS);
        assertEquals(0.25f, ZenBus.SFX.gain(), EPS);
        assertEquals(0.25f, ZenBus.UI.gain(), EPS);
    }

    @Test
    public void aMuteAnywhereUpTheChainSilencesTheBranchOnly() {
        Audio.register(TestBus.values());
        ZenBus.SFX.mute(true);
        assertEquals(0f, ZenBus.SFX.gain(), EPS);
        assertEquals(0f, TestBus.VOICE.gain(), EPS);
        assertEquals(0.5f, ZenBus.MUSIC.gain(), EPS);
        assertTrue(ZenBus.SFX.muted());
        assertFalse(TestBus.VOICE.muted());                // the flag is the bus's own, the gain is inherited
        assertEquals(0.5f, ZenBus.SFX.volume(), EPS);      // muting keeps the volume for the unmute
        ZenBus.SFX.mute(false);
        assertEquals(0.5f, TestBus.VOICE.gain(), EPS);
    }

    @Test
    public void musicAndUiDoNotPauseWithTheGame() {
        assertTrue(ZenBus.MASTER.pausable());
        assertTrue(ZenBus.SFX.pausable());
        assertFalse(ZenBus.MUSIC.pausable());
        assertFalse(ZenBus.UI.pausable());
        assertTrue(TestBus.VOICE.pausable());
        assertFalse(TestBus.AMBIENT.pausable());
    }
}
