package com.zendogames.zenlib.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

/** Batch draw helpers that take a rectangle and a tint and leave the batch color as they found it. */
public final class Draw {

    private static final Color prevColor = new Color();

    private Draw() {}

    public static void region(Batch batch, TextureRegion region, Rectangle rect) {
        region(batch, region, rect, Color.WHITE);
    }

    public static void region(Batch batch, TextureRegion region, Rectangle rect, Color tint) {
        prevColor.set(batch.getColor());
        batch.setColor(tint);
        batch.draw(region, rect.x, rect.y, rect.width, rect.height);
        batch.setColor(prevColor);
    }

    /** Scales and rotates about ({@code ox}, {@code oy}), an offset from the rectangle's corner. */
    public static void region(Batch batch, TextureRegion region, Rectangle rect, Color tint, float ox, float oy, float sx, float sy, float rotation) {
        prevColor.set(batch.getColor());
        batch.setColor(tint);
        batch.draw(region, rect.x, rect.y, ox, oy, rect.width, rect.height, sx, sy, rotation);
        batch.setColor(prevColor);
    }

    /** Fills the rectangle by repeating the texture, which must use {@code TextureWrap.Repeat}. */
    public static void texture(Batch batch, Texture texture, Rectangle rect, Color tint) {
        prevColor.set(batch.getColor());
        batch.setColor(tint);
        float u2 = rect.width  / texture.getWidth();
        float v2 = rect.height / texture.getHeight();
        batch.draw(texture, rect.x, rect.y, rect.width, rect.height, 0, 0, u2, -v2);
        batch.setColor(prevColor);
    }
}