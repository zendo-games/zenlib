package com.zendogames.zenlib.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.ControllerPowerLevel;
import com.badlogic.gdx.utils.Array;

import java.util.function.Supplier;

/**
 * A gamepad for headless tests: buttons and axes are plain arrays indexed by {@link Pad} and
 * {@link PadAxis} ordinals, and its {@link ControllerMapping} hands those same ordinals out as codes,
 * except {@code buttonL2} / {@code buttonR2}, which are {@code UNDEFINED} the way Jamepad's are.
 */
final class FakeController implements Controller {

    static final class Mapping extends ControllerMapping {
        Mapping() {
            super(PadAxis.LEFT_X.ordinal(), PadAxis.LEFT_Y.ordinal(), PadAxis.RIGHT_X.ordinal(), PadAxis.RIGHT_Y.ordinal(),
                    Pad.A.ordinal(), Pad.B.ordinal(), Pad.X.ordinal(), Pad.Y.ordinal(), Pad.BACK.ordinal(), Pad.START.ordinal(),
                    Pad.L1.ordinal(), UNDEFINED, Pad.R1.ordinal(), UNDEFINED,
                    Pad.LEFT_STICK.ordinal(), Pad.RIGHT_STICK.ordinal(),
                    Pad.DPAD_UP.ordinal(), Pad.DPAD_DOWN.ordinal(), Pad.DPAD_LEFT.ordinal(), Pad.DPAD_RIGHT.ordinal());
        }
    }

    private static final Mapping MAPPING = new Mapping();

    final boolean[] buttons = new boolean[Pad.values().length];
    final float[] axes = new float[PadAxis.values().length];
    boolean connected = true;
    String name = "fake pad";

    /** A controller source listing these pads in order, so {@code pads[i]} fills gamepad slot {@code i}. */
    static Supplier<Array<Controller>> source(Controller... pads) {
        var list = new Array<Controller>(pads);
        return () -> list;
    }

    FakeController press(Pad pad)               { buttons[pad.ordinal()] = true;  return this; }
    FakeController release(Pad pad)             { buttons[pad.ordinal()] = false; return this; }
    FakeController axis(PadAxis axis, float v)  { axes[axis.ordinal()] = v;       return this; }

    @Override public boolean getButton(int buttonCode) {
        return buttonCode >= 0 && buttonCode < buttons.length && buttons[buttonCode];
    }

    @Override public float getAxis(int axisCode) {
        return (axisCode >= 0 && axisCode < axes.length) ? axes[axisCode] : 0f;
    }

    @Override public String getName()                  { return name; }
    @Override public String getUniqueId()              { return name; }
    @Override public int getMinButtonIndex()           { return 0; }
    @Override public int getMaxButtonIndex()           { return buttons.length - 1; }
    @Override public int getAxisCount()                { return axes.length; }
    @Override public boolean isConnected()             { return connected; }
    @Override public boolean canVibrate()              { return false; }
    @Override public boolean isVibrating()             { return false; }
    @Override public void startVibration(int duration, float strength) {}
    @Override public void cancelVibration()            {}
    @Override public boolean supportsPlayerIndex()     { return false; }
    @Override public int getPlayerIndex()              { return PLAYER_IDX_UNSET; }
    @Override public void setPlayerIndex(int index)    {}
    @Override public ControllerMapping getMapping()    { return MAPPING; }
    @Override public ControllerPowerLevel getPowerLevel() { return ControllerPowerLevel.POWER_UNKNOWN; }
    @Override public void addListener(ControllerListener listener)    {}
    @Override public void removeListener(ControllerListener listener) {}
}
