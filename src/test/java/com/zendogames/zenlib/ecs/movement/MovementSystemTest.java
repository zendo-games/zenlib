package com.zendogames.zenlib.ecs.movement;

import com.badlogic.gdx.Input.Keys;
import com.zendogames.zenlib.collision.CollisionMask;
import com.zendogames.zenlib.collision.CollisionWorld;
import com.zendogames.zenlib.ecs.Entity;
import com.zendogames.zenlib.ecs.World;
import com.zendogames.zenlib.ecs.components.*;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerMotion;
import com.zendogames.zenlib.ecs.movement.platformer.PlatformerTuning;
import com.zendogames.zenlib.ecs.movement.topdown.TopDownMotion;
import com.zendogames.zenlib.ecs.movement.topdown.TopDownTuning;
import com.zendogames.zenlib.ecs.systems.InputSystem;
import com.zendogames.zenlib.ecs.systems.MovementSystem;
import com.zendogames.zenlib.input.InputControls;
import com.zendogames.zenlib.input.InputState;
import com.zendogames.zenlib.sim.SimulationStep;
import org.junit.Test;

import static org.junit.Assert.*;

/** {@link MovementSystem} inside a {@link World}: interpolation bookkeeping, collision routing, contacts, input. */
public class MovementSystemTest {

    enum Mask implements CollisionMask {
        SOLID, PLAYER;
        @Override public int bit() { return 1 << ordinal(); }
    }

    static void tick(World world) {
        world.update(SimulationStep.FIXED_DT);
    }

    static Entity wall(World world, int x, int y, int w, int h) {
        return world.createEntity().add(new Position(x, y)).add(Collider.rect(Mask.SOLID, 0, 0, w, h));
    }

    static Collider heroCollider() {
        return Collider.rect(Mask.PLAYER, 0, 0, 8, 16).blockedBy(Mask.SOLID);
    }

    @Test
    public void savesPrevAndMoves() {
        var world = new World();
        world.addSystem(new MovementSystem());
        var e = world.createEntity().add(new Position(10, 10)).add(new Mover(Motion.NONE, 120f, -60f));

        tick(world);
        var pos = Position.get(e);
        assertEquals(10, pos.prevX);
        assertEquals(10, pos.prevY);
        assertEquals(12, pos.x);
        assertEquals(9, pos.y);

        tick(world);
        assertEquals(12, pos.prevX);
        assertEquals(14, pos.x);
    }

    @Test
    public void withoutACollisionWorldEveryMoverIsOpen() {
        var world = new World();
        world.addSystem(new MovementSystem());
        wall(world, 20, 0, 10, 100);
        var e = world.createEntity().add(new Position(10, 10)).add(heroCollider()).add(new Mover(Motion.NONE, 600f, 0f));
        tick(world);
        assertEquals("walked straight through", 20, Position.get(e).x);
    }

    @Test
    public void collidersStopAtWallsAndReportToContacts() {
        var world = new World();
        var collision = new CollisionWorld(world);
        world.addSystem(new MovementSystem(collision));
        var wall = wall(world, 30, 0, 10, 100);
        var e = world.createEntity()
                .add(new Position(10, 10))
                .add(heroCollider())
                .add(new Contacts())
                .add(new Mover(Motion.NONE, 600f, 0f));

        tick(world); // 10 px per tick: 10 -> 20
        tick(world); // 20 -> 22, blocked
        var pos = Position.get(e);
        var mover = Mover.get(e);
        var contacts = Contacts.get(e);
        assertEquals(22, pos.x);
        assertEquals(+1, mover.xHit);
        assertSame(wall, contacts.blockerX);
        assertEquals(+1, contacts.hitX);
        assertNull(contacts.blockerY);
    }

    @Test
    public void onHitCallbacksFireOncePerBlockedAxisAndMayDespawn() {
        var world = new World();
        var collision = new CollisionWorld(world);
        world.addSystem(new MovementSystem(collision));
        wall(world, 30, 0, 10, 100);
        var mover = new Mover(Motion.NONE, 600f, 0f);
        var hits = new int[2];
        var seen = new Entity[1];
        mover.onHitX = (entity, m, dir) -> {
            hits[0]++;
            hits[1] = dir;
            seen[0] = entity;
            assertSame(mover, m);
            world.despawn(entity); // inside a system: deferred until MovementSystem returns
            assertTrue("still alive inside the callback", entity.isAlive());
        };
        mover.onHitY = (entity, m, dir) -> fail("y was never blocked");
        var e = world.createEntity().add(new Position(10, 10)).add(heroCollider()).add(mover);

        tick(world); // 10 -> 20, open
        assertEquals(0, hits[0]);
        tick(world); // 20 -> 22, blocked
        assertEquals(1, hits[0]);
        assertEquals(+1, hits[1]);
        assertSame(e, seen[0]);
        assertFalse("despawned after the system returned", e.isAlive());
    }

