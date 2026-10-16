package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.utils.GdxRuntimeException;

import static com.zendogames.zenlib.collision.shapes.CollisionGrid.CellShape;

/**
 * How a Tiled tile says which {@link CellShape} it is: a {@code shape} tile property holding one
 * of {@link #NAMES} (case-insensitive, {@code FULL} when absent), then the cell's flips and
 * rotation applied so one ramp tile flipped or rotated in Tiled collides the way it looks.
 */
public final class TileShapes {

    private static final String TAG = TileShapes.class.getSimpleName();

    public static final String PROPERTY = "shape";

    /** Accepted {@code shape} values; {@code none} is an alias for {@code empty}. */
    public static final String NAMES = "full, empty, none, ramp_ne, ramp_nw, ramp_se, ramp_sw";

    private TileShapes() {}

    /** Whether the tile carries a {@code shape} property at all (a {@code tagged} layer consults only these. */
    public static boolean has(TiledMapTile tile) {
        return (tile != null) && tile.getProperties().containsKey(PROPERTY);
    }

    /** The shape the cell contributes to a collision grid: its tile's shape, flipped and rotated as the cell is. */
    public static CellShape of(TiledMapTileLayer.Cell cell) {
        var tile = cell.getTile();
        if (tile == null) return CellShape.EMPTY;

        var shape = of(tile);
        shape = flip(shape, cell.getFlipHorizontally(), cell.getFlipVertically());
        return rotate(shape, cell.getRotation());
    }

    /** The tile's {@code shape} property, {@code FULL} when absent; throws naming the tile for an unknown value. */
    public static CellShape of(TiledMapTile tile) {
        var value = tile.getProperties().get(PROPERTY, null, String.class);
        if (value == null) return CellShape.FULL;

        try {
            return parse(value);
        } catch (GdxRuntimeException e) {
            throw new GdxRuntimeException(TAG + ": tile %d: %s".formatted(tile.getId(), e.getMessage()));
        }
    }

    public static CellShape parse(String value) {
        return switch (value.trim().toUpperCase()) {
            case "FULL"          -> CellShape.FULL;
            case "EMPTY", "NONE" -> CellShape.EMPTY;
            case "RAMP_NE"       -> CellShape.RAMP_NE;
            case "RAMP_NW"       -> CellShape.RAMP_NW;
            case "RAMP_SE"       -> CellShape.RAMP_SE;
            case "RAMP_SW"       -> CellShape.RAMP_SW;
            default -> throw new GdxRuntimeException("unknown shape '%s', expected one of: %s".formatted(value, NAMES));
        };
    }

    /** Mirror: horizontally swaps east and west, vertically swaps floor and ceiling. */
    static CellShape flip(CellShape shape, boolean horizontal, boolean vertical) {
        if (horizontal) {
            shape = switch (shape) {
                case RAMP_NE -> CellShape.RAMP_NW;
                case RAMP_NW -> CellShape.RAMP_NE;
                case RAMP_SE -> CellShape.RAMP_SW;
                case RAMP_SW -> CellShape.RAMP_SE;
                default      -> shape;
            };
        }
        if (vertical) {
            shape = switch (shape) {
                case RAMP_NE -> CellShape.RAMP_SE;
                case RAMP_SE -> CellShape.RAMP_NE;
                case RAMP_NW -> CellShape.RAMP_SW;
                case RAMP_SW -> CellShape.RAMP_NW;
                default      -> shape;
            };
        }
        return shape;
    }

    /** {@code Cell.ROTATE_90/180/270} are counter-clockwise quarter turns, applied after the flips like the renderer does. */
    static CellShape rotate(CellShape shape, int rotation) {
        for (int i = 0; i < rotation; i++) {
            shape = switch (shape) {
                case RAMP_NE -> CellShape.RAMP_SE; // lower-right triangle turns into the upper-right one
                case RAMP_SE -> CellShape.RAMP_SW;
                case RAMP_SW -> CellShape.RAMP_NW;
                case RAMP_NW -> CellShape.RAMP_NE;
                default      -> shape;
            };
        }
        return shape;
    }
}
