package com.zendogames.zenlib.ecs.movement;

import com.badlogic.gdx.utils.Array;
import com.github.tommyettinger.gdcrux.PointI2;
import com.zendogames.zenlib.collision.Block;
import com.zendogames.zenlib.collision.shapes.CollisionAASeg;
import com.zendogames.zenlib.collision.shapes.CollisionRect;
import com.zendogames.zenlib.collision.shapes.CollisionShape;

/**
 * A {@link MoveWorld} over a handful of placed shapes, no ECS: solid rects and one-way platforms
 * tested with the real shape math. Records the {@code onBlocked} calls for the tests to inspect.
 */
final class ScriptedWorld implements MoveWorld {

    record Body(CollisionShape shape, PointI2 pos) {}

    final Array<Body> solids  = new Array<>();
    final Array<Body> oneWays = new Array<>();

    int blocks;
    int lastDx, lastDy;

    private final PointI2 probePos    = new PointI2();
    private final PointI2 probeOffset = new PointI2();

    ScriptedWorld solid(int x, int y, int w, int h) {
        solids.add(new Body(new CollisionRect(0, 0, w, h), new PointI2(x, y)));
        return this;
    }

    /** A one-way platform along {@code y} from {@code x} to {@code x + w}, stood on from above. */
    ScriptedWorld platform(int x, int y, int w) {
        oneWays.add(new Body(new CollisionAASeg(CollisionAASeg.Axis.HORIZONTAL, 0, 0, w, CollisionAASeg.Facing.POSITIVE), new PointI2(x, y)));
        return this;
    }

    @Override
    public Block probe(CollisionShape shape, int x, int y, int dx, int dy) {
        var pos    = probePos.set(x, y);
        var offset = probeOffset.set(dx, dy);
        for (int i = 0; i < solids.size; i++) {
            var body = solids.get(i);
            if (shape.overlaps(body.shape(), pos, body.pos(), offset)) {
                return Block.SOLID;
            }
        }
        for (int i = 0; i < oneWays.size; i++) {
            var body = oneWays.get(i);
            var seg  = (CollisionAASeg) body.shape();
            if (shape.overlaps(seg, pos, body.pos(), offset) && seg.blocks(shape, pos, body.pos(), dx, dy)) {
                return Block.ONE_WAY;
            }
        }
        return Block.NONE;
    }

    @Override
    public void onBlocked(int dx, int dy) {
        blocks++;
        lastDx = dx;
        lastDy = dy;
    }
}
