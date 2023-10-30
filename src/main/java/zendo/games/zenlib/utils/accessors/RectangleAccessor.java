package zendo.games.zenlib.utils.accessors;

import aurelienribon.tweenengine.TweenAccessor;
import com.badlogic.gdx.math.Rectangle;

public class RectangleAccessor implements TweenAccessor<Rectangle> {

    public static final int X = 1;
    public static final int Y = 2;
    public static final int W = 3;
    public static final int H = 4;
    public static final int XY = 5;
    public static final int WH = 6;
    public static final int XYWH = 7;

    @Override
    public int getValues(Rectangle target, int tweenType, float[] returnValues) {
        switch (tweenType) {
            case X:
                returnValues[0] = target.x;
                return 1;
            case Y:
                returnValues[0] = target.y;
                return 1;
            case W:
                returnValues[0] = target.width;
                return 1;
            case H:
                returnValues[0] = target.height;
                return 1;
            case XY:
                returnValues[0] = target.x;
                returnValues[1] = target.y;
                return 2;
            case WH:
                returnValues[0] = target.getWidth();
                returnValues[1] = target.getHeight();
                return 2;
            case XYWH:
                returnValues[0] = target.x;
                returnValues[1] = target.y;
                returnValues[2] = target.width;
                returnValues[3] = target.height;
                return 4;
            default:
                assert false;
                return -1;
        }
    }

    @Override
    public void setValues(Rectangle target, int tweenType, float[] newValues) {
        switch (tweenType) {
            case X:
                target.x = newValues[0];
                break;
            case Y:
                target.y = newValues[0];
                break;
            case W:
                target.width = newValues[0];
                break;
            case H:
                target.height = newValues[0];
                break;
            case XY:
                target.x = newValues[0];
                target.y = newValues[1];
                break;
            case WH:
                target.width = newValues[0];
                target.height = newValues[1];
                break;
            case XYWH:
                target.x = newValues[0];
                target.y = newValues[1];
                target.width = newValues[2];
                target.height = newValues[3];
                break;
            default:
                assert false;
        }
    }
}
