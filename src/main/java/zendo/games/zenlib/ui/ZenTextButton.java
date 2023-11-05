package zendo.games.zenlib.ui;

import com.kotcrab.vis.ui.widget.VisTextButton;
import zendo.games.zenlib.assets.ZenPatch;

public class ZenTextButton extends VisTextButton {
    public TextButtonStyle style;

    public ZenTextButton(String text) {
        super(text);
        setFocusBorderEnabled(false);
        style = new TextButtonStyle(getStyle());
        style.up = ZenPatch.glass.ninePatchDrawable;
        style.over = ZenPatch.glass_active.ninePatchDrawable;
        style.down = ZenPatch.glass_dim.ninePatchDrawable;
        setStyle(style);
    }

    public ZenTextButton(float x, float y, String text) {
        this(text);
        setPosition(x, y);
    }

    public ZenTextButton(float x, float y, float width, float height, String text) {
        this(x, y, text);
        setSize(width, height);
    }
}
