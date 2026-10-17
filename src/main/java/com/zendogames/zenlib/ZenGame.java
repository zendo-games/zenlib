package com.zendogames.zenlib;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.zendogames.zenlib.accessors.*;
import com.zendogames.zenlib.assets.Assets;
import com.zendogames.zenlib.assets.ZenFont;
import com.zendogames.zenlib.imgui.ImGuiGdx;
import com.zendogames.zenlib.screens.ZenScreen;
import com.zendogames.zenlib.screens.transition.ScreenTransition;
import com.zendogames.zenlib.screens.transition.ScreenTransitionEffect;
import com.zendogames.zenlib.ui.DebugUI;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.util.Optional;

public abstract class ZenGame extends ApplicationAdapter {

    public TweenManager tween;
    public InputMultiplexer inputMux;
    public OrthographicCamera windowCamera;
    public ScreenTransition screenTransition;
    public Assets assets;
    public Texture pixel;
    public Batch batch;
    public ShapeDrawer shapes;
    public DebugUI debugUI;
    public GlyphLayout layout;
    public BitmapFont defaultFont;

    /** Change with {@link #setScreen}, which is the only place a screen becomes current. */
    protected ZenScreen currentScreen;

    /** Target of instant screen change, swapped in at the start of the next update. */
    protected ZenScreen pendingScreen;

    /** Settings the library needs from the game. */
    public abstract ZenConfig config();

    /** Register game flags and asset types here. Called once, after the library is ready. */
    protected void init() {}

    /** Called once, after everything (including imgui) has finished loading. */
    protected abstract ZenScreen createFirstScreen();

    /**
     * The one batch everything draws with. A {@code PolygonSpriteBatch} so Spine meshes and clipping
     * draw in the same pass as sprites, tile layers and UI; override to return spine's {@code TwoColorPolygonBatch}
     * when a skeleton uses dark (two color) tinting.
     */
    protected Batch createBatch() {
        return new PolygonSpriteBatch(4000); // 1000 quads per flush, SpriteBatch's default
    }

    /** Dispose what {@link #init} created here. Called once, before the library disposes anything. */
    protected void shutdown() {}

    @Override
    public final void create() {
        Zen.init(config());

        this.tween = new TweenManager();
        Tween.setWaypointsLimit(4);
        Tween.setCombinedAttributesLimit(4);
        Tween.registerAccessor(OrthographicCamera.class, new CameraAccessor());
        Tween.registerAccessor(Circle.class, new CircleAccessor());
        Tween.registerAccessor(Color.class, new ColorAccessor());
        Tween.registerAccessor(Rectangle.class, new RectangleAccessor());
        Tween.registerAccessor(Vector2.class, new Vector2Accessor());
        Tween.registerAccessor(Vector3.class, new Vector3Accessor());
        this.inputMux = new InputMultiplexer();
        this.windowCamera = new OrthographicCamera();
        this.windowCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.windowCamera.update();
        this.screenTransition = new ScreenTransition();
        this.assets = new Assets();
        this.pixel = createPixel();
        this.batch = createBatch();
        this.shapes = new ShapeDrawer(batch, new TextureRegion(pixel));
        this.debugUI = new DebugUI();
        this.layout = new GlyphLayout();

        // Generate default font separately from its ZenFont generation
        // before assets are fully loaded, for the loading screen text
        var font = ZenFont.JETBRAINS_MONO_16;
        var generator = new FreeTypeFontGenerator(font.file());
        this.defaultFont = generator.generateFont(font.params());
        generator.dispose();

//        VisUI.setSkipGdxVersionCheck(true);
//        VisUI.load(SkinType.ZENDO.get());

        ImGuiGdx.load();

        init();
        assets.load();
    }

    @Override
    public void dispose() {
        shutdown();

        var nextScreen = screenTransition.next();
        if (nextScreen != null) nextScreen.dispose();
        if (pendingScreen != null) pendingScreen.dispose();
        if (currentScreen != null) currentScreen.dispose();
        screenTransition.dispose();

        pixel.dispose();
        batch.dispose();
        defaultFont.dispose();
//        VisUI.dispose();
        ImGuiGdx.dispose();
        assets.dispose();
    }

    protected void renderLoading(float progress) {
        ScreenUtils.clear(Color.LIGHT_GRAY);
        batch.setProjectionMatrix(windowCamera.combined);
        batch.begin();
        var text = "Loading %.0f%%...".formatted(progress * 100f);
        layout.setText(defaultFont, text, Color.WHITE, windowCamera.viewportWidth, Align.center, false);
        defaultFont.draw(batch, layout, 0, (windowCamera.viewportHeight - layout.height) * 0.5f);
        batch.end();
    }