    @Test
    public void noCallbackIsFine() {
        var world = new World();
        var collision = new CollisionWorld(world);
        world.addSystem(new MovementSystem(collision));
        wall(world, 30, 0, 10, 100);
        var e = world.createEntity().add(new Position(10, 10)).add(heroCollider()).add(new Mover(Motion.NONE, 600f, 0f));
        tick(world);
        tick(world);
        assertEquals(+1, Mover.get(e).xHit);
    }

    @Test
    public void landingIsReportedAsABlockBelow() {
        var world = new World();
        var collision = new CollisionWorld(world);
        world.addSystem(new MovementSystem(collision));
        var floor = wall(world, 0, 0, 200, 16);
        var e = world.createEntity()
                .add(new Position(50, 30))
                .add(heroCollider())
                .add(new Contacts())
                .add(new Mover(new PlatformerMotion(PlatformerTuning.ballistic(16))));

        var p = Mover.get(e).motion(PlatformerMotion.class);
        for (int i = 0; i < 120 && !p.landed; i++) tick(world);
        assertTrue(p.landed);
        assertEquals(16, Position.get(e).y);
        var contacts = Contacts.get(e);
        assertSame(floor, contacts.blockerY);
        assertEquals(-1, contacts.hitY);

        // Resting: no new block report once the contacts are consumed
        contacts.clearBlocked();
        tick(world);
        assertNull(contacts.blockerY);
        assertTrue(p.grounded);
    }

    @Test
    public void noContactsComponentIsFine() {
        var world = new World();
        var collision = new CollisionWorld(world);
        world.addSystem(new MovementSystem(collision));
        wall(world, 30, 0, 10, 100);
        var e = world.createEntity().add(new Position(10, 10)).add(heroCollider()).add(new Mover(Motion.NONE, 600f, 0f));
        tick(world);
        tick(world);
        assertEquals(22, Position.get(e).x);
    }

    @Test
    public void noColliderMeansNoGround() {
        var world = new World();
        world.addSystem(new MovementSystem(new CollisionWorld(world)));
        wall(world, 0, 0, 200, 16);
        var e = world.createEntity().add(new Position(50, 30)).add(new Mover(new PlatformerMotion(PlatformerTuning.player(16))));
        for (int i = 0; i < 60; i++) tick(world);
        var p = Mover.get(e).motion(PlatformerMotion.class);
        assertFalse(p.grounded);
        assertTrue(Position.get(e).y < 0);
    }

    @Test
    public void inputDrivesTheMover() {
        var input = new InputState();
        var world = new World();
        world.addSystem(new InputSystem(input));
        world.addSystem(new MovementSystem());
        var e = world.createEntity()
                .add(new Position(0, 0))
                .add(new Input(InputControls.wasd(Keys.SPACE)))
                .add(new Mover(new TopDownMotion(TopDownTuning.walker(16))));

        input.keyDown(Keys.D);
        input.keyDown(Keys.W);
        for (int i = 0; i < 30; i++) frame(input, world);
        var mover = Mover.get(e);
        assertTrue(mover.xVel > 0);
        assertTrue(mover.yVel > 0);
        assertTrue(Position.get(e).x > 0);
        assertTrue(Position.get(e).y > 0);

        input.keyUp(Keys.D);
        input.keyUp(Keys.W);
        for (int i = 0; i < 30; i++) frame(input, world);
        assertFalse(mover.isMoving());
    }

    /** One frame with one tick: the state steps, the world updates, the frame ends. */
    static void frame(InputState input, World world) {
        input.step(SimulationStep.FIXED_DT);
        tick(world);
        input.endFrame();
    }

    @Test
    public void aMoverWithoutInputGetsNoIntent() {
        var world = new World();
        world.addSystem(new MovementSystem());
        var e = world.createEntity().add(new Position(0, 0)).add(new Mover(new TopDownMotion(TopDownTuning.walker(16))));
        for (int i = 0; i < 10; i++) tick(world);
        assertFalse(Mover.get(e).isMoving());
    }
}
