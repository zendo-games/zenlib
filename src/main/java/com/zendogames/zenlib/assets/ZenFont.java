package com.zendogames.zenlib.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.github.tommyettinger.textra.Font;

import static com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.*;

public enum ZenFont implements FontType {
      JETBRAINS_MONO_16("fonts/JetBrainsMono-Regular.ttf", 16)
    , JETBRAINS_MONO_32("fonts/JetBrainsMono-Regular.ttf", 32)
    , JETBRAINS_MONO_64("fonts/JetBrainsMono-Regular.ttf", 64)
    ;

    private final Asset<Font> asset;
    private final String path;
    private final FreeTypeFontParameter params;

    ZenFont(String path, int size) {
        this.path = Assets.LIBRARY_PREFIX + path;
        this.asset = new Asset<>(this);
        this.params = new FreeTypeFontParameter();
        this.params.borderColor = Color.BLACK;
        this.params.borderWidth = 2;
        this.params.color = Color.WHITE;
        this.params.size = size;
    }

    @Override public Asset<Font> asset() { return asset; }
    @Override public FileHandle file()   { return Gdx.files.classpath(path) ; }
    @Override public FreeTypeFontParameter params() { return params; }
}
