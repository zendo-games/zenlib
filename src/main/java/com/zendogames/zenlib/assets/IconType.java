package com.zendogames.zenlib.assets;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxRuntimeException;

public interface IconType extends AssetType<TextureRegion> {

    /** The atlas holding this icon, registered before this enum so it has initialized. */
    AtlasType atlas();

    String regionName();

    @Override
    default TextureRegion init(Assets asset) {
        var region = atlas().get().findRegion(regionName());
        if (region == null) {
            throw new GdxRuntimeException("%s: region '%s' not in atlas %s"
                    .formatted(asset().label(), regionName(), atlas().asset().label()));
        }
        return region;
    }
}
