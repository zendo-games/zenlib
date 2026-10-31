package com.zendogames.zenlib.ecs.combat;

import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.shapes.CollisionCaps;
import com.zendogames.zenlib.collision.shapes.CollisionCirc;
import com.zendogames.zenlib.collision.shapes.CollisionGrid;
import com.zendogames.zenlib.collision.shapes.CollisionRect;
import org.junit.Test;

import static com.zendogames.zenlib.ecs.combat.Hitbox.*;
import static org.junit.Assert.*;

public class HitboxTest {

    static final Attack JAB = attack(1, 0.5f, 0);

    @Test
    public void mirrorReflectsEachShapeAboutX() {
        var r = ((CollisionRect) mirror(rect(2, 0, 10, 4))).rectangle();
        assertEquals(-12, r.x, 0);
        assertEquals(0, r.y, 0);
        assertEquals(10, r.width, 0);
        assertEquals(4, r.height, 0);

        var c = ((CollisionCirc) mirror(circ(3, 5, 2))).circle();
        assertEquals(-3, c.x, 0);
        assertEquals(5, c.y, 0);
        assertEquals(2, c.radius, 0);

        var caps = (CollisionCaps) mirror(capsule(1, 0, 4, 10));
        assertEquals(-5, caps.bounds().x, 0);
        assertEquals(CollisionCaps.Axis.VERTICAL, caps.axis());
        assertEquals(CollisionCaps.Axis.HORIZONTAL, ((CollisionCaps) capsule(0, 0, 10, 4)).axis());
    }

    @Test
    public void facingPicksTheMirroredShape() {
        var box = hit("fist", rect(2, 0, 10, 4), JAB);
        assertSame(box.right(), box.shape(+1));
        assertSame(box.left(), box.shape(-1));
    }

    @Test
    public void aHitBoxNeedsAnAttackAndAHurtBoxHasNone() {
        var r = rect(0, 0, 4, 4);
        assertThrows(GdxRuntimeException.class, () -> new Hitbox("fist", Kind.HIT, r, r, null, null));
        assertThrows(GdxRuntimeException.class, () -> new Hitbox("body", Kind.HURT, r, r, null, JAB));
        assertTrue(hit("fist", r, JAB).isHit());
        assertTrue(hurt("body", r).isHurt());
    }

    @Test
    public void overlapsPlacesEachShapeByItsFacingAndTouchingIsNot() {
        var fist = hit("fist", rect(2, 0, 10, 4), JAB); // 2..12 facing right, -12..-2 facing left
        var body = hurt("body", rect(-4, 0, 8, 8));     // -4..4 either way
        var origin = new PointI2(0, 0);
        assertTrue(fist.overlaps(+1, origin, body, +1, new PointI2(10, 0)));   // body 6..14
        assertFalse(fist.overlaps(-1, origin, body, +1, new PointI2(10, 0)));  // the fist faces away
        assertTrue(fist.overlaps(-1, origin, body, -1, new PointI2(-10, 0)));
        assertFalse(fist.overlaps(+1, origin, body, +1, new PointI2(16, 0)));  // body from 12: edges touch
        assertFalse(fist.overlaps(+1, origin, body, +1, new PointI2(10, 4)));  // body from y 4: edges touch
    }

    @Test
    public void copiesKeepEverythingElse() {
        var fist = hit("fist", rect(0, 0, 4, 4), JAB).on("front-fist");
        assertEquals("fist", fist.name());
        assertEquals(Kind.HIT, fist.kind());
        assertEquals("front-fist", fist.bone());
        assertSame(JAB, fist.attack());
        assertFalse(attack(1, 1, 0).heavy());
        assertTrue(attackHeavy(3, 2, 1).heavy());
    }

    @Test
    public void aGridShapeIsRejected() {
        var grid = new CollisionGrid(16, 2, 2);
        assertThrows(GdxRuntimeException.class, () -> hurt("floor", grid));
    }

    @Test
    public void awayIsTheTargetsSideOrTheFacingWhenStacked() {
        var fist = hit("fist", rect(0, 0, 4, 4), JAB);
        var body = hurt("body", rect(0, 0, 4, 4));
        assertEquals(+1, new Strike(null, fist, null, body, -1, 5, 0).away());
        assertEquals(-1, new Strike(null, fist, null, body, +1, -5, 0).away());
        assertEquals(-1, new Strike(null, fist, null, body, -1, 0, 0).away());
        assertSame(JAB, new Strike(null, fist, null, body, -1, 0, 0).attack());
    }
}
