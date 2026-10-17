package com.zendogames.zenlib.ecs.systems;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.zendogames.zenlib.ZenFlag;
import com.zendogames.zenlib.ecs.*;
import com.zendogames.zenlib.ecs.components.*;
import com.zendogames.zenlib.render.RenderLayer;
import com.zendogames.zenlib.render.Visual;

/**
 * Render-rate system: draws every entity with a {@link Position} and at least one
 * {@link Renderable}, sorted by layer, then order, then entity id so the order is stable.
 * {@code World.render} runs it from the screen's {@code renderWorld}, between
 * {@code batch.begin()} and {@code end()}, with the projection already set to the world camera.
 * Positions are interpolated with {@code World.alpha()} while {@code ZenFlag.RENDER_INTERP} is on.
 * <p>
 * The library's {@link Image}, {@link Animator}, {@link SpineAnimator} and {@link TileLayer} are always drawn;
 * a game passes the types of its own {@code Renderable} subclasses to the constructor.
 */
public class RenderSystem extends EntitySystem {

    private final Batch batch;
    private final Array<ComponentType<? extends Renderable>> types;

    private final Array<Item> items;
    private final Pool<Item> pool;

    private Family family;

    @SafeVarargs
    public RenderSystem(Batch batch, ComponentType<? extends Renderable>... gameTypes) {
        super(SystemOrder.MANUAL);
        this.batch = batch;
        this.types = new Array<>();
        this.types.add(Image.TYPE);
        this.types.add(Animator.TYPE);
        this.types.add(SpineAnimator.TYPE);
        this.types.add(TileLayer.TYPE);
        for (var type : gameTypes) {
            this.types.add(type);
        }
        this.items = new Array<>(false, 256);
        this.pool = new Pool<>() {
            @Override
            protected Item newObject() {
                return new Item();
            }
        };
    }

    @Override
    public boolean runsOnTick() {
        return false;
    }

    @Override
    public void addedToWorld(World world) {
        var keys = new Family.Key[types.size];
        for (int i = 0; i < types.size; i++) {
            keys[i] = types.get(i);
        }
        family = world.family(Family.all(Position.TYPE).one(keys));
    }

    @Override
    public void removedFromWorld(World world) {
        pool.freeAll(items);
        items.clear();
        family = null;
    }

    @Override
    public void update(float dt) {
        pool.freeAll(items);
        items.clear();

        var alpha = world.alpha();
        var interpolate = ZenFlag.RENDER_INTERP.on();

        var entities = family.entities();
        for (int i = 0; i < entities.size; i++) {
            var e   = entities.get(i);
            var pos = Position.get(e);

            // Whole pixels: the world is drawn at one texel per unit
            float x = interpolate ? MathUtils.round(pos.renderX(alpha)) : pos.x;
            float y = interpolate ? MathUtils.round(pos.renderY(alpha)) : pos.y;

            // An entity may carry more than one renderable, draw all of them
            for (int t = 0; t < types.size; t++) {
                var type = types.get(t);
                var renderable = e.get(type);
                if (renderable != null) {
                    add(renderable, e.id, x, y);
                }
            }
        }

        items.sort();
        for (int i = 0; i < items.size; i++) {
            var item = items.get(i);
            item.visual.render(batch, item.x, item.y);
        }
    }

    private void add(Renderable renderable, int id, float x, float y) {
        var item = pool.obtain();
        item.key = Item.key(renderable.layer, renderable.order, id);
        item.x = x;
        item.y = y;
        item.visual = renderable;
        items.add(item);
    }

    /** One draw, sortable by a packed key: layer, then order, then entity id as the tiebreaker. */
    private static final class Item implements Pool.Poolable, Comparable<Item> {

        long key;
        float x, y;
        Visual visual;

        /**
         * Packs (layer, order, id) into a {@code long} so z-order is a single numeric compare.
         * <ul>
         *     <li>Bits 55..48: layer (8 bits, enum ordinal)</li>
         *     <li>Bits 47..32: order (16 bits, signed shifted to non-negative)</li>
         *     <li>Bits 31..0: entity id</li>
         * </ul>
         * <p>
         * Layer sits highest, so any layer difference wins; if same layer, order decides;
         * if same order, id decides, so two items never compare equal and draw order is
         * stable across frames.
         */
        static long key(RenderLayer layer, int order, int id) {
            long l = layer.ordinal() & 0xFF;
            long o = (order + 0x8000) & 0xFFFF;
            long i = id & 0xFFFFFFFFL;
            return (l << 48) | (o << 32) | i;
        }

        @Override
        public void reset() {
            key = -1L;
            x = 0;
            y = 0;
            visual = null;
        }

        @Override
        public int compareTo(Item that) {
            return Long.compare(this.key, that.key);
        }
    }
}
