package zendo.games.zenlib.physics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

public class CollisionShapeCircle extends CollisionShape {

    public float radius;
    public Vector2 center;

    public CollisionShapeCircle(float radius, float x, float y) {
        this.radius = radius;
        this.center = new Vector2(x, y);
    }

    public void set(float x, float y) {
        this.center.set(x, y);
    }

    public void set(float radius, float x, float y) {
        this.radius = radius;
        this.center.set(x, y);
    }

    public void debugRender(SpriteBatch batch, TextureRegion circleRegion) {
        batch.setColor(1f, 1f, 0, 1f);
        batch.draw(circleRegion, center.x - radius, center.y - radius, radius * 2f, radius * 2f);
        batch.setColor(Color.WHITE);
    }
}
