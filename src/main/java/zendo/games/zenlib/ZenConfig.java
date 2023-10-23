package zendo.games.zenlib;

public class ZenConfig {

    public final Window window;

    public ZenConfig() {
        this("zenlib", 1280, 720);
    }

    public ZenConfig(String title, int width, int height) {
        this.window = new Window(title, width, height);
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
