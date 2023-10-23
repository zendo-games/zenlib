package zendo.games.zenlib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import zendo.games.zenlib.screens.ZenScreen;
import zendo.games.zenlib.ui.ZenTextButton;

public class ZenlibTest extends ZenMain {

    public static final ZenConfig config = new ZenConfig();

    public ZenlibTest() {
        super(config);
    }

    @Override
    public ZenAssets createAssets() {
        return new Assets();
    }

    @Override
    public ZenScreen<Assets> createStartScreen() {
        return new TestScreen();
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

    public static class TestScreen extends ZenScreen<Assets> {
        public TestScreen() {
            super(Assets.class);
            Gdx.input.setInputProcessor(uiStage);
            ZenTextButton textButton = new ZenTextButton("Test Button");
            uiStage.addActor(textButton);
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

}

