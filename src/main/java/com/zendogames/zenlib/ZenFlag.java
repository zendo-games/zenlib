package com.zendogames.zenlib;

import static com.zendogames.zenlib.Flag.Kind.*;

// @formatter:off
public enum ZenFlag implements Flag {
      GLOBAL         (true,  FEATURE) // master switch
    , FIXED_TIMESTEP (true,  FEATURE)
    , LOG_INFO       (true,  FEATURE)
    , LOG_DEBUG      (false, FEATURE)
    , LOG_WARN       (true,  FEATURE)
    , RENDER_INTERP  (true,  FEATURE)
    , RENDER_FBO     (true,  FEATURE)
    , FRAME_STEP     (false, DEBUG)
    , DEBUG_RENDER   (false, DEBUG)
    , SHOW_IMGUI     (false, DEBUG)
    ;

    private final Kind kind;
    private final State state;

    ZenFlag(boolean enabledByDefault, Kind kind) {
        this.kind = kind;
        this.state = new State(this, enabledByDefault);
    }

    @Override public Kind  kind()  { return kind; }
    @Override public State state() { return state; }
}
// @formatter:on
