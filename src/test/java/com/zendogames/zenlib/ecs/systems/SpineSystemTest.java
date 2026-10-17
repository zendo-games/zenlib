package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Event;
import com.esotericsoftware.spine.SkeletonJson;
import com.esotericsoftware.spine.Skin;
import com.esotericsoftware.spine.attachments.*;
import com.zendogames.zenlib.assets.Assets;
import com.zendogames.zenlib.assets.SpineData;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Position;
import com.zendogames.zenlib.ecs.components.SpineAnimator;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

/**
 * Headless: spineboy's JSON export loads through an {@link AttachmentLoader} that builds attachments
 * without texture regions, so no atlas or texture is needed; bones, animations, events and skins load.
 * The file is the stock Spine example under {@code src/main/resources/spine/spineboy/}.
 */
public class SpineSystemTest {

    /** Bare attachments with no texture region: deform timelines need them to exist, nothing here draws them. */
    private static final AttachmentLoader NO_TEXTURES = new AttachmentLoader() {
        @Override public RegionAttachment      newRegionAttachment(Skin skin, String placeholder, String name, String path, Sequence sequence) { return new RegionAttachment(name, sequence); }
        @Override public MeshAttachment        newMeshAttachment(Skin skin, String placeholder, String name, String path, Sequence sequence)   { return new MeshAttachment(name, sequence); }
        @Override public BoundingBoxAttachment newBoundingBoxAttachment(Skin skin, String placeholder, String name) { return new BoundingBoxAttachment(name); }
        @Override public ClippingAttachment    newClippingAttachment(Skin skin, String placeholder, String name)    { return new ClippingAttachment(name); }
        @Override public PathAttachment        newPathAttachment(Skin skin, String placeholder, String name)        { return new PathAttachment(name); }
        @Override public PointAttachment       newPointAttachment(Skin skin, String placeholder, String name)       { return new PointAttachment(name); }
    };

    private static SpineData spineboy;

    @BeforeClass
    public static void loadSpineboy() throws Exception {
        var url = SpineSystemTest.class.getResource("/" + Assets.LIBRARY_PREFIX  + "spine/spineboy/spineboy-pro.json");
        assertNotNull("spineboy-pro.json on the test classpath", url);
        var json = new SkeletonJson(NO_TEXTURES);
        var skeleton = json.readSkeletonData(new FileHandle(new File(url.toURI())));
        var mixes = new AnimationStateData(skeleton);
        spineboy = new SpineData(skeleton, mixes, true);
    }

    private static World world() {
        var world = new World();
        world.addSystem(new SpineSystem());
        return world;
    }

    @Test
    public void advancesTrackTimeAndPosesAtTheEntityPosition() {
        var world = world();
        var anim = new SpineAnimator(spineboy);
        anim.play("run");
        world.createEntity().add(new Position(100, 50)).add(anim);

        world.update(FIXED_DT);

        assertEquals(FIXED_DT, anim.state.getTrack(0).getTrackTime(), 0.0001f);
        assertEquals(100f, anim.skeleton.getX(), 0f);
        assertEquals(50f, anim.skeleton.getY(), 0f);
        assertEquals(100f, anim.skeleton.getRootBone().getAppliedPose().getWorldX(), 0.01f);
        // The hip sits above the root in the setup pose and stays above it while running
        assertTrue(anim.bonePose("hip").getWorldY() > 50f);
    }

    @Test
    public void facingFlipsTheSkeletonAndScaleEasesBack() {
        var world = world();
        var anim = new SpineAnimator(spineboy);
        anim.scale.set(2f, 0.5f);
        anim.scaleReturnSpeed = 60f; // one unit per second of ticks -> 1 per tick at FIXED_DT
        anim.facing = -1;
        world.createEntity().add(new Position(0, 0)).add(anim);

        world.update(FIXED_DT);
        assertEquals(-1f, anim.scale.x, 0.001f); // 2 -> 1, then negated by facing
        assertEquals(1f, anim.scale.y, 0.001f);  // 0.5 -> 1 in one step, clamped at the default
        assertEquals(-1f, anim.skeleton.getScaleX(), 0.001f);
        assertEquals(1f, anim.skeleton.getScaleY(), 0.001f);
    }

    @Test
    public void playKeepsTheCurrentAnimationAndStartRestartsIt() {
        var world = world();
        var anim = new SpineAnimator(spineboy, "run");
        world.createEntity().add(new Position(0, 0)).add(anim);
        world.update(FIXED_DT);
        world.update(FIXED_DT);

        var entry = anim.play("run");
        assertTrue(anim.is("run"));
        assertEquals(2 * FIXED_DT, entry.getTrackTime(), 0.0001f);

        entry = anim.start("run");
        assertEquals(0f, entry.getTrackTime(), 0f);

        anim.play("walk");
        assertTrue(anim.is("walk"));
        assertTrue(anim.isNot("run"));
    }

    @Test
    public void oneShotCompletesAndEventsFireOnTheTick() {
        var world = world();
        var anim = new SpineAnimator(spineboy);
        var events = new Array<Event>();
        anim.onEvent(events::add);
        anim.start("jump", false); // has one "footstep" event, at 1.2 s
        world.createEntity().add(new Position(0, 0)).add(anim);

        float duration = spineboy.skeleton().findAnimation("jump").getDuration();
        int ticks = (int) Math.ceil(duration / FIXED_DT) + 1;
        for (int i = 0; i < ticks; i++) {
            assertFalse("not complete before tick " + i, anim.isComplete() && i * FIXED_DT < duration);
            world.update(FIXED_DT);
        }

        assertTrue(anim.isComplete());
        assertEquals(1, events.size);
        assertEquals("footstep", events.first().getData().getName());
    }

    @Test
    public void rectIsTheSetupBoundsPlacedAtTheRootAndMirroredByFacing() {
        var data = spineboy.skeleton();
        var anim = new SpineAnimator(spineboy);

        var rect = anim.rect(100, 50);
        assertEquals(100 + data.getX(), rect.x, 0.001f);
        assertEquals(50 + data.getY(), rect.y, 0.001f);
        assertEquals(data.getWidth(), rect.width, 0.001f);
        assertEquals(data.getHeight(), rect.height, 0.001f);

        anim.scale.x = -1;
        var mirrored = anim.rect(100, 50);
        assertEquals(100 - (rect.x + rect.width - 100), mirrored.x, 0.001f);
        assertEquals(rect.width, mirrored.width, 0.001f);
    }

    @Test
    public void skinSwitchesByName() {
        var anim = new SpineAnimator(spineboy);
        assertNull(anim.skeleton.getSkin()); // a new skeleton shows the default skin without naming it
        anim.skin("default");
        assertEquals("default", anim.skeleton.getSkin().getName());
    }
}
