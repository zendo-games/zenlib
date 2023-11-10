package zendo.games.zenlib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import space.earlygrey.shapedrawer.ShapeDrawer;
import zendo.games.zenlib.physics.*;
import zendo.games.zenlib.screens.ZenScreen;
import zendo.games.zenlib.ui.ZenTextButton;
import zendo.games.zenlib.ui.ZenWindow;

public class ZenlibMainTest extends ZenMain<ZenlibMainTest.Assets> {

    public static final ZenConfig config = new ZenConfig();

    public static ZenlibMainTest game;

    public ZenlibMainTest() {
        super(config);
        ZenlibMainTest.game = this;
    }

    @Override
    public Assets createAssets() {
        return new Assets();
    }

    @Override
    public ZenScreen createStartScreen() {
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

    public static class TestScreen1 extends ZenScreen {
        public TestScreen1() {
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

            var window = new ZenWindow(300f, 400f);
            var button = new ZenTextButton(100f, 100f, "Switch to screen 2");
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.setScreen(new TestScreen2());
                }
            });
            window.add(button);
            uiStage.addActor(window);
        }

        @Override
        public void render(SpriteBatch batch) {
            ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);

            batch.setProjectionMatrix(worldCamera.combined);
            batch.begin();
            {
                var image = game.assets.gdx;
                var scale = 1 / 4f;
                var imageWidth = scale * image.getWidth();
                var imageHeight = scale * image.getHeight();
                batch.draw(
                        image,
                        (worldCamera.viewportWidth - imageWidth) / 2f,
                        (worldCamera.viewportHeight - imageHeight) / 2f,
                        imageWidth,
                        imageHeight);
            }
            batch.end();
            uiStage.draw();
        }
    }

    // ------------------------------------------------------------------------
    // Test Screen 2 (for transition testing)
    // ------------------------------------------------------------------------

    public static class TestScreen2 extends ZenScreen {
        public TestScreen2() {
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

            var button = new ZenTextButton("Switch to physics test screen");
            button.setPosition(windowCamera.viewportWidth - button.getWidth() - 100, 100);
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.setScreen(new PhysicsTestScreen());
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
                var image = game.assets.gdx;
                var scale = 2 / 4f;
                var imageWidth = scale * image.getWidth();
                var imageHeight = scale * image.getHeight();
                batch.draw(
                        image,
                        (worldCamera.viewportWidth - imageWidth) / 2f,
                        (worldCamera.viewportHeight - imageHeight) / 2f,
                        imageWidth,
                        imageHeight);
            }
            batch.end();
            uiStage.draw();
        }
    }

    // ------------------------------------------------------------------------
    // Physics Test Screen
    // ------------------------------------------------------------------------

    public static class PhysicsTestScreen extends ZenScreen {

        PhysicsSystem physics;
        Array<Influencer> influencers;
        Array<Collidable> collidables;
        Array<Collidable> bounds;
        Array<PhysicsBall> balls;

        public PhysicsTestScreen() {
            // override the default 'ScreenViewport'
            int screenWidth = config.window.width / 2;
            int screenHeight = config.window.height / 2;
            this.viewport = new StretchViewport(screenWidth, screenHeight, worldCamera);
            this.viewport.apply(true);

            this.physics = new PhysicsSystem(new Rectangle(
                    viewport.getScreenX(),
                    viewport.getScreenY(),
                    viewport.getScreenWidth(),
                    viewport.getScreenHeight()));
            this.influencers = new Array<>();
            this.collidables = new Array<>();

            this.bounds = new Array<>();
            // spotless:off
            this.bounds.add(new Boundary(0,           0,            0,           screenHeight));
            this.bounds.add(new Boundary(0,           screenHeight, screenWidth, screenHeight));
            this.bounds.add(new Boundary(screenWidth, screenHeight, screenWidth, 0));
            this.bounds.add(new Boundary(screenWidth, 0,            0,           0));
            this.bounds.add(new Boundary(
                    screenWidth / 2f - 20f, screenHeight * 2f / 5f,
                    screenWidth / 2f - 20f, screenHeight * 3 / 5f));
            // spotless:on
            this.collidables.addAll(bounds);

            this.balls = new Array<>();
            for (int i = 0; i < 40; i++) {
                balls.add(new PhysicsBall(
                        new Vector2(MathUtils.random(0f, screenWidth), MathUtils.random(0f, screenHeight)),
                        new Vector2(MathUtils.random(-100, 100f), MathUtils.random(-100, 100f))));
            }
            this.collidables.addAll(balls);

            Gdx.input.setInputProcessor(uiStage);
        }

        @Override
        protected void initializeUI() {
            super.initializeUI();

            // var window = new ZenWindow(300f, 400f);
            // var button = new ZenTextButton(100f, 100f, "Switch to screen 2");
            // button.addListener(new ClickListener() {
            //     @Override
            //     public void clicked(InputEvent event, float x, float y) {
            //         game.setScreen(new TestScreen2());
            //     }
            // });
            // window.add(button);
            // uiStage.addActor(window);
        }

        @Override
        public void update(float dt) {
            super.update(dt);

            if (Gdx.input.justTouched()) {
                game.setScreen(new TestScreen1());
            }

            for (var ball : balls) {
                ball.getVelocity().y -= 60f * dt;
            }

            physics.update(dt, collidables, influencers);
        }

        @Override
        public void render(SpriteBatch batch) {
            ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);

            batch.setProjectionMatrix(worldCamera.combined);
            batch.begin();
            {
                var image = game.assets.gdx;
                var scale = 1 / 4f;
                var imageWidth = scale * image.getWidth();
                var imageHeight = scale * image.getHeight();
                batch.draw(
                        image,
                        (worldCamera.viewportWidth - imageWidth) / 2f,
                        (worldCamera.viewportHeight - imageHeight) / 2f,
                        imageWidth,
                        imageHeight);

                for (var collidable : collidables) {
                    collidable.renderDebug(game.assets.shapes);
                }
            }
            batch.end();
            uiStage.draw();
        }

        public static class PhysicsBall implements Collidable {

            final Vector2 pos;
            final Vector2 vel;
            float size;
            final Rectangle collisionBounds;
            final CollisionShapeCircle collisionShape;

            public PhysicsBall(Vector2 position, Vector2 velocity) {
                this.pos = position.cpy();
                this.vel = velocity.cpy();
                this.size = MathUtils.random(5f, 50f);
                this.collisionBounds = new Rectangle(pos.x - size / 2f, pos.y - size / 2f, size, size);
                this.collisionShape = new CollisionShapeCircle(size / 2f, pos.x, pos.y);
            }

            @Override
            public void renderDebug(ShapeDrawer shapes) {
                shapes.filledCircle(collisionShape.center, collisionShape.radius, Color.MAGENTA);
            }

            @Override
            public float getFriction() {
                return 1f;
            }

            @Override
            public float getMass() {
                return 10;
            }

            @Override
            public Vector2 getVelocity() {
                return vel;
            }

            @Override
            public void setVelocity(Vector2 newVel) {
                setVelocity(newVel.x, newVel.y);
            }

            @Override
            public void setVelocity(float x, float y) {
                vel.set(x, y);
            }

            @Override
            public Vector2 getPosition() {
                return pos;
            }

            @Override
            public void setPosition(float x, float y) {
                collisionShape.center.set(x, y);
                collisionBounds.setPosition(x - size / 2f, y - size / 2f);
                pos.set(x, y);
            }

            @Override
            public void setPosition(Vector2 newPos) {
                setPosition(newPos.x, newPos.y);
            }

            @Override
            public Rectangle getCollisionBounds() {
                return collisionBounds;
            }

            @Override
            public CollisionShape getCollisionShape() {
                return collisionShape;
            }

            @Override
            public void collidedWith(Collidable object) {}

            @Override
            public boolean shouldCollideWith(Collidable object) {
                return true;
            }

            @Override
            public float getAngularMomentum() {
                return 0;
            }

            @Override
            public void addAngularMomentum(float dA) {}
        }

        public static class Boundary implements Collidable {

            final Rectangle collisionBounds;
            final CollisionShapeSegment collisionShape;

            public Boundary(float x1, float y1, float x2, float y2) {
                collisionShape = new CollisionShapeSegment(x1, y1, x2, y2);

                var size = 10f;
                collisionBounds = new Rectangle(
                        Math.min(x1, x2) - size / 2f,
                        Math.min(y1, y2) - size / 2f,
                        Math.abs(x2 - x1) + size,
                        Math.abs(y2 - y1) + size);
            }

            @Override
            public void renderDebug(ShapeDrawer shapes) {
                shapes.line(
                        collisionShape.start.x, collisionShape.start.y,
                        collisionShape.end.x, collisionShape.end.y,
                        Color.YELLOW, 2f);
                // shapes.filledCircle(collisionShape.start, 2f, Color.GOLDENROD);
                // shapes.filledCircle(collisionShape.end, 2f, Color.GOLDENROD);
            }

            @Override
            public float getFriction() {
                return 0;
            }

            @Override
            public float getMass() {
                return Collidable.IMMOVABLE;
            }

            @Override
            public Vector2 getVelocity() {
                return Vector2.Zero;
            }

            @Override
            public void setVelocity(Vector2 newVel) {}

            @Override
            public void setVelocity(float x, float y) {}

            @Override
            public Vector2 getPosition() {
                return null;
            }

            @Override
            public void setPosition(float x, float y) {}

            @Override
            public void setPosition(Vector2 newPos) {}

            @Override
            public Rectangle getCollisionBounds() {
                return collisionBounds;
            }

            @Override
            public CollisionShape getCollisionShape() {
                return collisionShape;
            }

            @Override
            public void collidedWith(Collidable object) {}

            @Override
            public boolean shouldCollideWith(Collidable object) {
                return true;
            }

            @Override
            public float getAngularMomentum() {
                return 0;
            }

            @Override
            public void addAngularMomentum(float dA) {}
        }
    }
}
