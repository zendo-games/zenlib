package com.zendogames.zenlib;

import com.badlogic.gdx.utils.Array;

public class Flags {

    private static final Array<Flag> all = new Array<>();

    /** Registration is explicit because an enum's constants don't exist until its class is first used. */
    public static void register(Flag[] flags) {
        all.clear();
        all.addAll(flags);
    }

    public static Array<Flag> all() {
        return all;
    }

    static boolean globalOn(Flag flag) {
        return (flag.kind() == Flag.Kind.FEATURE) || ZenFlag.GLOBAL.state().enabled;
    }
}
