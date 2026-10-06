package com.zendogames.zenlib;

import com.badlogic.gdx.utils.Array;

public class Flags {

    private static final Array<Flag> all = new Array<>();

    static void init() {
        // A second init starts clean: every flag the previous run registered goes back to its default
        for (var flag : all) {
            flag.state().reset();
        }
        all.clear();
        register(ZenFlag.values());
    }

    static boolean globalOn(Flag flag) {
        return (flag.kind() == Flag.Kind.FEATURE) || ZenFlag.GLOBAL.state().enabled;
    }

    /** Registration is explicit because an enum's constants don't exist until its class is first used. */
    public static void register(Flag[] flags) {
        all.addAll(flags);
    }

    public static Array<Flag> all() {
        return all;
    }
}
