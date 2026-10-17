package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapImageLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.FramePool;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.collision.CollisionMask;
import com.zendogames.zenlib.collision.shapes.CollisionGrid;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.render.RenderLayer;
import com.zendogames.zenlib.tilemap.TileShapes;

/**
 * A Tiled map as the game sees it: size in tiles, every tile layer classified for rendering or
 * collision, and every object from every object layer in one list. Built from a loaded
 * {@link TiledMap} (a {@code MapType} constant's {@code get()}), then {@link #spawn}ed:
 * <pre>{@code
 * var tilemap = new Tilemap(GameMap.LEVEL_1.get());
 * tilemap.spawn(world, GameMask.SOLID, batch, worldCamera);   // map entity + one entity per tile layer
 * spawner.spawnAll(tilemap);                                  // the game's MapObjectSpawner
 * cameraFollow.bounds = new Rectangle(0, 0, tilemap.width(), tilemap.height());
 * }</pre>
 * <b>Layer conventions</b> (all optional):
 * <ul>
 *   <li>A tile layer with property {@code collision = true}, or named {@code collision} or
 *       {@code solid}, is a collision layer: its cells become the map entity's {@link CollisionGrid}
 *       through {@link TileShapes} and it is spawned hidden ({@link TileLayer#visible}).</li>
 *   <li>Property {@code layer} names the {@link RenderLayer} ({@code background}, {@code world},
 *       {@code foreground}, {@code overlay}; default {@code world}); property {@code order}
 *       (int) replaces the layer's position in the file as the order within that render layer.</li>
 *   <li>Image layers follow the same {@code layer} / {@code order} conventions and spawn a
 *       {@link TileImageLayer} entity each; repeat, parallax, tint and opacity come from Tiled.</li>
 *   <li>Group layers are walked into; their offsets and visibility apply to their children.</li>
 * </ul>
 * One instance is one map entity: {@code spawn} adds this component, so a reload builds a new one.
 */
public class Tilemap implements Component {

    public static final ComponentType<Tilemap> TYPE = ComponentType.register(Tilemap.class);
    public static Tilemap get(Entity e) { return TYPE.get(e); }
    public static boolean has(Entity e) { return TYPE.has(e); }

    /** How a tile layer is interpreted for the collision grid; see {@link #isCollisionLayer}. */
    public enum CollisionMode {
        /** Provides no collision info */
        NONE,
        /** Every placed tile is a collision grid cell, shaped by its {@code shape} property (default {@code full}); hidden at runtime */
        ALL,
        /** Only tiles with a {@code shape} property are collision grid cells; the rest are art. Drawn as usual. */
        TAGGED;

        public boolean collides() {
            return this != NONE;
        }
    }

    public static final String COLLISION_PROPERTY = "collision";
    public static final String LAYER_PROPERTY     = "layer";
    public static final String ORDER_PROPERTY     = "order";

    private static final String TAG = Tilemap.class.getSimpleName();

    /**
     * One tile layer as classified. {@code order} is the walk index unless the layer sets
     * {@code order}; {@code visible} is the layer's own flag and its groups'.
     */
    public record Layer(TiledMapTileLayer tiles, RenderLayer renderLayer, int order, CollisionMode mode, boolean visible) {
        public String  name()      { return tiles.getName(); }
        public boolean collision() { return mode.collides(); }
    }

    /** One image layer as classified: the same {@code order} and {@code visible} rules as {@link Layer}. */
    public record Image(TiledMapImageLayer image, RenderLayer renderLayer, int order, boolean visible) {
        public String name() { return image.getName(); }
    }

    public final TiledMap map;
    public final int cols;
    public final int rows;
    public final int tileSize;

    /** Every tile layer in file order, collision layers included. */
    public final Array<Layer> layers = new Array<>();

    /** Every collision layer in file order. */
    public final Array<Layer> collisionLayers = new Array<>();

    /** Every object of every object layer, in file order. */
    public final Array<MapObject> objects = new Array<>();

