package com.zendogames.zenlib.collision.shapes;

import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.shapes.CollisionAASeg.Axis;
import com.zendogames.zenlib.collision.shapes.CollisionAASeg.Facing;
import com.zendogames.zenlib.collision.shapes.CollisionGrid.CellShape;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Per-shape ray math: the distance along a unit ray to where it first enters the shape, {@code -1}
 * for a miss within range. Edges count as hits, an origin inside hits at zero, the range is
 * inclusive. Positions are world, shapes entity-local, as for {@code overlaps}.
 */
public class ShapeRaycastTest {

    private static final float EPS  = 1e-4f;
    private static final float DIAG = (float) Math.sqrt(0.5);   // a unit diagonal's components
    private static final double ROOT2 = Math.sqrt(2);

    private static PointI2 at(int x, int y) { return new PointI2(x, y); }

    // ------------------------------------------------------------------------
    // Rect
    // ------------------------------------------------------------------------

    @Test
    public void rectFromEachSideAndPastIt() {
        var rect = new CollisionRect(0, 0, 10, 10);   // at (20, 0): x 20..30, y 0..10
        assertEquals(15, rect.raycast(5, 5, 1, 0, 100, at(20, 0)), EPS);      // from the left
        assertEquals(10, rect.raycast(40, 5, -1, 0, 100, at(20, 0)), EPS);    // from the right
        assertEquals(20, rect.raycast(25, 30, 0, -1, 100, at(20, 0)), EPS);   // from above
        assertEquals(5,  rect.raycast(25, -5, 0, 1, 100, at(20, 0)), EPS);    // from below
        assertEquals(-1, rect.raycast(5, 15, 1, 0, 100, at(20, 0)), 0);       // passes above it
        assertEquals(-1, rect.raycast(5, 5, -1, 0, 100, at(20, 0)), 0);       // pointing away
    }

    @Test
    public void rectEdgesCornersAndInsideCount() {
        var rect = new CollisionRect(0, 0, 10, 10);
        assertEquals(0,  rect.raycast(25, 5, 1, 0, 100, at(20, 0)), 0);       // origin inside
        assertEquals(15, rect.raycast(5, 10, 1, 0, 100, at(20, 0)), EPS);     // grazing the top edge
        assertEquals(15, rect.raycast(5, 0, 1, 0, 100, at(20, 0)), EPS);      // grazing the bottom edge
        assertEquals(10 * ROOT2, rect.raycast(10, 20, DIAG, -DIAG, 100, at(20, 0)), 1e-3);   // through the top-left corner
    }

    @Test
    public void rectDiagonalEntersTheNearerSlab() {
        var rect = new CollisionRect(0, 0, 10, 10);   // at (20, 15): x 20..30, y 15..25
        // Along y = x the left edge (x 20) is reached before the bottom edge (y 15): entry at (20, 20)
        assertEquals(20 * ROOT2, rect.raycast(0, 0, DIAG, DIAG, 100, at(20, 15)), 1e-3);
    }

    @Test
    public void rectRangeIsInclusive() {
        var rect = new CollisionRect(0, 0, 10, 10);
        assertEquals(15, rect.raycast(5, 5, 1, 0, 15, at(20, 0)), EPS);
        assertEquals(-1, rect.raycast(5, 5, 1, 0, 14.9f, at(20, 0)), 0);
    }

    // ------------------------------------------------------------------------
    // Circle
    // ------------------------------------------------------------------------

    @Test
    public void circleHeadOnTangentAndBeside() {
        var circ = new CollisionCirc(0, 0, 5);        // at (20, 0): center (20, 0), r 5
        assertEquals(10, circ.raycast(5, 0, 1, 0, 100, at(20, 0)), EPS);        // head on: 15 to the center minus r
        assertEquals(15, circ.raycast(5, 5, 1, 0, 100, at(20, 0)), 1e-3);       // tangent at the top, (20, 5)
        assertEquals(-1, circ.raycast(5, 5.1f, 1, 0, 100, at(20, 0)), 0);       // just above the tangent
        assertEquals(15, circ.raycast(20, 20, 0, -1, 100, at(20, 0)), EPS);     // from above
        assertEquals(0,  circ.raycast(22, 1, 1, 0, 100, at(20, 0)), 0);         // origin inside
        assertEquals(-1, circ.raycast(5, 0, -1, 0, 100, at(20, 0)), 0);         // pointing away
        assertEquals(-1, circ.raycast(5, 0, 1, 0, 9.9f, at(20, 0)), 0);         // out of range
        assertEquals(10, circ.raycast(5, 0, 1, 0, 10, at(20, 0)), EPS);         // range inclusive
    }

    // ------------------------------------------------------------------------
    // Capsule
    // ------------------------------------------------------------------------

