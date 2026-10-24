package com.zendogames.zenlib.input;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Enough of {@code Gdx.app} and {@code Gdx.graphics} for {@code Log.error} to run headless: proxies
 * that do nothing and answer zero, false or null (the log stamp reads {@code getFrameId}). Install in a
 * test that expects the library to report an error, and {@link #uninstall} in {@code @After} so the
 * rest of the suite keeps its null {@code Gdx.app}.
 */
final class HeadlessApp {

    private HeadlessApp() {}

    static void install() {
        var loader = HeadlessApp.class.getClassLoader();
        Gdx.app      = (Application) Proxy.newProxyInstance(loader, new Class<?>[] { Application.class }, HeadlessApp::nothing);
        Gdx.graphics = (Graphics)    Proxy.newProxyInstance(loader, new Class<?>[] { Graphics.class },    HeadlessApp::nothing);
    }

    static void uninstall() {
        Gdx.app      = null;
        Gdx.graphics = null;
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
}
