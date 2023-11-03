package zendo.games.zenlib.physics;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;

public interface Influencer {
    /**
     * The Strength of the influencer negative repels, positive attracts
     * @return the strength
     */
    float getStrength();
    Vector2 getPosition();
    float getRange();
    void debugRender(SpriteBatch batch);
    boolean shouldEffect(Collidable c);
    void updateInfluence(float dt);
    void renderInfluence(SpriteBatch batch);
}
