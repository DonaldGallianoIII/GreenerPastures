package com.greenerpastures.notebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Headless tests for the console APPLY_AUGMENT arg grammar (#34/#35) - malformed input must reject cleanly. */
class AugmentArgTest {

    @Test
    void bareTypeParsesWithNoValue() {
        AugmentArg a = AugmentArg.parse("SHINY");
        assertEquals("SHINY", a.type());
        assertEquals(0, a.index());
        assertNull(a.ev());
    }

    @Test
    void selectorIndexParses() {
        AugmentArg a = AugmentArg.parse("NATURE:7");
        assertEquals("NATURE", a.type());
        assertEquals(7, a.index());
        assertNull(a.ev());
    }

    @Test
    void evSpreadParsesSixInts() {
        AugmentArg a = AugmentArg.parse("EV:6,252,0,0,0,252");
        assertEquals("EV", a.type());
        assertArrayEquals(new int[]{6, 252, 0, 0, 0, 252}, a.ev());
    }

    @Test
    void evToleratesSpaces() {
        assertArrayEquals(new int[]{0, 0, 4, 0, 252, 252}, AugmentArg.parse("EV: 0, 0, 4, 0, 252, 252").ev());
    }

    @Test
    void malformedRejects() {
        assertNull(AugmentArg.parse(null));
        assertNull(AugmentArg.parse(""));
        assertNull(AugmentArg.parse("NATURE:"));         // missing value
        assertNull(AugmentArg.parse(":7"));              // missing type
        assertNull(AugmentArg.parse("NATURE:x"));        // not a number
        assertNull(AugmentArg.parse("NATURE:0"));        // selector indexes are 1-based
        assertNull(AugmentArg.parse("NATURE:-3"));
        assertNull(AugmentArg.parse("EV:1,2,3"));        // wrong arity
        assertNull(AugmentArg.parse("EV:1,2,3,4,5,x"));  // bad int
        assertNull(AugmentArg.parse("EV:1,2,3,4,5,-6")); // negative stat
        assertNull(AugmentArg.parse("EV:0,0,0,0,0,0"));  // all-zero spread is a REMOVE, not an install
    }
}
