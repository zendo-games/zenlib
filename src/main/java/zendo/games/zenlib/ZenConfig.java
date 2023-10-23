package zendo.games.zenlib;

public class ZenConfig {

    public final Window window;
    public final String uiSkinPath;

    public ZenConfig() {
        this("zenlib", 1280, 720, null);
    }

    public ZenConfig(String title, int width, int height) {
        this(title, width, height, null);
    }

    public ZenConfig(String title, int width, int height, String uiSkinPath) {
        this.window = new Window(title, width, height);
        this.uiSkinPath = uiSkinPath;
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
