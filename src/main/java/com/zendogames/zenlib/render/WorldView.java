package com.zendogames.zenlib.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.zendogames.zenlib.ZenFlag;

import java.util.function.Consumer;

/**
 * The window onto the game world: a world camera, the {@link Viewport} that maps world units onto the
 * window, and an optional framebuffer pass ({@link ZenFlag#RENDER_FBO}) that renders the world at one
 * texel per world unit and scales the result onto the window, which is what keeps pixel art crisp.
 * <p>
 * The world is drawn in two halves because {@code FrameBuffer.end()} rebinds the default framebuffer,
 * so a world buffer must never be begun inside another buffer (the screen transition's, for example).
 * {@link #renderOffscreen} runs from {@code ZenScreen.renderOffscreenBuffers}, before any buffer is
 * bound, and does nothing unless the buffer path is on. {@link #renderOnscreen} runs from
 * {@code ZenScreen.render} and either draws the finished buffer or, when the buffer path is off,
 * draws the world directly through the viewport. Both call the same {@code drawWorld}, which receives
 * the batch with its projection already set to {@link #camera} and the target already cleared, and is
 * expected to call {@code batch.begin()} / {@code end()} itself.
 * <p>
 * {@link Policy#EXTEND} shows at least the configured world size and reveals more world on one axis as
 * the window's aspect differs; {@link Policy#FIT} shows exactly the configured size and letterboxes.
 * Neither moves {@link #camera}: its position belongs to the screen (or a view system), unless
 * {@link #centerOnResize} is set for a static screen.
 */
public class WorldView implements Disposable {

    /** How the world rectangle maps onto the window. */
    public enum Policy { EXTEND, FIT }

    public final OrthographicCamera camera;
    public final Viewport viewport;

    /** Cleared at the start of every world pass; also what {@link Policy#FIT} gutters show. */
    public final Color clearColor;

    /** Recenter {@link #camera} on the world on every {@link #resize}. Off by default: a view system owns the position. */
    public boolean centerOnResize;

    private FrameBuffer buffer;
    private TextureRegion bufferRegion;
    private Texture.TextureFilter bufferFilter;

    public WorldView(OrthographicCamera camera, int worldWidth, int worldHeight) {
        this(camera, worldWidth, worldHeight, Policy.EXTEND);
    }

    public WorldView(OrthographicCamera camera, int worldWidth, int worldHeight, Policy policy) {
        this.camera = camera;
        this.viewport = switch (policy) {
            case EXTEND -> new ExtendViewport(worldWidth, worldHeight, camera);
            case FIT    -> new FitViewport(worldWidth, worldHeight, camera);
        };
        this.clearColor = Color.DARK_GRAY.cpy();
        this.centerOnResize = false;
        this.bufferFilter = Texture.TextureFilter.Nearest;
    }

    /** Current world size in world units; for {@link Policy#EXTEND} this is only known after the first {@link #resize}. */
    public float worldWidth()  { return viewport.getWorldWidth(); }
    public float worldHeight() { return viewport.getWorldHeight(); }

    /** True while the framebuffer path is active. */
    public boolean buffered() { return ZenFlag.RENDER_FBO.on(); }

    public void resize(int width, int height) {
        viewport.update(width, height, centerOnResize);
    }

    public void centerCamera() {
        camera.position.set(worldWidth() / 2f, worldHeight() / 2f, 0);
        camera.update();
    }

    /** Sampling of the world buffer when it is scaled onto the window: {@code Nearest} (default) for pixel art, {@code Linear} otherwise. */
    public void setBufferFilter(Texture.TextureFilter filter) {
        bufferFilter = filter;
        if (buffer != null) {
            buffer.getColorBufferTexture().setFilter(filter, filter);
        }
    }

    /** Window pixels (top-left origin, as {@code InputProcessor} reports them) to world units. */
    public Vector2 unproject(float screenX, float screenY, Vector2 out) {
        return viewport.unproject(out.set(screenX, screenY));
    }

    /** World units to window pixels (bottom-left origin, as {@code Batch} uses them). */
    public Vector2 project(float worldX, float worldY, Vector2 out) {
        return viewport.project(out.set(worldX, worldY));
    }

    /**
     * Offscreen half: when the buffer path is on, draws the world into the buffer.
     * Call only from {@code ZenScreen.renderOffscreenBuffers}, never inside another framebuffer.
     */
    public void renderOffscreen(Batch batch, Consumer<Batch> drawWorld) {
        if (!buffered()) return;
        if (viewport.getScreenWidth() <= 0 || viewport.getScreenHeight() <= 0) return; // minimized, nothing to render

        ensureBuffer();
        buffer.begin();
        {
            // One world unit per texel: the camera sees exactly the buffer, not the fractional viewport size
            // (the buffer grows with the camera's zoom, so a zoomed out view keeps its resolution)
            camera.viewportWidth  = buffer.getWidth()  / camera.zoom;
            camera.viewportHeight = buffer.getHeight() / camera.zoom;
            camera.update();

            ScreenUtils.clear(clearColor);
            batch.setProjectionMatrix(camera.combined);
            drawWorld.accept(batch);
        }
        buffer.end();
    }

    /**
     * Onscreen half: draws the buffer over the viewport's rectangle, or when the buffer path is off,
     * draws the world directly through the viewport. Call from {@code ZenScreen.render}.
     */
    public void renderOnscreen(Batch batch, OrthographicCamera windowCamera, Consumer<Batch> drawWorld) {
        ScreenUtils.clear(clearColor);
        if (buffered() && buffer != null) {
            batch.setProjectionMatrix(windowCamera.combined);
            batch.begin();
            batch.setColor(Color.WHITE);
            batch.draw(bufferRegion, viewport.getScreenX(), viewport.getScreenY(), viewport.getScreenWidth(), viewport.getScreenHeight());
            batch.end();
        } else {
            viewport.apply(); // glViewport to the world rectangle, camera viewport back to the world size
            batch.setProjectionMatrix(camera.combined);
            drawWorld.accept(batch);
        }
    }

    @Override
    public void dispose() {
        if (buffer != null) {
            buffer.dispose();
            buffer = null;
            bufferRegion = null;
        }
    }

    /** Creates the buffer on first use and again whenever the world size changes (Extend changes it on resize). */
    private void ensureBuffer() {
        var width  = Math.max(1, MathUtils.round(worldWidth()  * camera.zoom));
        var height = Math.max(1, MathUtils.round(worldHeight() * camera.zoom));
        if (buffer != null && buffer.getWidth() == width && buffer.getHeight() == height) return;

        if (buffer != null) buffer.dispose();
        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);

        var texture = buffer.getColorBufferTexture();
        texture.setFilter(bufferFilter, bufferFilter);

        bufferRegion = new TextureRegion(texture);
        bufferRegion.flip(false, true);
    }

}
