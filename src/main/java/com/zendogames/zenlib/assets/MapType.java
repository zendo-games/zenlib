package com.zendogames.zenlib.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.zendogames.zenlib.tilemap.ZenTmxMapLoader;

/**
 * A Tiled map ({@code .tmx)}) loaded by {@code Assets} through {@link ZenTmxMapLoader};
 * the game wraps the value in a {@code Tilemap} component when a screen needs it.
 * <pre>{@code
 * public enum GameMap implements MapType {
 *     LEVEL_1("maps/level-1.tmx");
 *     ...
 *     @Override public FileHandle file()    { return Gdx.files.internal(path); }
 *     @Override public String projectFile() { return "maps/game.tiled-project; }
 * }
 * }</pre>
 * Tileset images and the project file resolve relative to the map through the same resolver,
 * so a map under {@code Assets.LIBRARY_PREFIX} loads from the classpath like the library atlas.
 */
public interface MapType extends AssetType<TiledMap> {

    /** Resolved lazily (not in the enum constructor) so {@code Gdx.files} is available. */
    FileHandle file();

    /** The Tiled project file, needed for class property defaults and nested classes; null when the map uses none. */
    default String projectFile() { return null; }

    /** Tilesets as regions of the packed atlas named by the map's {@code atlas} property, instead of their images. */
    default boolean fromAtlas() { return false; }

    /** Nearest sampling and no mipmaps: tiles are drawn at one texel per pixel. */
    default ZenTmxMapLoader.Parameters parameters() {
        var params = new ZenTmxMapLoader.Parameters();
        params.projectFilePath  = projectFile();
        params.fromAtlas        = fromAtlas();
        params.textureMinFilter = Texture.TextureFilter.Nearest;
        params.textureMagFilter = Texture.TextureFilter.Nearest;
        params.generateMipMaps  = false;
        return params;
    }

    @Override
    default void load(Assets assets) {
        assets.mgr.load(file().path(), TiledMap.class, parameters());
    }

    @Override
    default TiledMap init(Assets assets) {
        return assets.mgr.get(file().path(), TiledMap.class);
    }

    /**
     * Reloads the file from disk, synchronously, and refreshes the slot: the edit-in-Tiled loop.
     * Entities built from the old map must be rebuilt by the caller ({@code world.clear()} and spawn again).
     */
    default void reload(Assets assets) {
        var path = file().path();
        assets.mgr.unload(path);
        assets.mgr.load(path, TiledMap.class, parameters());
        assets.mgr.finishLoadingAsset(path);
        asset().set(init(assets));
    }
}
