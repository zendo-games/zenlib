package zendo.games.zenlib;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;

public class ZenConfig {

    public final Window window;
    public final Skin uiSkin;

    public ZenConfig() {
        this("zenlib", 1280, 720, null);
    }

    public ZenConfig(String title, int width, int height) {
        this(title, width, height, null);
    }

    public ZenConfig(String title, int width, int height, Skin uiSkin) {
        this.window = new Window(title, width, height);
        this.uiSkin = uiSkin;
    }

    public static class Window {
        public final String title;
        public final int width;
        public final int height;

        public Window(String title, int width, int height) {
            this.title = title;
            this.width = width;
            this.height = height;
        }
    }

}