    /** What {@link #spawn} created for {@link #layers}, same order; empty until then. */
    public final Array<Entity> layerEntities = new Array<>();

    /** Every image layer in file order. */
    public final Array<Image> images = new Array<>();

    /** What {@link #spawn} created for {@link #images}, same order; empty until then. */
    public final Array<Entity> imageEntities = new Array<>();

    private int nextOrder = 0;

    public Tilemap(TiledMap map) {
        var props = map.getProperties();
        this.map  = map;
        this.cols = props.get("width",  Integer.class);
        this.rows = props.get("height", Integer.class);
        int tileWidth  = props.get("tilewidth",  Integer.class);
        int tileHeight = props.get("tileheight", Integer.class);
        if (tileWidth != tileHeight) {
            throw new GdxRuntimeException(TAG + ": tiles must be square for the collision grid, map has %dx%d"
                    .formatted(tileWidth, tileHeight));
        }
        this.tileSize = tileWidth;
        classify(map.getLayers(), true);
    }

    public int width()  { return cols * tileSize; }
    public int height() { return rows * tileSize; }

    /** (pooled) The map's bounds for a map entity at {@code (x, y)}. */
    public Rectangle bounds(int x, int y) {
        return FramePool.rect(x, y, width(), height());
    }

    /** The tile layer with this name, or null. */
    public Layer layer(String name) {
        for (int i = 0; i < layers.size; i++) {
            var layer = layers.get(i);
            if (name.equals(layer.name())) {
                return layer;
            }
        }
        return null;
    }

    /** The image layer with this name, or null. */
    public Image image(String name) {
        for (int i = 0; i < images.size; i++) {
            var image = images.get(i);
            if (name.equals(image.name())) {
                return image;
            }
        }
        return null;
    }

    public boolean hasCollision() {
        return collisionLayers.size > 0;
    }

    /** One grid over every collision layer; where layers overlap the later one wins. Throws when the map has none. */
    public Collider gridCollider(CollisionMask mask) {
        if (!hasCollision()) throw new GdxRuntimeException(TAG + ": no collision layer (property '%s' or a layer named 'collision' / 'solid')".formatted(COLLISION_PROPERTY));
        var collider = Collider.grid(mask, tileSize, cols, rows);
        var grid = collider.shape(CollisionGrid.class);
        for (int i = 0; i < collisionLayers.size; i++) {
            fill(grid, collisionLayers.get(i));
        }
        return collider;
    }

    /** One grid over every collision layer; where layers overlap the later one wins. Throws when the map has none. */
    public Collider gridCollider(CollisionMask mask, String layerName) {
        var layer = layer(layerName);
        if (layer == null) throw new GdxRuntimeException(TAG + ": no tile layer named '%s'".formatted(layerName));
        var collider = Collider.grid(mask, tileSize, cols, rows);
        fill(collider.shape(CollisionGrid.class), layer);
        return collider;
    }

    /**
     * Creates the map entity ({@code Name}, {@code Position} at the origin, this, the
     * {@link #gridCollider} when the map has collision layers) and one entity per tile layer
     * ({@code Name "layer:<name>"}, {@code Position}, {@link TileLayer}) and per image layer
     * (same, with a {@link TileImageLayer}), all drawn by one {@link OrthogonalTiledMapRenderer} on
     * {@code batch}, the batch the {@code RenderSystem} draws with. {@code camera} is the world
     * camera, for culling and parallax. Returns the map entity.
     */
    public Entity spawn(World world, CollisionMask solid, Batch batch, OrthographicCamera camera) {
        var entity = world.createEntity()
                .add(new Name("map"))
                .add(new Position(0, 0))
                .add(this);
        if (hasCollision()) {
            entity.add(gridCollider(solid));
        }

        var renderer = new OrthogonalTiledMapRenderer(map, 1f, batch);

        layerEntities.clear();
        for (int i = 0; i < layers.size; i++) {
            var layer = layers.get(i);
            layerEntities.add(world.createEntity()
                    .add(new Name("layer:" + layer.name()))
                    .add(new Position(0, 0))
                    .add(new TileLayer(renderer, camera, layer)));
        }

        imageEntities.clear();
        for (int i = 0; i < images.size; i++) {
            var image = images.get(i);
            imageEntities.add(world.createEntity()
                    .add(new Name("layer:" + image.name()))
                    .add(new Position(0, 0))
                    .add(new TileImageLayer(renderer, camera, image)));
        }

        Log.debug(TAG, "spawned %s: %dx%d tiles of %d px, %d layers (%d collision), %d images, %d objects",
                entity, cols, rows, tileSize, layers.size, collisionLayers.size, images.size, objects.size);
        return entity;
    }

