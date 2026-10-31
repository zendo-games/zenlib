package com.zendogames.zenlib.ecs.components;

import org.junit.Test;

import static org.junit.Assert.*;

public class HealthTest {

    @Test
    public void startsFullAndDamageClampsAtZero() {
        var health = new Health(3);
        assertEquals(3, health.current);
        assertTrue(health.isAlive());

        assertEquals(1, health.damage(2));
        assertEquals(0, health.damage(5));
        assertEquals(0, health.current);
        assertTrue(health.isDead());
    }

    @Test
    public void restoreAndKill() {
        var health = new Health(3);
        health.kill();
        assertTrue(health.isDead());
        health.restore();
        assertEquals(3, health.current);
        assertFalse(health.isDead());
    }
}
