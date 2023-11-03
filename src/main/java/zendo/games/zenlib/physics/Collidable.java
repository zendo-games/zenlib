package zendo.games.zenlib.physics;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import space.earlygrey.shapedrawer.ShapeDrawer;

public interface Collidable {

    float IMMOVABLE = Float.MAX_VALUE;

    void renderDebug(ShapeDrawer shapes);

    /**
     * This should be a value 0-1 for a percentage that should be lost to friction 0 = full stop 1 = no friction
     * @return the friction percentage
     */
    float getFriction();
    float getMass();

    Vector2 getVelocity();
    void setVelocity(Vector2 newVel);
    void setVelocity(float x, float y);

    Vector2 getPosition();
    void setPosition(float x, float y);
    void setPosition(Vector2 newPos);

    Rectangle getCollisionBounds();
//    void setCollisionBounds(float dt);
    CollisionShape getCollisionShape();

    void collidedWith(Collidable object);
    boolean shouldCollideWith(Collidable object);

}
