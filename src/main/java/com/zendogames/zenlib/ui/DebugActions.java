package com.zendogames.zenlib.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.Array;
import com.github.xpenatan.jparser.runtime.helper.NativeBoolArray;
import com.zendogames.zenlib.Flags;
import imgui.ImGui;
import imgui.enums.ImGuiButtonFlags;
import imgui.enums.ImGuiCol;

/**
 * A screen's debug controls as a table of named actions, each a button in the Debug window and
 * optionally a key. Two kinds:
 * <ul>
 *     <li>{@link #add}: runs when the button or key is pressed.</li>
 *     <li>{@link #arm}: the button or key arms it; the next click in the world runs it at the
 *         pointer. A second press disarms. "sticky" keeps it armed across clicks.</li>
 * </ul>
 * The screen forwards {@code onKeyDown} to {@link #key} and {@code onTouchDown} to
 * {@link #click}, so the key list lives in one place and the Debug window documents it.
 * <pre>{@code
 * actions = new DebugActions()
 *         .arm("spitter", Input.Keys.S, enemies::spitter)
 *         .add("kill enemies", Input.Keys.K, enemies::killEnemies);
 * // in the input processor:
 * protected boolean onKeyDown(int keycode) { return actions.key(keycode); }
 * protected boolean onTouchDown(...)       { return actions.click((int) pointer.x, (int) pointer.y); }
 * }</pre>
 */
public class DebugActions implements DebugView {

    /** Runs at a world position. */
    @FunctionalInterface
    public interface Spawn {
        void at(int x, int y);
    }

    private record Action(String label, int key, Runnable run, Spawn spawn) {
        String buttonLabel() {
            return (key == Input.Keys.UNKNOWN) ? label : label + " (" + Input.Keys.toString(key) + ")";
        }
    }

    private static final int ARMED_COLOR = 0xff2a9d2a; // IM_COL32 is 0xAABBGGRR

    private final Array<Action> actions = new Array<>();

    private Action armed;
    private Spawn defaultClick;

    /** A button (and key) that runs {@code run} at once. {@code Input.Keys.UNKNOWN} for no key. */
    public DebugActions add(String label, int key, Runnable run) {
        actions.add(new Action(label, key, run, /*spawn*/ null));
        return this;
    }

    /** A button (and key) that arms {@code spawn} for the next world click. */
    public DebugActions arm(String label, int key, Spawn spawn) {
        actions.add(new Action(label, key, /*runnable*/ null, spawn));
        return this;
    }

    /** What a click does while nothing is armed; null (the default) does nothing. */
    public DebugActions defaultClick(Spawn spawn) {
        this.defaultClick = spawn;
        return this;
    }

    public boolean isArmed() {
        return (armed != null);
    }

    public void disarm() {
        armed = null;
    }

    /** Runs or arms the action bound to {@code keycode}; false when none is bound. */
    public boolean key(int keycode) {
        for (int i = 0; i < actions.size; i++) {
            var action = actions.get(i);
            if (action.key == keycode) {
                trigger(action);
                return true;
            }
        }
        return false;
    }

    /** A click in the world at {@code (x, y)}: runs the armed action, else the default; false if neither. */
    public boolean click(int x, int y) {
        if (armed != null) {
            armed.spawn.at(x, y);
            if (!Flags.sticky.getValue(0)) {
                armed = null;
            }
            return true;
        }
        if (defaultClick != null) {
            defaultClick.at(x, y);
            return true;
        }
        return false;
    }

    private void trigger(Action action) {
        if (action.run != null) {
            action.run.run();
        } else {
            armed = (armed == action) ? null : action;
        }
    }

    // ------------------------------------------------------------------------
    // DebugView
    // ------------------------------------------------------------------------

    @Override
    public String title() {
        return "Screen";
    }

    @Override
    public void draw() {
        // armed actions first, as a row of toggles
        var any = false;
        for (int i = 0; i < actions.size; i++) {
            var action = actions.get(i);
            if (action.spawn == null) continue;
            if (any) ImGui.SameLine();
            any = true;
            var isArmed = (action == armed);
            if (isArmed) ImGui.PushStyleColor(ImGuiCol.Button, ARMED_COLOR);
            if (ImGui.Button(action.buttonLabel())) {
                trigger(action);
            }
            if (isArmed) ImGui.PopStyleColor();
        }

        if (any) {
            ImGui.SameLine();
            ImGui.Checkbox("sticky", Flags.sticky);
            if (armed != null) {
                ImGui.SameLine();
                ImGui.TextDisabled("click in the world to spawn " + armed.label);
            } else {
                ImGui.SameLine();
                ImGui.TextDisabled((defaultClick != null) ? "click: default spawn" : "arm one, then click in the world");
            }
        }

        // then the immediate ones
        var first = true;
        for (int i = 0; i < actions.size; i++) {
            var action = actions.get(i);
            if (action.run == null) continue;
            first = false;
            if (ImGui.Button(action.buttonLabel())) {
                trigger(action);
            }
        }
    }

    /** Native buffers allocated on first use, after {@code ImGuiGdx.load()}. */
    private static final class Flags {
        static final NativeBoolArray sticky = new NativeBoolArray(1);
    }
}
