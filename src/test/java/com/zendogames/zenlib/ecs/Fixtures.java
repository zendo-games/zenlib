package com.zendogames.zenlib.ecs;

import com.badlogic.gdx.utils.Array;

/** Components, tags and systems shared by the ecs tests. */
final class Fixtures {

    private Fixtures() {}

    static final class Pos implements Component {
        static final ComponentType<Pos> TYPE = ComponentType.register(Pos.class);
        static Pos get(Entity e) { return TYPE.get(e); }
        static boolean has(Entity e) { return TYPE.has(e); }

        int x, y;

        Pos(int x, int y) {
            this.x = x;
            this.y = y;
        }

        private final Link link = new Link(TYPE);
        @Override public Link link() { return link; }
    }

    static final class Vel implements Component {
        static final ComponentType<Vel> TYPE = ComponentType.register(Vel.class);
        static Vel get(Entity e) { return TYPE.get(e); }

        int dx, dy;

        Vel(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        private final Link link = new Link(TYPE);
        @Override public Link link() { return link; }
    }

    /** Records its hook calls. */
    static final class Hooked implements Component {
        static final ComponentType<Hooked> TYPE = ComponentType.register(Hooked.class);

        int added = 0;
        int removed = 0;
        Entity removedFrom = null;

        private final Link link = new Link(TYPE);
        @Override public Link link() { return link; }

        @Override public void onAdd(Entity e) { added++; }

        @Override public void onRemove(Entity e) {
            removed++;
            removedFrom = e;
        }
    }

    static final Tag HURT = Tag.register("Hurt");
    static final Tag FROZEN = Tag.register("Frozen");

    /** Appends its name to a shared log on every update, so run order is checkable. */
    static class Recorder extends EntitySystem {
        final Array<String> log;
        final String name;
        int updates = 0;
        float lastDt = Float.NaN;

        Recorder(String name, int priority, Array<String> log) {
            super(priority);
            this.name = name;
            this.log = log;
        }

        @Override
        public void update(float dt) {
            updates++;
            lastDt = dt;
            log.add(name);
        }
    }

    static class RecorderA extends Recorder {
        RecorderA(int priority, Array<String> log) { super("A", priority, log); }
    }

    static class RecorderB extends Recorder {
        RecorderB(int priority, Array<String> log) { super("B", priority, log); }
    }

    static class RecorderC extends Recorder {
        RecorderC(int priority, Array<String> log) { super("C", priority, log); }
    }

    static final class RenderRecorder extends Recorder {
        RenderRecorder(Array<String> log) { super("R", SystemOrder.MANUAL, log); }
        @Override public boolean runsOnTick() { return false; }
    }

    /** Runs a lambda as a system, for tests that need to act from inside an update. */
    static final class Action extends EntitySystem {
        interface Body { void run(World world, float dt); }

        private final Body body;

        Action(int priority, Body body) {
            super(priority);
            this.body = body;
        }

        @Override public void update(float dt) { body.run(world, dt); }
    }

    /** A second lambda system, so two can be in one world. */
    static final class Action2 extends EntitySystem {
        private final Action.Body body;

        Action2(int priority, Action.Body body) {
            super(priority);
            this.body = body;
        }

        @Override public void update(float dt) { body.run(world, dt); }
    }
}
