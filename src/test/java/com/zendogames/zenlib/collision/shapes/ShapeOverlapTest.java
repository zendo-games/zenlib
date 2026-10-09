package com.zendogames.zenlib.collision.shapes;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.shapes.CollisionAASeg.Axis;
import com.zendogames.zenlib.collision.shapes.CollisionAASeg.Facing;
import com.zendogames.zenlib.collision.shapes.CollisionGrid.CellShape;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pairwise overlap math, pinned at the touching boundary: flush contact is never an overlap,
 * one pixel further always is. Positions are world, shapes entity-local.
 */
public class ShapeOverlapTest {

    private static PointI2 at(int x, int y) { return new PointI2(x, y); }

    private static final PointI2 STILL = at(0, 0);
    private static final PointI2 LEFT  = at(-1, 0);
    private static final PointI2 RIGHT = at(1, 0);
    private static final PointI2 DOWN  = at(0, -1);
    private static final PointI2 UP    = at(0, 1);

    // ------------------------------------------------------------------------
    // Rect, circle, capsule
    // ------------------------------------------------------------------------

    @Test
    public void rectRectTouchingIsNotOverlap() {
        var a = new CollisionRect(0, 0, 10, 10);
        var b = new CollisionRect(0, 0, 10, 10);
        assertFalse(a.overlaps(b, at(0, 0), at(10, 0), STILL));
        assertTrue (a.overlaps(b, at(0, 0), at(10, 0), RIGHT));
        assertTrue (b.overlaps(a, at(10, 0), at(0, 0), LEFT));
    }

    @Test
    public void rectCircBothDirections() {
        var rect = new CollisionRect(0, 0, 10, 10);   // at (5, 0): x 5..15
        var circ = new CollisionCirc(0, 0, 5);        // at (20, 5): left edge x 15
        assertFalse(rect.overlaps(circ, at(5, 0), at(20, 5), STILL));
        assertTrue (rect.overlaps(circ, at(5, 0), at(20, 5), RIGHT));
        assertFalse(circ.overlaps(rect, at(20, 5), at(5, 0), STILL));
        assertTrue (circ.overlaps(rect, at(20, 5), at(5, 0), LEFT));
    }

    @Test
    public void circCirc() {
        var a = new CollisionCirc(0, 0, 5);
        var b = new CollisionCirc(0, 0, 5);
        assertFalse(a.overlaps(b, at(0, 0), at(10, 0), STILL));
        assertTrue (a.overlaps(b, at(0, 0), at(10, 0), RIGHT));
    }

    @Test
    public void capsRectBothDirections() {
        var caps = new CollisionCaps(0, 0, 10, 20);   // vertical, r 5, axis (5,5)-(5,15)
        var rect = new CollisionRect(0, 0, 10, 10);   // at (10, 5): x 10..20
        assertFalse(caps.overlaps(rect, at(0, 0), at(10, 5), STILL));
        assertTrue (caps.overlaps(rect, at(0, 0), at(10, 5), RIGHT));
        assertFalse(rect.overlaps(caps, at(10, 5), at(0, 0), STILL));
        assertTrue (rect.overlaps(caps, at(10, 5), at(0, 0), LEFT));
    }

    @Test
    public void capsCapsAndCapsCirc() {
        var a = new CollisionCaps(0, 0, 10, 20);
        var b = new CollisionCaps(0, 0, 10, 20);
        assertFalse(a.overlaps(b, at(0, 0), at(10, 0), STILL));   // axes 10 apart = r + r
        assertTrue (a.overlaps(b, at(0, 0), at(10, 0), RIGHT));

        var circ = new CollisionCirc(0, 0, 5);
        assertFalse(a.overlaps(circ, at(0, 0), at(15, 10), STILL)); // center 10 from the axis
        assertTrue (a.overlaps(circ, at(0, 0), at(15, 10), RIGHT));
        assertFalse(circ.overlaps(a, at(15, 10), at(0, 0), STILL));
        assertTrue (circ.overlaps(a, at(15, 10), at(0, 0), LEFT));
    }

    @Test
    public void horizontalCapsuleUsesTheSameMath() {
        var caps = new CollisionCaps(CollisionCaps.Axis.HORIZONTAL, 0, 0, 20, 10); // r 5, axis (5,5)-(15,5)
        var rect = new CollisionRect(0, 0, 10, 10);                                 // at (0, 10): y 10..20
        assertFalse(caps.overlaps(rect, at(0, 0), at(0, 10), STILL));
        assertTrue (caps.overlaps(rect, at(0, 0), at(0, 10), UP));
        assertEquals(5f, caps.radius(), 0f);
        assertEquals(0, caps.slideUpHeight());
    }

