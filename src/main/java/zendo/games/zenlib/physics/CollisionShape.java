package zendo.games.zenlib.physics;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public abstract class CollisionShape {
    public abstract void debugRender(SpriteBatch batch, TextureRegion shapeRegion);
}