    @Override
    public void render() {
        Time.update();

        // First screen waits for imgui and assets, which load async
        if (ImGuiGdx.isLoading() || !assets.update()) {
            renderLoading(assets.progress());
            return;
        }
        if (currentScreen == null) {
            setScreen(createFirstScreen());
        }

        update();
        draw();
    }

    protected void update() {
        var dt = Time.delta;
        var frameDt = Time.frameDelta;

        // Finish an instant screen change requested during the previous frame
        if (pendingScreen != null) {
            makeCurrent(pendingScreen);
            pendingScreen = null;
        }

        // Update things that must update every tick, these keep running through a Time.pauseFor
        FramePool.resetAll();
        tween.update(frameDt);
        windowCamera.update();
        currentScreen.alwaysUpdate(frameDt);
        if (screenTransition.inProgress()) {
            screenTransition.next().alwaysUpdate(frameDt);

            var finished = screenTransition.update(frameDt);
            if (finished != null) {
                makeCurrent(finished);
            }
        }

        // Skip updates if paused
        if (Time.isPaused()) return;

        currentScreen.update(dt);
        if (screenTransition.inProgress()) {
            screenTransition.next().update(dt);
        }
    }

    protected void draw() {
        ScreenUtils.clear(Color.DARK_GRAY);
        if (screenTransition.inProgress()) {
            screenTransition.render(batch, windowCamera);
        } else {
            currentScreen.renderOffscreenBuffers(batch);
            currentScreen.render(Time.delta);
        }
        ImGuiGdx.render(currentScreen::renderImGui);
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;

        windowCamera.setToOrtho(false, width, height);
        windowCamera.update();

        var nextScreen = screenTransition.next();
        if (nextScreen    != null) nextScreen.resize(width, height);
        if (currentScreen != null) currentScreen.resize(width, height);
        if (pendingScreen != null) pendingScreen.resize(width, height);
    }

    @Override
    public void pause() {
        if (currentScreen != null) currentScreen.pause();
    }

    @Override
    public void resume() {
        if (currentScreen != null) currentScreen.resume();
    }

    public ZenScreen getScreen() { return currentScreen; }

    public void setScreen(ZenScreen newScreen)                                { setScreen(newScreen, null, false); }
    public void setScreen(ZenScreen newScreen, ScreenTransitionEffect effect) { setScreen(newScreen, effect, false); }
    public void setScreen(ZenScreen newScreen, ScreenTransitionEffect effect, boolean instant) {
        Log.debug("Screen", "transition: requested %s", newScreen.getClass().getSimpleName());
        // Only one transition allowed at a time
        if (screenTransition.inProgress() || pendingScreen != null) {
            Log.warn("Screen", "transition: only one allowed at a time: inProgress=%b, pending=%s",
                    screenTransition.inProgress(), Optional.ofNullable(pendingScreen).map(Object::getClass).map(Class::getSimpleName).orElse("null"));
            return;
        }

        // Ready the new screen before anything updates or renders it
        newScreen.show();
        newScreen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // Nothing to transition from, just set the current screen
        if (currentScreen == null) {
            Log.debug("Screen", "transition: no current screen, setting %s...", newScreen.getClass().getSimpleName());
            makeCurrent(newScreen);
            return;
        }

        // An instant change (or an unusable effect) waits for the next update,
        // so the current screen is never disposed in the middle of its own update
        var blending = !instant && screenTransition.start(currentScreen, newScreen, effect);
        if (!blending) {
            pendingScreen = newScreen;
        }
    }

    /** The one place a screen becomes current. The previous screen, if any, is hidden and disposed. */
    protected void makeCurrent(ZenScreen newScreen) {
        var previous = currentScreen;

        currentScreen = newScreen;
        routeInputTo(currentScreen);

        if (previous != null) {
            previous.hide();
            previous.dispose();
        }

        Log.info("Screen", "transition: current -> %s", currentScreen.getClass().getSimpleName());
    }

    protected void routeInputTo(ZenScreen screen) {
        inputMux.clear();
        if (ImGuiGdx.isInitialized()) {
            inputMux.addProcessor(ImGuiGdx.input());
        }
        inputMux.addProcessor(screen.inputMux);
        Gdx.input.setInputProcessor(inputMux);
    }

    protected Texture createPixel() {
        var pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.drawPixel(0, 0);
        pixmap.drawPixel(1, 0);
        pixmap.drawPixel(0, 1);
        pixmap.drawPixel(1, 1);
        var texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }
}
