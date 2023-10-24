package zendo.games.zenlib;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import zendo.games.zenlib.screens.ZenScreen;
import zendo.games.zenlib.utils.Time;
import zendo.games.zenlib.utils.accessors.*;

public abstract class ZenMain extends ApplicationAdapter {

    /**
     * Debug flags, toggle these values as needed, or override in project's ZenMain subclass
     */
    public static class Debug {
        public static boolean general = false;
        public static boolean shaders = false;
        public static boolean ui = false;
    }

    public static ZenMain instance;

    public ZenAssets zenAssets;
    public TweenManager tween;
    public FrameBuffer frameBuffer;
    public TextureRegion frameBufferRegion;
    public OrthographicCamera windowCamera;

    public ZenScreen screen;
    public ZenConfig config;

    public ZenMain(ZenConfig config) {
        ZenMain.instance = this;
        this.config = config;
    }

    /**
     * Override to create project-specific ZenAssets subclass instance
     */
    public abstract ZenAssets createAssets();

    /**
     * Override to create project-specific ZenScreen subclass instance that will be used as the starting screen
     */
    public abstract ZenScreen createStartScreen();

    @Override
    public void create() {
        Time.init();

        // TODO - consider moving to ZenAssets
        tween = new TweenManager();
        Tween.setWaypointsLimit(4);
        Tween.setCombinedAttributesLimit(4);
        Tween.registerAccessor(Color.class, new ColorAccessor());
        Tween.registerAccessor(Rectangle.class, new RectangleAccessor());
        Tween.registerAccessor(Vector2.class, new Vector2Accessor());
        Tween.registerAccessor(Vector3.class, new Vector3Accessor());
        Tween.registerAccessor(OrthographicCamera.class, new CameraAccessor());

        frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, config.window.width, config.window.height, true);
        var texture = frameBuffer.getColorBufferTexture();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        frameBufferRegion = new TextureRegion(texture);
        frameBufferRegion.flip(false, true);

        windowCamera = new OrthographicCamera();
        windowCamera.setToOrtho(false, config.window.width, config.window.height);
        windowCamera.update();

        zenAssets = createAssets();
        screen = createStartScreen();

        // TODO - setScreen() to handle transitions
    }

    @Override
    public void dispose() {
        frameBuffer.dispose();
        zenAssets.dispose();
    }

    @Override
    public void resize(int width, int height) {
        if (screen != null) {
            screen.resize(width, height);
        }
    }

    public void update() {
        // handle global input
        // TODO - might not want to keep these in library code
        {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                Gdx.app.exit();
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
                Debug.general = !Debug.general;
                Debug.ui = !Debug.ui;
            }
        }

        // update things that must update every tick
        {
            Time.update();
            tween.update(Time.delta);
            screen.alwaysUpdate(Time.delta);
        }

        // handle a pause
        if (Time.pause_timer > 0) {
            Time.pause_timer -= Time.delta;
            if (Time.pause_timer <= -0.0001f) {
                Time.delta = -Time.pause_timer;
            } else {
                // skip updates if we're paused
                return;
            }
        }
        Time.millis += (long) Time.delta;
        Time.previous_elapsed = Time.elapsed_millis();

        // update normally (if not paused)
        screen.update(Time.delta);
    }

    @Override
    public void render() {
        update();
        screen.render(zenAssets.batch);
    }

}
