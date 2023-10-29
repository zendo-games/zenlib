package zendo.games.zenlib.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;
import zendo.games.zenlib.ZenMain;

public enum ZenTransition {
      blinds
    , circle_crop
    , crosshatch
    , cube
    , dissolve
    , doom_drip
    , dreamy
    , heart
    , pixelize
    , radial
    , ripple
    , simple_zoom
    , stereo
    ;

    public ShaderProgram shader;
    public static boolean initialized = false;

    ZenTransition() {
        this.shader = null;
    }

    public static void init() {
        var vertSrcPath = "shaders/transitions/default.vert";
        for (var value : values()) {
            var fragSrcPath = "shaders/transitions/" + value.name() + ".frag";
            value.shader = loadShader(vertSrcPath, fragSrcPath);
        }
        initialized = true;
    }

    public static ShaderProgram random() {
        if (!initialized) {
            throw new GdxRuntimeException("Failed to get random screen transition shader, ScreenTransitions is not initialized");
        }

        var random = (int) (Math.random() * values().length);
        return values()[random].shader;
    }

    public static ShaderProgram loadShader(String vertSourcePath, String fragSourcePath) {
        ShaderProgram.pedantic = false;
        var shaderProgram = new ShaderProgram(
                Gdx.files.classpath("zendo/games/zenlib/assets/" + vertSourcePath),
                Gdx.files.classpath("zendo/games/zenlib/assets/" + fragSourcePath));
        var log = shaderProgram.getLog();

        if (!shaderProgram.isCompiled()) {
            throw new GdxRuntimeException("LoadShader: compilation failed for "
                    + "'" + vertSourcePath + "' and '" + fragSourcePath + "':\n" + log);
        } else if (ZenMain.Debug.shaders) {
            Gdx.app.debug("LoadShader", "ShaderProgram compilation log: " + log);
        }

        return shaderProgram;
    }
}
