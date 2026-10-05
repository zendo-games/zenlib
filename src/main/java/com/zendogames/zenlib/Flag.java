package com.zendogames.zenlib;

public interface Flag {

    String name();
    Kind kind();
    State state();

    default boolean on()  { return state().enabled && Flags.globalOn(this); }
    default boolean off() { return !on(); }

    default boolean enable()  { return set(true); }
    default boolean disable() { return set(false); }
    default boolean toggle()  { return set(!state().enabled); }

    default boolean set(boolean enabled) {
        state().enabled = enabled;
        Log.info("Flag", "%s=%b", name(), enabled);
        return enabled;
    }

    enum Kind { FEATURE, DEBUG }

    /** Mutable value of a single flag, owned by the enum constant that declares it. */
    class State {
        boolean enabled;

        public State(Flag flag, boolean enabledByDefault) {
            // A JVM option overrides the default without touching code:
            //   -Dflag.{name_lowercase}=[true|false]
            var override = System.getProperty("flag." + flag.name().toLowerCase());
            this.enabled = (override != null) ? Boolean.parseBoolean(override) : enabledByDefault;
        }
    }
}
