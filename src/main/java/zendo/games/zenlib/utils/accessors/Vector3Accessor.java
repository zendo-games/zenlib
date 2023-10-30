package zendo.games.zenlib.utils.accessors;

import aurelienribon.tweenengine.TweenAccessor;
import com.badlogic.gdx.math.Vector3;

/**
 * Brian Ploeckelman created on 3/10/2015.
 */
public class Vector3Accessor implements TweenAccessor<Vector3> {

    public static final int X = 1;
    public static final int Y = 2;
    public static final int Z = 3;
    public static final int XY = 4;
    public static final int XZ = 5;
    public static final int XYZ = 6;

    @Override
    public int getValues(Vector3 target, int tweenType, float[] returnValues) {
        switch (tweenType) {
            case X:
                returnValues[0] = target.x;
                return 1;
            case Y:
                returnValues[0] = target.y;
                return 1;
            case Z:
                returnValues[0] = target.z;
                return 1;
            case XY:
                returnValues[0] = target.x;
                returnValues[1] = target.y;
                return 2;
            case XZ:
                returnValues[0] = target.x;
                returnValues[1] = target.z;
                return 2;
            case XYZ:
                returnValues[0] = target.x;
                returnValues[1] = target.y;
                returnValues[2] = target.z;
                return 3;
            default:
                assert false;
                return -1;
        }
    }

    @Override
    public void setValues(Vector3 target, int tweenType, float[] newValues) {
        switch (tweenType) {
            case X:
                target.x = newValues[0];
                break;
            case Y:
                target.y = newValues[0];
                break;
            case Z:
                target.z = newValues[0];
                break;
            case XY:
                target.x = newValues[0];
                target.y = newValues[1];
                break;
            case XZ:
                target.x = newValues[0];
                target.z = newValues[1];
                break;
            case XYZ:
                target.x = newValues[0];
                target.y = newValues[1];
                target.z = newValues[2];
                break;
            default:
                assert false;
        }
    }
}
