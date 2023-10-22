package zendo.games.zenlib;

import aurelienribon.tweenengine.TweenManager;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

public abstract class LibMain extends ApplicationAdapter {

    public static LibMain game;

    public TweenManager tween;
    public FrameBuffer frameBuffer;
    public TextureRegion frameBufferRegion;
    public OrthographicCamera windowCamera;

    public LibMain() {
        LibMain.game = this;
    }

}
