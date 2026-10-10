package com.zendogames.zenlib.ui;

/**
 * One section of the debug UI: a tittle and an ImGui body. {@code DebugUI#render(DebugView...)}
 * draws each one as a collapsing header in the Debug window; the same object could be drawn in
 * a window of its own later. Screens build the views they need once (via a {@link DebugViews}
 * factory or a {@link DebugActions}) and pass them every frame from {@code renderImGui}, so the
 * current screen alone decides what shows and nothing has to be unregistered on a transition.
 */
public interface DebugView {

    String title();

    /** Called inside the open header; draw the body with no Begin/End of its own. */
    void draw();

    /** Header open on first show. */
    default boolean openByDefault() { return true; }
}
