package zendo.games.zenlib.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;

public class Time {

    private static class CallbackInfo {
        private final Callback callback;
        private final Object[] params;
        private float timer;

        public float timeout = 0;
        public float interval = 0;

        CallbackInfo(Callback callback, Object... params) {
            this.callback = callback;
            this.params = params;
            this.timer = 0;
        }

        boolean isInterval() {
            return (interval > 0);
        }

        boolean isTimeout() {
            return (timeout > 0);
        }

        boolean update(float dt) {
            boolean called = false;

            timer += dt;

            if (interval > 0) {
                if (timer >= interval) {
                    timer -= interval;
                    if (callback != null) {
                        callback.run(params);
                        called = true;
                    }
                }
            } else {
                if (timer >= timeout) {
                    if (callback != null) {
                        callback.run(params);
                        called = true;
                    }
                }
            }

            return called;
        }
    }

    private static long start_millis = 0;

    public static long millis = 0;
    public static long previous_elapsed = 0;
    public static float delta = 0;
    public static float pause_timer = 0;

    private static Array<CallbackInfo> callbacks;

    public static void init() {
        start_millis = TimeUtils.millis();
        callbacks = new Array<>();
    }

    public static void update() {
        Time.delta = Calc.min(1 / 30f, Gdx.graphics.getDeltaTime());

        for (int i = callbacks.size - 1; i >= 0; i--) {
            CallbackInfo info = callbacks.get(i);
            boolean wasCalled = info.update(Time.delta);
            if (wasCalled && info.isTimeout()) {
                callbacks.removeIndex(i);
            }
        }
    }

    public static void do_after_delay(float seconds, Callback callback) {
        CallbackInfo info = new CallbackInfo(callback);
        info.timeout = seconds;
        callbacks.add(info);
    }

    public static void do_at_interval(float seconds, Callback callback) {
        CallbackInfo info = new CallbackInfo(callback);
        info.interval = seconds;
        callbacks.add(info);
    }

    public static long elapsed_millis() {
        return TimeUtils.timeSinceMillis(start_millis);
    }

    public static void pause_for(float time) {
        if (time >= pause_timer) {
            pause_timer = time;
        }
    }

    public static boolean on_time(float time, float timestamp) {
        return (time >= timestamp) && ((time - Time.delta) < timestamp);
    }

    public static boolean on_interval(float time, float delta, float interval, float offset) {
        return Calc.floor((time - offset - delta) / interval) < Calc.floor((time - offset) / interval);
    }

    public static boolean on_interval(float delta, float interval, float offset) {
        return Time.on_interval(Time.elapsed_millis(), delta, interval, offset);
    }

    public static boolean on_interval(float interval, float offset) {
        return Time.on_interval(Time.elapsed_millis(), Time.delta, interval, offset);
    }

    public static boolean on_interval(float interval) {
        return Time.on_interval(interval, 0);
    }

    public static boolean between_interval(float time, float interval, float offset) {
        return Calc.modf(time - offset, interval * 2) >= interval;
    }

    public static boolean between_interval(float interval, float offset) {
        return Time.between_interval(Time.elapsed_millis(), interval, offset);
    }

    public static boolean between_interval(float interval) {
        return Time.between_interval(interval, 0);
    }
}
