package zendo.games.zenlib.assets;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

public enum ZenParticles {
    // spotless:off
      pixel
//    , TODO - add more built-in particle textures
    ;
    // spotless:on

    public TextureRegion region;

    ZenParticles() {
        this.region = null;
    }

    // TODO - this should be initialized with a TextureAtlas rather than a single region for testing
    public static void init(TextureRegion pixel) {
        for (var particle : values()) {
            particle.region = pixel;
            //            var regionName = particle.name().replaceAll("_", "-");
            //            particle.region = atlas.findRegion(regionName);
        }
    }
}
