package com.zendogames.zenlib.ecs.components;

import com.zendogames.zenlib.ecs.Component;
import com.zendogames.zenlib.ecs.ComponentType;
import com.zendogames.zenlib.ecs.Entity;

public class Name implements Component {

    public static final ComponentType<Name> TYPE = ComponentType.register(Name.class);
    public static Name    get(Entity e) { return TYPE.get(e); }
    public static boolean has(Entity e) { return TYPE.has(e); }

    public String name;

    public Name(String name) {
        this.name = name;
    }

    public boolean is(String name) {
        return this.name.equals(name);
    }

    /** The entity's name for a log line, or {@code Entity#id} when it has none or is dead. */
    public static String of(Entity e) {
        var name = (e.isAlive() && has(e)) ? get(e) : null;
        return (name != null) ? name.name : e.toString();
    }

    @Override
    public String toString() {
        return "Name{" + name + "}";
    }
}
