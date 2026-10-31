package com.zendogames.zenlib.ecs.combat;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.zendogames.zenlib.FramePool;
import com.zendogames.zenlib.ZenFlag;
import com.zendogames.zenlib.collision.CollisionDraw;
import com.zendogames.zenlib.ecs.components.Hitboxes;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.systems.HitSystem;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Debug outlines for this tick's hitboxes, drawn with the screen's {@link ShapeDrawer} between the
 * batch's begin and end, at the interpolated position like {@code CollisionDraw.all}. Colors say
 * the state: HURT green, HIT red; yellow (and thicker) while touching an opposing box; grey for the
 * HURT boxes of a fighter that is not vulnerable. Each fighter's boxes are drawn from its
 * {@code Hitboxes.anchor}, moved with the interpolated position, so a game space that places them
 * away from the position is drawn where it tests them.
 */
public final class HitboxDraw {

    public static final Color HURT     = new Color(0.2f, 0.9f, 0.3f, 0.8f);
    public static final Color HIT      = new Color(1.0f, 0.2f, 0.2f, 0.9f);
    public static final Color TOUCHING = new Color(1.0f, 1.0f, 0.2f, 1.0f);
    public static final Color IMMUNE   = new Color(0.6f, 0.6f, 0.6f, 0.6f);

    private HitboxDraw() {}

    /** Every fighter of {@code system}; {@code alpha} is {@code World.alpha()}. */
    public static void all(ShapeDrawer shapes, HitSystem system, float alpha) {
        var a = ZenFlag.RENDER_INTERP.on() ? alpha : 1f;
        var fighters = system.fighters().entities();
        for (int i = 0; i < fighters.size; i++) {
            var e = fighters.get(i);
            var pos = Position.get(e);
            var boxes = Hitboxes.get(e);
            int x = MathUtils.round(pos.renderX(a)) + boxes.anchor.x - pos.x;
            int y = MathUtils.round(pos.renderY(a)) + boxes.anchor.y - pos.y;
            for (int j = 0; j < boxes.active.size; j++) {
                var slot = boxes.active.get(j);
                var at = FramePool.pi2(x + slot.x, y + slot.y);
                CollisionDraw.shape(shapes, slot.box.shape(boxes.facing), at, color(boxes, slot), slot.touching ? 2f : 1f);
            }
        }
    }

    private static Color color(Hitboxes boxes, Hitboxes.Active slot) {
        if (slot.box.isHurt() && !boxes.vulnerable) return IMMUNE;
        if (slot.touching) return TOUCHING;
        return slot.box.isHurt() ? HURT : HIT;
    }
}
