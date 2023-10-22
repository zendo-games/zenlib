package zendo.games.zenlib.utils.accessors;

import aurelienribon.tweenengine.TweenAccessor;
import com.badlogic.gdx.graphics.PerspectiveCamera;

public class PerspectiveCameraAccessor implements TweenAccessor<PerspectiveCamera> {

    public static final int POS = 1;
    public static final int DIR = 2;
    public static final int UP = 3;
    public static final int POS_DIR = 4;
    public static final int POS_UP = 5;
    public static final int POS_DIR_UP = 6;

    @Override
    public int getValues(PerspectiveCamera target, int tweenType, float[] returnValues) {
        switch (tweenType) {
            case POS:
                returnValues[0] = target.position.x;
                returnValues[1] = target.position.y;
                returnValues[2] = target.position.z;
                return 3;
            case DIR:
                returnValues[0] = target.direction.x;
                returnValues[1] = target.direction.y;
                returnValues[2] = target.direction.z;
                return 3;
            case UP:
                returnValues[0] = target.up.x;
                returnValues[1] = target.up.y;
                returnValues[2] = target.up.z;
                return 3;
            case POS_DIR:
                returnValues[0] = target.position.x;
                returnValues[1] = target.position.y;
                returnValues[2] = target.position.z;
                returnValues[3] = target.direction.x;
                returnValues[4] = target.direction.y;
                returnValues[5] = target.direction.z;
                return 6;
            case POS_UP:
                returnValues[0] = target.position.x;
                returnValues[1] = target.position.y;
                returnValues[2] = target.position.z;
                returnValues[3] = target.up.x;
                returnValues[4] = target.up.y;
                returnValues[5] = target.up.z;
                return 6;
            case POS_DIR_UP:
                returnValues[0] = target.position.x;
                returnValues[1] = target.position.y;
                returnValues[2] = target.position.z;
                returnValues[3] = target.direction.x;
                returnValues[4] = target.direction.y;
                returnValues[5] = target.direction.z;
                returnValues[6] = target.up.x;
                returnValues[7] = target.up.y;
                returnValues[8] = target.up.z;
                return 9;

            default: assert false; return -1;
        }
    }

    @Override
    public void setValues(PerspectiveCamera target, int tweenType, float[] newValues) {
        switch (tweenType) {
            case POS:
                target.position.x = newValues[0];
                target.position.y = newValues[1];
                target.position.z = newValues[2];
                break;
            case DIR:
                target.direction.x = newValues[0];
                target.direction.y = newValues[1];
                target.direction.z = newValues[2];
                break;
            case UP:
                target.up.x = newValues[0];
                target.up.y = newValues[1];
                target.up.z = newValues[2];
                break;
            case POS_DIR:
                target.position.x = newValues[0];
                target.position.y = newValues[1];
                target.position.z = newValues[2];
                target.direction.x = newValues[3];
                target.direction.y = newValues[4];
                target.direction.z = newValues[5];
                break;
            case POS_UP:
                target.position.x = newValues[0];
                target.position.y = newValues[1];
                target.position.z = newValues[2];
                target.up.x = newValues[3];
                target.up.y = newValues[4];
                target.up.z = newValues[5];
                break;
            case POS_DIR_UP:
                target.position.x = newValues[0];
                target.position.y = newValues[1];
                target.position.z = newValues[2];
                target.direction.x = newValues[3];
                target.direction.y = newValues[4];
                target.direction.z = newValues[5];
                target.up.x = newValues[6];
                target.up.y = newValues[7];
                target.up.z = newValues[8];
                break;
            default: assert false;
        }
    }

}
