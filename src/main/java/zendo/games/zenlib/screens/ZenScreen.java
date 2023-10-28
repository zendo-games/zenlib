package zendo.games.zenlib.screens;

import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.kotcrab.vis.ui.VisUI;
import zendo.games.zenlib.ZenAssets;
import zendo.games.zenlib.ZenMain;

public abstract class ZenScreen<AssetsType extends ZenAssets> implements Disposable {

    public final AssetsType assets;
    public final SpriteBatch batch;
    public final TweenManager tween;
    public final OrthographicCamera windowCamera;
    public final Vector3 pointerPos;

    protected OrthographicCamera worldCamera;
    protected Viewport viewport;
    protected Stage uiStage;
    protected Skin skin;
    protected boolean exitingScreen;

    /**
     * Create a new ZenScreen instance. This class is parameterized by {@code <AssetsType>}
     * to enable type-aware access to the main project's subclass of {@code ZenAssets}.
     *
     * @param assetsClazz the generic {@code AssetsType} class reference for the client subclass of {@code ZenAssets}
     */
    @SuppressWarnings("unchecked")
    public ZenScreen(Class<AssetsType> assetsClazz) {
        var main = ZenMain.instance;

        // enforce the generic type parameter for the assets class
        if (!ClassReflection.isInstance(assetsClazz, main.zenAssets)) {
            throw new GdxRuntimeException("ZenScreen: zenAssets must be an instance of " + assetsClazz.getSimpleName());
        }

        this.assets = (AssetsType) main.zenAssets;
        this.batch = assets.batch;
        this.tween = main.tween;
        this.windowCamera = main.windowCamera;
        this.pointerPos = new Vector3();
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
     * {@code Time.pause_for()} is being processed
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
        skin = VisUI.getSkin();

        var viewport = new ScreenViewport(windowCamera);
        uiStage = new Stage(viewport, batch);

        // extend and setup any per-screen ui widgets in here...
    }

}
