package zendo.games.zenlib.ui;

import com.kotcrab.vis.ui.widget.VisWindow;
import zendo.games.zenlib.assets.ZenPatch;

public class ZenWindow extends VisWindow {
    public WindowStyle style;

    /**
     * Creates a window that is not movable or resizable.
     */
    public ZenWindow() {
        super("");
        setMovable(false);
        setResizable(false);
        setModal(true);
        style = new WindowStyle(getStyle());
        style.background = ZenPatch.glass_red.ninePatchDrawable;
        setStyle(style);
    }

    /**
     * Creates a window with width and height. Set position to center of parent.
     */
    public ZenWindow(float width, float height) {
        this();
        setSize(width, height);
        setCenterOnAdd(true);
    }

    /**
     * Creates a window with width and height. Set position to x, y.
     */
    public ZenWindow(float x, float y, float width, float height) {
        this();
        setSize(width, height);
        setPosition(x, y);
    }
}
