package com.zendogames.zenlib.ecs.combat;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.junit.Test;

import static com.zendogames.zenlib.ecs.combat.Hitbox.*;
import static org.junit.Assert.*;

/**
 * Pins the shape of the combat data types as a file format will see them: the mirror is derived and
 * checked, windows and sets compare by value and keep authoring order, and the tables cannot be
 * grown past their limits from outside.
 */
public class HitboxShapeTest {

    static final Attack JAB = attack(1, 0.5f, 0);

    @Test
    public void theFiveArgumentConstructorMirrorsTheShape() {
        var box = new Hitbox("fist", Kind.HIT, rect(2, 0, 10, 4), "front-fist", JAB);
        assertEquals(mirror(rect(2, 0, 10, 4)), box.left());
        assertEquals("front-fist", box.bone());
        assertEquals(box, hit("fist", rect(2, 0, 10, 4), JAB).on("front-fist")); // the factories build the same value
    }

    @Test
    public void theCanonicalConstructorRejectsAMirrorThatIsNotTheMirror() {
        var right = rect(2, 0, 10, 4);
        var thrown = assertThrows(GdxRuntimeException.class, () -> new Hitbox("fist", Kind.HIT, right, right, null, JAB));
        assertTrue(thrown.getMessage(), thrown.getMessage().contains("left"));
        assertNotNull(new Hitbox("fist", Kind.HIT, right, mirror(right), null, JAB));
    }

    @Test
    public void hitboxesCompareByValue() {
        assertEquals(hit("fist", rect(2, 0, 10, 4), JAB), hit("fist", rect(2, 0, 10, 4), attack(1, 0.5f, 0)));
        assertNotEquals(hit("fist", rect(2, 0, 10, 4), JAB), hit("fist", rect(3, 0, 10, 4), JAB));
        assertNotEquals(hurt("body", circ(0, 0, 4)), hurt("body", capsule(0, 0, 4, 8)));
    }

    @Test
    public void windowsCompareByValueAndReadTheirBoxes() {
        var a = new HitboxSet.Window(0.2f, 0.4f, hit("fist", rect(2, 0, 10, 4), JAB));
        var b = new HitboxSet.Window(0.2f, 0.4f, Array.with(hit("fist", rect(2, 0, 10, 4), JAB)));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new HitboxSet.Window(0.2f, 0.4f, hit("fist", rect(2, 0, 10, 5), JAB)));
        assertTrue(a.toString(), a.toString().contains("fist")); // not an array hash
        assertEquals(1, a.boxes().size);
    }

    @Test
    public void aWindowNeedsAtLeastOneBox() {
        assertThrows(GdxRuntimeException.class, () -> new HitboxSet.Window(0.2f, 0.4f));
        assertThrows(GdxRuntimeException.class, () -> new HitboxSet.Window(0.2f, 0.4f, (Array<Hitbox>) null));
        assertThrows(GdxRuntimeException.class, () -> new HitboxSet().window("p", 0.2f, 0.4f));
    }

    @Test
    public void clipsKeepAuthoringOrder() {
        var set = new HitboxSet()
                .window("zeta",  0, 1, hit("a", rect(0, 0, 1, 1), JAB))
                .window("alpha", 0, 1, hit("a", rect(0, 0, 1, 1), JAB))
                .clip("mid", hurt("b", rect(0, 0, 1, 1)))
                .window("zeta",  1, 2, hit("a", rect(0, 0, 1, 1), JAB));
        assertEquals(Array.with("zeta", "alpha", "mid"), set.clipNames());
        assertEquals(2, set.windows("zeta").size);
    }

    @Test
    public void theTablesAreReadThroughAccessorsAndClearedTogether() {
        var set = new HitboxSet()
                .base(hurt("body", rect(-4, 0, 8, 8)))
                .window("p", 0, 1, hit("fist", rect(0, 0, 4, 4), JAB));
        assertEquals(1, set.base().size);
        assertEquals("body", set.base().first().name());
        set.clear();
        assertEquals(0, set.base().size);
        assertEquals(0, set.clipNames().size);
        assertNull(set.windows("p"));
    }

    @Test
    public void aClipHoldsAtMostMaxWindowsAndTheBaseBitIsOutsideThem() {
        var set = new HitboxSet();
        for (int i = 0; i < HitboxSet.MAX_WINDOWS; i++) {
            set.window("flurry", i, i + 0.5f, hit("a", rect(0, 0, 1, 1), JAB));
        }
        assertThrows(GdxRuntimeException.class, () -> set.window("flurry", 40, 41, hit("a", rect(0, 0, 1, 1), JAB)));
        assertEquals(31, HitboxSet.MAX_WINDOWS);
        assertEquals(0, HitboxSet.BASE & ((1 << HitboxSet.MAX_WINDOWS) - 1)); // the base source bit is none of the window bits
    }
}
