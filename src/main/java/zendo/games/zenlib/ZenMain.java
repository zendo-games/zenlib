package zendo.games.zenlib;

import aurelienribon.tweenengine.Timeline;
import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenManager;
import aurelienribon.tweenengine.primitives.MutableFloat;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import zendo.games.zenlib.assets.ZenTransitionShader;
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

    public static class ZenScreens {
        ZenScreen current;
        ZenScreen next;
    }
    public final ZenScreens screens = new ZenScreens();

    public ZenConfig config;

    private static class Transition {
        static final float DEFAULT_SPEED = 0.66f;

        boolean active;
        MutableFloat percent;
        ShaderProgram shader;

        static class FrameBuffers {
            FrameBuffer from;
            FrameBuffer to;
        }
        final FrameBuffers fbo = new FrameBuffers();

        static class Textures {
            Texture from;
            Texture to;
        }
        final Textures tex = new Textures();
    }
    private final Transition transition = new Transition();

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

        transition.active = false;
        transition.percent = new MutableFloat(0);
        transition.fbo.from = new FrameBuffer(Pixmap.Format.RGBA8888, config.window.width, config.window.height, false);
        transition.fbo.to = new FrameBuffer(Pixmap.Format.RGBA8888, config.window.width, config.window.height, false);
        transition.tex.from = transition.fbo.from.getColorBufferTexture();
        transition.tex.to = transition.fbo.to.getColorBufferTexture();

        setScreen(createStartScreen());
    }

    @Override
    public void dispose() {
        frameBuffer.dispose();
        zenAssets.dispose();
        instance.screens.current.dispose();
    }

    @Override
    public void resize(int width, int height) {
        if (screens.current != null) {
            screens.current.resize(width, height);
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
            screens.current.alwaysUpdate(Time.delta);
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
        screens.current.update(Time.delta);
    }

    @Override
    public void render() {
        update();
        var batch = zenAssets.batch;
        screens.current.renderFrameBuffers(batch);
        if (screens.next != null) {
            screens.next.update(Time.delta);
            screens.next.renderFrameBuffers(batch);

            // draw the next screen to the transition buffer
            transition.fbo.to.begin();
            {
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                screens.next.render(batch);
            }
            transition.fbo.to.end();

            // draw the current screen to the original buffer
            transition.fbo.from.begin();
            {
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                screens.current.render(batch);
            }
            transition.fbo.from.end();

            // draw the transition buffer to the screen
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setShader(transition.shader);
            batch.setProjectionMatrix(windowCamera.combined);
            batch.begin();
            {
                transition.tex.from.bind(1);
                transition.shader.setUniformi("u_texture1", 1);

                transition.tex.to.bind(0);
                transition.shader.setUniformi("u_texture", 0);

                transition.shader.setUniformf("u_percent", transition.percent.floatValue());

                batch.setColor(Color.WHITE);
                batch.draw(transition.tex.to, 0,0, config.window.width, config.window.height);
            }
            batch.end();
            batch.setShader(null);
        } else {
            screens.current.render(batch);
        }
    }

    public void setScreen(ZenScreen currentScreen) {
        setScreen(currentScreen, null, Transition.DEFAULT_SPEED);
    }

    public void setScreen(final ZenScreen newScreen, ZenTransitionShader type, float transitionSpeed) {
        // only one transition at a time
        if (transition.active) return;
        if (screens.next != null) return;

        if (screens.current == null) {
            // no current screen set, go ahead and set it
            screens.current = newScreen;
        } else {
            // current screen is active, so trigger transition to new screen
            transition.active = true;
            transition.percent.setValue(0);
            transition.shader = (type != null) ? type.shader : ZenTransitionShader.random();

            Timeline.createSequence()
                    .pushPause(.1f)
                    .push(Tween.call((i, baseTween) -> screens.next = newScreen))
                    .push(Tween.to(transition.percent, 0, transitionSpeed).target(1))
                    .push(Tween.call((i, baseTween) -> {
                        screens.current = screens.next;
                        screens.next = null;
                        transition.active = false;
                    }))
                    .start(tween);
        }
    }

}
