package com.zendogames.zenlib.input;

import com.badlogic.gdx.controllers.ControllerMapping;

/**
 * The stick axes a binding can name. {@link DeviceState.Gamepad#axes} is indexed by {@code ordinal()} and
 * holds the raw libGDX value in -1..1: left and up are negative. {@code StickBinding} flips y so that
 * stick, d-pad and keys all read up as positive.
 */
public enum PadAxis {
    LEFT_X, LEFT_Y,
    RIGHT_X, RIGHT_Y;

    /** The controller's code for this axis under {@code m}, or {@link ControllerMapping#UNDEFINED}. */
    int code(ControllerMapping m) {
        return switch (this) {
            case LEFT_X  -> m.axisLeftX;
            case LEFT_Y  -> m.axisLeftY;
            case RIGHT_X -> m.axisRightX;
            case RIGHT_Y -> m.axisRightY;
        };
    }
}
