package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.zendogames.zenlib.ecs.components.Tilemap;

/**
 * Turns a map's objects into entities: one {@link Spawn} per object key, the key being the
 * object's Tiled class, else its name ({@link TiledProps#key}). A game builds one in its screen
 * constructor and calls {@link #spawnAll} after {@code Tilemap.spawn}.
 * <pre>{@code
 * spawner = new MapObjectSpawner()
 *         .on("player", (map, obj) -> hero = factory.platformerHero(TiledProps.x(obj), TiledProps.y(obj), controls, tuning))
 *         .on("crate",  (map, obj) -> factory.crate(pixel, TiledProps.x(obj), TiledProps.y(obj)));
 * }</pre>
 * An object without a spawn goes to {@link #otherwise}, which throws by default, listing the known keys.
 */
public final class MapObjectSpawner {

    public interface Spawn {
        void spawn(Tilemap tilemap, MapObject object);
    }

    private final ObjectMap<String, Spawn> spawns = new ObjectMap<>();
    private Spawn otherwise = this::unknown;

    public MapObjectSpawner on(String key, Spawn spawn) {
        spawns.put(key, spawn);
        return this;
    }

    /** What happens to an object whose key has no spawn; the default throws. */
    public MapObjectSpawner otherwise(Spawn spawn) {
        this.otherwise = spawn;
        return this;
    }

    public boolean has(String key) {
        return spawns.containsKey(key);
    }

    /** Spawns every object of the map in file order; returns how many. */
    public int spawnAll(Tilemap tilemap) {
        for (int i = 0; i < tilemap.objects.size; i++) {
            spawn(tilemap, tilemap.objects.get(i));
        }
        return tilemap.objects.size;
    }

    public void spawn(Tilemap tilemap, MapObject object) {
        var spawn = spawns.get(TiledProps.key(object), otherwise);
        spawn.spawn(tilemap, object);
    }

    private void unknown(Tilemap tilemap, MapObject object) {
        throw new GdxRuntimeException("no spawn for map object %s, known keys: %s"
                .formatted(TiledProps.describe(object), spawns.keys().toArray()));
    }
}
