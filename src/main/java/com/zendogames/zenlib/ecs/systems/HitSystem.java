package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Queue;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.Log;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.Family;
import com.zendogames.zenlib.ecs.IteratingSystem;
import com.zendogames.zenlib.ecs.SystemOrder;
import com.zendogames.zenlib.ecs.combat.Hitbox;
import com.zendogames.zenlib.ecs.combat.Strike;
import com.zendogames.zenlib.ecs.components.Animator;
import com.zendogames.zenlib.ecs.components.Hitboxes;
import com.zendogames.zenlib.ecs.components.Name;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.components.SpineAnimator;

import static com.zendogames.zenlib.ZenFlag.LOG_COMBAT;

/**
 * Resolves every fighter's {@link Hitboxes} for the tick, finds HIT boxes overlapping opposing HURT
 * boxes, and hands each new hit to the {@link Strike.Responder}, then to the target's
 * {@code onStruck} and the attacker's {@code onStrike}. Runs at {@code PRESENT + 10}, after the
 * {@code AnimationSystem} and {@code SpineSystem} have advanced the clips and posed the bones, so the
 * boxes match the frame that is drawn. All strikes are found first, then responded to in order, so a
 * trade (two fighters hitting each other on the same tick) reports both.
 * <p>
 * <b>One strike per target per activation.</b> An activation is the set of boxes out for one
 * stretch of a clip: it ends, and the struck targets are forgotten, when the clip changes, restarts
 * or loops, or a window opens or closes. So a window strikes each target once while it stays open
 * and a two-hit move is two windows. Of the target's HURT boxes, the first overlapped (resolve
 * order) is the one reported, so list a "head" before a "body" to let it win.
 * <p>
 * <b>The {@link Space}</b> is the world's geometry: where a fighter's boxes are placed from and
 * whether two boxes meet. The library's is {@link Space#FLAT}, boxes overlapping where they are
 * drawn (platformer, top-down); a game with another geometry implements its own, as a game motion
 * implements {@code Motion}. The rules are the {@link Strike.Responder}'s and each fighter's
 * reactions its own callbacks, so a game changes what a strike does without touching this class.
 * <p>
 * {@link #strikes} holds this tick's strikes, {@link #recent} the last {@link #RECENT} for a debug
 * view. Every pair of fighters is tested, no broadphase, the {@code CollisionWorld} stance.
 */
public class HitSystem extends IteratingSystem {

    public static final int PRIORITY = SystemOrder.PRESENT + 10;
    public static final int RECENT = 16;
    private static final String TAG = HitSystem.class.getSimpleName();

    /**
     * Where a fighter's boxes are and whether two of them meet; every method has the flat default.
     * A 2.5D brawler, where the position's y is a floor line and the art jumps up from it, raises
     * the anchor by the jump height and tests the floor lines before the shapes:
     * <pre>{@code
     * enum BrawlSpace implements HitSystem.Space {
     *     STREET;
     *     static final int BAND = 12; // floor lines closer than this can trade blows
     *     @Override public void anchor(Entity e, PointI2 out) {
     *         var pos = Position.get(e);
     *         out.set(pos.x, pos.y + Jump.get(e).height);
     *     }
     *     @Override public boolean touches(Entity attacker, Hitboxes.Active hit, PointI2 hitAt, Entity target, Hitboxes.Active hurt, PointI2 hurtAt) {
     *         int floorA = Position.get(attacker).y, floorT = Position.get(target).y;
     *         if (Math.abs(floorA - floorT) >= BAND) return false;
     *         // overlap in the body plane: x along the street, y up from each fighter's own floor line
     *         return hit.box.overlaps(Hitboxes.get(attacker).facing, pi2(hitAt.x, hitAt.y - floorA),
     *                 hurt.box, Hitboxes.get(target).facing, pi2(hurtAt.x, hurtAt.y - floorT));
     *     }
     * }
     * }</pre>
     */
    public interface Space {

        /** Boxes from the position, overlapping where they are drawn; reach is a radius. */
        Space FLAT = new Space() {
            @Override public String toString() { return "FLAT"; }
        };

        /** Where {@code e}'s boxes are placed from this tick, world pixels: where its art is drawn from. Bone boxes are offset from it, so the skeleton must be posed there. */
        default void anchor(Entity e, PointI2 out) {
            var pos = Position.get(e);
            out.set(pos.x, pos.y);
        }

