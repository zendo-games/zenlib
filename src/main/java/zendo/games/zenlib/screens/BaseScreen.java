package zendo.games.zenlib.screens;

import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Disposable;
import zendo.games.zenlib.LibMain;
import zendo.games.zenlib.utils.screenshake.CameraShaker;

public abstract class BaseScreen implements Disposable {

    public final LibMain game;
//    public final Assets assets;
    public final TweenManager tween;
//    public final SpriteBatch batch;
    public final Vector3 pointerPos;
    public final OrthographicCamera windowCamera;

    protected OrthographicCamera worldCamera;
    protected CameraShaker screenShaker;
    protected Stage uiStage;
    protected Skin skin;
    protected boolean exitingScreen;

    public BaseScreen(int worldCamWidth, int worldCamHeight) {
        this.game = LibMain.game;
//        this.assets = game.assets;
        this.tween = game.tween;
//        this.batch = assets.batch;
        this.pointerPos = new Vector3();
        this.windowCamera = game.windowCamera;
        this.exitingScreen = false;
        this.worldCamera = new OrthographicCamera();
        this.screenShaker = new CameraShaker(worldCamera);

        worldCamera.setToOrtho(false, worldCamWidth, worldCamHeight);
        worldCamera.update();

        initializeUI();
    }

    @Override
    public void dispose() {}

    /**
     * Update something in the screen even when
     * Time.pause_for thing is being processed
     * @param dt the time in seconds since the last frame
     */
    public void alwaysUpdate(float dt) {}

    public void update(float dt) {
        windowCamera.update();
        if (worldCamera != null) {
            worldCamera.update();
        }

        screenShaker.update(dt);
        uiStage.act(dt);
    }

    public void renderFrameBuffers(SpriteBatch batch) {}

    public abstract void render(SpriteBatch batch);

    protected void initializeUI() {
//        skin = VisUI.getSkin();
//
//        var viewport = new StretchViewport(windowCamera.viewportWidth, windowCamera.viewportHeight);
//        uiStage = new Stage(viewport, batch);

        // extend and setup any per-screen ui widgets in here...
    }

}
