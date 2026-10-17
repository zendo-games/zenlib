package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.maps.tiled.TiledMapImageLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;

/**
 * One image layer of a {@link Tilemap}, drawn by the {@code RenderSystem} like a {@link TileLayer}:
 * {@code layer} and {@code order} come from the map, so a backdrop sorts under the tile layers and
 * sprites by the same rules. The renderer's {@code renderImageLayer} does the rest: culling against
 * the view bounds set here, {@code repeatx} / {@code repeaty} tiling, parallax relative to the view,
 * and the layer's tint and opacity.
 * <p>
 * The loader stores an image layer's position y-up in world pixels (unlike a tile layer's offsets),
 * so the entity position is simply added. The renderer places the image by that position alone and
 * ignores the render offset a parent group contributes, so the group part is added once here.
 * Parallax moves the image by a fraction of the view, which lands between texels; the fraction is
 * canceled so the image is drawn at whole pixels like everything else.
 */
public class TileImageLayer extends Renderable {

    public static final ComponentType<TileImageLayer> TYPE = ComponentType.register(TileImageLayer.class);
    public static TileImageLayer get(Entity e) { return TYPE.get(e); }
    public static boolean        has(Entity e) { return TYPE.has(e); }

    private static final String TAG = TileImageLayer.class.getSimpleName();

    public final TiledMapImageLayer image;
    public final OrthogonalTiledMapRenderer renderer;
    public final OrthographicCamera camera;

    /** Starts as the layer's visibility in Tiled (its groups' included). A debug view toggles it. */
    public boolean visible;

    private final float authoredX;
    private final float authoredY;

    public TileImageLayer(OrthogonalTiledMapRenderer renderer, OrthographicCamera camera, Tilemap.Image layer) {
        this.image    = layer.image();
        this.renderer = renderer;
        this.camera   = camera;
        this.visible  = layer.visible();
        this.layer    = layer.renderLayer();
        this.order    = layer.order();
        // Group offsets are y-down like every Tiled offset; the image's own offset is already in x/y
        this.authoredX = image.getX() + (image.getRenderOffsetX() - image.getOffsetX());
        this.authoredY = image.getY() - (image.getRenderOffsetY() - image.getOffsetY());
        var region = image.getTextureRegion();
        if (region != null) {
            this.size.set(region.getRegionWidth(), region.getRegionHeight());
        }
    }

    public String name() {
        return image.getName();
    }

    /** Where the image's bottom-left corner sits for a layer entity at {@code (x, y)}, before parallax. */
    public float imageX(float x) { return authoredX + x; }
    public float imageY(float y) { return authoredY + y; }

    @Override
    public void render(Batch batch, float x, float y) {
        if (!visible) return;
        if (renderer.getBatch() != batch) {
            throw new GdxRuntimeException(TAG + ": '%s' was spawned on another batch than the RenderSystem draws with".formatted(name()));
        }

        // Culling and parallax: the camera's view in world units; the world camera never rotates
        float w = camera.viewportWidth  * camera.zoom;
        float h = camera.viewportHeight * camera.zoom;
        var view = renderer.getViewBounds();
        view.set(camera.position.x - w / 2f, camera.position.y - h / 2f, w, h);

        // The renderer subtracts view * (parallax - 1); keep the result on whole pixels
        float shiftX = view.x * (image.getParallaxX() - 1f);
        float shiftY = view.y * (image.getParallaxY() - 1f);
        image.setX(imageX(x) + (shiftX - Math.round(shiftX)));
        image.setY(imageY(y) + (shiftY - Math.round(shiftY)));

        float prevColor = batch.getPackedColor();
        batch.setColor(tint);
        renderer.renderImageLayer(image);
        batch.setPackedColor(prevColor);
    }
}
