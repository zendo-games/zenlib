package com.zendogames.zenlib.input;

import com.badlogic.gdx.controllers.ControllerMapping;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** {@code code(mapping)} reads the mapping field for the constant; the fake mapping hands out ordinals. */
public class PadTest {

    final ControllerMapping mapping = new FakeController.Mapping();

    @Test
    public void everyButtonReadsItsMappingField() {
        for (var pad : Pad.values()) {
            int expected = (pad == Pad.L2 || pad == Pad.R2) ? ControllerMapping.UNDEFINED : pad.ordinal();
            assertEquals(pad.name(), expected, pad.code(mapping));
        }
    }

    @Test
    public void everyAxisReadsItsMappingField() {
        for (var axis : PadAxis.values()) {
            assertEquals(axis.name(), axis.ordinal(), axis.code(mapping));
        }
    }
}
