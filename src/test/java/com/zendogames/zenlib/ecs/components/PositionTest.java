package com.zendogames.zenlib.ecs.components;

import com.zendogames.zenlib.ecs.World;
import org.junit.Test;

import static org.junit.Assert.*;

public class PositionTest {

    @Test
    public void constructorsStartWithNoStreak() {
        var pos = new Position(3, 4);
        assertEquals(3, pos.prevX);
        assertEquals(4, pos.prevY);

        var rounded = new Position(2.6f, 3.4f);
        assertTrue(rounded.is(3, 3));
        assertEquals(3, rounded.prevX);
    }

    @Test
    public void savePrevThenMoveInterpolates() {
        var pos = new Position(0, 0);
        pos.savePrev();
        pos.set(10, 20);

        assertEquals(0f,  pos.renderX(0f),   0.001f);
        assertEquals(5f,  pos.renderX(0.5f), 0.001f);
        assertEquals(10f, pos.renderX(1f),   0.001f);
        assertEquals(10f, pos.renderY(0.5f), 0.001f);
    }

    @Test
    public void teleportMovesBothSoNothingInterpolates() {
        var pos = new Position(0, 0);
        pos.teleport(100, 50);
        assertEquals(100f, pos.renderX(0f), 0.001f);
        assertEquals(50f,  pos.renderY(0f), 0.001f);
    }

    @Test
    public void attachesLikeAnyComponent() {
        var world = new World();
        var e = world.createEntity().add(new Position(1, 2));
        assertTrue(Position.has(e));
        assertSame(Position.TYPE, Position.get(e).type());
        assertTrue(Position.get(e).is(1, 2));
    }
}
