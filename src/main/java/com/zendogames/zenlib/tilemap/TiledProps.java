package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.Arrays;

/**
 * Reads of a {@link MapObject}'s properties as a spawner wants them. The loader stores every
 * object's {@code x}, {@code y} (bottom-left, already y-up), {@code width}, {@code height} as
 * floats and its Tiled class as {@code type}; game properties keep the type Tiled gave them, so a
 * property left as {@code string} in Tiled is a {@code String} here, which {@link #require} reports
 * instead of throwing a {@code ClassCastException} at the call site.
 */
public final class TiledProps {

    public static final String TYPE = "type";

    /** Tiled's own object attributes, which libGDX stores beside the class members; a class member must not reuse one. */
    public static final String RESERVED = "x, y, width, height, id, name, type, gid, rotation, visible";

    private TiledProps() {}

    public static int    id(MapObject object)   { return object.getProperties().get("id", 0, Integer.class); }
    public static String type(MapObject object) { return object.getProperties().get(TYPE, null, String.class); }

    public static int x(MapObject object)      { return MathUtils.round(object.getProperties().get("x",      0f, Float.class)); }
    public static int y(MapObject object)      { return MathUtils.round(object.getProperties().get("y",      0f, Float.class)); }
    public static int width(MapObject object)  { return MathUtils.round(object.getProperties().get("width",  0f, Float.class)); }
    public static int height(MapObject object) { return MathUtils.round(object.getProperties().get("height", 0f, Float.class)); }

    /** The spawner key: the object's Tiled class ({@code type}), else its name; throws when it has neither. */
    public static String key(MapObject object) {
        var type = type(object);
        if (type != null && !type.isBlank()) return type;

        var name = object.getName();
        if (name != null && !name.isBlank()) return name;

        throw new GdxRuntimeException("map object has neither a class nor a name: " + describe(object));
    }

    /** The region of a tile (gid) object as placed in Tiled, flips applied, or null for a shape or point object. */
    public static TextureRegion region(MapObject object) {
        return (object instanceof TiledMapTileMapObject tile) ? tile.getTextureRegion() : null;
    }

    /** A string member parsed into an enum (case-insensitive), with the object named on the bad value. */
    public static <E extends Enum<E>> E enumOf(MapObject object, String name, Class<E> type) {
        var value = require(object, name, String.class);
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new GdxRuntimeException("property '%s' on map object %s is '%s', expected one of %s"
                    .formatted(name, describe(object), value, Arrays.toString(type.getEnumConstants())));
        }
    }

    /** A property that must be there, with the type Tiled was asked for. */
    public static <T> T require(MapObject object, String name, Class<T> type) {
        var value = object.getProperties().get(name);
        if (value == null) {
            throw new GdxRuntimeException("missing property '%s' on map object %s".formatted(name, describe(object)));
        }
        return cast(object, name, value, type);
    }

    /** A property with a fallback when absent; a present value of the wrong type still throws. */
    public static <T> T get(MapObject object, String name, T fallback, Class<T> type) {
        var value = object.getProperties().get(name);
        return (value == null) ? fallback : cast(object, name, value, type);
    }

    /** {@code 'launcher' (class launcher, id 8)}, for error messages and logs. */
    public static String describe(MapObject object) {
        var type = type(object);
        return "'%s' (%s, id %d)".formatted(object.getName(), (type == null) ? "no class" : "class " + type, id(object));
    }

    private static <T> T cast(MapObject object, String name, Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new GdxRuntimeException("property '%s' on map object %s is a %s, not a %s (check its type in Tiled)"
                    .formatted(name, describe(object), value.getClass().getSimpleName(), type.getSimpleName()));
        }
        return type.cast(value);
    }
}
