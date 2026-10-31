package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapImageLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.zendogames.zenlib.collision.CollisionMask;

/** Maps built in code, the way the loader would build them, so no texture or file is needed. */
final class TestMaps {

    static final int TILE = 16;

    enum Mask implements CollisionMask {
        SOLID, HAZARD;
        @Override public int bit() { return 1 << ordinal(); }
    }

    private TestMaps() {}

    static TiledMap map(int cols, int rows) {
        return map(cols, rows, TILE, TILE);
    }

    static TiledMap map(int cols, int rows, int tileWidth, int tileHeight) {
        var map = new TiledMap();
        var props = map.getProperties();
        props.put("width", cols);
        props.put("height", rows);
        props.put("tilewidth", tileWidth);
        props.put("tileheight", tileHeight);
        return map;
    }

    static TiledMapTileLayer tiles(TiledMap map, String name) {
        return tiles(map, map.getLayers(), name);
    }

    static TiledMapTileLayer tiles(TiledMap map, MapLayers into, String name) {
        var props = map.getProperties();
        var layer = new TiledMapTileLayer(
                props.get("width", Integer.class), props.get("height", Integer.class),
                props.get("tilewidth", Integer.class), props.get("tileheight", Integer.class));
        layer.setName(name);
        into.add(layer);
        return layer;
    }

    static MapGroupLayer group(MapLayers into, String name) {
        var group = new MapGroupLayer();
        group.setName(name);
        into.add(group);
        return group;
    }

    /**
     * An image layer of a {@code w x h} image at Tiled offset {@code (offsetX, offsetY)}, placed the way the
     * loader does it: x as is, y flipped to the image's bottom edge in y-up map pixels, the offsets also kept
     * on the {@code MapLayer}. The texture is headless, see {@link HeadlessGl}.
     */
    static TiledMapImageLayer image(TiledMap map, MapLayers into, String name, int w, int h, float offsetX, float offsetY) {
        var props = map.getProperties();
        int mapHeight = props.get("height", Integer.class) * props.get("tileheight", Integer.class);
        var layer = new TiledMapImageLayer(HeadlessGl.region(w, h), offsetX, mapHeight - offsetY - h, false, false);
        layer.setName(name);
        layer.setOffsetX(offsetX);
        layer.setOffsetY(offsetY);
        into.add(layer);
        return layer;
    }

    static MapLayer objectLayer(MapLayers into, String name) {
        var layer = new MapLayer();
        layer.setName(name);
        into.add(layer);
        return layer;
    }

    /** A tile with a {@code shape} property, or none when {@code shape} is null. */
    static StaticTiledMapTile tile(int id, String shape) {
        var tile = new StaticTiledMapTile(new TextureRegion());
        tile.setId(id);
        if (shape != null) tile.getProperties().put(TileShapes.PROPERTY, shape);
        return tile;
    }

    static Cell cell(TiledMapTile tile) {
        return new Cell().setTile(tile);
    }

    static Cell cell(TiledMapTile tile, boolean flipH, boolean flipV, int rotation) {
        return new Cell().setTile(tile).setFlipHorizontally(flipH).setFlipVertically(flipV).setRotation(rotation);
    }

    /** A rectangle object with the properties the loader writes ({@code id}, {@code type}, {@code x/y/width/height}). */
    static MapObject object(MapLayer layer, int id, String name, String type, float x, float y, float w, float h) {
        var object = new RectangleMapObject(x, y, w, h);
        object.setName(name);
        var props = object.getProperties();
        props.put("id", id);
        if (type != null) props.put(TiledProps.TYPE, type);
        props.put("x", x);
        props.put("y", y);
        props.put("width", w);
        props.put("height", h);
        layer.getObjects().add(object);
        return object;
    }
}
