package com.zendogames.zenlib.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.TimeUtils;
import com.zendogames.zenlib.Log;

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

    private static final String TAG = "Assets";

    public final AssetManager mgr;
    public final Array<Disposable> disposables;

    private final Array<AssetType<?>> types;
    private boolean queued;
    private boolean loaded;

    public Assets() {
        this.mgr = new AssetManager();
        this.mgr.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(mgr.getFileHandleResolver()));
        this.disposables = new Array<>();
        this.types = new Array<>();
        this.queued = false;
        this.loaded = false;

        // TODO: Library assets first: a category may use anything registered before it (eg. icons use an atlas)
//        register(ZenAtlas.values());
//        register(ZenIcon.values());
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

    // TODO: temp to simulate long loading
    private static final float MIN_LOAD_SECONDS = 3f;
    private long loadStartMillis;
    private float elapsed() {
        return MathUtils.clamp(TimeUtils.timeSinceMillis(loadStartMillis) / (MIN_LOAD_SECONDS * 1_000f), 0f, 1f);
    }
    // TODO: temp to simulate long loading

    /** Call every frame until it returns true; blocks only on the frame the manager finishes. */
    public boolean update() {
        if (!queued) throw new GdxRuntimeException("assets updated before load");
        if (loaded) return true;
        if (!mgr.update()) return false;

        // TODO: temp to simulate long loading
        if (elapsed() < 1f) return false;
        // TODO: temp to simulate long loading

        for (var type : types) {
            init(type);
        }
        loaded = true;
        Log.info(TAG, "loaded %d assets", types.size);
        return true;
    }

    public float progress() {
        if (loaded) return 1;
        return Math.min(mgr.getProgress(), elapsed()); // TODO: temp, remove the min and just use mgr progress if !loaded
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