    @Test
    public void capsConstructorRejectsCirclesAndWrongAspect() {
        assertThrows(GdxRuntimeException.class, () -> new CollisionCaps(0, 0, 10, 10));
        assertThrows(GdxRuntimeException.class, () -> new CollisionCaps(CollisionCaps.Axis.VERTICAL, 0, 0, 20, 10));
        assertThrows(GdxRuntimeException.class, () -> new CollisionRect(0, 0, 0, 10));
        assertThrows(GdxRuntimeException.class, () -> new CollisionCirc(0, 0, 0));
    }

    // ------------------------------------------------------------------------
    // Segments: overlap is geometry, blocks() is the one-way rule
    // ------------------------------------------------------------------------

    @Test
    public void capsRestsOnPlatformAndBlocksOnlyFromAbove() {
        var caps = new CollisionCaps(0, 0, 10, 20);
        var seg  = new CollisionAASeg(Axis.HORIZONTAL, 0, 0, 100, Facing.POSITIVE);
        var segPos = at(0, 10);                       // line y = 10

        // Resting flush: no overlap; one pixel down: overlap, and blocked
        var resting = at(0, 10);
        assertFalse(caps.overlaps(seg, resting, segPos, STILL));
        assertTrue (caps.overlaps(seg, resting, segPos, DOWN));
        assertTrue (seg.blocks(caps, resting, segPos, 0, -1));
        assertTrue (seg.overlaps(caps, segPos, resting, STILL) == caps.overlaps(seg, resting, segPos, STILL));

        // Rising through from below: overlaps, never blocks
        var below = at(0, -5);                        // bounds y -5..15, line inside
        assertTrue (caps.overlaps(seg, below, segPos, UP));
        assertFalse(seg.blocks(caps, below, segPos, 0, 1));

        // Sideways while straddling the line: not against the facing
        assertFalse(seg.blocks(caps, below, segPos, 1, 0));
    }

    @Test
    public void capsSlidesOffPlatformEndAlongItsArc() {
        var caps = new CollisionCaps(0, 0, 10, 20);                                    // bottom cap center 5 in from the left
        var seg  = new CollisionAASeg(Axis.HORIZONTAL, 0, 0, 100, Facing.POSITIVE);    // ends at x 100
        var segPos = at(0, 10);
        assertTrue (caps.overlaps(seg, at(97, 10), segPos, DOWN));   // cap center 2 past the end: arc still touches
        assertFalse(caps.overlaps(seg, at(98, 10), segPos, DOWN));   // 3 past: falls off
    }

    @Test
    public void rectAgainstHorizontalPlatformAndCeiling() {
        var rect = new CollisionRect(0, 0, 10, 10);
        var platform = new CollisionAASeg(Axis.HORIZONTAL, 0, 0, 100, Facing.POSITIVE);
        var segPos = at(0, 10);
        assertFalse(rect.overlaps(platform, at(0, 10), segPos, STILL));   // flush on top
        assertTrue (rect.overlaps(platform, at(0, 10), segPos, DOWN));
        assertTrue (platform.blocks(rect, at(0, 10), segPos, 0, -1));
        assertFalse(rect.overlaps(platform, at(0, 0), segPos, STILL));    // flush underneath
        assertTrue (rect.overlaps(platform, at(0, 0), segPos, UP));
        assertFalse(platform.blocks(rect, at(0, 0), segPos, 0, 1));
        assertFalse(rect.overlaps(platform, at(100, 10), segPos, DOWN));  // past the end

        var ceiling = new CollisionAASeg(Axis.HORIZONTAL, 0, 0, 100, Facing.NEGATIVE);
        var ceilPos = at(0, 50);
        assertTrue (rect.overlaps(ceiling, at(0, 40), ceilPos, UP));      // top at 50, rising
        assertTrue (ceiling.blocks(rect, at(0, 40), ceilPos, 0, 1));
        assertTrue (rect.overlaps(ceiling, at(0, 45), ceilPos, DOWN));    // straddling, falling
        assertFalse(ceiling.blocks(rect, at(0, 45), ceilPos, 0, -1));
    }

