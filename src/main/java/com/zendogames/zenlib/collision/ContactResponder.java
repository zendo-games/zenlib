package com.zendogames.zenlib.collision;

import com.zendogames.zenlib.ecs.systems.ContactSystem;

/** The game's half of contacts: called once per {@link Contact} after {@link ContactSystem} has detected them all for the tick. */
@FunctionalInterface
public interface ContactResponder {

    void respond(Contact contact);

    ContactResponder NONE = contact -> {};
}
