package zendo.games.zenlib.ui;

import com.badlogic.gdx.graphics.Color;
import com.kotcrab.vis.ui.widget.VisTextButton;

public class ZenTextButton extends VisTextButton {
    public ZenTextButton(String text) {
        super(text);
        // TODO - add default styling with ZenNinePatches once those are ready
        getStyle().fontColor = Color.RED;
        getStyle().overFontColor = Color.BLUE;
    }
}