    @Test
    public void capsuleSidesCapsAndRoundedCorners() {
        var caps = new CollisionCaps(0, 0, 10, 20);   // vertical, r 5, at (20, 0): x 20..30, y 0..20, caps centered (25, 5) and (25, 15)
        assertEquals(15, caps.raycast(5, 10, 1, 0, 100, at(20, 0)), EPS);       // the straight side
        assertEquals(10, caps.raycast(25, -10, 0, 1, 100, at(20, 0)), EPS);     // bottom cap from below, y 0
        assertEquals(10, caps.raycast(25, 30, 0, -1, 100, at(20, 0)), EPS);     // top cap from above, y 20
        assertEquals(-1, caps.raycast(5, 20.5f, 1, 0, 100, at(20, 0)), 0);      // just over the top

        // Where a rect of the same bounds would be hit at its edge (x 20), the cap is farther in
        assertEquals(20, caps.raycast(5, 0, 1, 0, 100, at(20, 0)), 1e-3);                      // along the bottom: tangent at (25, 0)
        assertEquals(25 - Math.sqrt(25 - 4.5 * 4.5) - 5, caps.raycast(5, 0.5f, 1, 0, 100, at(20, 0)), 1e-3);  // half a pixel up
    }

    @Test
    public void horizontalCapsule() {
        var caps = new CollisionCaps(CollisionCaps.Axis.HORIZONTAL, 0, 0, 20, 10);   // at (0, 0): r 5, caps centered (5, 5) and (15, 5)
        assertEquals(10, caps.raycast(-10, 5, 1, 0, 100, at(0, 0)), EPS);       // left cap head on, x 0
        assertEquals(20, caps.raycast(10, 30, 0, -1, 100, at(0, 0)), EPS);      // the straight top, y 10
        assertEquals(-1, caps.raycast(-10, 0, -1, 0, 100, at(0, 0)), 0);        // pointing away
    }

    // ------------------------------------------------------------------------
    // Segment
    // ------------------------------------------------------------------------

    @Test
    public void segmentIsSeenFromEitherSideWithinItsSpan() {
        var seg = new CollisionAASeg(Axis.HORIZONTAL, 0, 0, 40, Facing.POSITIVE);   // at (10, 20): y 20, x 10..50
        assertEquals(10, seg.raycast(30, 30, 0, -1, 100, at(10, 20)), EPS);     // from above
        assertEquals(15, seg.raycast(30, 5, 0, 1, 100, at(10, 20)), EPS);       // from below: not the one-way rule
        assertEquals(10, seg.raycast(50, 30, 0, -1, 100, at(10, 20)), EPS);     // the endpoint counts
        assertEquals(-1, seg.raycast(60, 30, 0, -1, 100, at(10, 20)), 0);       // past the end
        assertEquals(10 * ROOT2, seg.raycast(20, 30, DIAG, -DIAG, 100, at(10, 20)), 1e-3);   // diagonal, crossing at (30, 20)
        assertEquals(-1, seg.raycast(30, 30, 0, -1, 9.9f, at(10, 20)), 0);      // out of range
    }

    @Test
    public void segmentParallelAndCollinear() {
        var seg = new CollisionAASeg(Axis.HORIZONTAL, 0, 0, 40, Facing.POSITIVE);
        assertEquals(-1, seg.raycast(0, 25, 1, 0, 100, at(10, 20)), 0);         // parallel, off the line
        assertEquals(10, seg.raycast(0, 20, 1, 0, 100, at(10, 20)), EPS);       // along the line: the near endpoint, x 10
        assertEquals(10, seg.raycast(60, 20, -1, 0, 100, at(10, 20)), EPS);     // from the other end, x 50
        assertEquals(0,  seg.raycast(30, 20, 1, 0, 100, at(10, 20)), 0);        // starting on it
        assertEquals(-1, seg.raycast(60, 20, 1, 0, 100, at(10, 20)), 0);        // along the line, moving away
    }

    @Test
    public void verticalSegment() {
        var seg = new CollisionAASeg(Axis.VERTICAL, 0, 0, 40, Facing.NEGATIVE);   // at (30, 0): x 30, y 0..40
        assertEquals(20, seg.raycast(10, 20, 1, 0, 100, at(30, 0)), EPS);
        assertEquals(20, seg.raycast(50, 20, -1, 0, 100, at(30, 0)), EPS);
        assertEquals(-1, seg.raycast(10, 50, 1, 0, 100, at(30, 0)), 0);
    }

    // ------------------------------------------------------------------------
    // Grid
    // ------------------------------------------------------------------------

