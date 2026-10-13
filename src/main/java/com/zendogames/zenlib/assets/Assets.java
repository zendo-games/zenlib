package com.zendogames.zenlib.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.TimeUtils;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ZenFlag;
import com.zendogames.zenlib.tilemap.ZenTmxMapLoader;

/**
 * Owns the {@link AssetManager} and runs the two phases over every registered {@link AssetType}:
 * {@link #load()} queues files, {@link #update()} waits for the manager and then calls
 * {@code init} in registration order, filling each constant's {@link Asset} slot.
 * One instance per game: the slots live in the enum constants, so a second instance would
 * overwrite the first (the same trade-off {@code Flags} makes, in exchange for {@code KEY.get()}
 * needing no reference).
 */
public class Assets implements Disposable {

    /** Classpath root of the library's own files. */
    public static final String LIBRARY_PREFIX = "com/zendogames/zenlib/assets/";

    /** Resolver that finds asset files in the library classpath or in internal storage based on it's name prefix. */
    private static final FileHandleResolver FILE_HANDLE_RESOLVER = fileName ->
            fileName.startsWith(LIBRARY_PREFIX)
                    ? Gdx.files.classpath(fileName)
                    : Gdx.files.internal(fileName);

    /** When {@code ZenFlag.DEBUG_ASSET_LOAD} is enabled, enforce a minimum load duration to test the loading progress view. */
    private static final float DEBUG_MIN_LOAD_SECS = 3f;

    private static final String TAG = "Assets";

    public final AssetManager mgr;
    public final Array<Disposable> disposables;

    private final Array<AssetType<?>> types;
    private long loadStartMillis;
    private boolean queued;
    private boolean loaded;

    public Assets() {
        this.mgr = new AssetManager(FILE_HANDLE_RESOLVER);
        this.mgr.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(mgr.getFileHandleResolver()));
        this.mgr.setLoader(TiledMap.class, new ZenTmxMapLoader(mgr.getFileHandleResolver()));
        this.disposables = new Array<>();
        this.types = new Array<>();
        this.loadStartMillis = TimeUtils.millis();
        this.queued = false;
        this.loaded = false;

        // Library assets first: a category may use anything registered before it (eg. icons use an atlas)
        register(ZenAtlas.values());
        register(ZenFont.values());
        register(ZenAnimHero.values());
    }

    /** Appends, in order. GAmes call this from {@code ZenGame.init()}, atlases and fonts before what uses them. */
    public void register(AssetType<?>... assetTypes) {
        if (queued) throw new GdxRuntimeException("register after load: " + assetTypes[0].asset().label());
        types.addAll(assetTypes);
    }

    /** Queue every registered type. Called once by {@code ZenGame} after {@code init()}. */
    public void load() {
        for (var type : types) {
            type.load(this);
        }
        queued = true;
        loadStartMillis = TimeUtils.millis();
    }

    /** Call every frame until it returns true; blocks only on the frame the manager finishes. */
    public boolean update() {
        if (!queued) throw new GdxRuntimeException("assets updated before load");
        if (loaded) return true;
        if (!mgr.update()) return false;

        // Enforce a minimum loading duration if the debug flag is enabled
        if (ZenFlag.DEBUG_ASSET_LOAD.on()) {
            var elapsedSecs = TimeUtils.timeSinceMillis(loadStartMillis) / 1_000f;
            if (elapsedSecs < DEBUG_MIN_LOAD_SECS) return false;
        }

        for (var type : types) {
            init(type);
        }
        loaded = true;

        var elapsedSecs = TimeUtils.timeSinceMillis(loadStartMillis) / 1_000f;
        Log.info(TAG, "loaded %d assets in %.2f seconds", types.size, elapsedSecs);
        return true;
    }

    public float progress() {
        return loaded ? 1 : mgr.getProgress();
    }

    /** Synchronous alternative to polling {@link #update()}. */
    public void finishLoading() {
        if (!queued) throw new GdxRuntimeException("assets finishLoading before load");
        mgr.finishLoading();
        update();
    }

    private <T> void init(AssetType<T> type) {
        var value = type.init(this);
        type.asset().set(value);
    }

    @Override
    public void dispose() {
        // Clear the slots so a recreated game can't see disposed GPU handles through a stale KEY.get()
        for (var type : types) {
            type.asset().clear();
        }
        for (var disposable : disposables) {
            disposable.dispose();
        }
        disposables.clear();
        mgr.dispose();
    }
}
