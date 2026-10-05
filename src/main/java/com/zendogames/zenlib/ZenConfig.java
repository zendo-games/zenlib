package com.zendogames.zenlib;

public class ZenConfig {

    /** Name of the game's libGDX preferences file, must be unique per game. */
    public String preferencesName;

    /** Native resolution width that the world is rendered at before scaling to the window. */
    public int framebufferWidth;

    /** Native resolution height that the world is rendered at before scaling to the window. */
    public int framebufferHeight;

    public ZenConfig(String preferencesName, int framebufferWidth, int framebufferHeight) {
        this.preferencesName = preferencesName;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
    }

    @Override
    public String toString() {
        return "%s[preferencesName=%s, framebufferWidth=%d, framebufferHeight=%d]"
                .formatted(getClass().getSimpleName(), preferencesName, framebufferWidth, framebufferHeight);
    }
}
