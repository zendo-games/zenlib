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
import zendo.games.zenlib.assets.ZenTransition;
import zendo.games.zenlib.screens.ZenScreen;
import zendo.games.zenlib.screens.transitions.Transition;
import zendo.games.zenlib.utils.Time;
import zendo.games.zenlib.utils.accessors.*;

public abstract class ZenMain<AssetsType extends ZenAssets> extends ApplicationAdapter {

    /**
     * Debug flags, toggle these values as needed, or override in project's ZenMain subclass
     */
    public static class Debug {
        public static boolean general = false;
        public static boolean shaders = false;
        public static boolean ui = false;
    }

    @SuppressWarnings("rawtypes")
    public static ZenMain instance;

    public ZenConfig config;
    public AssetsType assets;
    public TweenManager tween;
    public FrameBuffer frameBuffer;
    public TextureRegion frameBufferRegion;
    public OrthographicCamera windowCamera;

    private Transition transition;

    public ZenMain(ZenConfig config) {
        ZenMain.instance = this;
        this.config = config;
    }

    /**
     * Override to create project-specific ZenAssets subclass instance
     */
    public abstract AssetsType createAssets();

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

        assets = createAssets();

        transition = new Transition(config);
        setScreen(createStartScreen());
    }

    @Override
    public void dispose() {
        frameBuffer.dispose();
        transition.dispose();
        assets.dispose();
    }

    @Override
    public void resize(int width, int height) {
        var screen = currentScreen();
        if (screen != null) {
            screen.resize(width, height);
        }
    }

    public ZenScreen currentScreen() {
        return transition.screens.current;
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
            transition.alwaysUpdate(Time.delta);
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
        transition.update(Time.delta);
    }

    @Override
    public void render() {
        update();
        var batch = assets.batch;
        var screens = transition.screens;

        screens.current.renderFrameBuffers(batch);
        if (screens.next == null) {
            screens.current.render(batch);
        } else {
            transition.render(batch, windowCamera);
        }
    }

    public void setScreen(ZenScreen currentScreen) {
        setScreen(currentScreen, null, Transition.DEFAULT_SPEED);
    }

    public void setScreen(final ZenScreen newScreen, ZenTransition type, float transitionSpeed) {
        // only one transition at a time
        if (transition.active) return;
        if (transition.screens.next != null) return;

        var screens = transition.screens;
        if (screens.current == null) {
            // no current screen set, go ahead and set it
            screens.current = newScreen;
        } else {
            // current screen is set, so trigger transition to new screen
            transition.startTransition(newScreen, type, transitionSpeed);
        }
    }
}