    @Test
    public void verticalSegmentIsAOneWayWall() {
        var rect = new CollisionRect(0, 0, 10, 10);
        var wall = new CollisionAASeg(Axis.VERTICAL, 0, 0, 50, Facing.POSITIVE);  // supports the +x side
        var wallPos = at(20, 0);                                                 // line x = 20
        assertFalse(rect.overlaps(wall, at(20, 5), wallPos, STILL));            // flush on the right
        assertTrue (rect.overlaps(wall, at(20, 5), wallPos, LEFT));
        assertTrue (wall.blocks(rect, at(20, 5), wallPos, -1, 0));
        assertTrue (rect.overlaps(wall, at(15, 5), wallPos, RIGHT));            // coming from the left: passes
        assertFalse(wall.blocks(rect, at(15, 5), wallPos, 1, 0));
        assertFalse(rect.overlaps(wall, at(20, 50), wallPos, LEFT));            // past the end

        var caps = new CollisionCaps(0, 0, 10, 20);
        assertFalse(caps.overlaps(wall, at(20, 5), wallPos, STILL));
        assertTrue (caps.overlaps(wall, at(20, 5), wallPos, LEFT));
        var circ = new CollisionCirc(0, 0, 5);
        assertFalse(circ.overlaps(wall, at(25, 5), wallPos, STILL));
        assertTrue (circ.overlaps(wall, at(25, 5), wallPos, LEFT));
    }

    @Test
    public void circAgainstPlatform() {
        var circ = new CollisionCirc(0, 0, 5);
        var seg  = new CollisionAASeg(Axis.HORIZONTAL, 0, 0, 100, Facing.POSITIVE);
        assertFalse(circ.overlaps(seg, at(50, 15), at(0, 10), STILL));
        assertTrue (circ.overlaps(seg, at(50, 15), at(0, 10), DOWN));
        assertTrue (seg.blocks(circ, at(50, 15), at(0, 10), 0, -1));
    }

    @Test
    public void segmentExtentsAndValidation() {
        var seg = new CollisionAASeg(Axis.VERTICAL, 3, 10, 40, Facing.NEGATIVE);
        var pos = at(100, 200);
        assertEquals(103f, seg.left(pos),   0f);
        assertEquals(103f, seg.right(pos),  0f);
        assertEquals(210f, seg.bottom(pos), 0f);
        assertEquals(240f, seg.top(pos),    0f);
        assertEquals(30f,  seg.length(),    0f);
        assertFalse(seg.contains(103, 220));
        assertThrows(GdxRuntimeException.class, () -> new CollisionAASeg(Axis.HORIZONTAL, 0, 10, 0, Facing.POSITIVE));
    }

    // ------------------------------------------------------------------------
    // Grid: full cells for every mover, ramps only for capsules
    // ------------------------------------------------------------------------

    private static CollisionGrid grid() {
        var grid = new CollisionGrid(16, 4, 4);
        grid.set(0, 0, CellShape.FULL);
        grid.set(1, 0, CellShape.RAMP_NE);
        return grid;
    }

    @Test
    public void gridIsNeverAMover() {
        assertThrows(GdxRuntimeException.class, () -> grid().overlaps(new CollisionRect(0, 0, 1, 1), STILL, STILL, STILL));
        assertThrows(GdxRuntimeException.class, () -> new CollisionGrid(16, 2, 2, new CellShape[3]));
        assertThrows(GdxRuntimeException.class, () -> grid().get(4, 0));
        assertThrows(GdxRuntimeException.class, () -> grid().set(0, -1, true));
    }

    @Test
    public void rectOnFullCellAndOffTheGrid() {
        var grid = grid();
        var rect = new CollisionRect(0, 0, 8, 8);
        assertFalse(rect.overlaps(grid, at(4, 16), STILL, STILL));   // flush on top of (0,0)
        assertTrue (rect.overlaps(grid, at(4, 16), STILL, DOWN));
        assertFalse(rect.overlaps(grid, at(-8, 0), STILL, STILL));   // flush against the grid's left edge
        assertFalse(rect.overlaps(grid, at(200, 200), STILL, STILL));
        assertFalse(rect.overlaps(grid, at(40, 40), STILL, DOWN));   // inside the grid, empty cells
    }

