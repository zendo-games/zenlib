package zendo.games.zenlib.particles;

import static zendo.games.zenlib.particles.ZenParticleSystem.Layer.background;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.kotcrab.vis.ui.util.ColorUtils;
import zendo.games.zenlib.ZenAssets;
import zendo.games.zenlib.assets.ZenParticles;

public class ZenParticleSpawner<AssetsType extends ZenAssets> {

    public interface EffectType {}

    public enum ZenEffectType implements EffectType {
        // spotless:off
        point
        //        , TODO - add other built-in effect types
        //        ;
        // spotless:off
    }

    // TODO - this should be an interface too since some effects will need more / different params
    public static class SpawnParams {
        public float x;
        public float y;
    }

    protected final AssetsType assets;

    public ZenParticleSpawner(AssetsType assets) {
        this.assets = assets;
    }

    /**
     * Spawn a particle effect of the specified type.
     * This should always be called from subclass implementations
     * so that the built-in effect types are handled.
     *
     * @param type the type of particle effect to spawn
     * @param params any extra params required to create the particle effect
     */
    public void spawn(ZenParticleSystem particles, EffectType type, SpawnParams params) {
        var isUnsupportedType = !(type instanceof ZenEffectType);
        if (isUnsupportedType) {
            return;
        }

        var zenType = (ZenEffectType) type;
        switch (zenType) {
            case point:
                spawnPoint(particles, params);
                break;
        }
    }

    private void spawnPoint(ZenParticleSystem particles, SpawnParams info) {
        var hue = 360f * MathUtils.random();
        var sat = 100f * (0.3f + MathUtils.random() * 0.3f);
        var val = 100f * (0.8f * MathUtils.random() + 0.2f);
        var startColor = ColorUtils.HSVtoRGB(hue, sat, val);
        // darker version of start color
        var endColor = startColor.cpy().lerp(Color.BLACK, 0.5f);
        var particle = ZenParticle.initializer(particles.obtainParticle())
                .keyframe(ZenParticles.pixel.region)
                .startPos(info.x, info.y)
                .velocity(MathUtils.random(-10f, 10f), MathUtils.random(-50f, -80f))
                .startColor(startColor)
                .endColor(endColor)
                .startSize(MathUtils.random(4f, 6f))
                .endSize(0.5f)
                .timeToLive(MathUtils.random(2f, 4f))
                .init();
        particles.getLayer(background).add(particle);
    }
}
