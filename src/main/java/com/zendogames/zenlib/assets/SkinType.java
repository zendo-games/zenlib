package com.zendogames.zenlib.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.github.tommyettinger.textra.Styles;

public interface SkinType extends AssetType<Skin> {

    FileHandle file();

    /** Fonts added to the skin by {@link FontType#skinName()}, with a textra label style each. Explicit, unlike the references. */
    default FontType[] fonts() { return new FontType[0]; }

    @Override
    default void load(Assets assets) {
        assets.mgr.load(new AssetDescriptor<>(file(), Skin.class));
    }

    @Override
    default Skin init(Assets assets) {
        var skin = assets.mgr.get(file().path(), Skin.class);
        for (var fontType : fonts()) {
            var font = fontType.get();
            skin.add(fontType.skinName(), font);
            skin.add("label-" + fontType.skinName(), new Styles.LabelStyle(font, Color.WHITE));
        }
        return skin;
    }
}
