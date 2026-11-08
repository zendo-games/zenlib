package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.zendogames.zenlib.assets.AnimType;
import com.zendogames.zenlib.assets.Asset;
import com.zendogames.zenlib.assets.AtlasType;
import com.zendogames.zenlib.collision.CollisionMask;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.combat.Hitbox;
import com.zendogames.zenlib.ecs.combat.HitboxSet;
import com.zendogames.zenlib.ecs.combat.Strike;
import com.zendogames.zenlib.ecs.components.Animator;
import com.zendogames.zenlib.ecs.components.Hitboxes;
import com.zendogames.zenlib.ecs.components.Name;
import com.zendogames.zenlib.ecs.components.Position;
import org.junit.Before;
import org.junit.Test;

import static com.zendogames.zenlib.ecs.combat.Hitbox.rect;
import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

/**
 * Pins that an activation belongs to one source, the base set or one window: overlapping windows
 * do not re-arm each other, a persistent HIT box is not re-armed by windows toggling, and a clip
 * name change is a new activation even when the {@code Animation} object stays.
 */
public class HitActivationTest {

    enum Team implements CollisionMask {
        RED, BLUE;
        @Override public int bit() { return 1 << ordinal(); }
    }

    enum Clip implements AnimType {
        PUNCH, KICK;
        private final Asset<Animation<TextureRegion>> asset = new Asset<>(this);
        @Override public Asset<Animation<TextureRegion>> asset() { return asset; }
        @Override public AtlasType atlas()   { return null; }
        @Override public String regionName() { return name(); }
    }

    static final Hitbox.Attack JAB = Hitbox.attack(1, 1, 0);
    static final Hitbox FIST  = Hitbox.hit("fist",  rect(8, 24, 14, 6), JAB);
    static final Hitbox ELBOW = Hitbox.hit("elbow", rect(8, 24, 14, 6), JAB);
    static final Hitbox FOOT  = Hitbox.hit("foot",  rect(8, 24, 14, 6), JAB);
    static final Hitbox BODY  = Hitbox.hurt("body", rect(-8, 0, 16, 34));

    /** A long fist window with a short elbow window inside it; a kick clip with its own window. */
    static final HitboxSet SET = new HitboxSet()
            .base(BODY)
            .window(Clip.PUNCH, 0.1f, 0.5f, FIST)
            .window(Clip.PUNCH, 0.3f, 0.4f, ELBOW)
            .window(Clip.KICK,  0.2f, 0.4f, FOOT);

    /** A spike: a HIT box that is always out, on a fighter whose clip has windows. */
    static final HitboxSet SPIKES = new HitboxSet()
            .base(BODY, Hitbox.hit("spike", rect(8, 0, 14, 34), JAB))
            .window(Clip.PUNCH, 0.1f, 0.2f, FIST)
            .window(Clip.PUNCH, 0.3f, 0.4f, FIST);

    World world;
    HitSystem system;
    final Array<Strike> strikes = new Array<>();

    @Before
    public void world() {
        world = new World();
        world.addSystem(new AnimationSystem());
        system = new HitSystem(strikes::add);
        world.addSystem(system);
    }

    @Test
    public void overlappingWindowsAreSeparateActivations() {
        var red = fighter("red", 0, 0, +1, SET, Team.RED, Team.BLUE);
        fighter("blue", 20, 0, -1, SET, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);

        ticks(12); // 0.2 s: the fist window open
        assertEquals(Array.with("fist"), names());
        ticks(9);  // 0.35 s: the elbow window opened inside it
        assertEquals(Array.with("fist", "elbow"), names());
        ticks(6);  // 0.45 s: the elbow window closed, the fist is still out and has struck already
        assertEquals(Array.with("fist", "elbow"), names());
        ticks(6);  // 0.55 s: everything closed
        assertEquals(2, strikes.size);
        assertFalse(Hitboxes.get(red).isAttacking());
    }

    @Test
    public void aWindowClosingForgetsOnlyItsOwnTargets() {
        var red  = fighter("red", 0, 0, +1, SET, Team.RED, Team.BLUE);
        var blue = fighter("blue", 20, 0, -1, SET, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);
        ticks(21); // 0.35 s: both windows have struck
        assertEquals(2, strikes.size);
        var struck = Hitboxes.get(red).struck;
        assertEquals(1 | 2, struck.get(blue, 0)); // window 0 and window 1

        ticks(6); // 0.45 s: the elbow window closed
        assertEquals(1, struck.get(blue, 0));
        ticks(6); // 0.55 s: the fist window closed
        assertEquals(0, struck.size);
    }

    @Test
    public void aPersistentHitBoxStrikesOnceUntilRearmedOrTheClipChanges() {
        var red = fighter("red", 0, 0, +1, SPIKES, Team.RED, Team.BLUE);
        fighter("blue", 20, 0, -1, SET, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);

        ticks(1);
        assertEquals(Array.with("spike"), names());
        ticks(29); // 0.5 s: two fist windows opened and closed meanwhile
        assertEquals(Array.with("spike", "fist", "fist"), names());

        Hitboxes.get(red).rearm();
        ticks(1);
        assertEquals(Array.with("spike", "fist", "fist", "spike"), names());

        play(red, Clip.KICK); // a new clip is a new activation for the base boxes too
        ticks(1);
        assertEquals(5, strikes.size);
        assertEquals("spike", strikes.peek().hit().name());
    }

    @Test
    public void aClipNameChangeIsANewActivationEvenWithTheSameAnimationObject() {
        var red = fighter("red", 0, 0, +1, SET, Team.RED, Team.BLUE);
        fighter("blue", 20, 0, -1, SET, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);
        ticks(21); // 0.35 s: fist and elbow
        assertEquals(2, strikes.size);

        Animator.get(red).type = Clip.KICK; // the time and the Animation stay: the kick's window contains 0.35 s
        ticks(1);
        assertEquals(3, strikes.size);
        assertEquals("foot", strikes.peek().hit().name());
        assertEquals("KICK", Hitboxes.get(red).clip);
    }

    @Test
    public void twoBoxesOfOneWindowAreOneActivation() {
        var set = new HitboxSet()
                .base(BODY)
                .window(Clip.PUNCH, 0.1f, 0.5f, FIST, ELBOW);
        var red = fighter("red", 0, 0, +1, set, Team.RED, Team.BLUE);
        fighter("blue", 20, 0, -1, SET, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);
        ticks(30);
        assertEquals(1, strikes.size);
        assertEquals("fist", strikes.first().hit().name()); // the first box out
    }

    // ------------------------------------------------------------------------

    private Array<String> names() {
        var names = new Array<String>();
        for (var s : strikes) names.add(s.hit().name());
        return names;
    }

    private Entity fighter(String name, int x, int y, int facing, HitboxSet set, Team team, Team target) {
        var frames = new Array<TextureRegion>();
        for (int i = 0; i < 10; i++) frames.add(new TextureRegion());
        var animator = new Animator(new Animation<>(0.1f, frames, Animation.PlayMode.NORMAL));
        animator.facing = facing;
        return world.createEntity()
                .add(new Name(name))
                .add(new Position(x, y))
                .add(animator)
                .add(new Hitboxes(set, team, target));
    }

    private static void play(Entity e, Clip clip) {
        var animator = Animator.get(e);
        animator.type = clip;
        animator.time = 0;
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) world.update(FIXED_DT);
    }
}
