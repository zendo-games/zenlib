package com.zendogames.zenlib.input;

/**
 * Which keys are down right now, as seen by whoever records key events. {@link ScreenInputProcessor}
 * implements it from the events that pass its imgui and transition gates, so a key typed into an
 * imgui field is never "held" for the game; a test implements it with a set.
 */
public interface HeldKeys {

    boolean isHeld(int keycode);

    /** Nothing is every held. */
    HeldKeys NONE = keycode -> false;
}
