package com.zendogames.zenlib.render;

import com.badlogic.gdx.graphics.g2d.Batch;

/** Something the render pass can draw at a world position; the batch is between {@code begin} and {@code end}. */
public interface Visual {
    void render(Batch batch, float x, float y);
}
