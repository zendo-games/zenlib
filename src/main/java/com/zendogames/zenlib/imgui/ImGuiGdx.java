package com.zendogames.zenlib.imgui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.jparser.runtime.helper.NativeByteArray;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ZenFlag;
import imgui.*;
import imgui.enums.ImGuiConfigFlags;
import imgui.gdx.ImGuiGdxGLImpl;
import imgui.gdx.ImGuiGdxInputMultiplexer;

public class ImGuiGdx {

    protected static final String folder = "com/zendogames/zenlib/assets/fonts/";

    public static ImGuiImpl imgui;

    protected static InputProcessor input;
    protected static boolean loaded;
    protected static boolean initialized;

    /** Start the async native load. A failure is logged and turns {@code ZenFlag.SHOW_IMGUI} off. */
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
                ZenFlag.SHOW_IMGUI.disable();
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
        input = new ImGuiGdxInputMultiplexer();

        var font1 = Gdx.files.classpath(folder + "cousine-regular.ttf");
        var font2 = Gdx.files.classpath(folder + "droid-sans-mono.ttf");
        addFont(font1, font1.name());
        addFont(font2, font2.name());

        initialized = true;
        Log.info("ImGui", "initialized in %.3f seconds", (System.currentTimeMillis() - start) / 1_000.0);
    }

    public static boolean isLoaded()      { return loaded; }
    public static boolean isLoading()     { return !isLoaded(); }
    public static boolean isInitialized() { return initialized; }

    public static InputProcessor input() {
        if (input == null) {
            Log.warn("ImGui", "input processor not initialized");
        }
        return input;
    }

    /** Runs one imgui frame around the current screen's ui code. */
    public static void render(Runnable ui) {
        if (!initialized || ZenFlag.SHOW_IMGUI.off()) return;
        imgui.newFrame();                  // Start a new ImGui frame from scratch
        ui.run();                          // Screen-specific ImGui draw calls, ui logic -> draw list
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

        // Get the font file's name as bytes and set (character by character) in the imgui font config
        var nameBytes = name.getBytes();
        for(int i = 0; i < nameBytes.length; i++) {
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

    public static Texture createTexture(FileHandle fileHandle) {
        return new Texture(fileHandle);
    }

    public static Texture createTexture(Pixmap pixmap) {
        return new Texture(pixmap);
    }

    public static ImTextureRef getTextureRef(Texture texture) {
        return ImTemp.ImTextureRef_1(texture.getTextureObjectHandle());
    }
}
