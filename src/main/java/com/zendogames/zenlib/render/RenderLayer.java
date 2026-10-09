package com.zendogames.zenlib.render;

/** Coarse buckets for z-ordering what the render pass draws; {@code Renderable.order} sorts within a layer. */
public enum RenderLayer {
    BACKGROUND, // parallax, backdrop images, "behind everything" tile layers
    WORLD,      // default: terrain tile layers, players, props, projectiles
    FOREGROUND, // tile layers or decorations that occlude the world layer
    OVERLAY;    // world-space overlays that are not debug (markers, hints)

    public String layerName() { return name().toLowerCase(); }

    public static RenderLayer parse(String layerName) {
        return parse(layerName, WORLD);
    }

    public static RenderLayer parse(String layerName, RenderLayer fallback) {
        for (var layer : values()) {
            if (layer.layerName().equalsIgnoreCase(layerName)) {
                return layer;
            }
        }
        return fallback;
    }
}
