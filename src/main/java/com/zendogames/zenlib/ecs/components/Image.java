package com.zendogames.zenlib.ecs.components;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.utils.Draw;

/** A static image: one region drawn into {@link #rect}, or a texture repeated to fill it. */
public class Image extends Renderable {

    public static final ComponentType<Image> TYPE = ComponentType.register(Image.class);
    public static Image   get(Entity e) { return TYPE.get(e); }
    public static boolean has(Entity e) { return TYPE.has(e); }

    private Value value;

    public Image(TextureRegion region) {
        this(region, region.getRegionWidth(), region.getRegionHeight());
    }

    public Image(TextureRegion region, int width, int height) {
        set(region);
        size.set(width, height);
    }

    /** A repeating fill of {@code width x height}; the texture's wrap must be {@code Repeat}. */
    public Image(Texture texture, float width, float height) {
        set(texture);
        size.set(width, height);
    }

    public void set(TextureRegion region) { value = new Region(region); }
    public void set(Texture texture)      { value = new Repeat(texture); }

    @Override
    public void render(Batch batch, float x, float y) {
        var rect = rect(x, y);
        switch (value) {
            case Region(TextureRegion region) -> Draw.region(batch, region, rect, tint);
            case Repeat(Texture texture)      -> Draw.texture(batch, texture, rect, tint);
        }
    }

    /** One image holds either a region or a repeating texture, never both. */
    private sealed interface Value permits Region, Repeat {}
    private record Region(TextureRegion region) implements Value {};
    private record Repeat(Texture texture)      implements Value {};
}
