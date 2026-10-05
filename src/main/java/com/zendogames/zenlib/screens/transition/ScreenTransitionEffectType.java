package com.zendogames.zenlib.screens.transition;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.zendogames.zenlib.Log;

public enum ScreenTransitionEffectType implements ScreenTransitionEffect {
    BLINDS,
    CIRCLE_CROP,
    CROSSHATCH,
    CUBE,
    DISSOLVE,
    DOOM_DRIP,
    DOORWAY,
    DREAMY,
    HEART,
    PIXELIZE,
    RADIAL,
    RIPPLE,
    SIMPLE_ZOOM,
    STEREO;

    private static final String folder = "com/zendogames/zenlib/assets/shaders/transitions/";

    private ShaderProgram shader;

    /** Compiled on first use, so startup doesn't pay for effects a game never shows. */
    @Override
    public ShaderProgram shader() {
        if (shader == null) {
            var fragName = name().toLowerCase();
            var vert = Gdx.files.classpath(folder + "default.vert");
            var frag = Gdx.files.classpath(folder + fragName + ".frag");
            shader = new ShaderProgram(vert, frag);
            if (!shader.isCompiled()) {
                Log.error("Shader", "compilation failed (%s, %s): %s", vert.name(), frag.name(), shader.getLog());
            }
        }
        return shader;
    }

    public static ScreenTransitionEffect random() {
        var values = ScreenTransitionEffectType.values();
        var index  = MathUtils.random(values.length - 1);
        return values[index];
    }

    public static void disposeAll() {
        for (var effect : values()) {
            if (effect.shader != null && effect.shader.isCompiled()) {
                effect.shader.dispose();
            }
        }
    }
}
