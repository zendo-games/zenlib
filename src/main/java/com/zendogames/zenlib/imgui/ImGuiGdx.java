package com.zendogames.zenlib.imgui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.jparser.runtime.helper.NativeByteArray;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ZenFlag;
import imgui.*;
import imgui.enums.ImGuiConfigFlags;
import imgui.gdx.ImGuiGdxGLImpl;
import imgui.gdx.ImGuiGdxInput;
import imgui.gdx.ImGuiGdxInputMultiplexer;

public class ImGuiGdx {

    protected static final String folder = "com/zendogames/zenlib/assets/fonts/";

    /** Size of imgui's {@code ImFontConfig::Name} buffer, including its terminator. */
    protected static final int FONT_NAME_SIZE = 40;

    public static ImGuiImpl imgui;

    protected static InputProcessor input;
    protected static boolean loaded;
    protected static boolean initialized;

    /** Start the async native load. A failure is logged and the game runs without imgui. */
    public static void load() {
        loaded = false;
        initialized = false;
        ImGuiLoader.init((isSuccess, throwable) -> {
            try {
                if (!isSuccess) {
                    throw new GdxRuntimeException("native library failed to load, check browser console for a 404", throwable);
                }
                init();
            } catch (Exception e) {
                Log.error("ImGui", "initialization failed: %s", e.getMessage());
            }
            loaded = true;
        });
    }

    /** Creates the one context, renderer, and fonts. */
    protected static void init() {
        Log.debug("ImGui", "initializing...");
        var start = System.currentTimeMillis();

        var ctx = ImGui.CreateContext();
        if (ctx.native_isNULL()) {
            throw new GdxRuntimeException("Failed to set imgui context");
        }

        var io = ImGui.GetIO();
        io.set_ConfigFlags(ImGuiConfigFlags.DockingEnable);

        imgui = new ImGuiGdxGLImpl();
        input = new ImGuiGdxInputMultiplexer(new KeyboardGatedInput());

        var font = Gdx.files.classpath(folder + "JetBrainsMono-Regular.ttf");
        addFont(font, font.name());

        initialized = true;
        Log.info("ImGui", "initialized in %.3f seconds", (System.currentTimeMillis() - start) / 1_000.0);
    }

    public static boolean isLoaded()      { return loaded; }
    public static boolean isLoading()     { return !isLoaded(); }
    public static boolean isInitialized() { return initialized; }

    /** True while imgui is hovering or dragging one of its windows, so the game should ignore the pointer. */
    public static boolean wantsMouse() {
        return initialized && ImGui.GetIO().get_WantCaptureMouse();
    }

    /** True while an imgui widget has keyboard focus, so game key bindings should not fire. */
    public static boolean wantsKeyboard() {
        return initialized && ImGui.GetIO().get_WantCaptureKeyboard();
    }

    private static final class KeyboardGatedInput extends ImGuiGdxInput {
        @Override public boolean keyDown(int keycode)     { super.keyDown(keycode);    return wantsKeyboard(); }
        @Override public boolean keyUp(int keycode)       { super.keyUp(keycode);      return false; }
        @Override public boolean keyTyped(char character) { super.keyTyped(character); return wantsKeyboard(); }
    }

    public static InputProcessor input() {
        if (input == null) {
            Log.warn("ImGui", "input processor not initialized");
        }
        return input;
    }

    /**
     * Runs one imgui frame around the current screen's ui code.
     * The frame still runs (empty) while {@code ZenFlag.SHOW_IMGUI} is off: imgui only recomputes what it
     * hovers and focuses during a frame, and its input processor consumes events based on that.
     */
    public static void render(Runnable ui) {
        if (!initialized) return;
        imgui.newFrame();                  // Start a new ImGui frame from scratch
        if (ZenFlag.SHOW_IMGUI.on()) {
            ui.run();                      // Screen-specific ImGui draw calls, ui logic -> draw list
        }
        ImGui.Render();                    // ImGui draw list -> draw data
        imgui.render(ImGui.GetDrawData()); // Bind, populate buffers w/draw data, invoke ImGui rendering pipeline
    }

    public static void dispose() {
        Log.debug("ImGui", "disposing...");

        if (initialized) {
            imgui.dispose();
            ImGui.DestroyContext();
        }

        loaded      = false;
        initialized = false;

        Log.info("ImGui", "disposed");
    }

    public static void addFont(FileHandle fontFile, String name) {
        // Create an imgui font config struct, populate it, and pass it along with the font data to imgui
        var config = new ImFontConfig();

        // Get the font file's name as bytes and set (character by character) in the imgui font config,
        // truncated to fit the fixed size buffer with room for its terminator
        var nameBytes  = name.getBytes();
        var nameLength = Math.min(nameBytes.length, FONT_NAME_SIZE - 1);
        for(int i = 0; i < nameLength; i++) {
            config.set_Name(i, nameBytes[i]);
        }

        // Load the font file bytes, and read them into a native array to pass to imgui
        var bytes = fontFile.readBytes();
        var bytesNative = new NativeByteArray(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            bytesNative.setValue(i, bytes[i]);
        }

        // Load the font config and data into imgui
        var sizePixels = 0; // Font size is controlled by ImGui FontSizeBase style setting
        ImGui.GetIO().get_Fonts().AddFontFromMemoryTTF(bytesNative, bytes.length, sizePixels, config);

        // Dispose of the font config reference in java land since imgui holds the data now
        // Note that we don't dispose the fontBytesArray, it's retained by imgui through the context lifetime
        config.dispose();

        Log.info("ImGui", "add font '%s'", name);
    }

    public static ImTextureRef getTextureRef(Texture texture) {
        return ImTemp.ImTextureRef_1(texture.getTextureObjectHandle());
    }
}
