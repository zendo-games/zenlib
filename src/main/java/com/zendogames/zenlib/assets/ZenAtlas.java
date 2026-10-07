package com.zendogames.zenlib.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public enum ZenAtlas implements AtlasType {
    SPRITES("sprites/zen.atlas");

    private final Asset<TextureAtlas> asset;
    private final String path;

    ZenAtlas(String path) {
        this.path = Assets.LIBRARY_PREFIX + path;
        this.asset = new Asset<>(this);
    }

    @Override public Asset<TextureAtlas> asset() { return asset; }
    @Override public FileHandle file() { return Gdx.files.classpath(path); }
}
