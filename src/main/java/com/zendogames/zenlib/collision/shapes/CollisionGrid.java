package com.zendogames.zenlib.collision.shapes;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Shape2D;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointI2;
import com.github.tommyettinger.gdcrux.PointI4;
import com.zendogames.zenlib.FramePool;

import java.util.Arrays;

/**
 * Grid of {@link CellShape}s {@code CollisionShape}: the usual tilemap collision layer. Cell
 * {@code (0, 0)} is at the entity position and rows go up. A grid is never a mover: it is only
 * ever the {@code that} side of an overlap test, so {@link #overlaps} throws.
 *
 * @param cellSize width and height of every cell, pixels
 * @param cols     number of columns
 * @param rows     number of rows
 * @param cells    {@code cols * rows} shapes, row-major from the bottom-left
 */
public record CollisionGrid(
        int cellSize,
        int cols,
        int rows,
        CellShape[] cells
) implements CollisionShape, Shape2D {

    private static final String TAG = CollisionGrid.class.getSimpleName();

    /**
     * Per-cell solid region. {@link #FULL} is the whole cell; the ramp variants are 45-degree
     * triangles, named by the corner of the cell that is solid: {@link #RAMP_NE} / {@link #RAMP_NW}
     * are floor ramps (walk up the slope), {@link #RAMP_SE} / {@link #RAMP_SW} are ceiling
     * overhangs (the slope hangs down from the top edge).
     */
    public enum CellShape {
        /** Air, not solid. */
        EMPTY(0),
        /** Whole cell solid. */
        FULL(0),
        /** Lower-right half (vertices BL, BR, TR). Hypotenuse rises from bottom-left to top-right: walk right, climb up. */
        RAMP_NE(+1),
        /** Lower-left half (vertices BL, BR, TL). Hypotenuse rises from bottom-right to top-left: walk left, climb up. */
        RAMP_NW(-1),
        /** Upper-right half (vertices TL, TR, BR). Ceiling overhang sloping down and to the right. */
        RAMP_SE(0),
        /** Upper-left half (vertices TL, TR, BL). Ceiling overhang sloping down and to the left. */
        RAMP_SW(0);

        /** Sign of the walk-uphill direction: {@code +1} for RAMP_NE, {@code -1} for RAMP_NW, {@code 0} otherwise. */
        private final int rampDir;

        CellShape(int rampDir) { this.rampDir = rampDir; }

        public int rampDir() { return rampDir; }

        public boolean isSolid()       { return this != EMPTY; }
        public boolean isFullSolid()   { return this == FULL; }
        public boolean isRamp()        { return this == RAMP_NE || this == RAMP_NW || this == RAMP_SE || this == RAMP_SW; }
        /** Floor ramps only; gates slide-on-ramp so a ceiling overhang never produces horizontal drift. */
        public boolean isFloorRamp()   { return this == RAMP_NE || this == RAMP_NW; }
        public boolean isCeilingRamp() { return this == RAMP_SE || this == RAMP_SW; }

        /**
         * True when the point at cell-local {@code (x, y)} is inside this shape's solid region;
         * edges count as inside. {@code size} is the cell size.
         */
        public boolean contains(float x, float y, float size) {
            if (x < 0 || x > size || y < 0 || y > size) return false;
            return switch (this) {
                case EMPTY   -> false;
                case FULL    -> true;
                case RAMP_NE -> x >= y;             // on/below the hypotenuse rising left to right
                case RAMP_NW -> (size - x) >= y;    // on/below the hypotenuse rising right to left
                case RAMP_SE -> x + y >= size;      // on/above the hypotenuse falling left to right
                case RAMP_SW -> y >= x;             // on/above the hypotenuse falling right to left
            };
        }
    }

    /** An all-{@link CellShape#EMPTY} grid. */
    public CollisionGrid(int cellSize, int cols, int rows) {
        this(cellSize, cols, rows, new CellShape[cols * rows]);
        Arrays.fill(cells, CellShape.EMPTY);
    }

    public CollisionGrid {
        if (cellSize <= 0 || cols <= 0 || rows <= 0) throw new GdxRuntimeException(TAG + ": cellSize, cols and rows must be positive");
        if (cells.length != cols * rows) throw new GdxRuntimeException(TAG + ": cells must hold cols * rows entries");
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < cols && y >= 0 && y < rows;
    }

    public void set(int x, int y, boolean solid) {
        set(x, y, solid ? CellShape.FULL : CellShape.EMPTY);
    }

    public void set(int x, int y, CellShape shape) {
        if (!inBounds(x, y)) throw new GdxRuntimeException(TAG + ": set(%d, %d) is out of bounds".formatted(x, y));
        cells[x + y * cols] = shape;
    }

    public CellShape get(int x, int y) {
        if (!inBounds(x, y)) throw new GdxRuntimeException(TAG + ": get(%d, %d) is out of bounds".formatted(x, y));
        return cells[x + y * cols];
    }

    @Override
    public Shape2D shape2d() {
        return this;
    }

    @Override public float left(PointI2 position)   { return position.x; }
    @Override public float right(PointI2 position)  { return position.x + cols * cellSize; }
    @Override public float top(PointI2 position)    { return position.y + rows * cellSize; }
    @Override public float bottom(PointI2 position) { return position.y; }

    @Override
    public boolean overlaps(CollisionShape that, PointI2 thisPos, PointI2 thatPos, PointI2 offset) {
        throw new GdxRuntimeException(TAG + ": a grid is never a mover, test the other shape against the grid instead");
    }

    /**
     * Cell walk (DDA) from where the ray enters the grid: a {@link CellShape#FULL} cell is hit where
     * the ray enters it, a ramp cell at its entry point when that lies in the solid half, else where
     * the ray crosses the hypotenuse inside the cell; EMPTY cells are stepped over. Edges count, so a
     * ray grazing a solid cell's corner reports it. Stops at {@code range} or on leaving the grid.
     */
    @Override
    public float raycast(float ox, float oy, float dirX, float dirY, float range, PointI2 pos) {
        float t = CollisionRect.raycastBox(ox, oy, dirX, dirY, range, left(pos), bottom(pos), right(pos), top(pos));
        if (t < 0f) return -1f;

        // The cell holding the entry point; clamped, since a ray entering across the far edge lands exactly on it
        int cx = MathUtils.clamp(MathUtils.floor((ox + dirX * t - pos.x) / cellSize), 0, cols - 1);
        int cy = MathUtils.clamp(MathUtils.floor((oy + dirY * t - pos.y) / cellSize), 0, rows - 1);

        // Per axis: which way the walk steps, the distance between boundaries, and the distance to the next one
        int   stepX  = (dirX > 0f) ? 1 : (dirX < 0f) ? -1 : 0;
        int   stepY  = (dirY > 0f) ? 1 : (dirY < 0f) ? -1 : 0;
        float deltaX = (stepX == 0) ? Float.POSITIVE_INFINITY : cellSize / Math.abs(dirX);
        float deltaY = (stepY == 0) ? Float.POSITIVE_INFINITY : cellSize / Math.abs(dirY);
        float nextX  = (stepX == 0) ? Float.POSITIVE_INFINITY : (pos.x + (cx + Math.max(stepX, 0)) * cellSize - ox) / dirX;
        float nextY  = (stepY == 0) ? Float.POSITIVE_INFINITY : (pos.y + (cy + Math.max(stepY, 0)) * cellSize - oy) / dirY;

        while (inBounds(cx, cy) && t <= range) {
            var shape = cells[cx + cy * cols];
            if (shape == CellShape.FULL) return t;
            if (shape != CellShape.EMPTY) {
                float exit = Math.min(nextX, nextY);
                float ex   = ox + dirX * t - (pos.x + cx * cellSize);
                float ey   = oy + dirY * t - (pos.y + cy * cellSize);
                float hit  = raycastRamp(shape, ex, ey, dirX, dirY, exit - t);
                if (hit >= 0f && t + hit <= range) return t + hit;
            }
            if (nextX < nextY) { t = nextX; nextX += deltaX; cx += stepX; }
            else               { t = nextY; nextY += deltaY; cy += stepY; }
        }
        return -1f;
    }

    /**
     * Unit ray from cell-local {@code (ex, ey)}, a point on the cell's boundary or inside it, against
     * a ramp's solid triangle within {@code span} (the distance to where the ray leaves the cell):
     * {@code 0} when the start is already in the solid half (its hypotenuse included), else the
     * crossing of the hypotenuse, or {@code -1}.
     */
    public float raycastRamp(CellShape shape, float ex, float ey, float dirX, float dirY, float span) {
        // Signed distance to the hypotenuse, positive on the solid side, and its rate of change along the ray
        float f, df;
        switch (shape) {
            case RAMP_NE -> { f = ex - ey;            df = dirX - dirY; }   // solid where x >= y
            case RAMP_NW -> { f = cellSize - ex - ey; df = -dirX - dirY; }  // solid where x + y <= size
            case RAMP_SE -> { f = ex + ey - cellSize; df = dirX + dirY; }   // solid where x + y >= size
            case RAMP_SW -> { f = ey - ex;            df = dirY - dirX; }   // solid where y >= x
            default -> throw new GdxRuntimeException(TAG + ": " + shape + " is not a ramp");
        }
        if (f >= 0f) return 0f;             // starts in the solid half
        if (df <= 0f) return -1f;           // parallel to the hypotenuse or moving away from it
        float u = -f / df;                  // f + u * df == 0
        return (u <= span) ? u : -1f;
    }

    /** (pooled) The whole grid's bounds at the entity's world position. */
    public Rectangle bounds(PointI2 position) {
        return FramePool.rect(position.x, position.y, cols * cellSize, rows * cellSize);
    }

    /** (pooled) One cell's bounds at the entity's world position. */
    public Rectangle cellRect(int x, int y, PointI2 position) {
        return FramePool.rect(position.x + x * cellSize, position.y + y * cellSize, cellSize, cellSize);
    }

    /**
     * (pooled) The cells a world-space AABB can touch, as {@code x} = first column, {@code z} = one
     * past the last column, {@code y} = first row, {@code w} = one past the last row. Empty
     * ({@code x >= z} or {@code y >= w}) when the AABB misses the grid or only touches its edge.
     */
    public PointI4 cellRange(Rectangle aabb, PointI2 position) {
        float relX = aabb.x - position.x;
        float relY = aabb.y - position.y;
        int left   = MathUtils.clamp(MathUtils.floor(relX / cellSize), 0, cols);
        int right  = MathUtils.clamp(MathUtils.ceil((relX + aabb.width) / cellSize), 0, cols);
        int bottom = MathUtils.clamp(MathUtils.floor(relY / cellSize), 0, rows);
        int top    = MathUtils.clamp(MathUtils.ceil((relY + aabb.height) / cellSize), 0, rows);
        return FramePool.pi4(left, bottom, right, top);
    }

    @Override
    public boolean contains(Vector2 point) {
        return contains(point.x, point.y);
    }

    /** True when the grid-local point is inside a solid cell's region. */
    @Override
    public boolean contains(float x, float y) {
        int cx = MathUtils.floor(x / cellSize);
        int cy = MathUtils.floor(y / cellSize);
        if (!inBounds(cx, cy)) return false;
        return get(cx, cy).contains(x - cx * cellSize, y - cy * cellSize, cellSize);
    }
}
