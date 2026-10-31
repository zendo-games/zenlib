package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.Test;

import static com.zendogames.zenlib.tilemap.TestMaps.object;
import static org.junit.Assert.*;

public class TiledPropsTest {

    private final MapLayer layer = new MapLayer();

    @Test
    public void positionAndSizeAreRoundedInts() {
        var o = object(layer, 1, "a", null, 10.6f, 20.4f, 32.5f, 16f);
        assertEquals(11, TiledProps.x(o));
        assertEquals(20, TiledProps.y(o));
        assertEquals(33, TiledProps.width(o));
        assertEquals(16, TiledProps.height(o));
        assertEquals(1,  TiledProps.id(o));
    }

    @Test
    public void keyIsTheClassThenTheName() {
        assertEquals("player", TiledProps.key(object(layer, 1, "player-1", "player", 0, 0, 0, 0)));
        assertEquals("jumpthru", TiledProps.key(object(layer, 2, "jumpthru", null, 0, 0, 0, 0)));
        assertEquals("jumpthru", TiledProps.key(object(layer, 3, "jumpthru", "", 0, 0, 0, 0)));
        var e = assertThrows(GdxRuntimeException.class, () -> TiledProps.key(object(layer, 4, "", null, 0, 0, 0, 0)));
        assertTrue(e.getMessage(), e.getMessage().contains("id 4"));
    }

    @Test
    public void requireReportsMissingAndMistypedProperties() {
        var o = object(layer, 8, "launcher", "launcher", 0, 0, 0, 0);
        o.getProperties().put("period", 0.5f);
        o.getProperties().put("xVel", "200"); // left as a string in Tiled

        assertEquals(0.5f, TiledProps.require(o, "period", Float.class), 0f);

        var missing = assertThrows(GdxRuntimeException.class, () -> TiledProps.require(o, "yVel", Integer.class));
        assertTrue(missing.getMessage(), missing.getMessage().contains("missing property 'yVel'"));
        assertTrue(missing.getMessage(), missing.getMessage().contains("'launcher' (class launcher, id 8)"));

        var mistyped = assertThrows(GdxRuntimeException.class, () -> TiledProps.require(o, "xVel", Integer.class));
        assertTrue(mistyped.getMessage(), mistyped.getMessage().contains("is a String, not a Integer"));
    }

    @Test
    public void getFallsBackOnlyWhenAbsent() {
        var o = object(layer, 1, "a", null, 0, 0, 0, 0);
        o.getProperties().put("dwell", 0.3f);
        assertEquals(0.3f, TiledProps.get(o, "dwell", 1f, Float.class), 0f);
        assertEquals(1f,   TiledProps.get(o, "other", 1f, Float.class), 0f);
        assertThrows(GdxRuntimeException.class, () -> TiledProps.get(o, "dwell", "x", String.class));
    }

    @Test
    public void describeWorksWithoutAClass() {
        assertEquals("'spot' (no class, id 3)", TiledProps.describe(object(layer, 3, "spot", null, 0, 0, 0, 0)));
    }
}
