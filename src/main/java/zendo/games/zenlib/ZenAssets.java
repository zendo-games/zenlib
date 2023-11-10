package zendo.games.zenlib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.VisUI;
import space.earlygrey.shapedrawer.ShapeDrawer;
import zendo.games.zenlib.assets.ZenParticles;
import zendo.games.zenlib.assets.ZenPatch;
import zendo.games.zenlib.assets.ZenTransition;

public abstract class ZenAssets implements Disposable {

    public static final AssetDescriptor<TextureAtlas> ZEN_PATCH_DESCRIPTOR =
            new AssetDescriptor<>(Gdx.files.classpath("zendo/games/zenlib/assets/zenpatch.atlas"), TextureAtlas.class);

    public static String PREFS_NAME = "zenlib_prefs";

    public final SpriteBatch batch;
    public final ShapeDrawer shapes;
    public final AssetManager mgr;
    public final Texture pixelTexture;
    public final TextureRegion pixelRegion;
    private final Preferences preferences;
    private boolean initialized;

    public ZenAssets() {
        initialized = false;
        // create a single pixel texture and associated region
        var pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        {
            pixmap.setColor(Color.WHITE);
            pixmap.drawPixel(0, 0);
            pixelTexture = new Texture(pixmap);
        }
        pixmap.dispose();
        pixelRegion = new TextureRegion(pixelTexture);

        batch = new SpriteBatch();
        shapes = new ShapeDrawer(batch, pixelRegion);
        preferences = Gdx.app.getPreferences(PREFS_NAME);

        mgr = new AssetManager();

        // load the skin if specified in config
        if (ZenMain.instance.config.ui.skinPath != null) {
            mgr.load(ZenMain.instance.config.ui.skinPath, Skin.class);
        }

        // load the zen patch atlas
        mgr.load(ZEN_PATCH_DESCRIPTOR);

        loadManagerAssets();

        // TODO - add support for sync/async loading
        mgr.finishLoading();
        initialize();
    }

    /**
     * Override in subclasses with calls to `mgr.load(...)` for all project assets
     */
    public abstract void loadManagerAssets();

    /**
     * Override in subclasses with project specific asset init and lookup/caching code.
     * This is called after all assets have been loaded in the manager and can be accessed
     * for things like `mgr.get(...)` calls and sprite atlas region lookups.
     */
    public abstract void initCachedAssets();

    public float initialize() {
        if (!mgr.update()) return mgr.getProgress();
        if (initialized) return 1;
        loadLibraryAssets();
        initCachedAssets();
        initialized = true;
        return 1;
    }

    @Override
    public void dispose() {
        batch.dispose();
        mgr.dispose();
        pixelTexture.dispose();
    }

    public void putPref(String key, String value) {
        preferences.putString(key, value);
        preferences.flush();
    }

    public String getPref(String key) {
        return preferences.getString(key, "");
    }

    private void loadLibraryAssets() {
        loadVisUI();
        ZenParticles.init(pixelRegion);
        ZenPatch.init(mgr.get(ZEN_PATCH_DESCRIPTOR));
        ZenTransition.init();
    }

    private void loadVisUI() {
        var path = ZenMain.instance.config.ui.skinPath;
        if (path == null) {
            VisUI.load(VisUI.SkinScale.X2);
            Gdx.app.debug("LoadVisUI", "No uiSkinPath specified in ZenConfig, loading default VisUI skin (x2 scale)");
        } else {
            VisUI.load(mgr.get(path, Skin.class));
        }
    }
}
