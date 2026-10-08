package com.zendogames.zenlib.screens;

import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.github.tommyettinger.textra.Font;
import com.github.tommyettinger.textra.Layout;
import com.zendogames.zenlib.FramePool;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.Zen;
import com.zendogames.zenlib.ZenGame;
import com.zendogames.zenlib.assets.Assets;
import com.zendogames.zenlib.input.ScreenInputProcessor;
import com.zendogames.zenlib.render.WorldView;
import space.earlygrey.shapedrawer.ShapeDrawer;

public abstract class ZenScreen implements Screen {

    private static final String TAG = "Screen";

    public final ZenGame game;
    public final Assets assets;
    public final SpriteBatch batch;
    public final ShapeDrawer shapes;
    public final TweenManager tween;
    public final OrthographicCamera windowCamera;
    public final OrthographicCamera worldCamera;
    public final WorldView worldView;
    public final Stage uiStage;
    public final ScreenInputProcessor inputProcessor;

    public InputMultiplexer inputMux;
    public Font font;
    public Layout layout;

    /** True while this screen is either side of a screen transition. */
    public boolean transitioning = false;

    public ZenScreen(ZenGame game) {
        this.game = game;
        this.assets = this.game.assets;
        this.batch  = this.game.batch;
        this.shapes = this.game.shapes;
        this.tween  = this.game.tween;
        this.windowCamera = this.game.windowCamera;
        this.worldCamera = new OrthographicCamera();
        this.worldView = createWorldView();
        this.uiStage  = new Stage(new ScreenViewport(windowCamera), batch);
        this.inputProcessor = createInputProcessor();
        this.inputMux = new InputMultiplexer();
        this.inputMux.addProcessor(uiStage);
        this.inputMux.addProcessor(inputProcessor);
    }

    /**
     * Override to pick the world size or policy. Runs inside this constructor, before the subclass body,
     * so only {@link #worldCamera} is available; configure the view inline and return it. The default is
     * an {@code EXTEND} view of the size in {@link Zen#config}.
     */
    protected WorldView createWorldView() {
        return new WorldView(worldCamera, Zen.config.framebufferWidth(), Zen.config.framebufferHeight());
    }

    /**
     * Override to return a game screen specific handler. Runs inside this constructor, before the subclass body,
     * so return {@code new MyHandler(this)} and read screen fields from the handler at event time only.
     */
    protected ScreenInputProcessor createInputProcessor() {
        return new ScreenInputProcessor(this);
    }

    /** @see ApplicationListener#resize(int, int) */
    @Override
    public void resize(int width, int height) {
        Log.debug(TAG, "%s resize -> (%d, %d)", getClass().getSimpleName(), width, height);
        // If the window is minimized on a desktop (LWJGL3) platform, width and height are 0, which causes problems.
        // In that case, we don't resize anything, and wait for the window to be a normal size before updating.
        if(width <= 0 || height <= 0) return;

        // Resize screen, parameters represent the new window size. The window camera is resized by ZenGame. */
        worldView.resize(width, height);
        uiStage.getViewport().update(width, height, true);
    }

    /** Called once, when this screen is handed to {@link ZenGame#setScreen}, before its first update or render. */
    @Override
    public void show() {
        Log.debug(TAG, "%s show", getClass().getSimpleName());
    }

    /** @see ApplicationListener#pause() */
    @Override
    public void pause() {
        Log.debug(TAG, "%s pause", getClass().getSimpleName());
    }

    /** @see ApplicationListener#resume() */
    @Override
    public void resume() {
        Log.debug(TAG, "%s resume", getClass().getSimpleName());
    }

    /** Called once, when this screen stops being the current screen, just before {@link #dispose()}. */
    @Override
    public void hide() {
        Log.debug(TAG, "%s hide", getClass().getSimpleName());
    }

    /** Called when this screen should release all resources. */
    @Override
    public void dispose() {
        Log.debug(TAG, "%s dispose", getClass().getSimpleName());
        worldView.dispose();
        uiStage.dispose();
    }

    /**
     * Called every frame, even during a {@code Time.pauseFor}.
     * @param delta {@code Time.frameDelta}, which is never zeroed by a pause.
     */
    public void alwaysUpdate(float delta) {}

    /**
     * Called every frame except during a {@code Time.pauseFor}.
     * @param delta {@code Time.delta}
     */
    public void update(float delta) {
        worldCamera.update();
        uiStage.act(delta);
    }

    // ------------------------------------------------------------------------
    // Rendering: offscreen passes first (ZenGame and ScreenTransition both call them before
    // binding any framebuffer), then the onscreen pass, which is the world view followed by UI.
    // ------------------------------------------------------------------------

    /**
     * Draw world-space content. The batch's projection is already {@link #worldCamera} and the target is
     * cleared with {@code worldView.clearColor}; call {@code batch.begin()} / {@code end()} here. Runs once
     * per frame on whichever path {@link WorldView} is using, so never assume a framebuffer is bound.
     */
    protected void renderWorld(SpriteBatch batch) {}

    /** Draw window-space content over the world. The default draws {@link #uiStage}. */
    protected void renderUI() {
        uiStage.getViewport().apply();
        uiStage.draw();
    }

    /** Framebuffer passes. Override to add your own, and call super so the world view gets its pass. */
    public void renderOffscreenBuffers(SpriteBatch batch) {
        worldView.renderOffscreen(batch, this::renderWorld);
    }

    /**
     * Called when the screen should render itself. The default is the world view then {@link #renderUI()};
     * override for a different arrangement.
     * @param delta The time in seconds since the last render.
     */
    @Override
    public void render(float delta) {
        worldView.renderOnscreen(batch, windowCamera, this::renderWorld);
        renderUI();
    }

    public void renderImGui() {}

    public void initializeUI() {}

    /** The mouse position in world units, through the world view, valid for this frame only. */
    protected Vector2 mouseWorldPosition() {
        return worldView.unproject(Gdx.input.getX(), Gdx.input.getY(), FramePool.vec2(0, 0));
    }
}
