package zendo.games.zenlib.utils.accessors;

import aurelienribon.tweenengine.TweenAccessor;
import com.badlogic.gdx.math.Circle;

public class CircleAccessor implements TweenAccessor<Circle> {

    public static final int X = 1;
    public static final int Y = 2;
    public static final int XY = 3;
    public static final int RADIUS = 4;
    public static final int XY_RADIUS = 5;

    @Override
    public int getValues(Circle target, int tweenType, float[] returnValues) {
        switch (tweenType) {
            case X:
                returnValues[0] = target.x;
                return 1;
            case Y:
                returnValues[0] = target.y;
                return 1;
            case XY:
                returnValues[0] = target.x;
                returnValues[1] = target.y;
                return 2;
            case RADIUS:
                returnValues[0] = target.radius;
                return 1;
            case XY_RADIUS:
                returnValues[0] = target.x;
                returnValues[1] = target.y;
                returnValues[2] = target.radius;
                return 3;
            default:
                assert false;
                return -1;
        }
    }

    @Override
    public void setValues(Circle target, int tweenType, float[] newValues) {
        switch (tweenType) {
            case X:
                target.x = newValues[0];
                break;
            case Y:
                target.y = newValues[0];
                break;
            case XY:
                target.x = newValues[0];
                target.y = newValues[1];
                break;
            case RADIUS:
                target.radius = newValues[0];
                break;
            case XY_RADIUS:
                target.x = newValues[0];
                target.y = newValues[1];
                target.radius = newValues[1];
                break;
            default:
                assert false;
        }
    }
}
