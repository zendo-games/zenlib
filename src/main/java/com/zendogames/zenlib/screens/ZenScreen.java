package com.zendogames.zenlib.screens;

import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.github.tommyettinger.textra.Font;
import com.github.tommyettinger.textra.Layout;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ZenGame;
import space.earlygrey.shapedrawer.ShapeDrawer;

public abstract class ZenScreen implements Screen {

    public final ZenGame game;
    //public final Assets assets;
    public final SpriteBatch batch;
    public final ShapeDrawer shapes;
    public final TweenManager tween;
    public final OrthographicCamera windowCamera;
    public final Stage uiStage;

    public InputMultiplexer inputMux;
    public OrthographicCamera worldCamera;
    //public WorldView worldView;
    public Font font;
    public Layout layout;

    /** True while this screen is either side of a screen transition. */
    public boolean transitioning = false;

    public ZenScreen(ZenGame game) {
        this.game = game;
        //this.assets = this.game.assets;
        this.batch  = this.game.batch;
        this.shapes = this.game.shapes;
        this.tween  = this.game.tween;
        this.windowCamera = this.game.windowCamera;
        this.uiStage  = new Stage(new ScreenViewport(windowCamera), batch);
        this.inputMux = new InputMultiplexer();
        this.inputMux.addProcessor(uiStage);
        //this.inputMux.addProcessor(new ScreenInputHandler(this));
        this.worldCamera = new OrthographicCamera();
        //this.worldView = new WorldView(worldCamera);
    }

    /** @see ApplicationListener#resize(int, int) */
    @Override
    public void resize(int width, int height) {
        Log.debug("Screen", "%s resize -> (%d, %d)", getClass().getSimpleName(), width, height);
        // If the window is minimized on a desktop (LWJGL3) platform, width and height are 0, which causes problems.
        // In that case, we don't resize anything, and wait for the window to be a normal size before updating.
        if(width <= 0 || height <= 0) return;

        // Resize screen, parameters represent the new window size. The window camera is resized by ZenGame. */
        //worldView.resize(width, height);
        uiStage.getViewport().update(width, height, true);
    }

    /** Called once, when this screen is handed to {@link ZenGame#setScreen}, before its first update or render. */
    @Override
    public void show() {
        Log.debug("Screen", "%s show", getClass().getSimpleName());
    }

    /** @see ApplicationListener#pause() */
    @Override
    public void pause() {
        Log.debug("Screen", "%s pause", getClass().getSimpleName());
    }

    /** @see ApplicationListener#resume() */
    @Override
    public void resume() {
        Log.debug("Screen", "%s resume", getClass().getSimpleName());
    }

    /** Called once, when this screen stops being the current screen, just before {@link #dispose()}. */
    @Override
    public void hide() {
        Log.debug("Screen", "%s hide", getClass().getSimpleName());
    }

    /** Called when the screen should render itself.
     * @param delta The time in seconds since the last render. */
    public abstract void render(float delta);

    /** Called when this screen should release all resources. */
    @Override
    public void dispose() {
        Log.debug("Screen", "%s dispose", getClass().getSimpleName());
        //worldView.dispose();
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
        if (worldCamera != null) {
            worldCamera.update();
        }
        uiStage.act(delta);
    }

    protected Vector3 mouseWorldPosition() {
        // TODO: restore FramePool
//        var screenPos = FramePool.vec3(Gdx.input.getX(), Gdx.input.getY(), 0);
        var screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        return worldCamera.unproject(screenPos);
    }

    public void renderOffscreenBuffers(SpriteBatch batch) {}
    public void renderImGui() {}

    public void initializeUI() {}
}
