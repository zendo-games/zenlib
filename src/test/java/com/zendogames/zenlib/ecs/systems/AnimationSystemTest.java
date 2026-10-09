package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.Animator;
import org.junit.Test;

import static com.zendogames.zenlib.sim.SimulationStep.FIXED_DT;
import static org.junit.Assert.*;

/** Headless: empty {@code TextureRegion}s carry no texture, and {@code Animation} only indexes them. */
public class AnimationSystemTest {

    private static Animation<TextureRegion> frames(int count, float frameDuration, Animation.PlayMode mode) {
        var regions = new TextureRegion[count];
        for (int i = 0; i < count; i++) regions[i] = new TextureRegion();
        return new Animation<>(frameDuration, regions);
    }

    @Test
    public void advancesTimeAndPicksKeyframe() {
        var world = new World();
        world.addSystem(new AnimationSystem());
        var anim = frames(3, FIXED_DT * 2, Animation.PlayMode.NORMAL);
        var animator = new Animator(anim);
        world.createEntity().add(animator);

        assertSame(anim.getKeyFrame(0), animator.keyframe);

        world.update(FIXED_DT * 2);
        assertEquals(FIXED_DT * 2, animator.time, 0.0001f);
        assertSame(anim.getKeyFrames()[1], animator.keyframe);
    }

    @Test
    public void scaleEasesBackAndFacingFlipsX() {
        var world = new World();
        world.addSystem(new AnimationSystem());
        var animator = new Animator(frames(1, 1f, Animation.PlayMode.NORMAL));
        animator.scale.set(2f, 0.5f);
        animator.scaleReturnSpeed = 60f; // one unit per second of ticks -> 1 per tick at FIXED_DT
        animator.facing = -1;
        world.createEntity().add(animator);

        world.update(FIXED_DT);
        assertEquals(-1f, animator.scale.x, 0.001f); // 2 -> 1, then negated by facing
        assertEquals(1f, animator.scale.y, 0.001f);  // 0.5 -> 1 in one step, clamped at the default

        world.update(FIXED_DT);
        assertEquals(-1f, animator.scale.x, 0.001f); // stays at default, facing still applied
    }

    @Test
    public void startResetsTimeAndPlayKeepsIt() {
        var a = frames(2, 1f, Animation.PlayMode.NORMAL);
        var b = frames(2, 1f, Animation.PlayMode.NORMAL);
        var animator = new Animator(a);
        animator.time = 0.5f;

        assertEquals(2f, animator.play(b), 0.001f);
        assertSame(b, animator.animation);
        assertEquals(0.5f, animator.time, 0.001f);

        animator.time = 2.5f;
        assertTrue(animator.isComplete());
        assertNull(animator.type); // raw animation, no asset key
    }

    @Test
    public void rectAppliesOriginAndScale() {
        var animator = new Animator(frames(1, 1f, Animation.PlayMode.NORMAL));
        animator.size.set(16, 24);
        animator.origin.set(8, 0);
        animator.scale.set(2, 2);
        var rect = animator.rect(100, 50);
        assertEquals(100 - 16, rect.x, 0.001f);
        assertEquals(50, rect.y, 0.001f);
        assertEquals(32, rect.width, 0.001f);
        assertEquals(48, rect.height, 0.001f);
    }
}
