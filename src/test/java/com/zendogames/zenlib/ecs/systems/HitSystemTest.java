package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.*;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.assets.AnimType;
import com.zendogames.zenlib.assets.Asset;
import com.zendogames.zenlib.assets.Assets;
import com.zendogames.zenlib.assets.AtlasType;
import com.zendogames.zenlib.assets.SpineData;
import com.zendogames.zenlib.collision.CollisionMask;
import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.combat.Hitbox;
import com.zendogames.zenlib.ecs.combat.HitboxSet;
import com.zendogames.zenlib.ecs.combat.Strike;
import com.zendogames.zenlib.ecs.components.Animator;
import com.zendogames.zenlib.ecs.components.Hitboxes;
import com.zendogames.zenlib.ecs.components.Name;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.components.SpineAnimator;
import com.zendogames.zenlib.ecs.systems.HitSystem.Space;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static com.zendogames.zenlib.FramePool.pi2;
import static com.zendogames.zenlib.ecs.combat.Hitbox.circ;
import static com.zendogames.zenlib.ecs.combat.Hitbox.rect;
import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

/**
 * Headless: a World with the AnimationSystem, the SpineSystem and the HitSystem; sprite clips are
 * driven by setting {@code Animator} fields, spineboy loads from the classpath without textures as
 * in {@code SpineSystemTest}. Everything runs in the library's {@code FLAT} space except the last
 * section, which plugs in a game's space ({@link BrawlSpace}, a 2.5D brawler as the {@code Space}
 * javadoc sketches it) to pin what an override controls.
 */
public class HitSystemTest {

    enum Team implements CollisionMask {
        RED, BLUE;
        @Override public int bit() { return 1 << ordinal(); }
    }

    /** Clip names for sprite fighters; never loaded, the test sets {@code Animator.type} directly. */
    enum Clip implements AnimType {
        PUNCH, CROUCH;
        private final Asset<Animation<TextureRegion>> asset = new Asset<>(this);
        @Override public Asset<Animation<TextureRegion>> asset() { return asset; }
        @Override public AtlasType atlas()   { return null; }
        @Override public String regionName() { return name(); }
    }

    static final Hitbox.Attack JAB = new Hitbox.Attack(1, 1, 0);

    /** Head over body; a crouch shrinks the body for the whole clip; a punch is two windows, so two hits. */
    static final HitboxSet SET = new HitboxSet()
            .base(Hitbox.hurt("head", circ(0, 40, 6)),
                  Hitbox.hurt("body", rect(-8, 0, 16, 34)))
            .clip(Clip.CROUCH, Hitbox.hurt("body", rect(-8, 0, 16, 20)))
            .window(Clip.PUNCH, 0.2f, 0.4f, Hitbox.hit("fist", rect(8, 24, 14, 6), JAB))
            .window(Clip.PUNCH, 0.6f, 0.7f, Hitbox.hit("fist", rect(8, 24, 14, 6), JAB));

    /** Bare attachments with no texture region, as in {@code SpineSystemTest}. */
    private static final AttachmentLoader NO_TEXTURES = new AttachmentLoader() {
        @Override public RegionAttachment      newRegionAttachment(Skin skin, String placeholder, String name, String path, Sequence sequence) { return new RegionAttachment(name, sequence); }
        @Override public MeshAttachment        newMeshAttachment(Skin skin, String placeholder, String name, String path, Sequence sequence)   { return new MeshAttachment(name, sequence); }
        @Override public BoundingBoxAttachment newBoundingBoxAttachment(Skin skin, String placeholder, String name) { return new BoundingBoxAttachment(name); }
        @Override public ClippingAttachment    newClippingAttachment(Skin skin, String placeholder, String name)    { return new ClippingAttachment(name); }
        @Override public PathAttachment        newPathAttachment(Skin skin, String placeholder, String name)        { return new PathAttachment(name); }
        @Override public PointAttachment       newPointAttachment(Skin skin, String placeholder, String name)       { return new PointAttachment(name); }
    };

    /** A game's space: y is the floor line, {@link Jump} raises the art, floor lines closer than {@link #BAND} can trade blows. */
    enum BrawlSpace implements Space {
        STREET;

        static final int BAND = 12;

        @Override public void anchor(Entity e, PointI2 out) {
            var pos = Position.get(e);
            out.set(pos.x, pos.y + (Jump.has(e) ? Jump.get(e).height : 0));
        }

