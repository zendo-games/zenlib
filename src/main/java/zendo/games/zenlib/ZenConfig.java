package zendo.games.zenlib;

public class ZenConfig {

    public static class Window {
        public String title;
        public int width;
        public int height;

        public Window() {
            this("zenlib", 1280, 720);
        }

        public Window(String title, int width, int height) {
            this.title = title;
            this.width = width;
            this.height = height;
        }
    }

    public final Window window;

    public ZenConfig() {
        this(new Window());
    }

    public ZenConfig(Window window) {
        this.window = window;
    }

}
