package com.zendogames.zenlib.input;

import com.badlogic.gdx.controllers.ControllerMapping;

/**
 * The gamepad buttons a binding can name, in the Xbox layout gdx-controllers maps every pad to.
 * {@link DeviceState.Gamepad#buttons} is indexed by {@code ordinal()}; the controller's own button code
 * comes from {@link #code(ControllerMapping)} at poll time, so a game never sees raw codes.
 * <p>
 * {@code L2}/{@code R2} are {@link ControllerMapping#UNDEFINED} under Jamepad (the desktop backend reports
 * the triggers as axes the mapping does not name), so they never fire on desktop. They stay for the web
 * backend and for a later trigger-axis slice.
 */
public enum Pad {
    A, B, X, Y,
    L1, L2,
    R1, R2,
    BACK, START,
    LEFT_STICK, RIGHT_STICK,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT;

    /** The controller's code for this button under {@code m}, or {@link ControllerMapping#UNDEFINED}. */
    int code(ControllerMapping m) {
        return switch (this) {
            case A           -> m.buttonA;
            case B           -> m.buttonB;
            case X           -> m.buttonX;
            case Y           -> m.buttonY;
            case L1          -> m.buttonL1;
            case L2          -> m.buttonL2;
            case R1          -> m.buttonR1;
            case R2          -> m.buttonR2;
            case BACK        -> m.buttonBack;
            case START       -> m.buttonStart;
            case LEFT_STICK  -> m.buttonLeftStick;
            case RIGHT_STICK -> m.buttonRightStick;
            case DPAD_UP     -> m.buttonDpadUp;
            case DPAD_DOWN   -> m.buttonDpadDown;
            case DPAD_LEFT   -> m.buttonDpadLeft;
            case DPAD_RIGHT  -> m.buttonDpadRight;
        };
    }
}
