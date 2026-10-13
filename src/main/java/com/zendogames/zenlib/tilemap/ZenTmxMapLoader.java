package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.*;
import com.badlogic.gdx.utils.Array;

/**
 * The {@code TiledMap} loader {@code Assets} registers. Delegates to a {@link TmxMapLoader}
 * (tileset images loaded as textures) or, when {@link Parameters#fromAtlas} is set, to an
 * {@link AtlasTmxMapLoader} (every tileset is a region set in the packed atlas named by the map's
 * {@code atlas} property). One class because the {@code AssetManager} keys loaders by asset type
 * and file suffix, and both read {@code .tmx}. Both delegates carry the fix below.
 * <p>
 * <b>The fix.</b> In libGDX 1.14.2 a custom property of type {@code object} that is a member of
 * a Tiled <em>class</em> loads as {@code null} even when the map object sets it:
 * <ol>
 *   <li>{@code BaseTmxMapLoader.loadObject} calls {@code loadProperties} for the object's own
 *       {@code <properties>}; an {@code object} property goes to {@link Atlas#loadObjectProperty},
 *       which puts nothing in the map yet: it queues a {@code Runnable} on
 *       {@code runOnEndOfLoadTiled} to do {@code properties.put(name, idToObject.get(id))}
 *       once every object exists.</li>
 *   <li>{@code loadObject} then calls {@code loadMapPropertiesClassDefaults}, which skips members
 *       already present with {@code containsKey}. The deferred reference is not present, so the
 *       class default {@code "0"} is queued as a second {@code Runnable}.</li>
 *   <li>Both run in order at the end of {@code loadTiledMap}: the real reference is written, then
 *       overwritten with {@code idToObject.get(0)}, which is {@code null} (Tiled ids start at 1).</li>
 * </ol>
 * The override drops the unset reference, so only the object's own value is queued and an unset
 * reference is an absent key rather than a {@code null} value. {@code ZenTmxMapLoaderTest} loads a
 * map through both this loader and the stock one, so it also tells when upstream fixes the bug.
 */
public class ZenTmxMapLoader extends AsynchronousAssetLoader<TiledMap, ZenTmxMapLoader.Parameters> {

    /** The stock parameters plus the atlas switch. {@code MapType.parameters()} builds one per map. */
    public static class Parameters extends BaseTiledMapLoader.Parameters {
        /** Load tilesets from the packed atlas named by the map's {@code atlas} property instead of their images. */
        public boolean fromAtlas = false;
    }

    private final BaseTmxMapLoader<BaseTiledMapLoader.Parameters> tmx;
    private final BaseTmxMapLoader<BaseTiledMapLoader.Parameters> atlas;

    public ZenTmxMapLoader(FileHandleResolver resolver) {
        super(resolver);
        this.tmx   = new Tmx(resolver);
        this.atlas = new Atlas(resolver);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, Parameters parameter) {
        return loader(parameter).getDependencies(fileName, file, parameter);
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, Parameters parameter) {
        loader(parameter).loadAsync(manager, fileName, file, parameter);
    }

    @Override
    public TiledMap loadSync(AssetManager manager, String fileName, FileHandle file, Parameters parameter) {
        return loader(parameter).loadSync(manager, fileName, file, parameter);
    }

    /** The manager calls {@code getDependencies}, {@code loadAsync} and {@code loadSync} in a row on the same loader, which is what the delegates' {@code root} field relies on. */
    private BaseTmxMapLoader<BaseTiledMapLoader.Parameters> loader(Parameters parameter) {
        return (parameter != null && parameter.fromAtlas) ? atlas : tmx;
    }

    /** Tiled writes {@code 0} for an object reference that points at nothing. */
    static boolean isUnsetReference(String value) {
        return "0".equals(value);
    }

    private static final class Tmx extends TmxMapLoader {
        Tmx(FileHandleResolver resolver) { super(resolver); }

        @Override
        protected void loadObjectProperty(MapProperties properties, String name, String value) {
            if (isUnsetReference(value)) return;
            super.loadObjectProperty(properties, name, value);
        }
    }

    private static final class Atlas extends AtlasTmxMapLoader {
        Atlas(FileHandleResolver resolver) { super(resolver); }

        @Override
        protected void loadObjectProperty(MapProperties properties, String name, String value) {
            if (isUnsetReference(value)) return;
            super.loadObjectProperty(properties, name, value);
        }
    }
}
