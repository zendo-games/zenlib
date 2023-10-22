package zendo.games.zenlib.utils.data;

import com.badlogic.gdx.math.Rectangle;

public class RectI {

    public int x, y, w, h;

    public RectI() {
        this(0, 0, 0, 0);
    }

    public RectI(Rectangle rect) {
        this((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height);
    }

    public RectI(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public int left() {
        return x;
    }

    public int right() {
        return x + w;
    }

    public int top() {
        return y + h;
    }

    public int bottom() {
        return y;
    }

}
