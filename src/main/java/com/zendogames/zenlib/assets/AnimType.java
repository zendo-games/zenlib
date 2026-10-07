package com.zendogames.zenlib.assets;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Animation.PlayMode;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxRuntimeException;

public interface AnimType extends AssetType<Animation<TextureRegion>> {

    float DEFAULT_FRAME_DURATION = 0.1f;
    PlayMode DEFAULT_PLAY_MODE = PlayMode.LOOP;

    AtlasType atlas();
    String regionName();
    default float frameDuration() { return DEFAULT_FRAME_DURATION; }
    default PlayMode playMode()   { return DEFAULT_PLAY_MODE; }

    @Override
    default Animation<TextureRegion> init(Assets assets) {
        var regions = atlas().get().findRegions(regionName());
        if (regions.isEmpty()) {
            throw new GdxRuntimeException("%s: no regions '%s' in atlas %s"
                    .formatted(asset().label(), regionName(), atlas().asset().label()));
        }
        return new Animation<>(frameDuration(), regions, playMode());
    }
}
