package com.zendogames.zenlib.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public interface AtlasType extends AssetType<TextureAtlas> {

    /** Resolved lazily (not in the enum constructor) so {@code Gdx.files} is available. */
    FileHandle file();

    @Override
    default void load(Assets assets) {
        assets.mgr.load(new AssetDescriptor<>(file(), TextureAtlas.class));
    }

    @Override
    default TextureAtlas init(Assets assets) {
        return assets.mgr.get(file().path(), TextureAtlas.class);
    }
}