        @Override public boolean touches(Entity attacker, Hitboxes.Active hit, PointI2 hitAt, Entity target, Hitboxes.Active hurt, PointI2 hurtAt) {
            int floorA = Position.get(attacker).y, floorT = Position.get(target).y;
            if (Math.abs(floorA - floorT) >= BAND) return false;
            return hit.box.overlaps(Hitboxes.get(attacker).facing, pi2(hitAt.x, hitAt.y - floorA),
                    hurt.box, Hitboxes.get(target).facing, pi2(hurtAt.x, hurtAt.y - floorT));
        }

        @Override public boolean near(Entity e, Entity other, float reach) {
            var a = Position.get(e);
            var b = Position.get(other);
            return Math.abs(b.x - a.x) <= reach && Math.abs(b.y - a.y) < BAND;
        }
    }

    /** The brawler's jump height above its floor line, pixels; a game component. */
    static final class Jump implements Component {
        static final ComponentType<Jump> TYPE = ComponentType.register(Jump.class);
        static Jump    get(Entity e) { return TYPE.get(e); }
        static boolean has(Entity e) { return TYPE.has(e); }
        int height;
        Jump(int height) { this.height = height; }
    }

    private static SpineData spineboy;

    World world;
    HitSystem system;
    final Array<Strike> strikes = new Array<>();

    @BeforeClass
    public static void loadSpineboy() throws Exception {
        var url = HitSystemTest.class.getResource("/" + Assets.LIBRARY_PREFIX + "spine/spineboy/spineboy-pro.json");
        assertNotNull("spineboy-pro.json on the test classpath", url);
        var json = new SkeletonJson(NO_TEXTURES);
        json.setScale(0.25f);
        var skeleton = json.readSkeletonData(new FileHandle(new File(url.toURI())));
        spineboy = new SpineData(skeleton, new AnimationStateData(skeleton), false);
    }

    private void world() {
        world(null);
    }

    /** A null space is the one-argument constructor's {@code FLAT}. */
    private void world(Space space) {
        world = new World();
        world.addSystem(new AnimationSystem());
        world.addSystem(new SpineSystem());
        system = (space == null) ? new HitSystem(strikes::add) : new HitSystem(space, strikes::add);
        world.addSystem(system);
    }

    // ------------------------------------------------------------------------
    // Activations
    // ------------------------------------------------------------------------

