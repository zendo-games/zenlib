package zendo.games.zenlib.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class Utils {

    public static String intToDollarString(int money) {
        final String commaRegex = "(\\d)(?=(\\d{3})+$)";
        String source = "$" + Integer.toString(money, 10);
        String moneyString = source.replaceAll(commaRegex, "$1,");
        return moneyString;
    }

    public static Color hsvToRgb(float hue, float saturation, float value, Color outColor) {
        if (outColor == null) outColor = new Color();
        while (hue < 0) hue += 10f;
        hue = hue % 1f;
        int h = (int) (hue * 6);
        h = h % 6;
        float f = hue * 6 - h;
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);

        switch (h) {
            case 0: outColor.set(value, t, p, 1f); break;
            case 1: outColor.set(q, value, p, 1f); break;
            case 2: outColor.set(p, value, t, 1f); break;
            case 3: outColor.set(p, q, value, 1f); break;
            case 4: outColor.set(t, p, value, 1f); break;
            case 5: outColor.set(value, p, q, 1f); break;
            default: throw new GdxRuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        }
        return outColor;
    }

    /**
     * Returns a smooth step where value < edge1 = 0 value > edge = 1 and smooth between
     */
    public static float smoothStep(float edge0, float edge1, float value) {
        float x = MathUtils.clamp((value-edge0)/ (edge1 - edge0), 0, 1);
        return x * x * (3 - 2 * x);
    }

    public static boolean overlaps(Polygon polygon, Circle circle) {
        float[] vertices = polygon.getTransformedVertices();
        Vector2 center=new Vector2(circle.x, circle.y);
        float squareRadius=circle.radius*circle.radius;
        for (int i=0;i<vertices.length;i+=2){
            if (i==0){
                if (Intersector.intersectSegmentCircle(new Vector2(vertices[vertices.length - 2], vertices[vertices.length - 1]), new Vector2(vertices[i], vertices[i + 1]), center, squareRadius))
                    return true;
            } else {
                if (Intersector.intersectSegmentCircle(new Vector2(vertices[i-2], vertices[i-1]), new Vector2(vertices[i], vertices[i+1]), center, squareRadius))
                    return true;
            }
        }
        return polygon.contains(circle.x, circle.y);
    }

    public static TextureRegion getColoredTextureRegion(Color color) {
        Pixmap pixMap = new Pixmap(30, 30, Pixmap.Format.RGBA8888);
        pixMap.setColor(color);
        pixMap.fill();
        TextureRegion textureRegion = new TextureRegion(new Texture(pixMap));
        pixMap.dispose();
        return textureRegion;
    }


}