        /** Whether {@code hit}, placed at {@code hitAt}, touches {@code hurt} at {@code hurtAt} (anchor plus the box offset, world pixels, valid for the call). */
        default boolean touches(Entity attacker, Hitboxes.Active hit, PointI2 hitAt, Entity target, Hitboxes.Active hurt, PointI2 hurtAt) {
            return hit.box.overlaps(Hitboxes.get(attacker).facing, hitAt, hurt.box, Hitboxes.get(target).facing, hurtAt);
        }

        /** Whether {@code other} stands within {@code reach} of {@code e}, for {@link HitSystem#inRange}: a radius between the positions. */
        default boolean near(Entity e, Entity other, float reach) {
            var a = Position.get(e);
            var b = Position.get(other);
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            return dx * dx + dy * dy <= reach * reach;
        }
    }

    public final Space space;
    public Strike.Responder responder;

    /** This tick's strikes, in detection order; valid until the next update. */
    public final Array<Strike> strikes = new Array<>(16);

    /** The last {@link #RECENT} strikes, oldest first, and how many there have been; for a debug view. */
    public final Queue<Strike> recent = new Queue<>(RECENT);
    public int total;

    // Scratch for the overlap test: where the two boxes are placed
    private final PointI2 hitAt  = new PointI2();
    private final PointI2 hurtAt = new PointI2();

    /** In the {@link Space#FLAT} space. */
    public HitSystem(Strike.Responder responder) {
        this(Space.FLAT, responder);
    }

    public HitSystem(Space space, Strike.Responder responder) {
        super(Family.all(Position.TYPE, Hitboxes.TYPE), PRIORITY);
        this.space = (space != null) ? space : Space.FLAT;
        this.responder = (responder != null) ? responder : Strike.Responder.NONE;
    }

    /** Every entity with {@link Position} and {@link Hitboxes}, a live view; what the overlay walks. */
    public Family fighters() {
        return family();
    }

    @Override
    public void update(float dt) {
        strikes.clear();
        super.update(dt); // resolve, one fighter at a time

        var fighters = family().entities();
        for (int i = 0; i < fighters.size; i++) {
            var attacker = fighters.get(i);
            if (!Hitboxes.get(attacker).isAttacking()) continue;
            for (int j = 0; j < fighters.size; j++) {
                if (i != j) detect(attacker, fighters.get(j));
            }
        }
        for (int i = 0; i < strikes.size; i++) {
            respond(strikes.get(i));
        }
    }

