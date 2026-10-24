package com.zendogames.zenlib.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.Log;

import java.util.function.Supplier;

import static com.badlogic.gdx.Application.ApplicationType;

/**
 * The game's input, one instance as {@code ZenGame.input}: this frame's devices ({@link #now}), last
 * frame's ({@link #last}) and a clock that never pauses. The frame protocol, run by {@code ZenGame}:
 * <ol>
 *   <li>events arrive from {@code ScreenInputProcessor} between frames and write {@code now} at once,
 *       stamped with {@link #time} as it stands;</li>
 *   <li>{@link #step} advances the clock by the frame delta and polls the gamepads;</li>
 *   <li>bindings {@code update(input)} and systems read, as many times as the frame ticks;</li>
 *   <li>{@link #endFrame} rolls {@code now} into {@code last} and clears the edges.</li>
 * </ol>
 * Edges ({@code pressed}/{@code released}) are therefore per frame, and a binding updated twice in one
 * frame sees the same edge twice; {@code InputSystem} derives per-tick edges from the levels. Gamepads
 * come from {@link #controllerSource}, null by default so a headless or controller-less process polls
 * nothing; {@code ZenGame} installs {@link #platformControllers()}.
 */
public final class InputState {

    private static final String TAG = "Input";

    public final DeviceState now = new DeviceState();
    public final DeviceState last = new DeviceState();

    /** Seconds, {@code += dt} per {@link #step}. Press timestamps and press buffers read it. Never paused. */
    public double time;

    /**
     * Where {@link #step} finds the gamepads, in slot order; null polls none. A source that throws
     * (gdx-controllers without its platform jar) is reported once with {@code Log.error} and set to null.
     */
    public Supplier<Array<Controller>> controllerSource;

    /** {@code Controllers::getControllers} where gdx-controllers has a backend (desktop and web), else null. */
    public static Supplier<Array<Controller>> platformControllers() {
        var type = Gdx.app.getType();
        if (type != ApplicationType.Desktop && type != ApplicationType.WebGL) return null;
        return Controllers::getControllers;
    }

    // ---- events, written by ScreenInputProcessor between frames ----------------------------------------

    public void keyDown(int keycode) {
        now.keyboard.press(keycode, time);
    }

    public void keyUp(int keycode) {
        now.keyboard.release(keycode);
    }

    public void mouseDown(int button) {
        now.mouse.press(button, time);
    }

    public void mouseUp(int button) {
        now.mouse.release(button);
    }

    public void mouseMoved(int x, int y) {
        now.mouseX = x;
        now.mouseY = y;
    }

    public void scrolled(float amountX, float amountY) {
        now.wheelX += amountX;
        now.wheelY += amountY;
    }

    // ---- the frame ------------------------------------------------------------------------------------

    /** Advances the clock, then polls every gamepad slot into {@link #now} with edges against {@link #last}. */
    public void step(float dt) {
        time += dt;
        pollGamepads();
    }

    /** Closes the frame: {@link #last} becomes this frame, {@link #now} keeps its levels and loses its edges and wheel. */
    public void endFrame() {
        last.set(now);
        now.clearEdges();
    }

    /** Everything up with no edges in both snapshots; for {@code hide()} and {@code pause()}, where key-ups are lost. */
    public void zeroOut() {
        now.zeroOut();
        last.zeroOut();
    }

    /** A key, mouse button or pad button went down this framtton". */
    public boolean anyPressed() {
        return now.anyPressed();
    }

    private void pollGamepads() {
        var controllers = (Array<Controller>) null;
        if (controllerSource != null) {
            try {
                controllers = controllerSource.get();
            } catch (RuntimeException e) {
                Log.error(TAG, "gamepads unavailable, polling stopped: %s", e.getMessage());
                controllerSource = null;
            }
        }
        for (int slot = 0; slot < DeviceState.GAMEPADS; slot++) {
            var controller = (controllers != null && slot < controllers.size) ? controllers.get(slot) : null;
            if (controller != null && controller.isConnected()) {
                now.gamepads[slot].poll(controller, last.gamepads[slot], time);
            } else {
                now.gamepads[slot].disconnect(last.gamepads[slot], time);
            }
        }
    }
}
