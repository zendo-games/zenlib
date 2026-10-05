package com.zendogames.zenlib.screens.transition;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;
import com.zendogames.zenlib.Time;
import com.zendogames.zenlib.Zen;
import com.zendogames.zenlib.ZenGame;
import com.zendogames.zenlib.screens.ZenScreen;

public class ScreenTransition implements Disposable {

    private final ZenGame game;
    private final FrameBuffers fbos;

    private ScreenTransitionEffect effect;
    private ZenScreen curr;
    private ZenScreen next;

    private float percent;
    private boolean instant;

    public ScreenTransition(ZenGame game) {
        this.game = game;
        this.fbos = new FrameBuffers();
        this.effect = ScreenTransitionEffectType.random();
        this.curr = null;
        this.next = null;
        this.percent = 1; // Must be 1 on construction to indicate that there's not a transition in progress
    }

    @Override
    public void dispose() {
        ScreenTransitionEffectType.disposeAll();
        fbos.dispose();
    }

    public boolean inProgress() {
        return percent < 1;
    }

    public void to(ZenScreen newScreen, boolean immediate) { to(newScreen, effect, immediate); }
    public void to(ZenScreen newScreen, ScreenTransitionEffect transitionEffect, boolean immediate) {
        if (inProgress()) return;

        percent = 0;
        instant = immediate;

        next = newScreen;
        curr = game.currentScreen;

        // Update transition effect if needed and ensure it's shader program is compiled
        effect = (transitionEffect != null) ? transitionEffect : ScreenTransitionEffectType.random();
        effect.shader();

        next.transitioning = true;
        curr.transitioning = true;
    }

    public void update(float dt) {
        if (!inProgress()) return;

        if (instant) percent = 1;
        else         percent += dt;

        if (percent >= 1) {
            percent = 1;
            var prevCurrent = curr;

            next.transitioning = false;
            curr.transitioning = false;

            curr = next;
            next = null;

            game.currentScreen = curr;
            game.currentScreen.show();
            game.currentScreen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

            prevCurrent.hide();
            prevCurrent.dispose();
        }
    }

    public void render(SpriteBatch batch) {
        // Update transition between current and next screens
        next.update(Time.delta);
        next.renderOffscreenBuffers(batch);

        // Render next screen to a buffer
        fbos.transition.begin();
        {
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            next.render(Time.delta);
        }
        fbos.transition.end();

        // Render current screen to a buffer
        fbos.original.begin();
        {
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            game.currentScreen.render(Time.delta);
        }
        fbos.original.end();

        // Combine next and current screen buffers with transition effect shader, drawing into on-screen buffer
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        var shader = effect.shader();
        batch.setShader(shader);
        {
            var camera = game.windowCamera;
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            {
                fbos.originalTexture.bind(1);
                fbos.transitionTexture.bind(0);

                shader.setUniformi("u_texture1", 1);
                shader.setUniformf("u_percent", percent);

                batch.setColor(Color.WHITE);
                batch.draw(fbos.transitionTexture, 0, 0, camera.viewportWidth, camera.viewportHeight);
            }
            batch.end();
        }
        batch.setShader(null);
    }

    protected record FrameBuffers(
            FrameBuffer original,
            FrameBuffer transition,
            Texture originalTexture,
            Texture transitionTexture
    ) implements Disposable {
        FrameBuffers() {
            var format = Pixmap.Format.RGB888;
            var width  = Zen.config.framebufferWidth;
            var height = Zen.config.framebufferHeight;
            var orig   = new FrameBuffer(format, width, height, false);
            var trans  = new FrameBuffer(format, width, height, false);
            this(orig, trans, orig.getColorBufferTexture(), trans.getColorBufferTexture());
        }

        @Override
        public void dispose() {
            original.dispose();
            transition.dispose();
        }
    }
}
