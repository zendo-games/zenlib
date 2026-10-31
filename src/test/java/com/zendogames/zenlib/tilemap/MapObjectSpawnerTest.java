package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.ecs.components.Tilemap;
import org.junit.Test;

import static com.zendogames.zenlib.tilemap.TestMaps.*;
import static org.junit.Assert.*;

public class MapObjectSpawnerTest {

    /** Two objects in one layer, one in another: file order is the spawn order. */
    private static Tilemap tilemap() {
        var map = map(2, 2);
        var first = objectLayer(map.getLayers(), "first");
        object(first, 1, "player-1", "player", 0, 0, 16, 16);
        object(first, 2, "spot",     "marker", 8, 8, 0, 0);
        var second = objectLayer(map.getLayers(), "second");
        object(second, 3, "jumpthru", null, 0, 16, 32, 0);
        return new Tilemap(map);
    }

    @Test
    public void spawnsEveryObjectInFileOrderByClassThenName() {
        var seen = new Array<String>();
        var spawner = new MapObjectSpawner()
                .on("player",   (map, o) -> seen.add("player:" + o.getName()))
                .on("marker",   (map, o) -> seen.add("marker:" + o.getName()))
                .on("jumpthru", (map, o) -> seen.add("jumpthru:" + TiledProps.width(o)));

        assertEquals(3, spawner.spawnAll(tilemap()));
        assertEquals(Array.with("player:player-1", "marker:spot", "jumpthru:32"), seen);
        assertTrue(spawner.has("player"));
        assertFalse(spawner.has("crate"));
    }

    @Test
    public void unknownKeyThrowsListingTheKnownOnes() {
        var spawner = new MapObjectSpawner().on("player", (map, o) -> {});
        var e = assertThrows(GdxRuntimeException.class, () -> spawner.spawnAll(tilemap()));
        assertTrue(e.getMessage(), e.getMessage().contains("'spot' (class marker, id 2)"));
        assertTrue(e.getMessage(), e.getMessage().contains("player"));
    }

    @Test
    public void otherwiseReplacesTheThrow() {
        var skipped = new Array<String>();
        var spawner = new MapObjectSpawner()
                .on("player", (map, o) -> {})
                .otherwise((map, o) -> skipped.add(TiledProps.key(o)));
        spawner.spawnAll(tilemap());
        assertEquals(Array.with("marker", "jumpthru"), skipped);
    }
}
