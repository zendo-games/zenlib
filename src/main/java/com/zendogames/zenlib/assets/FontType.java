package com.zendogames.zenlib.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.github.tommyettinger.textra.Font;

public interface FontType extends AssetType<Font>{

    FileHandle file();
    FreeTypeFontParameter params();

    /** Name this font gets inside a {@code SkinType} that lists it, eg. {@code "cousine"}. */
    default String skinName() { return name().toLowerCase(); }

    @Override
    default void load(Assets assets) {
        // Several constants may share one file: each load() bumps the manager's refcount, each init() releases one
        assets.mgr.load(new AssetDescriptor<>(file(), FreeTypeFontGenerator.class));
    }

    @Override
    default Font init(Assets assets) {
        var path = file().path();
        var generator = assets.mgr.get(path, FreeTypeFontGenerator.class);
        var bitmapFont = generator.generateFont(params());
        assets.disposables.add(bitmapFont);

        var font = new Font(bitmapFont);
        assets.disposables.add(font);
        assets.mgr.unload(path);
        return font;
    }
}
