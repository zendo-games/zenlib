package com.zendogames.zenlib.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public interface AtlasType extends AssetType<TextureAtlas> {

    /** Resolved lazily (not in the enum constructor) so {@code Gdx.files} is available. */
    FileHandle file();

    /**
     * Changes the sampling of every page.
     * <p>
     * Mipmaps are only generated if the atlas was packed with one: {@link TextureAtlas.TextureAtlasData#load},
     * if mipmaps aren't generated but a game tries to use them anyway, it will be rendered as black.
     * <p>
     * Usage: <code>FooAtlas.SPRITES.setFilter(TextureFilter.Nearest, TextureFilter.Nearest)</code>
     */
    default void setFilter(Texture.TextureFilter minFilter, Texture.TextureFilter maxFilter) {
        //noinspection GDXJavaUnsafeIterator
        for (var texture : get().getTextures()) {
            texture.setFilter(minFilter, maxFilter);
        }
    }

    @Override
    default void load(Assets assets) {
        assets.mgr.load(file().path(), TextureAtlas.class);
    }

    @Override
    default TextureAtlas init(Assets assets) {
        return assets.mgr.get(file().path(), TextureAtlas.class);
    }
}
