package com.zendogames.zenlib.input;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ZenFlag;
import com.zendogames.zenlib.imgui.ImGuiGdx;
import com.zendogames.zenlib.screens.ZenScreen;

import static com.badlogic.gdx.Input.Keys.*;

/**
 * A screen's slot in the input chain, after imgui and the screen's scene2d stage.
 * The {@code InputProcessor} methods are final: they log, track the pointer, drop
 * events that imgui wants or that arrive mid-transition, then call the {@code onXxx}
 * hooks. Games override the hooks and return true to consume an event. Held keys
 * may still be polled, gated by {@link ImGuiGdx#wantsKeyboard()}.
 */
public class ScreenInputProcessor extends InputAdapter {

    private static final String TAG = "Input";

    protected final ZenScreen screen;

    /** Last pointer position in window pixels, top-left origin, as libGDX reports it. */
    public final Vector2 pointerScreen = new Vector2();

    /** Last pointer position in world units, through the screen's world view (outside {@code [0, worldWidth]} in a gutter). */
    public final Vector2 pointer = new Vector2();

    /** Drop every event while the screen is either side of a transition. */
    public boolean ignoreWhileTransitioning = true;

    public ScreenInputProcessor(ZenScreen screen) {
        this.screen = screen;
    }

    // ------------------------------------------------------------------------
    // Hooks, screens can implement and return true to consume the event
    // ------------------------------------------------------------------------

    protected boolean onKeyDown(int keycode)                                              { return false; }
    protected boolean onKeyUp(int keycode)                                                { return false; }
    protected boolean onKeyTyped(char character)                                          { return false; }
    protected boolean onTouchDown(int screenX, int screenY, int pointer, int button)      { return false; }
    protected boolean onTouchUp(int screenX, int screenY, int pointer, int button)        { return false; }
    protected boolean onTouchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    protected boolean onTouchDragged(int screenX, int screenY, int pointer)               { return false; }
    protected boolean onMouseMoved(int screenX, int screenY)                              { return false; }
    protected boolean onScrolled(float amountX, float amountY)                            { return false; }

    /**
     * Library debug bindings on the number row, checked on key up before {@link #onKeyUp}.
     * Override to remap, or return false for everything to disable.
     */
    protected boolean debugKey(int keycode) {
        switch (keycode) {
            case NUM_1 -> ZenFlag.GLOBAL.toggle();
            case NUM_2 -> ZenFlag.DEBUG_RENDER.toggle();
            case NUM_5 -> ZenFlag.SHOW_IMGUI.toggle();
            case NUM_6 -> ZenFlag.RENDER_FBO.toggle();
            case NUM_0 -> ZenFlag.FRAME_STEP.toggle();
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------------
    // Gates
    // ------------------------------------------------------------------------

    protected boolean acceptsKeyboard() {
        if (ignoreWhileTransitioning && screen.transitioning) return false;
        return !ImGuiGdx.wantsKeyboard();
    }

    protected boolean acceptsMouse() {
        if (ignoreWhileTransitioning && screen.transitioning) return false;
        return !ImGuiGdx.wantsMouse();
    }

    // ------------------------------------------------------------------------
    // InputProcessor: bookkeeping, gate, hook
    // ------------------------------------------------------------------------

    @Override
    public final boolean keyDown(int keycode) {
        log("keyDown %d", keycode);
        if (!acceptsKeyboard()) return false;
        return onKeyDown(keycode);
    }

    @Override
    public final boolean keyUp(int keycode) {
        log("keyUp %d", keycode);
        if (keycode == ESCAPE && isShiftPressed() && Gdx.app.getType() == Application.ApplicationType.Desktop) {
            Log.info(TAG, "exit requested by shift+esc");
            Gdx.app.exit();
            return true;
        }
        if (!acceptsKeyboard()) return false;
        if (debugKey(keycode))  return true;
        return onKeyUp(keycode);
    }

    @Override
    public final boolean keyTyped(char character) {
        if (!acceptsKeyboard()) return false;
        return onKeyTyped(character);
    }

    @Override
    public final boolean touchDown(int screenX, int screenY, int pointer, int button) {
        updatePointer(screenX, screenY);
        log("touchDown b%d p%d screen(%d, %d) world(%.0f, %.0f)", button, pointer, screenX, screenY, this.pointer.x, this.pointer.y);
        if (!acceptsMouse()) return false;
        return onTouchDown(screenX, screenY, pointer, button);
    }

    @Override
    public final boolean touchUp(int screenX, int screenY, int pointer, int button) {
        updatePointer(screenX, screenY);
        log("touchUp b%d p%d screen(%d, %d) world(%.0f, %.0f)", button, pointer, screenX, screenY, this.pointer.x, this.pointer.y);
        if (!acceptsMouse()) return false;
        return onTouchUp(screenX, screenY, pointer, button);
    }

    @Override
    public final boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        updatePointer(screenX, screenY);
        log("touchCancelled b%d p%d screen(%d, %d) world(%.0f, %.0f)", button, pointer, screenX, screenY, this.pointer.x, this.pointer.y);
        if (!acceptsMouse()) return false;
        return onTouchCancelled(screenX, screenY, pointer, button);
    }

    @Override
    public final boolean touchDragged(int screenX, int screenY, int pointer) {
        updatePointer(screenX, screenY);
        log("touchDragged p%d screen(%d, %d) world(%.0f, %.0f)", pointer, screenX, screenY, this.pointer.x, this.pointer.y);
        if (!acceptsMouse()) return false;
        return onTouchDragged(screenX, screenY, pointer);
    }

    @Override
    public final boolean mouseMoved(int screenX, int screenY) {
        // Not logged, too noisy
        updatePointer(screenX, screenY);
        if (!acceptsMouse()) return false;
        return onMouseMoved(screenX, screenY);
    }

    @Override
    public final boolean scrolled(float amountX, float amountY) {
        log("scrolled (%.1f, %.1f)", amountX, amountY);
        if (!acceptsMouse()) return false;
        return onScrolled(amountX, amountY);
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    protected boolean isShiftPressed() {
        return Gdx.input.isKeyPressed(SHIFT_LEFT) || Gdx.input.isKeyPressed(SHIFT_RIGHT);
    }

    protected void updatePointer(int screenX, int screenY) {
        pointerScreen.set(screenX, screenY);
        screen.worldView.unproject(screenX, screenY, pointer);
    }

    protected void log(String msg, Object... args) {
        Log.info(ZenFlag.LOG_INPUT, TAG, screen.getClass().getSimpleName() + " " + msg, args);
    }
}
