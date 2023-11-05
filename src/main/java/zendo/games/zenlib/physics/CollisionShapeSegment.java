package zendo.games.zenlib.physics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

public class CollisionShapeSegment extends CollisionShape {
    public Vector2 start;
    public Vector2 end;
    public Vector2 delta;
    public Vector2 normal;

    public CollisionShapeSegment(Vector2 start, Vector2 end) {
        this(start.x, start.y, end.x, end.y);
    }

    public CollisionShapeSegment(float x1, float y1, float x2, float y2) {
        start = new Vector2(x1, y1);
        end = new Vector2(x2, y2);
        delta = new Vector2(end).sub(start);
        float dx = x2 - x1;
        float dy = y2 - y1;
        normal = new Vector2(dy, -dx).nor();
    }

    public float getRotation() {
        return delta.angleDeg();
    }

    public void debugRender(SpriteBatch batch, TextureRegion pixelRegion) {
        float width = 2f;
        batch.setColor(Color.MAGENTA);
        batch.draw(pixelRegion, start.x, start.y - width / 2f, 0, width / 2f, delta.len(), width, 1, 1, getRotation());
        batch.setColor(Color.YELLOW);
        batch.draw(
                pixelRegion,
                (start.x + end.x) / 2f,
                (start.y + end.y) / 2f,
                0,
                width / 2f,
                10,
                width,
                1,
                1,
                normal.angleDeg());
        batch.setColor(Color.WHITE);
    }
}
