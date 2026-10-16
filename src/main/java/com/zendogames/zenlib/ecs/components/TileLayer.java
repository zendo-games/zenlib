package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;

/**
 * One tile layer of a {@link Tilemap}, drawn by the {@code RenderSystem} like any other
 * {@code Renderable}: {@code layer} and {@code order} come from the map, so tile layers
 * interleave with sprites. The renderer is the map's, built on the game's batch, and only
 * {@code renderTileLayer} is used, so culling bounds are set here from the camera (what
 * {@code setView(camera)} does minus re-setting the batch projection, which would flush per
 * layer). The offset authored in Tiled is kept and the entity position added to it.
 */
public class TileLayer extends Renderable {

    public static final ComponentType<TileLayer> TYPE = ComponentType.register(TileLayer.class);
    public static TileLayer get(Entity e) { return TYPE.get(e); }
    public static boolean   has(Entity e) { return TYPE.has(e); }

    private static final String TAG = TileLayer.class.getSimpleName();

    public final TiledMapTileLayer tiles;
    public final OrthogonalTiledMapRenderer renderer;
    public final OrthographicCamera camera;
    public final boolean collision;

    /** Starts as the layer's visibility in Tiled; {@code collision = all} overlays start hidden. A debug view toggles it. */
    public boolean visible;

    private final float authoredOffsetX;
    private final float authoredOffsetY;

    public TileLayer(OrthogonalTiledMapRenderer renderer, OrthographicCamera camera, Tilemap.Layer layer) {
        this.tiles     = layer.tiles();
        this.renderer  = renderer;
        this.camera    = camera;
        this.collision = layer.collision();
        this.visible   = layer.visible() && (layer.mode() != Tilemap.CollisionMode.ALL);
        this.layer     = layer.renderLayer();
        this.order     = layer.order();
        this.authoredOffsetX = tiles.getOffsetX();
        this.authoredOffsetY = tiles.getOffsetY();
        this.size.set(tiles.getWidth() * tiles.getTileWidth(), tiles.getHeight() * tiles.getTileHeight());
    }

    public String name() {
        return tiles.getName();
    }

    @Override
    public void render(SpriteBatch batch, float x, float y) {
        if (!visible) return;
        if (renderer.getBatch() != batch) {
            throw new GdxRuntimeException(TAG + ": '%s' was spawned on another batch than the RenderSystem draws with".formatted(name()));
        }

        // Culling: the camera's view in world units; the world camera never rotates
        float w = camera.viewportWidth  * camera.zoom;
        float h = camera.viewportHeight * camera.zoom;
        renderer.getViewBounds().set(camera.position.x - w / 2f, camera.position.y - h / 2f, w, h);

        // Tiled offsets are y-down and the renderer negates them, so the entity's y is subtracted
        tiles.setOffsetX(authoredOffsetX + x);
        tiles.setOffsetY(authoredOffsetY - y);

        AnimatedTiledMapTile.updateAnimationBaseTime();
        float prevColor = batch.getPackedColor();
        batch.setColor(tint);
        renderer.renderTileLayer(tiles);
        batch.setPackedColor(prevColor);
    }
}
