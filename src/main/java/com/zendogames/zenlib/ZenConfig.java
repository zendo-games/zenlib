package com.zendogames.zenlib;

/**
 * Settings the library needs from the game, handed over once in {@link ZenGame#config()}.
 *
 * @param preferencesName   Name of the game's libGDX preferences file, must be unique per game.
 * @param framebufferWidth  Native resolution width that the world is rendered at before scaling to the window.
 * @param framebufferHeight Native resolution height that the world is rendered at before scaling to the window.
 */
public record ZenConfig(
        String preferencesName,
        int framebufferWidth,
        int framebufferHeight
) {}