    @Test
    public void gridFullCellsAcrossAndDown() {
        var grid = new CollisionGrid(16, 4, 4);        // at (0, 0): 64x64
        grid.set(3, 0, true);                          // x 48..64, y 0..16
        grid.set(0, 2, true);                          // x 0..16, y 32..48
        assertEquals(64, grid.raycast(-16, 8, 1, 0, 100, at(0, 0)), EPS);       // three empty cells, then the left edge of (3, 0)
        assertEquals(16, grid.raycast(80, 8, -1, 0, 100, at(0, 0)), EPS);       // entering across the far edge, straight into (3, 0)
        assertEquals(52, grid.raycast(8, 100, 0, -1, 200, at(0, 0)), EPS);      // down to the top of (0, 2), y 48
        assertEquals(-1, grid.raycast(-16, 24, 1, 0, 100, at(0, 0)), 0);        // row 1 is empty all the way
        assertEquals(-1, grid.raycast(-16, 8, 1, 0, 60, at(0, 0)), 0);          // range stops short of (3, 0)
        assertEquals(0,  grid.raycast(56, 8, 1, 0, 100, at(0, 0)), 0);          // starting inside a solid cell
        assertEquals(-1, grid.raycast(-16, 100, 1, 0, 100, at(0, 0)), 0);       // misses the grid entirely
    }

    @Test
    public void gridDiagonalWalk() {
        var grid = new CollisionGrid(16, 4, 4);
        grid.set(2, 2, true);                          // x 32..48, y 32..48
        // y = x passes the cell corners (16, 16) and (32, 32): the walk steps through empty cells and hits the corner
        assertEquals(32 * ROOT2, grid.raycast(0, 0, DIAG, DIAG, 100, at(0, 0)), 1e-3);
        // y = x + 8 clips the cell's left edge at (32, 40)
        assertEquals(32 * ROOT2, grid.raycast(0, 8, DIAG, DIAG, 100, at(0, 0)), 1e-3);

        var other = new CollisionGrid(16, 4, 4);
        other.set(3, 0, true);                         // x 48..64, y 0..16
        assertEquals(-1, other.raycast(0, 8, DIAG, DIAG, 100, at(0, 0)), 0);    // y = x + 8 leaves through the top, never reaching row 0's last cell
    }

    @Test
    public void rampNeIsHitOnTheHypotenuse() {
        var grid = new CollisionGrid(16, 2, 2);
        grid.set(0, 0, CellShape.RAMP_NE);             // solid lower-right half: x >= y, hypotenuse from (0, 0) to (16, 16)
        assertEquals(18, grid.raycast(-10, 8, 1, 0, 100, at(0, 0)), EPS);       // from the left at mid height: crosses at (8, 8), not the cell edge
        assertEquals(22, grid.raycast(8, 30, 0, -1, 100, at(0, 0)), EPS);       // from above at x 8: crosses at (8, 8)
        assertEquals(10, grid.raycast(-10, 0, 1, 0, 100, at(0, 0)), EPS);       // along the bottom edge: the entry corner is solid
        assertEquals(14, grid.raycast(30, 8, -1, 0, 100, at(0, 0)), EPS);       // from the right: enters in the solid half
        assertEquals(8,  grid.raycast(4, 12, 1, 0, 100, at(0, 0)), EPS);        // starting in the open half: crosses at (12, 12)
        assertEquals(-1, grid.raycast(-8, 0, DIAG, DIAG, 100, at(0, 0)), 0);    // parallel to the hypotenuse, above it
        assertEquals(-1, grid.raycast(-10, 8, 1, 0, 17.9f, at(0, 0)), 0);       // range ends inside the open half
    }

    @Test
    public void everyRampOrientation() {
        var nw = new CollisionGrid(16, 1, 1);
        nw.set(0, 0, CellShape.RAMP_NW);               // solid lower-left: x + y <= 16
        assertEquals(22, nw.raycast(30, 8, -1, 0, 100, at(0, 0)), EPS);         // from the right: crosses at (8, 8)

        var se = new CollisionGrid(16, 2, 2);
        se.set(1, 1, CellShape.RAMP_SE);               // cell x 16..32, y 16..32, solid upper-right: x + y >= 16 locally
        assertEquals(24, se.raycast(24, 0, 0, 1, 100, at(0, 0)), EPS);          // up through the empty cell below: crosses at (24, 24)

        var sw = new CollisionGrid(16, 1, 1);
        sw.set(0, 0, CellShape.RAMP_SW);               // solid upper-left: y >= x
        assertEquals(18, sw.raycast(8, -10, 0, 1, 100, at(0, 0)), EPS);         // from below: crosses at (8, 8)
    }

    @Test
    public void rampRejectsNonRamps() {
        var grid = new CollisionGrid(16, 1, 1);
        assertThrows(com.badlogic.gdx.utils.GdxRuntimeException.class, () -> grid.raycastRamp(CellShape.FULL, 0, 0, 1, 0, 16));
    }
}
