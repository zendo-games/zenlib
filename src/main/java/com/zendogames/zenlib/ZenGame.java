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
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.zendogames.zenlib.accessors.*;
import com.zendogames.zenlib.imgui.ImGuiGdx;
import com.zendogames.zenlib.screens.ZenScreen;
import com.zendogames.zenlib.screens.transition.ScreenTransition;
import com.zendogames.zenlib.screens.transition.ScreenTransitionEffect;
import space.earlygrey.shapedrawer.ShapeDrawer;

public abstract class ZenGame extends ApplicationAdapter {

    //public ZenAssets zenAssets;
    public TweenManager tween;
    public InputMultiplexer inputMux;
    public OrthographicCamera windowCamera;
    public ScreenTransition screenTransition;

    // TODO: move to assets
    public Texture pixel;
    public SpriteBatch batch;
    public ShapeDrawer shapes;

    public ZenScreen currentScreen;

    /** Settings the library needs from the game. */
    public abstract ZenConfig config();

    /** Register game flags and asset types here. Called once, after the library is ready. */
    protected void init() {}

    /** Called once, after everything (including imgui) has finished loading. */
    protected abstract ZenScreen createFirstScreen();

    @Override
    public final void create() {
        Zen.init(config());

        this.pixel = createPixel();
        this.batch = new SpriteBatch();
        this.shapes = new ShapeDrawer(batch, new TextureRegion(pixel));
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
        this.windowCamera.setToOrtho(false, Zen.config.framebufferWidth, Zen.config.framebufferHeight);
        this.windowCamera.update();
        this.screenTransition = new ScreenTransition(this);

//        VisUI.setSkipGdxVersionCheck(true);
//        VisUI.load(SkinType.ZENDO.get());

        ImGuiGdx.load();

        init();
    }

    @Override
    public void dispose() {
//        VisUI.dispose();
//        assets.dispose();
        screenTransition.dispose();
        if (currentScreen != null) {
            currentScreen.dispose();
        }
        pixel.dispose();
        batch.dispose();
        ImGuiGdx.dispose();
    }

    @Override
    public void render() {
        Time.update();

        // First screen waits for imgui, which loads async
        if (ImGuiGdx.isLoading()) return;
        if (currentScreen == null) {
            setScreen(createFirstScreen());
        }

        update();
        draw();
    }

    protected void update() {
        var dt = Time.delta;

        // Update things that must update every tick
        //FramePool.get().resetAll(); // TODO: add FramePool (including reflection for TeaVM)
        tween.update(dt);
        currentScreen.alwaysUpdate(dt);
        screenTransition.update(dt);

        // Skip updates if paused
        if (Time.isPaused()) return;
        currentScreen.update(dt);
    }

    protected void draw() {
        ScreenUtils.clear(Color.DARK_GRAY);
        if (screenTransition.inProgress()) {
            screenTransition.render(batch);
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

        if (currentScreen == null) return;
        currentScreen.resize(width, height);
    }

    public ZenScreen getScreen() { return currentScreen; }

    public void setScreen(ZenScreen newScreen)                                { setScreen(newScreen, null, false); }
    public void setScreen(ZenScreen newScreen, ScreenTransitionEffect effect) { setScreen(newScreen, effect, false); }
    public void setScreen(ZenScreen newScreen, ScreenTransitionEffect effect, boolean instant) {
        // Only one transition allowed at a time
        if (screenTransition.inProgress()) return;

        // Nothing to transition from, just set the current screen
        if (currentScreen == null) {
            currentScreen = newScreen;
            currentScreen.show();
            currentScreen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            routeInputTo(currentScreen);
            return;
        }

        screenTransition.to(newScreen, effect, instant);
        routeInputTo(newScreen);
    }

    private void routeInputTo(ZenScreen screen) {
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
