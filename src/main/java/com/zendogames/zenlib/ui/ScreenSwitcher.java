package com.zendogames.zenlib.ui;

import com.badlogic.gdx.utils.Array;
import com.github.xpenatan.jparser.runtime.helper.NativeBoolArray;
import com.zendogames.zenlib.ZenGame;
import com.zendogames.zenlib.screens.ZenScreen;
import imgui.ImGui;
import imgui.enums.ImGuiCol;

import java.util.function.Function;

/**
 * A row of buttons that jump between screens, for the debug window: the game lists its screens
 * once and this view calls {@code game.setScreen} with a fresh instance. The current screen's
 * button is highlighted; "reload" replaces the current screen with a new one of the same kind,
 * which keeps two worlds alive through the transition. Pin it with {@code DebugUI.always} so it
 * shows on every screen.
 */
public class ScreenSwitcher implements DebugView {

    private record Entry(String label, Class<? extends ZenScreen> type, Function<ZenGame, ZenScreen> create) {}

    private static final int CURRENT_COLOR = 0xff2a9d2a; // IM_COL32 is 0xAABBGGRR

    private final ZenGame game;
    private final Array<Entry> entries;

    public ScreenSwitcher(ZenGame game) {
        this.game = game;
        this.entries = new Array<>();
    }

    /** {@code type} identifies the current screen; {@code create} builds a fresh one. */
    public <T extends ZenScreen> ScreenSwitcher add(String label, Class<T> type, Function<ZenGame, T> create) {
        entries.add(new Entry(label, type, create::apply));
        return this;
    }

    @Override
    public String title() {
        return "Screens";
    }

    @Override
    public boolean openByDefault() {
        return true;
    }

    @Override
    public void draw() {
        var current = game.getScreen();
        var instant = Buffers.instant.getValue(0);
        for (int i = 0; i < entries.size; i++) {
            var entry = entries.get(i);
            var isCurrent = entry.type.isInstance(current);
            if (i > 0) ImGui.SameLine();
            if (isCurrent) ImGui.PushStyleColor(ImGuiCol.Button, CURRENT_COLOR);
            if (ImGui.Button(entry.label) && !isCurrent) {
                game.setScreen(entry.create.apply(game), null, instant);
            }
            if (isCurrent) ImGui.PopStyleColor();
        }
        ImGui.SameLine();
        if (ImGui.Button("reload")) {
            for (int i = 0; i < entries.size; i++) {
                var entry = entries.get(i);
                if (entry.type.isInstance(current)) {
                    game.setScreen(entry.create.apply(game), null, instant);
                    break;
                }
            }
        }
        ImGui.SameLine();
        ImGui.Checkbox("instant", Buffers.instant);
    }

    /** Native buffers allocated on first use, after {@code ImGuiGdx.load()}. */
    private static final class Buffers {
        static final NativeBoolArray instant = new NativeBoolArray(1);
    }
}
