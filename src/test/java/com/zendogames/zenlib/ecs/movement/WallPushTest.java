package com.zendogames.zenlib.ecs.movement;

import com.zendogames.zenlib.collision.CollisionMask;
import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.collision.Contact;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.*;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerMotion;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerTuning;
import com.zendogames.zenlib.ecs.systems.ContactSystem;
import com.zendogames.zenlib.ecs.systems.MovementSystem;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Pushing into an adjacent blocker must count as being blocked by it (tiny_link's semantics).
 * The sweep only reports a block when it tries a pixel, and the platformer's wall hold keeps a
 * pusher at rest, so a mover that arrives exactly flush (one approach in three at 3.2 px/tick,
 * every approach that ends with a 1 px step) reports nothing while pressing on: no {@code xHit},
 * no {@code Contacts.blocked}, no BLOCKED contact. That is why the demo's hero-hero bump fires
 * for some approaches and never for others (2026-10-13). Fixed the same day by the
 * {@code reportPush} step in {@code PlatformerMotion.after}, which these tests pin.
 */
public class WallPushTest {

    enum Mask implements CollisionMask {
        SOLID, PLAYER;
        @Override public int bit() { return 1 << ordinal(); }
    }

    static final PlatformerTuning TUNING = PlatformerTuning.player(16);

    static Entity hero(World world, int x, int moveX) {
        var input = new Input();
        input.moveX = moveX;
        return world.createEntity()
                .add(new Position(x, 0))
                .add(input)
                .add(new Contacts())
                .add(new Mover(new PlatformerMotion(TUNING)))
                .add(Collider.rect(Mask.PLAYER, -4, 0, 8, 16).blockedBy(Mask.SOLID, Mask.PLAYER));
    }

    static Entity floor(World world) {
        return world.createEntity().add(new Position(-1000, -10)).add(Collider.rect(Mask.SOLID, 0, 0, 3000, 10));
    }

    static void tick(World world) {
        world.update(SimulationStep.FIXED_DT);
    }

    @Test
    public void arrivingFlushThenPushingStillReportsABlockedContact() {
        var world = new World();
        var collision = new CollisionWorld(world);
        var blocked = new int[1];
        world.addSystem(new MovementSystem(collision));
        world.addSystem(new ContactSystem(collision, c -> {
            if (c.kind() == Contact.Kind.BLOCKED && c.isEnter() && Collider.get(c.other()).mask() == Mask.PLAYER) blocked[0]++;
        }));
        floor(world);
        hero(world, 100, 0);
        var a = hero(world, 100 - 8 - 1, +1); // one pixel short: the first moving tick lands flush, never probing into b

        for (int i = 0; i < 60; i++) tick(world);

        assertEquals("a is flush with b", 92, Position.get(a).x);
        assertEquals(+1, Mover.get(a).motion(PlatformerMotion.class).wallDir);
        assertEquals("one BLOCKED ENTER, then STAYs while pushing", 1, blocked[0]);
    }

    @Test
    public void pushingAWallReportsTheBlockEveryTick() {
        var world = new World();
        var collision = new CollisionWorld(world);
        world.addSystem(new MovementSystem(collision));
        floor(world);
        var wall = world.createEntity().add(new Position(100, 0)).add(Collider.rect(Mask.SOLID, 0, 0, 10, 100));
        var hero = hero(world, 100 - 4, +1); // right edge at 100: flush with the wall from the start
        var mover = Mover.get(hero);
        var contacts = Contacts.get(hero);

        for (int i = 0; i < 5; i++) {
            tick(world);
            assertEquals("tick " + i, 96, Position.get(hero).x);
            assertEquals("tick " + i, +1, mover.xHit);
            assertEquals("tick " + i + ": a block zeroes velocity, like a sweep block does", 0f, mover.xVel, 0f);
            assertEquals("tick " + i, 0f, mover.xRem, 0f);
            // ContactSystem is not in this world, so the mover's report is still in place after the tick
            assertSame("tick " + i, wall, contacts.blockerX);
            assertEquals("tick " + i, +1, contacts.hitX);
        }
    }

    @Test
    public void steeringAwayFromAnAdjacentWallReportsNothing() {
        var world = new World();
        var collision = new CollisionWorld(world);
        world.addSystem(new MovementSystem(collision));
        floor(world);
        world.createEntity().add(new Position(100, 0)).add(Collider.rect(Mask.SOLID, 0, 0, 10, 100));
        var hero = hero(world, 100 - 4, -1);

        for (int i = 0; i < 6; i++) { // from rest the first pixel takes three ticks
            tick(world);
            assertEquals("tick " + i, 0, Mover.get(hero).xHit);
            assertNull("tick " + i, Contacts.get(hero).blockerX);
        }
        assertTrue("walked away", Position.get(hero).x < 96);
    }
}