    /**
     * Whether an opponent {@code e} could hit or be hit by stands within {@code reach}: another live
     * fighter, vulnerable, with either side's targets including the other's team, that the
     * {@link Space#near space calls near} (a radius in {@code FLAT}). For a range cue (a shadow that
     * lights up, an AI that swings).
     */
    public boolean inRange(Entity e, float reach) {
        var a = Hitboxes.get(e);
        var fighters = family().entities();
        for (int i = 0; i < fighters.size; i++) {
            var other = fighters.get(i);
            if (other == e || !other.isAlive() || other.isRemoving()) continue;

            var t = Hitboxes.get(other);
            if (!t.vulnerable || !(t.team.in(a.targets) || a.team.in(t.targets))) continue;
            if (space.near(e, other, reach)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // Resolve: which boxes are out this tick, and where
    // ------------------------------------------------------------------------

    @Override
    protected void process(Entity e, float dt) {
        var boxes = Hitboxes.get(e);

        // What is playing: a sprite Animator, or Spine track 0; with neither only the base boxes are out
        String clip = null;
        Object entry = null;
        float time = 0;
        var sprite = Animator.get(e);
        var spine  = SpineAnimator.get(e);
        if (sprite != null) {
            boxes.facing = sprite.facing;
            if (sprite.hasAnimation()) {
                clip  = (sprite.type != null) ? sprite.type.name() : null;
                time  = clipTime(sprite.animation, sprite.time);
                entry = sprite.animation;
            }
        } else if (spine != null) {
            boxes.facing = spine.facing;
            var track = spine.state.getTrack(0);
            if (track != null) {
                clip  = track.getAnimation().getName();
                time  = track.getAnimationTime(); // wrapped for a loop, clamped at the end otherwise
                entry = track;
            }
        }

        // The boxes out now, from the anchor: base, then the windows of the clip that contain its time; a later box replaces an earlier one of its name
        space.anchor(e, boxes.anchor);
        boxes.clearActive();
        int open = 0;
        if (boxes.set != null) {
            for (var box : boxes.set.base) {
                put(e, boxes, box);
            }
            var windows = boxes.set.windows(clip);
            if (windows != null) {
                for (int i = 0; i < windows.size; i++) {
                    var window = windows.get(i);
                    if (!window.contains(time)) continue;
                    open |= 1 << i;
                    for (var box : window.boxes()) {
                        put(e, boxes, box);
                    }
                }
            }
        }

        // A new activation forgets who was struck: a new clip, a restart or loop (time went back), a window opened or closed
        if (entry != boxes.clipEntry || time < boxes.clipTime || open != boxes.open) {
            boxes.struck.clear();
        }
        boxes.clip      = clip;
        boxes.clipTime  = time;
        boxes.clipEntry = entry;
        boxes.open      = open;
        for (int i = boxes.struck.size - 1; i >= 0; i--) {
            if (!boxes.struck.get(i).isAlive()) boxes.struck.removeIndex(i);
        }
    }

    /** A sprite animation's time within one play: wrapped for the looping modes, as {@code getKeyFrame} reads it. */
    private static float clipTime(Animation<?> animation, float time) {
        var duration = animation.getAnimationDuration();
        return switch (animation.getPlayMode()) {
            case NORMAL, REVERSED -> time;
            default -> (duration > 0) ? time % duration : 0;
        };
    }

    /** Puts {@code box} out in place of an earlier one with the same name, or after the rest. */
    private void put(Entity e, Hitboxes boxes, Hitbox box) {
        var slot = boxes.find(box.name());
        if (slot == null) {
            slot = boxes.obtain();
            boxes.active.add(slot);
        }
        slot.box = box;
        slot.touching = false;
        slot.x = 0;
        slot.y = 0;
        if (box.bone() == null) return;

        // The bone was posed this tick from where the art is drawn, the anchor; keep the anchor out so it is added once
        var spine = SpineAnimator.get(e);
        var bone  = (spine != null) ? spine.skeleton.findBone(box.bone()) : null;
        if (bone == null) {
            throw new GdxRuntimeException(TAG + ": %s box '%s' is on bone '%s', which %s has not got".formatted(
                    box.kind(), box.name(), box.bone(), (spine == null) ? "a fighter without a SpineAnimator" : Name.of(e)));
        }
        var pose = bone.getAppliedPose();
        slot.x = MathUtils.round(pose.getWorldX() - boxes.anchor.x);
        slot.y = MathUtils.round(pose.getWorldY() - boxes.anchor.y);
    }

    // ------------------------------------------------------------------------
    // Detect: HIT against HURT, where the space places them
    // ------------------------------------------------------------------------

    private void detect(Entity attacker, Entity target) {
        if (!target.isAlive() || target.isRemoving()) return;
        var a = Hitboxes.get(attacker);
        var t = Hitboxes.get(target);
        if (!t.team.in(a.targets) || !t.vulnerable) return;

        var aPos = Position.get(attacker);
        var tPos = Position.get(target);
        int dx = tPos.x - aPos.x;
        int dy = aPos.y - tPos.y;
        Hitbox hit = null, hurt = null;
        for (int i = 0; i < a.active.size; i++) {
            var h = a.active.get(i);
            if (!h.box.isHit()) continue;
            for (int j = 0; j < t.active.size; j++) {
                var u = t.active.get(j);
                if (!u.box.isHurt()) continue;
                hitAt.set(a.anchor.x + h.x, a.anchor.y + h.y);
                hurtAt.set(t.anchor.x + u.x, t.anchor.y + u.y);
                if (!space.touches(attacker, h, hitAt, target, u, hurtAt)) continue;
                h.touching = true;
                u.touching = true;
                if (hit == null) { hit = h.box; hurt = u.box; }
            }
        }
        if (hit == null || a.struck.contains(target, true)) return;

        a.struck.add(target);
        strikes.add(new Strike(attacker, hit, target, hurt, a.facing, dx, dy));
    }

    // ------------------------------------------------------------------------
    // Respond: the rule book, then each side's own reaction
    // ------------------------------------------------------------------------

    private void respond(Strike strike) {
        if (recent.size == RECENT) recent.removeFirst();
        recent.addLast(strike);
        total++;
        Log.info(LOG_COMBAT, TAG, "%s %s -> %s %s, %d damage, away %+d",
                Name.of(strike.attacker()), strike.hit().name(), Name.of(strike.target()), strike.hurt().name(),
                strike.attack().damage(), strike.away());

        responder.strike(strike);
        var target = Hitboxes.get(strike.target());
        if (target != null && target.onStruck != null) {
            target.onStruck.struck(strike.target(), strike);
        }
        var attacker = Hitboxes.get(strike.attacker());
        if (attacker != null && attacker.onStrike != null) {
            attacker.onStrike.struck(strike.attacker(), strike);
        }
    }
}