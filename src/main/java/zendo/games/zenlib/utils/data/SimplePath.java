package zendo.games.zenlib.utils.data;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.BSpline;
import com.badlogic.gdx.math.CatmullRomSpline;
import com.badlogic.gdx.math.Path;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class SimplePath {

    public enum Type { catmull, bspline }

    private static final int NUM_DEBUG_POINTS = 100;
    private static final boolean CONTINUOUS_BY_DEFAULT = false;

    private Vector2[] debugPoints;
    private Vector2[] controlPoints;
    private Vector2 output;
    private Path<Vector2> path;
    private Type type;

    public SimplePath(Vector2... controlPoints) {
        this(CONTINUOUS_BY_DEFAULT, controlPoints);
    }

    public SimplePath(boolean isContinuous, Vector2... controlPoints) {
        this(isContinuous, Type.catmull, controlPoints);
    }

    public SimplePath(boolean isContinuous, Type type, Vector2... controlPoints) {
        this.debugPoints = null;
        this.controlPoints = controlPoints;
        this.output = new Vector2();
        this.type = type;

        if (type == Type.catmull) {
            this.path = new CatmullRomSpline<>(this.controlPoints, isContinuous);
        } else if (type == Type.bspline) {
            this.path = new BSpline<>(this.controlPoints, 3, isContinuous);
        } else {
            throw new GdxRuntimeException("Path type must be either catmull or bspline");
        }
    }

    public SimplePath(float... controlPoints) {
        this(CONTINUOUS_BY_DEFAULT, controlPoints);
    }

    public SimplePath(boolean isContinuous, float... controlPoints) {
        this(isContinuous, Type.catmull, controlPoints);
    }

    public SimplePath(boolean isContinuous, Type type, float... controlPoints) {
        if (controlPoints.length % 2 != 0) {
            throw new GdxRuntimeException("Path control points array must have an even number of elements (x0, y0, x1, y1, ..., xN, yN)");
        }

        this.controlPoints = new Vector2[controlPoints.length / 2];
        for (int i = 0; i < controlPoints.length; i += 2) {
            this.controlPoints[i/2] = new Vector2(controlPoints[i], controlPoints[i+1]);
        }
        this.output = new Vector2();

        this.type = type;
        if (type == Type.catmull) {
            this.path = new CatmullRomSpline<>(this.controlPoints, isContinuous);
        } else if (type == Type.bspline) {
            this.path = new BSpline<>(this.controlPoints, 3, isContinuous);
        } else {
            throw new GdxRuntimeException("Path type must be either catmull or bspline");
        }
    }

    public Vector2 valueAt(float t) {
        return path.valueAt(output, t);
    }

    public Vector2 valueAt(Vector2 out, float t) {
        return path.valueAt(out, t);
    }

    public Vector2 derivativeAt(float t) {
        return path.derivativeAt(output, t);
    }

    public Vector2 derivativeAt(Vector2 out, float t) {
        return path.derivativeAt(out,t);
    }

    public float locate(Vector2 v) {
        return path.locate(v);
    }

    // NOTE: needed for arc-length approximation, only available for BSpline and CatmullRom
    public int spanCount() {
        if (type == Type.catmull) {
            return ((CatmullRomSpline<Vector2>) path).spanCount;
        } else if (type == Type.bspline) {
            return ((BSpline<Vector2>) path).spanCount;
        } else {
            throw new GdxRuntimeException("Path type must be either catmull or bspline");
        }
    }

    public void generateDebugPoints(int numPoints) {
        debugPoints = new Vector2[numPoints];
        for (int i = 0; i < numPoints; ++i) {
            debugPoints[i] = new Vector2();
            path.valueAt(debugPoints[i], ((float) i) / ((float) (numPoints - 1)));
        }
    }

    public void debugRender(ShapeRenderer shapeRenderer) {
        if (debugPoints == null) {
            generateDebugPoints(NUM_DEBUG_POINTS);
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        {
            shapeRenderer.setColor(Color.GREEN);
            for (int i = 0; i < debugPoints.length - 1; ++i) {
                shapeRenderer.line(debugPoints[i+0], debugPoints[i+1]);
            }
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        {
            shapeRenderer.setColor(Color.YELLOW);
            for (Vector2 controlPoint : controlPoints) {
                shapeRenderer.circle(controlPoint.x, controlPoint.y, 10f);
            }
        }
        shapeRenderer.end();

        shapeRenderer.setColor(Color.WHITE);
    }

}
