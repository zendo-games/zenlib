package com.zendogames.zenlib.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Position;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/** Headless, but {@code Camera.update()} reaches {@code Matrix4.prj}, a native in gdx 1.14.2: load the desktop natives first. */
public class CameraFollowTest {

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    private static OrthographicCamera camera(int w, int h) {
        var camera = new OrthographicCamera();
        camera.viewportWidth = w;
        camera.viewportHeight = h;
        return camera;
    }

    @Test
    public void snapsToTargetOnFirstUpdateThenPans() {
        var follow = new CameraFollow(camera(100, 100));
        var point = new Vector2(500, 300);
        follow.follow(point);

        follow.update(1f / 60f);
        assertEquals(500f, follow.camera.position.x, 0.001f);
        assertEquals(300f, follow.camera.position.y, 0.001f);

        point.set(600, 300);
        follow.update(1f / 60f);
        var x = follow.camera.position.x;
        assertTrue("panned toward target: " + x, x > 500f && x < 600f);
    }

    @Test
    public void staysInsideBoundsAndCentresWhenTheViewIsLarger() {
        var follow = new CameraFollow(camera(100, 300));
        follow.bounds = new Rectangle(0, 0, 400, 200); // wider than the view, shorter than it
        var point = new Vector2(10, 10);
        follow.follow(point);

        follow.update(1f / 60f);
        assertEquals(50f, follow.camera.position.x, 0.001f);  // clamped to the left edge
        assertEquals(100f, follow.camera.position.y, 0.001f); // centred: view is taller than the bounds

        point.set(1000, 10);
        follow.snap();
        follow.update(1f / 60f);
        assertEquals(350f, follow.camera.position.x, 0.001f); // clamped to the right edge
    }

    @Test
    public void followsAnEntityAndHoldsAfterItDies() {
        var world = new World();
        var e = world.createEntity().add(new Position(40, 60));
        var follow = new CameraFollow(camera(10, 10));
        follow.follow(e);

        follow.update(1f / 60f);
        assertEquals(40f, follow.camera.position.x, 0.001f);
        assertEquals(60f, follow.camera.position.y, 0.001f);

        var target = (CameraFollow.Target.Follow) follow.target();
        world.despawn(e);
        assertFalse(e.isAlive());
        assertEquals(40f, target.x(), 0.001f); // last known position, no throw
    }

    @Test
    public void roundsViewEdgesNotCentre() {
        var follow = new CameraFollow(camera(101, 101)); // odd size: centre lands on .5
        follow.follow(new Vector2(50.2f, 50.2f));
        follow.update(1f / 60f);
        assertEquals(50.5f, follow.camera.position.x, 0.001f); // edge at 0, centre at 50.5
    }
}
