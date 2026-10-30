package com.zendogames.zenlib.utils;

import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.Test;

import static org.junit.Assert.*;

public class WeightedTest {

    final Weighted<String> abc = new Weighted<String>().add("A", 70f).add("B", 20f).add("C", 10f);

    @Test
    public void pickWalksTheCumulativeWeights() {
        assertEquals("A", abc.pick(0f));
        assertEquals("A", abc.pick(70f));
        assertEquals("B", abc.pick(70.1f));
        assertEquals("B", abc.pick(90f));
        assertEquals("C", abc.pick(90.1f));
        assertEquals("C", abc.pick(100f));
    }

    @Test
    public void rollsOutsideTheTotalThrow() {
        assertThrows(GdxRuntimeException.class, () -> abc.pick(-1f));
        assertThrows(GdxRuntimeException.class, () -> abc.pick(100.1f));
    }

    @Test
    public void aNonPositiveWeightThrows() {
        assertThrows(GdxRuntimeException.class, () -> new Weighted<String>().add("A", 0f));
        assertThrows(GdxRuntimeException.class, () -> new Weighted<String>().add("A", -1f));
    }

    @Test
    public void anEmptyPickThrows() {
        assertThrows(GdxRuntimeException.class, () -> new Weighted<String>().pick());
    }

    @Test
    public void totalSizeAndAccessors() {
        assertEquals(100f, abc.total(), 1e-6f);
        assertEquals(3, abc.size());
        assertEquals("B", abc.get(1));
        assertEquals(20f, abc.weight(1), 1e-6f);
    }

    @Test
    public void randomPickStaysInside() {
        for (int i = 0; i < 1000; i++) {
            var pick = abc.pick();
            assertTrue(pick, pick.equals("A") || pick.equals("B") || pick.equals("C"));
        }
    }
}
