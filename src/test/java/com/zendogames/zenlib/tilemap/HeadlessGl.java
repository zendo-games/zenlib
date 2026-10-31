package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Enough of a GL context to construct a {@link Texture} headless: {@code Gdx.gl}, {@code Gdx.gl20}
 * and {@code Gdx.graphics} become proxies that do nothing and answer zero, false or null, so
 * {@code glGenTexture} hands out handle 0 and no extension is supported. {@code Gdx.app} stays
 * null (the data is unmanaged, so the texture is never registered for reload). Needed because
 * {@code TiledMapImageLayer}'s constructor reads the region's texture format.
 */
final class HeadlessGl {

    private HeadlessGl() {}

    static void install() {
        if (Gdx.gl != null) return;
        var loader = HeadlessGl.class.getClassLoader();
        var gl = (GL20) Proxy.newProxyInstance(loader, new Class<?>[] { GL20.class }, HeadlessGl::nothing);
        Gdx.gl   = gl;
        Gdx.gl20 = gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(loader, new Class<?>[] { Graphics.class }, HeadlessGl::nothing);
    }

    /** A {@code w x h} RGBA region over a texture that was never uploaded. */
    static TextureRegion region(int w, int h) {
        install();
        return new TextureRegion(new Texture(new Blank(w, h)), 0, 0, w, h);
    }

    private static Object nothing(Object proxy, Method method, Object[] args) {
        var type = method.getReturnType();
        if (type == boolean.class) return false;
        if (type == int.class)     return 0;
        if (type == long.class)    return 0L;
        if (type == float.class)   return 0f;
        if (type == double.class)  return 0d;
        return null;
    }

    /** Custom texture data that uploads nothing. */
    private record Blank(int width, int height) implements TextureData {
        @Override public TextureDataType getType()     { return TextureDataType.Custom; }
        @Override public boolean isPrepared()          { return true; }
        @Override public void prepare()                {}
        @Override public Pixmap consumePixmap()        { throw new UnsupportedOperationException(); }
        @Override public boolean disposePixmap()       { return false; }
        @Override public void consumeCustomData(int target) {}
        @Override public int getWidth()                { return width; }
        @Override public int getHeight()               { return height; }
        @Override public Pixmap.Format getFormat()     { return Pixmap.Format.RGBA8888; }
        @Override public boolean useMipMaps()          { return false; }
        @Override public boolean isManaged()           { return false; }
    }
}
