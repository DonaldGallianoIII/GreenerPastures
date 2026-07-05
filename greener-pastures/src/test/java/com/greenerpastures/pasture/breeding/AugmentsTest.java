package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The bounded shiny-proc math - the safety-critical bit (a proc can never make shiny "explode").
 * Loads the {@code Augments} record (which also builds MC codecs at class-init), so this exercises the
 * {@code fabric-loader-junit} bootstrap too. Headless.
 */
class AugmentsTest {

    @Test
    void percentBecomesAProbability() {
        assertEquals(0.0, new Augments(0).shinyProcChance(), 1e-9);
        assertEquals(0.4, new Augments(40).shinyProcChance(), 1e-9);
        assertEquals(1.0, new Augments(100).shinyProcChance(), 1e-9);
    }

    @Test
    void probabilityIsClampedBothEnds() {
        assertEquals(1.0, new Augments(150).shinyProcChance(), 1e-9, "above 100% clamps to 1.0 - bounded, can't explode");
        assertEquals(0.0, new Augments(-20).shinyProcChance(), 1e-9, "negative clamps to 0");
    }

    @Test
    void noneIsZeroProc() {
        assertEquals(0, Augments.NONE.shinyProcPercent());
        assertEquals(0.0, Augments.NONE.shinyProcChance(), 1e-9);
    }

    @Test
    void withShinyReplacesInPlace() {
        assertEquals(30, new Augments(10).withShiny(30).shinyProcPercent(), "one shiny augment per Kernel - replace, not stack");
    }
}
