package com.zendogames.zenlib.audio;

import com.badlogic.gdx.math.MathUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class SoundGroupTest {

    @Test
    public void ofGivesEveryMemberWeightOne() {
        var group = SoundGroup.of(TestSound.BELL, TestSound.BELL_HIGH, TestSound.QUIET);
        assertEquals(3, group.sounds.size());
        assertEquals(3f, group.sounds.total(), 1e-6f);
        for (int i = 0; i < 3; i++) assertEquals(1f, group.sounds.weight(i), 1e-6f);
        assertNull(group.last());
        assertEquals(0f, group.cooldown(), 0f);
        assertFalse(group.isNoRepeat());
    }

    @Test
    public void noRepeatNeverPicksTheLastMemberAgainWhenThereIsAChoice() {
        var group = new SoundGroup().add(TestSound.BELL, 99f).add(TestSound.BELL_HIGH, 1f).noRepeat();
        var last = group.pick();
        for (int i = 0; i < 500; i++) {
            var pick = group.pick();
            assertNotSame("pick " + i, last, pick);
            assertSame(pick, group.last());
            last = pick;
        }
    }

    @Test
    public void aSingleMemberRepeatsEvenUnderNoRepeat() {
        var group = SoundGroup.of(TestSound.BELL).noRepeat();
        assertSame(TestSound.BELL, group.pick());
        assertSame(TestSound.BELL, group.pick());
    }

    @Test
    public void withoutNoRepeatThePickFollowsTheWeights() {
        MathUtils.random.setSeed(42);
        var group = new SoundGroup().add(TestSound.BELL, 90f).add(TestSound.BELL_HIGH, 10f);
        var bells = 0;
        for (int i = 0; i < 1000; i++) {
            if (group.pick() == TestSound.BELL) bells++;
        }
        assertTrue("bells " + bells, bells > 800 && bells < 980);
    }
}
