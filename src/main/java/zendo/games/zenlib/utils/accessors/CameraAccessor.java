package zendo.games.zenlib.utils.accessors;

import aurelienribon.tweenengine.TweenAccessor;
import com.badlogic.gdx.graphics.OrthographicCamera;

/**
 * Created by dsgraham on 8/23/15.
 */
public class CameraAccessor implements TweenAccessor<OrthographicCamera> {

    public static final int XYZ = 1;


    @Override
    public int getValues(OrthographicCamera target, int tweenType, float[] returnValues) {
        switch (tweenType) {
            case XYZ:
                returnValues[0] = target.position.x;
                returnValues[1] = target.position.y;
                returnValues[2] = target.zoom;
                return 3;

            default: assert false; return -1;
        }
    }

    @Override
    public void setValues(OrthographicCamera target, int tweenType, float[] newValues) {
        switch (tweenType) {
            case XYZ:
                target.position.x = newValues[0];
                target.position.y = newValues[1];
                target.zoom = newValues[2];
                break;
            default: assert false;
        }
    }

}
