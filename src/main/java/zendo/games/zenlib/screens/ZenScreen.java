package zendo.games.zenlib.screens;

import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import zendo.games.zenlib.ZenAssets;
import zendo.games.zenlib.ZenMain;

public abstract class ZenScreen implements Disposable {

    public final ZenMain game;
    public final ZenAssets assets;
    public final TweenManager tween;
    public final SpriteBatch batch;
    public final Vector3 pointerPos;
    public final OrthographicCamera windowCamera;

    protected OrthographicCamera worldCamera;
    protected Viewport viewport;
    protected Stage uiStage;
    protected Skin skin;
    protected boolean exitingScreen;

    public ZenScreen() {
        this.game = ZenMain.game;
        this.assets = game.assets;
        this.tween = game.tween;
        this.batch = game.assets.batch;
        this.pointerPos = new Vector3();
        this.windowCamera = game.windowCamera;
        this.worldCamera = new OrthographicCamera();
        this.viewport = new ScreenViewport(worldCamera);
        this.exitingScreen = false;

        initializeUI();
    }

    @Override
    public void dispose() {}

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    /**
     * Update something in the screen even when
     * Time.pause_for thing is being processed
     * @param dt the time in seconds since the last frame
     */
    public void alwaysUpdate(float dt) {}

    public void update(float dt) {
        windowCamera.update();
        worldCamera.update();
        uiStage.act(dt);
    }

    public void renderFrameBuffers(SpriteBatch batch) {}

    public abstract void render(SpriteBatch batch);

    protected void initializeUI() {
//        skin = VisUI.getSkin();

        var viewport = new ScreenViewport(windowCamera);
        uiStage = new Stage(viewport, batch);

        // extend and setup any per-screen ui widgets in here...
    }

}
