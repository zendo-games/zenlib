package zendo.games.zenlib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.kotcrab.vis.ui.widget.VisTextButton;
import zendo.games.zenlib.assets.ZenPatch;
import zendo.games.zenlib.screens.ZenScreen;

public class ZenlibTest extends ZenMain {

    public static final ZenConfig config = new ZenConfig();
    public static ZenlibTest game;

    public ZenlibTest() {
        super(config);
        ZenlibTest.game = this;
    }

    @Override
    public ZenAssets createAssets() {
        return new Assets();
    }

    @Override
    public ZenScreen<Assets> createStartScreen() {
        return new TestScreen1();
    }

    // ------------------------------------------------------------------------
    // Test Assets (from src/test/resources)
    // ------------------------------------------------------------------------

    public static class Assets extends ZenAssets {
        Texture gdx;

        @Override
        public void loadManagerAssets() {
            mgr.load("libgdx.png", Texture.class);
        }

        @Override
        public void initCachedAssets() {
            gdx = mgr.get("libgdx.png", Texture.class);
        }
    }

    // ------------------------------------------------------------------------
    // Test Screen
    // ------------------------------------------------------------------------

    public static class TestScreen1 extends ZenScreen<Assets> {
        public TestScreen1() {
            super(Assets.class);

            // override the default 'ScreenViewport'
            int screenWidth = config.window.width / 2;
            int screenHeight = config.window.height / 2;
            this.viewport = new StretchViewport(screenWidth, screenHeight, worldCamera);
            this.viewport.apply(true);

            Gdx.input.setInputProcessor(uiStage);
        }

        @Override
        protected void initializeUI() {
            super.initializeUI();

            var button = new VisTextButton("Switch to screen 2");
            button.setPosition(100, 100);
            button.getStyle().up = ZenPatch.glass_active.ninePatchDrawable;
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    ZenlibTest.game.setScreen(new TestScreen2());
                }
            });
            uiStage.addActor(button);
        }

        @Override
        public void render(SpriteBatch batch) {
            ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);

            batch.setProjectionMatrix(worldCamera.combined);
            batch.begin();
            {
                var image = assets.gdx;
                var scale = 1 / 4f;
                var imageWidth = scale * image.getWidth();
                var imageHeight = scale * image.getHeight();
                batch.draw(image,
                        (worldCamera.viewportWidth - imageWidth) / 2f,
                        (worldCamera.viewportHeight - imageHeight) / 2f,
                        imageWidth, imageHeight);
            }
            batch.end();
            uiStage.draw();
        }
    }

    // ------------------------------------------------------------------------
    // Test Screen 2 (for transition testing)
    // ------------------------------------------------------------------------

    public static class TestScreen2 extends ZenScreen<Assets> {
        public TestScreen2() {
            super(Assets.class);

            // override the default 'ScreenViewport'
            int screenWidth = config.window.width / 4;
            int screenHeight = config.window.height / 4;
            this.viewport = new StretchViewport(screenWidth, screenHeight, worldCamera);
            this.viewport.apply(true);

            Gdx.input.setInputProcessor(uiStage);
        }

        @Override
        protected void initializeUI() {
            super.initializeUI();

            var button = new VisTextButton("Switch to screen 1");
            button.setPosition(windowCamera.viewportWidth - button.getWidth() - 100, 100);
            button.getStyle().up = ZenPatch.glass_active.ninePatchDrawable;
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    ZenlibTest.game.setScreen(new TestScreen1());
                }
            });
            uiStage.addActor(button);
        }

        @Override
        public void render(SpriteBatch batch) {
            ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);

            batch.setProjectionMatrix(worldCamera.combined);
            batch.begin();
            {
                var image = assets.gdx;
                var scale = 2 / 4f;
                var imageWidth = scale * image.getWidth();
                var imageHeight = scale * image.getHeight();
                batch.draw(image,
                        (worldCamera.viewportWidth - imageWidth) / 2f,
                        (worldCamera.viewportHeight - imageHeight) / 2f,
                        imageWidth, imageHeight);
            }
            batch.end();
            uiStage.draw();
        }
    }

}

