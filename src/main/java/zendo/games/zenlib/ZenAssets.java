package zendo.games.zenlib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import space.earlygrey.shapedrawer.ShapeDrawer;

public abstract class ZenAssets implements Disposable {

    /**
     * Override this value in project's ZenAssets subclass!
     */
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

        mgr = new AssetManager();
        loadManagerAssets();

        batch = new SpriteBatch();
        shapes = new ShapeDrawer(batch, pixelRegion);
        preferences = Gdx.app.getPreferences(PREFS_NAME);
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

    public float update() {
        if (!mgr.update()) return mgr.getProgress();
        if (initialized) return 1;
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

    public static ShaderProgram loadShader(String vertSourcePath, String fragSourcePath) {
        ShaderProgram.pedantic = false;

        var shaderProgram = new ShaderProgram(
                Gdx.files.internal(vertSourcePath),
                Gdx.files.internal(fragSourcePath));
        var log = shaderProgram.getLog();

        if (!shaderProgram.isCompiled()) {
            throw new GdxRuntimeException("LoadShader: compilation failed:\n" + log);
        } else if (ZenMain.Debug.shaders) {
            Gdx.app.debug("LoadShader", "compilation log:\n" + log);
        }

        return shaderProgram;
    }

    public void putPref(String key, String value) {
        preferences.putString(key, value);
        preferences.flush();
    }

    public String getPref(String key) {
        return preferences.getString(key, "");
    }

}
