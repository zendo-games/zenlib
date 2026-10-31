package com.zendogames.zenlib.tilemap;

import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.Test;

import static com.zendogames.zenlib.collision.shapes.CollisionGrid.CellShape.*;
import static com.zendogames.zenlib.tilemap.TestMaps.cell;
import static com.zendogames.zenlib.tilemap.TestMaps.tile;
import static org.junit.Assert.*;

public class TileShapesTest {

    @Test
    public void parsesEveryNameIgnoringCaseAndWhitespace() {
        assertEquals(FULL,    TileShapes.parse("full"));
        assertEquals(FULL,    TileShapes.parse(" Full "));
        assertEquals(EMPTY,   TileShapes.parse("empty"));
        assertEquals(EMPTY,   TileShapes.parse("none"));
        assertEquals(RAMP_NE, TileShapes.parse("ramp_ne"));
        assertEquals(RAMP_NW, TileShapes.parse("RAMP_NW"));
        assertEquals(RAMP_SE, TileShapes.parse("ramp_se"));
        assertEquals(RAMP_SW, TileShapes.parse("ramp_sw"));
    }

    @Test
    public void unknownValueNamesTheTile() {
        var e = assertThrows(GdxRuntimeException.class, () -> TileShapes.of(tile(7, "slope")));
        assertTrue(e.getMessage(), e.getMessage().contains("tile 7"));
        assertTrue(e.getMessage(), e.getMessage().contains("'slope'"));
        assertTrue(e.getMessage(), e.getMessage().contains("ramp_ne"));
    }

    @Test
    public void tileWithoutThePropertyIsFull_cellWithoutTileIsEmpty() {
        assertEquals(FULL,  TileShapes.of(tile(1, null)));
        assertEquals(FULL,  TileShapes.of(cell(tile(1, null))));
        assertEquals(EMPTY, TileShapes.of(new Cell()));
    }

    @Test
    public void flipsMirrorTheRamp() {
        assertEquals(RAMP_NW, TileShapes.flip(RAMP_NE, true,  false));
        assertEquals(RAMP_SW, TileShapes.flip(RAMP_SE, true,  false));
        assertEquals(RAMP_SE, TileShapes.flip(RAMP_NE, false, true));
        assertEquals(RAMP_SW, TileShapes.flip(RAMP_NW, false, true));
        assertEquals(RAMP_SW, TileShapes.flip(RAMP_NE, true,  true));
        assertEquals(FULL,    TileShapes.flip(FULL,    true,  true));
        assertEquals(EMPTY,   TileShapes.flip(EMPTY,   true,  false));
    }

    @Test
    public void rotationsAreCounterClockwiseQuarterTurns() {
        assertEquals(RAMP_SE, TileShapes.rotate(RAMP_NE, Cell.ROTATE_90));
        assertEquals(RAMP_SW, TileShapes.rotate(RAMP_NE, Cell.ROTATE_180));
        assertEquals(RAMP_NW, TileShapes.rotate(RAMP_NE, Cell.ROTATE_270));
        assertEquals(RAMP_NE, TileShapes.rotate(RAMP_NW, Cell.ROTATE_90));
        assertEquals(FULL,    TileShapes.rotate(FULL,    Cell.ROTATE_90));
    }

    @Test
    public void cellAppliesFlipsThenRotationLikeTheRenderer() {
        var ramp = tile(3, "ramp_ne");
        assertEquals(RAMP_NE, TileShapes.of(cell(ramp, false, false, Cell.ROTATE_0)));
        assertEquals(RAMP_NW, TileShapes.of(cell(ramp, true,  false, Cell.ROTATE_0)));
        assertEquals(RAMP_SE, TileShapes.of(cell(ramp, false, false, Cell.ROTATE_90)));
        assertEquals(RAMP_NE, TileShapes.of(cell(ramp, true,  false, Cell.ROTATE_90))); // NW after the flip, then one turn: lower-left becomes lower-right
    }
}
