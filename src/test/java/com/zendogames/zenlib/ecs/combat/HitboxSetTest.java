package com.zendogames.zenlib.ecs.combat;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.zendogames.zenlib.assets.AnimType;
import com.zendogames.zenlib.assets.Asset;
import com.zendogames.zenlib.assets.AtlasType;
import org.junit.Test;

import static com.zendogames.zenlib.ecs.combat.Hitbox.rect;
import static org.junit.Assert.*;

public class HitboxSetTest {

    enum Clip implements AnimType {
        PUNCH;
        private final Asset<Animation<TextureRegion>> asset = new Asset<>(this);
        @Override public Asset<Animation<TextureRegion>> asset() { return asset; }
        @Override public AtlasType atlas()   { return null; }
        @Override public String regionName() { return name(); }
    }

    static final Hitbox FIST = Hitbox.hit("fist", rect(8, 24, 14, 6), Hitbox.attack(1, 1, 0));

    @Test
    public void aWindowMustEndAfterItStarts() {
        assertThrows(GdxRuntimeException.class, () -> new HitboxSet().window("p", 0.4f, 0.2f, FIST));
        assertThrows(GdxRuntimeException.class, () -> new HitboxSet().window("p", 0.2f, 0.2f, FIST));
    }

    @Test
    public void containsIsStartInclusiveEndExclusive() {
        var w = new HitboxSet.Window(0.2f, 0.4f, FIST);
        assertFalse(w.contains(0.1999f));
        assertTrue(w.contains(0.2f));
        assertTrue(w.contains(0.3999f));
        assertFalse(w.contains(0.4f));
    }

    @Test
    public void clipIsAnUnboundedWindowAndUnknownClipsHaveNone() {
        var set = new HitboxSet().clip("crouch", Hitbox.hurt("body", rect(-8, 0, 16, 20)));
        assertEquals(Float.POSITIVE_INFINITY, set.windows("crouch").first().end(), 0);
        assertTrue(set.windows("crouch").first().contains(1000f));
        assertNull(set.windows("x"));
        assertNull(set.windows(null));
    }

    @Test
    public void windowsKeepAuthoringOrderAndAnimTypesKeyByName() {
        var set = new HitboxSet()
                .window(Clip.PUNCH, 0.6f, 0.7f, FIST)
                .window("PUNCH", 0.2f, 0.4f, FIST);
        assertEquals(2, set.windows("PUNCH").size);
        assertEquals(0.6f, set.windows("PUNCH").get(0).start(), 0);
        assertEquals(0.2f, set.windows("PUNCH").get(1).start(), 0);
    }

    @Test
    public void aClipHoldsAtMostThirtyTwoWindows() {
        var set = new HitboxSet();
        for (int i = 0; i < HitboxSet.MAX_WINDOWS; i++) {
            set.window("flurry", i, i + 0.5f, FIST);
        }
        assertThrows(GdxRuntimeException.class, () -> set.window("flurry", 40, 41, FIST));
    }
}