    @Test
    public void oneStrikePerWindow() {
        world();
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE);
        var blue = fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);

        ticks(30); // 0.5 s: through the first window
        assertEquals(1, strikes.size);
        var s = strikes.first();
        assertSame(red, s.attacker());
        assertSame(blue, s.target());
        assertEquals("fist", s.hit().name());
        assertEquals("body", s.hurt().name()); // the fist is under the head
        assertEquals(+1, s.facing());
        assertEquals(20, s.dx());
        assertEquals(0, s.dy());
        assertEquals(+1, s.away());

        ticks(30); // the second window lands again
        assertEquals(2, strikes.size);
        assertFalse(Hitboxes.get(red).isAttacking());
        assertEquals(2, system.total);
    }

    @Test
    public void restartRearmsAnOpenWindow() {
        world();
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE);
        var blue = fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);
        ticks(18); // 0.3 s, inside the first window
        assertEquals(1, strikes.size);
        assertTrue(Hitboxes.get(red).struck.contains(blue, true));

        Animator.get(red).time = 0.2f; // back to the window's start: the time range is open again at once
        ticks(1);
        assertEquals(2, strikes.size);

        Hitboxes.get(red).rearm(); // from code, mid-window
        ticks(1);
        assertEquals(3, strikes.size);
    }

    @Test
    public void aLoopingClipRearmsEachLoop() {
        world();
        var red = fighter("red", 0, 0, +1, Team.RED, Team.BLUE);
        fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        Animator.get(red).animation.setPlayMode(Animation.PlayMode.LOOP); // 1.0 s, wrapped
        play(red, Clip.PUNCH);

        ticks(60);
        assertEquals(2, strikes.size); // both windows of the first loop
        ticks(18);
        assertEquals(3, strikes.size); // the first window again, 0.3 s into the second loop
    }

    @Test
    public void tradesReportBothStrikes() {
        world();
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE);
        var blue = fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);
        play(blue, Clip.PUNCH);

        ticks(18);
        assertEquals(2, strikes.size);
        assertSame(red, strikes.get(0).attacker());
        assertSame(blue, strikes.get(1).attacker());
        assertEquals(-1, strikes.get(1).away());
    }

    // ------------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------------

    @Test
    public void facingMirrorsTheBoxesAndAwayIsTheTargetSide() {
        world();
        var red    = fighter("red",    40, 0, -1, Team.RED,  Team.BLUE);
        var front  = fighter("front",  20, 0, +1, Team.BLUE, Team.RED);
        var behind = fighter("behind", 60, 0, -1, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);
        ticks(18);
        assertEquals(1, strikes.size);
        assertSame(front, strikes.first().target());
        assertEquals(-1, strikes.first().facing());
        assertEquals(-1, strikes.first().away());
        assertFalse(Hitboxes.get(behind).find("body").touching);
        assertTrue(Hitboxes.get(front).find("body").touching);
        assertTrue(Hitboxes.get(red).find("fist").touching);
    }

    @Test
    public void boxesMeetWhereTheyAreDrawn() {
        world();
        var red   = fighter("red",   0,  100, +1, Team.RED,  Team.BLUE); // on a platform: the fist is at 124..130
        var step  = fighter("step",  20, 112, -1, Team.BLUE, Team.RED);  // 12 px higher: the body (112..146) is under the fist
        var ledge = fighter("ledge", 20, 130, -1, Team.BLUE, Team.RED);  // body from 130: touching the fist's top edge is not overlapping
        fighter("floor", 20, 0, -1, Team.BLUE, Team.RED);                // on the floor below: the same x, nowhere near
        play(red, Clip.PUNCH);
        ticks(18);
        assertEquals(1, strikes.size);
        assertSame(step, strikes.first().target());
        assertEquals(-12, strikes.first().dy());
        assertFalse(Hitboxes.get(ledge).find("body").touching);

        Position.get(ledge).y = 129; // one pixel lower: the feet now overlap the fist
        ticks(1);
        assertEquals(2, strikes.size);
        assertSame(ledge, strikes.get(1).target());
    }

    @Test
    public void teamsAndVulnerability() {
        world();
        var red = fighter("red", 0, 0, +1, Team.RED, Team.BLUE);
        fighter("friend", 20, 0, -1, Team.RED, Team.BLUE);
        var blue = fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        Hitboxes.get(blue).vulnerable = false;
        play(red, Clip.PUNCH);
        ticks(15); // 0.25 s, in the window
        assertEquals(0, strikes.size);

        Hitboxes.get(blue).vulnerable = true; // the window is still open and has struck no one
        ticks(1);
        assertEquals(1, strikes.size);
        assertSame(blue, strikes.first().target());
    }

    @Test
    public void aClipBoxReplacesTheBaseBoxOfItsName() {
        world();
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE);
        var blue = fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        play(blue, Clip.CROUCH);
        play(red, Clip.PUNCH);
        ticks(30);
        assertEquals(0, strikes.size); // ducked under it

        var boxes = Hitboxes.get(blue);
        assertEquals(2, boxes.active.size); // head and the one body
        assertEquals(20f, boxes.find("body").box.right().top(new Position(0, 0)), 0f);
    }

    @Test
    public void withoutAnAnimatorOnlyBaseBoxesAreOutAndFacingIsKept() {
        world();
        var post = world.createEntity()
                .add(new Name("post"))
                .add(new Position(0, 0))
                .add(new Hitboxes(SET, Team.BLUE));
        Hitboxes.get(post).facing = -1;
        ticks(1);
        var boxes = Hitboxes.get(post);
        assertNull(boxes.clip);
        assertEquals(2, boxes.active.size);
        assertEquals(-1, boxes.facing);
        assertEquals(0, boxes.targets);
    }

    // ------------------------------------------------------------------------
    // Spine bones
    // ------------------------------------------------------------------------

    @Test
    public void boneBoxesFollowTheSkeleton() {
        world();
        var boxes = new HitboxSet().base(Hitbox.hurt("head", circ(0, 0, 4)).on("head"));
        var right = spineFighter(100, 50, +1, boxes);
        var left  = spineFighter(200, 50, -1, boxes);

        ticks(1);
        var headR = Hitboxes.get(right).find("head");
        var headL = Hitboxes.get(left).find("head");
        assertEquals(expectedHeadY(right), headR.y);
        assertEquals(-headR.x, headL.x, 1); // mirrored about the feet
        int standing = headR.y;

        ticks(44); // about 0.73 s into the jump, near the top
        assertEquals(expectedHeadY(right), Hitboxes.get(right).find("head").y);
        assertTrue("head rose: " + standing + " -> " + Hitboxes.get(right).find("head").y,
                Hitboxes.get(right).find("head").y > standing + 60);
    }

    @Test(expected = GdxRuntimeException.class)
    public void anUnknownBoneThrows() {
        world();
        var boxes = new HitboxSet().base(Hitbox.hurt("tail", circ(0, 0, 4)).on("tail"));
        spineFighter(0, 0, +1, boxes);
        ticks(1);
    }

    // ------------------------------------------------------------------------
    // Responses and queries
    // ------------------------------------------------------------------------

    @Test
    public void callbacksRunAfterTheResponderTargetFirst() {
        var order = new Array<String>();
        world();
        system.responder = s -> order.add("responder " + Name.of(s.target()));
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE);
        var blue = fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        Hitboxes.get(red).onStrike  = (e, s) -> order.add("onStrike " + Name.of(e));
        Hitboxes.get(blue).onStruck = (e, s) -> order.add("onStruck " + Name.of(e));
        Hitboxes.get(red).onStruck  = (e, s) -> order.add("never: red is not struck");
        play(red, Clip.PUNCH);

        ticks(18);
        assertEquals("[responder blue, onStruck blue, onStrike red]", order.toString());
    }

    @Test
    public void aDespawnedTargetLeavesTheStruckList() {
        world();
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE);
        var blue = fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);
        ticks(18);
        assertEquals(1, strikes.size);
        world.despawn(blue);
        ticks(1);
        assertEquals(0, Hitboxes.get(red).struck.size);
    }

    @Test
    public void aResponderMayDespawnTheTarget() {
        world();
        system.responder = s -> s.target().world().despawn(s.target());
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE);
        var blue = fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        var struck = new Array<Entity>();
        Hitboxes.get(blue).onStruck = (e, s) -> struck.add(e); // still readable: the despawn waits for the system to return
        play(red, Clip.PUNCH);

        ticks(18);
        assertEquals(1, struck.size);
        assertFalse(blue.isAlive());
        assertEquals(1, system.fighters().entities().size);
    }

    @Test
    public void recentKeepsTheLastSixteen() {
        world();
        var red = fighter("red", 0, 0, +1, Team.RED, Team.BLUE);
        fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        Animator.get(red).type = Clip.PUNCH;
        for (int i = 0; i < HitSystem.RECENT + 4; i++) {
            Animator.get(red).time = 0.25f; // held inside the first window
            Hitboxes.get(red).rearm();
            ticks(1);
        }
        assertEquals(HitSystem.RECENT + 4, system.total);
        assertEquals(HitSystem.RECENT, system.recent.size);
        assertEquals(1, system.strikes.size); // this tick's only
    }

    @Test
    public void inRangeCountsEitherSidesTargetsAndVulnerability() {
        world();
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE);
        var blue = fighter("blue", 30, 0, -1, Team.BLUE, Team.RED);
        assertTrue(system.inRange(red, 40));
        assertTrue(system.inRange(blue, 40)); // either side's targets count
        assertFalse(system.inRange(red, 20));

        Hitboxes.get(blue).vulnerable = false;
        assertFalse(system.inRange(red, 40));
    }

    @Test
    public void inRangeIsARadiusInFlatAndIgnoresFriends() {
        world();
        var red = fighter("red", 0, 0, +1, Team.RED, Team.BLUE);
        fighter("friend", 10, 0, -1, Team.RED, Team.BLUE);
        assertFalse(system.inRange(red, 40));

        var blue = fighter("blue", 30, 40, -1, Team.BLUE, Team.RED); // 50 away
        assertFalse(system.inRange(red, 49));
        assertTrue(system.inRange(red, 50));
        world.despawn(blue);
        assertFalse(system.inRange(red, 50));
    }

    // ------------------------------------------------------------------------
    // A game's space
    // ------------------------------------------------------------------------

    @Test
    public void aGameSpaceDecidesWhichBoxesMeet() {
        world(BrawlSpace.STREET);
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE); // the fist is 24..30 up from the floor line
        var down = fighter("down", 20, -11, -1, Team.BLUE, Team.RED); // 11 px down the street: in the band, body 0..34 up from its own line
        var up   = fighter("up",   20, 12, -1, Team.BLUE, Team.RED);  // 12 px up: out of the band, though its sprite overlaps the fist
        play(red, Clip.PUNCH);
        ticks(18);
        assertEquals(1, strikes.size);
        assertSame(down, strikes.first().target());
        assertEquals(11, strikes.first().dy());
        assertFalse(Hitboxes.get(up).find("body").touching);

        strikes.clear(); // the same scene in FLAT: both sprites overlap the fist, so both are struck
        world();
        red = fighter("red", 0, 0, +1, Team.RED, Team.BLUE);
        down = fighter("down", 20, -11, -1, Team.BLUE, Team.RED);
        up = fighter("up", 20, 12, -1, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);
        ticks(18);
        assertEquals(2, strikes.size);
        assertSame(down, strikes.get(0).target());
        assertEquals("head", strikes.get(0).hurt().name()); // its head is at the fist's height on screen
        assertSame(up, strikes.get(1).target());
    }

    @Test
    public void aGameAnchorRaisesEveryBox() {
        world(BrawlSpace.STREET);
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE).add(new Jump(30)); // the fist passes over the head
        var blue = fighter("blue", 20, 0, -1, Team.BLUE, Team.RED);
        play(red, Clip.PUNCH);
        ticks(18);
        assertEquals(0, strikes.size);
        assertEquals(30, Hitboxes.get(red).anchor.y);
        assertEquals(0, Hitboxes.get(red).find("fist").y); // not a bone box: no offset, the anchor carries the jump

        blue.add(new Jump(30)); // both in the air, the same window still open
        ticks(1);
        assertEquals(1, strikes.size);
    }

    @Test
    public void boneOffsetsAreFromTheGameAnchor() {
        world(BrawlSpace.STREET);
        var boxes  = new HitboxSet().base(Hitbox.hurt("head", circ(0, 0, 4)).on("head"));
        var ground = spineFighter(100, 50, +1, boxes);
        var raised = spineFighter(200, 50, +1, boxes).add(new Jump(10));
        SpineAnimator.get(raised).origin.y = -10 / SpineAnimator.get(raised).scale.y; // the art drawn 10 px up, as the anchor says
        ticks(1);

        var headY = SpineAnimator.get(raised).bonePose("head").getWorldY() - SpineAnimator.get(ground).bonePose("head").getWorldY();
        assertEquals("the skeleton is raised", 10, headY, 0.01f);
        assertEquals(expectedHeadY(raised), Hitboxes.get(raised).find("head").y);
        assertEquals(Hitboxes.get(ground).find("head").y, Hitboxes.get(raised).find("head").y); // the jump is added once, by the anchor
    }

    @Test
    public void aGameSpaceDecidesWhatIsInRange() {
        world(BrawlSpace.STREET);
        var red  = fighter("red",  0,  0, +1, Team.RED,  Team.BLUE);
        var blue = fighter("blue", 40, 11, -1, Team.BLUE, Team.RED); // a radius would call it 41.5 away
        assertTrue(system.inRange(red, 40));
        Position.get(blue).y = 12; // off the band
        assertFalse(system.inRange(red, 40));
    }

    // ------------------------------------------------------------------------

    private Entity fighter(String name, int x, int y, int facing, Team team, Team target) {
        var frames = new Array<TextureRegion>();
        for (int i = 0; i < 10; i++) frames.add(new TextureRegion());
        var animator = new Animator(new Animation<>(0.1f, frames, Animation.PlayMode.NORMAL));
        animator.facing = facing;
        return world.createEntity()
                .add(new Name(name))
                .add(new Position(x, y))
                .add(animator)
                .add(new Hitboxes(SET, team, target));
    }

    private static void play(Entity e, Clip clip) {
        var animator = Animator.get(e);
        animator.type = clip; // what Animator.start does, minus loading the asset
        animator.time = 0;
    }

    private Entity spineFighter(int x, int y, int facing, HitboxSet set) {
        var animator = new SpineAnimator(spineboy, "jump");
        animator.facing = facing;
        animator.defaultScale.set(0.5f, 0.5f);
        animator.scale.set(0.5f, 0.5f);
        return world.createEntity()
                .add(new Name("spineboy"))
                .add(new Position(x, y))
                .add(animator)
                .add(new Hitboxes(set, Team.RED, Team.BLUE));
    }

    private static int expectedHeadY(Entity e) {
        var pose = SpineAnimator.get(e).bonePose("head");
        return Math.round(pose.getWorldY() - Hitboxes.get(e).anchor.y);
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) world.update(FIXED_DT);
    }
}
