package com.zendogames.zenlib.screens;

import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.github.tommyettinger.textra.Font;
import com.github.tommyettinger.textra.Layout;
import com.zendogames.zenlib.ZenFlag;
import com.zendogames.zenlib.ZenGame;
import com.zendogames.zenlib.imgui.ImGuiGdx;
import imgui.ImGui;
import imgui.ImGuiImpl;
import imgui.enums.ImGuiConfigFlags;
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
    public boolean transitioning = false;

    public ZenScreen(ZenGame game) {
        this.game = game;
        //this.assets = this.game.assets;
        this.batch  = this.game.batch;
        this.shapes = this.game.shapes;
        this.tween  = this.game.tween;
        this.windowCamera = this.game.windowCamera;
        this.uiStage  = new Stage(new ScreenViewport(windowCamera));
        this.inputMux = new InputMultiplexer();
        this.inputMux.addProcessor(uiStage);
        //this.inputMux.addProcessor(new ScreenInputHandler(this));
        this.worldCamera = new OrthographicCamera();
        //this.worldView = new WorldView(worldCamera);
    }

    /** @see ApplicationListener#resize(int, int) */
    @Override
    public void resize(int width, int height) {
        // If the window is minimized on a desktop (LWJGL3) platform, width and height are 0, which causes problems.
        // In that case, we don't resize anything, and wait for the window to be a normal size before updating.
        if(width <= 0 || height <= 0) return;

        // Resize screen, parameters represent the new window size.
        windowCamera.setToOrtho(false, width, height);
        //worldView.resize(width, height);
        uiStage.getViewport().update(width, height, true);
    }

    /** @see Screen#show() */
    @Override public void show() {}

    /** @see ApplicationListener#pause() */
    @Override public void pause() {}

    /** @see ApplicationListener#resume() */
    @Override public void resume() {}

    /** @see Screen#hide() */
    @Override public void hide() {}

    /** Called when the screen should render itself.
     * @param delta The time in seconds since the last render. */
    public abstract void render(float delta);

    /** Called when this screen should release all resources. */
    @Override
    public void dispose() {
        //worldView.dispose();
        uiStage.dispose();
    }

    public void alwaysUpdate(float delta) {}

    public void update(float delta) {
//        Config.stepped_frame = false;
        windowCamera.update();
        if (worldCamera != null) {
            worldCamera.update();
        }
        uiStage.act(Math.min(delta, 1 / 30f));
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