    @Test
    public void rectTreatsRampAsFullButCapsuleFollowsTheSlope() {
        var grid = grid();
        var rect = new CollisionRect(0, 0, 8, 8);
        var caps = new CollisionCaps(0, 0, 8, 16);              // r 4, bottom cap center (4, 4) local
        assertTrue (rect.overlaps(grid, at(20, 16), STILL, DOWN));   // over the open half of RAMP_NE: coarse, blocked
        assertFalse(caps.overlaps(grid, at(16, 16), STILL, DOWN));   // capsule: the slope is far below its cap
        assertTrue (caps.overlaps(grid, at(16, 4), STILL, STILL));   // cap center (20, 8): 2.8 px from the hypotenuse
        assertTrue (caps.overlaps(grid, at(28, 10), STILL, STILL));  // cap center (32, 14) inside the solid corner
    }

    @Test
    public void capsOnFullCell() {
        var grid = grid();
        var caps = new CollisionCaps(0, 0, 8, 16);
        assertFalse(caps.overlaps(grid, at(0, 16), STILL, STILL));
        assertTrue (caps.overlaps(grid, at(0, 16), STILL, DOWN));
        var circ = new CollisionCirc(0, 0, 4);
        assertFalse(circ.overlaps(grid, at(8, 20), STILL, STILL));
        assertTrue (circ.overlaps(grid, at(8, 20), STILL, DOWN));
    }

    @Test
    public void gridContainsIsShapeAware() {
        var grid = grid();
        assertTrue (grid.contains(8, 8));      // FULL
        assertTrue (grid.contains(20, 4));     // RAMP_NE, below the hypotenuse
        assertFalse(grid.contains(20, 12));    // RAMP_NE, above it
        assertTrue (grid.contains(24, 8));     // on the hypotenuse
        assertFalse(grid.contains(40, 40));    // EMPTY
        assertFalse(grid.contains(-1, 5));
        assertFalse(grid.contains(64, 5));
    }

    @Test
    public void cellShapeRegions() {
        float s = 16;
        assertTrue (CellShape.RAMP_NW.contains(2, 4, s));
        assertFalse(CellShape.RAMP_NW.contains(14, 4, s));
        assertTrue (CellShape.RAMP_SE.contains(14, 14, s));
        assertFalse(CellShape.RAMP_SE.contains(2, 2, s));
        assertTrue (CellShape.RAMP_SW.contains(2, 14, s));
        assertFalse(CellShape.RAMP_SW.contains(14, 2, s));
        assertFalse(CellShape.FULL.contains(17, 2, s));
        assertEquals(+1, CellShape.RAMP_NE.rampDir());
        assertEquals(-1, CellShape.RAMP_NW.rampDir());
        assertTrue (CellShape.RAMP_SW.isCeilingRamp());
        assertFalse(CellShape.RAMP_SW.isFloorRamp());
    }

    @Test
    public void cellRangeClampsToTheGrid() {
        var grid = grid();
        var range = grid.cellRange(new com.badlogic.gdx.math.Rectangle(-10, -10, 30, 100), at(0, 0));
        assertEquals(0, range.x);   // left col
        assertEquals(0, range.y);   // bottom row
        assertEquals(2, range.z);   // right col, exclusive: x 20 -> ceil(1.25)
        assertEquals(4, range.w);   // top row, exclusive: clamped
    }

    @Test
    public void extentsAndCenters() {
        var pos = at(100, 50);
        var rect = new CollisionRect(-4, 0, 8, 12);
        assertEquals(96f,  rect.left(pos),    0f);
        assertEquals(104f, rect.right(pos),   0f);
        assertEquals(50f,  rect.bottom(pos),  0f);
        assertEquals(62f,  rect.top(pos),     0f);
        assertEquals(100f, rect.centerX(pos), 0f);
        assertEquals(56f,  rect.centerY(pos), 0f);

        var caps = new CollisionCaps(0, 0, 10, 20);
        assertEquals(55f, caps.innerBottom(pos), 0f);
        assertEquals(65f, caps.innerTop(pos),    0f);
        assertEquals(5,   caps.slideUpHeight());

        var circ = new CollisionCirc(0, 0, 5);
        assertEquals(50f, circ.innerBottom(pos), 0f);
        assertEquals(50f, circ.innerTop(pos),    0f);

        var aabb = caps.aabb(pos);
        assertEquals(100f, aabb.x, 0f);
        assertEquals(50f,  aabb.y, 0f);
        assertEquals(10f,  aabb.width, 0f);
        assertEquals(20f,  aabb.height, 0f);
    }
}
