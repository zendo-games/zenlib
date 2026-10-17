package com.zendogames.zenlib.screens.transition;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import com.zendogames.zenlib.Time;
import com.zendogames.zenlib.screens.ZenScreen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import static com.badlogic.gdx.Gdx.gl;

/** Blends from one screen to another through an effect shader. The game owns both screens and their lifecycle. */
public class ScreenTransition implements Disposable {

    /** Seconds a transition takes. */
    public float duration = 1f;

    /** Used when a transition starts without an effect of its own, null picks a random library effect each time. */
    public ScreenTransitionEffect defaultEffect = null;

    protected FrameBuffers fbos;
    protected ScreenTransitionEffect effect;
    protected ZenScreen curr;
    protected ZenScreen next;
    protected float percent;

    protected final int defaultFramebufferHandle;

    public ScreenTransition() {
        // NOTE: replicates GLFrameBuffer.build() binding of the main framebuffer (screen) so we can wrap it for doing full screen effects around ZenGame.draw()
        if (Gdx.app.getType() == Application.ApplicationType.iOS) {
            IntBuffer intbuf = ByteBuffer.allocateDirect(16 * Integer.SIZE / 8).order(ByteOrder.nativeOrder()).asIntBuffer();
            gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, intbuf);
            this.defaultFramebufferHandle = intbuf.get(0);
        } else {
            this.defaultFramebufferHandle = 0;
        }
    }

    @Override
    public void dispose() {
        ScreenTransitionEffectType.disposeAll();
        if (fbos != null) {
            fbos.dispose();
        }
    }

    public boolean inProgress() {
        return (next != null);
    }

    /** The screen being transitioned to, null unless a transition is in progress. */
    public ZenScreen next() {
        return next;
    }

    /** Begins blending between two screens. Starts nothing and returns false if the effect's shader is unusable. */
    public boolean start(ZenScreen from, ZenScreen to, ScreenTransitionEffect transitionEffect) {
        if (inProgress()) return false;

        var chosen = (transitionEffect != null) ? transitionEffect
                   : (defaultEffect    != null) ? defaultEffect
                   : ScreenTransitionEffectType.random();

        // Compiles the effect's shader program if this is its first use
        if (!chosen.shader().isCompiled()) return false;

        effect = chosen;
        curr = from;
        next = to;
        percent = 0;

        curr.transitioning = true;
        next.transitioning = true;
        return true;
    }

    /** Advances the blend. Returns the screen to make current on the frame that transition completes, otherwise null. */
    public ZenScreen update(float dt) {
        if (!inProgress()) return null;

        percent += dt / duration;
        if (percent < 1) return null;

        var finished = next;
        curr.transitioning = false;
        next.transitioning = false;
        curr = null;
        next = null;
        return finished;
    }

    public void render(Batch batch, OrthographicCamera camera) {
        render(batch, camera, defaultFramebufferHandle);
    }

    public void render(Batch batch, OrthographicCamera camera, int mainFramebufferHandle) {
        // Nothing to draw into while the window is minimized
        var width  = Gdx.graphics.getBackBufferWidth();
        var height = Gdx.graphics.getBackBufferHeight();
        if (width <= 0 || height <= 0) return;

        // Created on first use, and again whenever the window changes size
        if (fbos == null || !fbos.matches(width, height)) {
            if (fbos != null) fbos.dispose();
            fbos = new FrameBuffers(width, height);
        }

        curr.renderOffscreenBuffers(batch);
        next.renderOffscreenBuffers(batch);

        // Render next screen to a buffer
        fbos.transition.begin();
        {
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            next.render(Time.delta);
        }
        fbos.transition.end();

        // Render next screen to a buffer
        fbos.original.begin();
        {
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            curr.render(Time.delta);
        }
        fbos.original.end();

        // Resets to incoming framebuffer handle; FrameBuffer.end() calls unbind() which defaults to the screen, typically 0
        Gdx.gl20.glBindFramebuffer(GL20.GL_FRAMEBUFFER, mainFramebufferHandle);

        // Combine next and current screen buffers with transition effect shader, drawing into onscreen buffer
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        var originalTexture   = fbos.original.getColorBufferTexture();
        var transitionTexture = fbos.transition.getColorBufferTexture();
        var shader = effect.shader();
        batch.setShader(shader);
        {
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            {
                originalTexture.bind(1);
                transitionTexture.bind(0);

                shader.setUniformi("u_texture1", 1);
                shader.setUniformf("u_percent", Math.min(percent, 1f));

                batch.setColor(Color.WHITE);
                batch.draw(transitionTexture, 0, 0, camera.viewportWidth, camera.viewportHeight);
            }
            batch.end();
        }
        batch.setShader(null);
    }

    protected record FrameBuffers(FrameBuffer original, FrameBuffer transition) implements Disposable {
        FrameBuffers(int width, int height) {
            var format = Pixmap.Format.RGB888;
            var orig   = new FrameBuffer(format, width, height, false);
            var trans  = new FrameBuffer(format, width, height, false);
            this(orig, trans);
        }

        boolean matches(int width, int height) {
            return (original.getWidth() == width) && (original.getHeight() == height);
        }

        @Override
        public void dispose() {
            original.dispose();
            transition.dispose();
        }
    }
}
