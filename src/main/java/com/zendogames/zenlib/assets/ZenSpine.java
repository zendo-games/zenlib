package com.zendogames.zenlib.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

public enum ZenSpine implements SpineType {
    SPINEBOY("spine/spineboy/spineboy-pro.json");

    private final Asset<SpineData> asset;
    private final String path;

    ZenSpine(String path) {
        this.asset = new Asset<>(this);
        this.path = Assets.LIBRARY_PREFIX + path;
    }

    @Override public Asset<SpineData> asset() { return asset; }
    @Override public FileHandle file() { return Gdx.files.classpath(path); }
}