    // ------------------------------------------------------------------------
    // Classification
    // ------------------------------------------------------------------------

    private void classify(MapLayers mapLayers, boolean parentVisible) {
        for (var layer : mapLayers) {
            var visible = parentVisible && layer.isVisible();
            switch (layer) {
                case MapGroupLayer      group -> classify(group.getLayers(), visible);
                case TiledMapTileLayer  tiles -> add(tiles, visible);
                case TiledMapImageLayer image -> add(image, visible);
                default -> {
                    for (var object : layer.getObjects()) {
                        objects.add(object);
                    }
                }
            }
            nextOrder++;
        }
    }

    private void add(TiledMapTileLayer tiles, boolean visible) {
        var props       = tiles.getProperties();
        var mode        = collisionMode(tiles);
        var renderLayer = RenderLayer.parse(props.get(LAYER_PROPERTY, "", String.class), RenderLayer.parse(tiles.getName()));
        var order       = props.get(ORDER_PROPERTY, nextOrder, Integer.class);
        var layer       = new Layer(tiles, renderLayer, order, mode, visible);
        layers.add(layer);
        if (mode.collides()) {
            collisionLayers.add(layer);
        }
    }

    private void add(TiledMapImageLayer image, boolean visible) {
        var props       = image.getProperties();
        var renderLayer = RenderLayer.parse(props.get(LAYER_PROPERTY, "", String.class), RenderLayer.parse(image.getName()));
        var order       = props.get(ORDER_PROPERTY, nextOrder, Integer.class);
        var layer       = new Image(image, renderLayer, order, visible);
        images.add(layer);
    }

    public static boolean isCollisionLayer(MapLayer layer) {
        return collisionMode(layer).collides();
    }

    /**
     * {@code collision = all} or the bool {@code true}, or a layer named {@code collision} / {@code solid}: every tile collides.
     * {@code collision = tagged}: only tiles with a {@code shape}.
     * Anything else: none. An unknown string throws naming the layer.
     */
    public static CollisionMode collisionMode(MapLayer layer) {
        var value = layer.getProperties().get(COLLISION_PROPERTY);
        if (value instanceof Boolean bool) return bool ? CollisionMode.ALL : CollisionMode.NONE;
        if (value instanceof String  prop) {
            return switch (prop.trim().toLowerCase()) {
                case "all", "true"   -> CollisionMode.ALL;
                case "tagged"        -> CollisionMode.TAGGED;
                case "none", "false" -> CollisionMode.NONE;
                default -> throw new GdxRuntimeException(TAG + ": layer '%s': unknown collision mode '%s', expected all, tagged or none"
                        .formatted(layer.getName(), prop));
            };
        }
        var name = layer.getName();
        return ("collision".equals(name) || "solid".equals(name)) ? CollisionMode.ALL : CollisionMode.NONE;
    }

    private void fill(CollisionGrid grid, Layer layer) {
        var tiles  = layer.tiles();
        var tagged = layer.mode() == CollisionMode.TAGGED;
        int w = Math.min(cols, tiles.getWidth());
        int h = Math.min(rows, tiles.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                var cell = tiles.getCell(x, y);
                if (cell == null) continue;
                if (tagged && !TileShapes.has(cell.getTile())) continue;
                grid.set(x, y, TileShapes.of(cell));
            }
        }
    }
}
