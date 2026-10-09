package com.zendogames.zenlib.ecs.components;


import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.zendogames.zenlib.FramePool;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.render.RenderLayer;
import com.zendogames.zenlib.render.Visual;

/**
 * Base for components the {@code RenderSystem} draws: where on the entity the image sits
 * ({@link #origin}, {@link #size}, {@link #scale}), how it is tinted, and where it sorts
 * ({@link #layer}, {@link #order}). Each subclass registers its own {@code ComponentType},
 * so one entity can carry more than one; a game adds its own by extending this and handing
 * the type to {@code RenderSystem}.
 */
public abstract class Renderable implements Component, Visual {

    public final Color tint = Color.WHITE.cpy();

    /** Offset from the entity position to the image's bottom-left corner, in unscaled pixels. */
    public final Vector2 origin = new Vector2();
    public final Vector2 size   = new Vector2();
    public final Vector2 scale  = new Vector2(1, 1);

    public RenderLayer layer = RenderLayer.WORLD;
    public int order = 0;

    /** The draw rectangle for an entity at ({@code Position.x,y}); a frame-pooled scratch value. */
    public Rectangle rect(Position position) {
        return rect(position.x, position.y);
    }

    /** The draw rectangle for an entity at ({@code x, y}); a frame-pooled scratch value. */
    public Rectangle rect(float x, float y) {
        return FramePool.rect(
                x - origin.x * scale.x,
                y - origin.y * scale.y,
                size.x * scale.x,
                size.y * scale.y);
    }
}
