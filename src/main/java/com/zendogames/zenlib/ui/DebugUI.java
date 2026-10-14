package com.zendogames.zenlib.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.github.xpenatan.jparser.runtime.helper.NativeBoolArray;
import imgui.ImGui;
import imgui.ImTemp;
import imgui.enums.ImGuiCond;
import imgui.enums.ImGuiTreeNodeFlags;

/**
 * The debug window: a full-width strip at the top, collapsed on first show, with the ImGui tool
 * toggles, the views the game pinned with {@link #always}, then one collapsing header per view
 * the current screen lists in {@code ZenScreen.debugViews}, and the frame pool last. Owned by
 * {@code ZenGame} as {@code debugUI}; drawn once per frame from {@code ZenGame.draw()}. ImGui
 * keeps each header's open state by title for the life of the process.
 */
public class DebugUI {

    private final Array<DebugView> pinned = new Array<>();
    private final DebugView framePool = DebugViews.framePool();

    /** What {@code Begin} reported last frame, and whether {@link #toggleCollapsed} asked to flip it. */
    private boolean collapsed, toggleRequested;

    /** Collapses or expands the window on the next frame; {@code ScreenInputProcessor.debugKey} binds it to the tilde key. */
    public void toggleCollapsed() {
        toggleRequested = true;
    }

    /** Views drawn on every screen, in order, before the screen's own. */
    public DebugUI always(DebugView... views) {
        pinned.addAll(views);
        return this;
    }

    /** Draws the window with the pinned views, {@code views}, then the frame pool. */
    public void render(Array<DebugView> views) {
        ImGui.SetNextWindowCollapsed(true, ImGuiCond.Once);
        ImGui.SetNextWindowPos(ImTemp.ImVec2_1(0, 0), ImGuiCond.Once);
        ImGui.SetNextWindowSize(ImTemp.ImVec2_1(Gdx.graphics.getWidth(), 0), ImGuiCond.Once);
        if (toggleRequested) {
            ImGui.SetNextWindowCollapsed(!collapsed, ImGuiCond.Always); // overrides the Once above for this frame
            toggleRequested = false;
        }
        ImGui.Begin("Debug");
        collapsed = ImGui.IsWindowCollapsed();
        {
            tools();
            for (int i = 0; i < pinned.size; i++) section(pinned.get(i));
            for (int i = 0; i < views.size;  i++) section(views.get(i));
            section(framePool);
        }
        ImGui.End();
    }

    private void section(DebugView view) {
        var flags = view.openByDefault() ? ImGuiTreeNodeFlags.DefaultOpen : ImGuiTreeNodeFlags.None;
        if (ImGui.CollapsingHeader(view.title(), flags)) {
            ImGui.Indent();
            view.draw();
            ImGui.Unindent();
        }
    }

    private void tools() {
        if (!ImGui.CollapsingHeader("ImGui Tools")) return;
                          ImGui.Checkbox("demo",      ImGuiTools.demo);
        ImGui.SameLine(); ImGui.Checkbox("metrics",   ImGuiTools.metrics);
        ImGui.SameLine(); ImGui.Checkbox("debug-log", ImGuiTools.debugLog);
        ImGui.SameLine(); ImGui.Checkbox("id-stack",  ImGuiTools.idStack);
        ImGui.SameLine(); ImGui.Checkbox("about",     ImGuiTools.about);
        ImGui.SameLine(); ImGui.Checkbox("styles",    ImGuiTools.styles);

        if (ImGuiTools.demo.getValue(0))     ImGui.ShowDemoWindow(ImGuiTools.demo);
        if (ImGuiTools.metrics.getValue(0))  ImGui.ShowMetricsWindow(ImGuiTools.metrics);
        if (ImGuiTools.debugLog.getValue(0)) ImGui.ShowDebugLogWindow(ImGuiTools.debugLog);
        if (ImGuiTools.idStack.getValue(0))  ImGui.ShowIDStackToolWindow(ImGuiTools.idStack);
        if (ImGuiTools.about.getValue(0))    ImGui.ShowAboutWindow(ImGuiTools.about);
        if (ImGuiTools.styles.getValue(0))   ImGui.ShowStyleEditor();
    }


    /** Native buffers allocated on first use, after {@code ImGuiGdx.load()}. */
    private static class ImGuiTools {
        static final NativeBoolArray demo     = new NativeBoolArray(1);
        static final NativeBoolArray metrics  = new NativeBoolArray(1);
        static final NativeBoolArray debugLog = new NativeBoolArray(1);
        static final NativeBoolArray idStack  = new NativeBoolArray(1);
        static final NativeBoolArray about    = new NativeBoolArray(1);
        static final NativeBoolArray styles   = new NativeBoolArray(1);
    }
}
