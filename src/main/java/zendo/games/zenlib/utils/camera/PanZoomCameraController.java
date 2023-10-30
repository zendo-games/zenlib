package zendo.games.zenlib.utils.camera;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.*;
import zendo.games.zenlib.utils.Calc;

public class PanZoomCameraController extends GestureDetector.GestureAdapter implements InputProcessor {

    private static final float DT_SCALE = 2f;
    private static final float INITIAL_ZOOM = 1f;
    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 4f;

    private final Vector3 tmp = new Vector3();
    private final Vector3 initialPos = new Vector3();

    private OrthographicCamera camera;
    private Rectangle worldBounds;
    private boolean dragZoom = false;

    public final Vector3 targetPos = new Vector3();
    public float targetZoom = INITIAL_ZOOM;

    public Interpolation interpolation = Interpolation.linear;

    public PanZoomCameraController(OrthographicCamera camera) {
        reset(camera);
    }

    public void reset(OrthographicCamera camera) {
        this.camera = camera;
        this.camera.update();

        targetZoom = INITIAL_ZOOM;
        targetPos.set(camera.position.x, camera.position.y, 0);
    }

    public void setWorldBounds(Rectangle worldBounds) {
        this.worldBounds = worldBounds;
    }

    public void clearWorldBounds() {
        this.worldBounds = null;
    }

    public void update(float dt) {
        var x = interpolation.apply(camera.position.x, targetPos.x, DT_SCALE * dt);
        var y = interpolation.apply(camera.position.y, targetPos.y, DT_SCALE * dt);
        camera.position.set(x, y, 0);

        var z = Calc.eerp(camera.zoom, targetZoom, DT_SCALE * dt);
        camera.zoom = z;

        clampCameraToBounds();
    }

    public void moveLeft(float amount) {
        amount = Calc.abs(amount);
        tmp.set(Vector3.X).scl(amount);
        targetPos.add(tmp);
    }

    public void moveRight(float amount) {
        amount = Calc.abs(amount);
        tmp.set(Vector3.X).scl(-amount);
        targetPos.add(tmp);
    }

    public void moveUp(float amount) {
        amount = Calc.abs(amount);
        tmp.set(Vector3.Y).scl(-amount);
        targetPos.add(tmp);
    }

    public void moveDown(float amount) {
        amount = Calc.abs(amount);
        tmp.set(Vector3.Y).scl(amount);
        targetPos.add(tmp);
    }

    public void setCameraPosition(float x, float y) {
        // no lerp - position will get clamped in update
        camera.position.set(x, y, 0);
        targetPos.set(x, y, 0);
    }

    public void clampCameraToBounds() {
        if (worldBounds == null) return;

        float boundX = camera.viewportWidth * camera.zoom / 2f;
        float boundY = camera.viewportHeight * camera.zoom / 2f;

        float x = MathUtils.clamp(camera.position.x, boundX, worldBounds.width - boundX);
        float y = MathUtils.clamp(camera.position.y, boundY, worldBounds.height - boundY);

        camera.position.set(x, y, 0);
    }

    // --------------------------------------------------------------
    // GestureAdapter implementation
    // --------------------------------------------------------------

    @Override
    public boolean touchDown(float x, float y, int pointer, int button) {
        return false;
    }

    @Override
    public boolean tap(float x, float y, int count, int button) {
        return false;
    }

    @Override
    public boolean longPress(float x, float y) {
        return false;
    }

    @Override
    public boolean fling(float velocityX, float velocityY, int button) {
        return false;
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY) {
        return false;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, int button) {
        return false;
    }

    @Override
    public boolean zoom(float initialDistance, float distance) {
        targetZoom = MathUtils.clamp(distance / initialDistance, MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    @Override
    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2) {
        return false;
    }

    @Override
    public void pinchStop() {}

    // --------------------------------------------------------------
    // InputProcessor implementation
    // --------------------------------------------------------------

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        dragZoom = (button == Input.Buttons.LEFT && pointer == 0);
        if (dragZoom) {
            initialPos.set(screenX, screenY, 0);
            camera.unproject(initialPos);
        }
        return dragZoom;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (dragZoom) {
            dragZoom = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    Vector3 draggedPosition = new Vector3();

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!dragZoom) {
            return false;
        }

        //        var dx = Gdx.input.getDeltaX() * Time.delta * units_dragged_per_pixel;
        //        var dy = Gdx.input.getDeltaY() * Time.delta * units_dragged_per_pixel;
        //        if (dx < 0) moveLeft(dx);
        //        if (dx > 0) moveRight(dx);
        //        if (dy < 0) moveUp(dy);
        //        if (dy > 0) moveDown(dy);

        draggedPosition.set(screenX, screenY, 0);
        camera.unproject(draggedPosition);

        float dx = initialPos.x - draggedPosition.x;
        float dy = initialPos.y - draggedPosition.y;
        targetPos.x = initialPos.x + dx * camera.zoom;
        targetPos.y = initialPos.y + dy * camera.zoom;

        return true;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (amountY != 0) {
            var sign = Calc.sign(amountY);
            // NOTE - using Calc.eerp() in update() for exponential scaling
            //   so don't need to do this fiddly stuff
            //            var zoom = camera.zoom + sign * Time.delta * zoom_scale;

            var scale = 0.5f;
            var zoom = camera.zoom + sign * scale;
            targetZoom = MathUtils.clamp(zoom, MIN_ZOOM, MAX_ZOOM);

            //            if (Config.Debug.general) {
            //                Gdx.app.log("ZOOM", Stringf.format("current: %.2f  target: %.2f", camera.zoom,
            // targetZoom));
            //            }
            return true;
        }
        return false;
    }
}
