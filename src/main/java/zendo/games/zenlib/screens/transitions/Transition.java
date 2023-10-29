package zendo.games.zenlib.screens.transitions;

import aurelienribon.tweenengine.Timeline;
import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.primitives.MutableFloat;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import zendo.games.zenlib.ZenConfig;
import zendo.games.zenlib.ZenMain;
import zendo.games.zenlib.assets.ZenTransition;
import zendo.games.zenlib.screens.ZenScreen;
import zendo.games.zenlib.utils.Time;

public class Transition implements Disposable {

    public static final float DEFAULT_SPEED = 0.66f;

    public boolean active;
    public MutableFloat percent;
    public ShaderProgram shader;

    public final Screens screens = new Screens();
    public final FrameBuffers fbo = new FrameBuffers();
    public final Textures tex = new Textures();

    private final ZenConfig config;

    public Transition(ZenConfig config) {
        this.config = config;
        this.active = false;
        this.percent = new MutableFloat(0);
        this.fbo.from = new FrameBuffer(Pixmap.Format.RGBA8888, config.window.width, config.window.height, false);
        this.fbo.to = new FrameBuffer(Pixmap.Format.RGBA8888, config.window.width, config.window.height, false);
        this.tex.from = this.fbo.from.getColorBufferTexture();
        this.tex.to = this.fbo.to.getColorBufferTexture();
    }

    @Override
    public void dispose() {
        screens.dispose();
        fbo.dispose();
        // no need to dispose textures here,
        // they are owned by the frame buffers
    }

    public void alwaysUpdate(float dt) {
        screens.current.alwaysUpdate(dt);
        if (screens.next != null) {
            screens.next.alwaysUpdate(dt);
        }
    }

    public void update(float dt) {
        screens.current.update(dt);
        if (screens.next != null) {
            screens.next.update(dt);
        }
    }

    public void render(SpriteBatch batch, OrthographicCamera windowCamera) {
        screens.next.update(Time.delta);
        screens.next.renderFrameBuffers(batch);

        // draw the next screen to the 'to' buffer
        fbo.to.begin();
        {
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            screens.next.render(batch);
        }
        fbo.to.end();

        // draw the current screen to the 'from' buffer
        fbo.from.begin();
        {
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            screens.current.render(batch);
        }
        fbo.from.end();

        // draw the transition buffer to the screen
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setShader(shader);
        batch.setProjectionMatrix(windowCamera.combined);
        batch.begin();
        {
            tex.from.bind(1);
            shader.setUniformi("u_texture1", 1);

            tex.to.bind(0);
            shader.setUniformi("u_texture", 0);

            shader.setUniformf("u_percent", percent.floatValue());

            batch.setColor(Color.WHITE);
            batch.draw(tex.to, 0,0, config.window.width, config.window.height);
        }
        batch.end();
        batch.setShader(null);
    }

    public void startTransition(final ZenScreen newScreen, ZenTransition type, float transitionSpeed) {
        // current screen is active, so trigger transition to new screen
        active = true;
        percent.setValue(0);
        shader = (type != null) ? type.shader : ZenTransition.random();

        Timeline.createSequence()
                .pushPause(.1f)
                .push(Tween.call((i, baseTween) -> screens.next = newScreen))
                .push(Tween.to(percent, 0, transitionSpeed).target(1))
                .push(Tween.call((i, baseTween) -> {
                    screens.current = screens.next;
                    screens.next = null;
                    active = false;
                }))
                .start(ZenMain.instance.tween);
    }

    // ------------------------------------------------------------------------
    // Data Classes
    // ------------------------------------------------------------------------

    public static class Screens implements Disposable{
        public ZenScreen next;
        public ZenScreen current;

        @Override
        public void dispose() {
            if (next != null) next.dispose();
            if (current != null) current.dispose();
        }
    }

    public static class FrameBuffers implements Disposable {
        public FrameBuffer from;
        public FrameBuffer to;

        @Override
        public void dispose() {
            if (from != null) from.dispose();
            if (to != null) to.dispose();
        }
    }

    public static class Textures {
        public Texture from;
        public Texture to;
    }

}
