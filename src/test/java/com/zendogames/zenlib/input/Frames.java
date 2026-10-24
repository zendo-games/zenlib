package com.zendogames.zenlib.input;

import com.badlogic.gdx.utils.Array;

/**
 * One {@link InputState} with two fake gamepads (slots 0 and 1) and the frame sequence the game
 * runs: events land, {@code step}, the owners update their bindings, {@code endFrame}. Register the
 * binding updates with {@link #on}; feed events (and pad changes, which {@code step} polls) through
 * the {@code events} runnable.
 */
final class Frames {

    static final float DT = 0.01f;

    final InputState input = new InputState();
    final FakeController pad  = new FakeController();
    final FakeController pad2 = new FakeController();
    private final Array<Runnable> updates = new Array<>();

    Frames() {
        input.controllerSource = FakeController.source(pad, pad2);
    }

    Frames on(Runnable update) {
        updates.add(update);
        return this;
    }

    void frame(Runnable events) {
        frame(DT, events);
    }

    void frame(float dt, Runnable events) {
        events.run();
        input.step(dt);
        for (var update : updates) update.run();
        input.endFrame();
    }

    /** {@code n} frames with nothing happening. */
    void idle(int n) {
        idle(n, DT);
    }

    void idle(int n, float dt) {
        for (int i = 0; i < n; i++) frame(dt, () -> {});
    }
}
