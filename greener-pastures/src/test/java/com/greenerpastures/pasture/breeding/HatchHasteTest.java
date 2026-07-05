package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Hatch Haste: TIMER scaling per level, floored at one second, no-op at level 0. */
class HatchHasteTest {

    @Test
    void levelsScaleHalfQuarterTenth() {
        assertEquals(600, HatchHaste.scaledTimer(600, 0), "no augment = untouched");
        assertEquals(300, HatchHaste.scaledTimer(600, 1));
        assertEquals(150, HatchHaste.scaledTimer(600, 2));
        assertEquals(60, HatchHaste.scaledTimer(600, 3));
        assertEquals(60, HatchHaste.scaledTimer(600, 99), "levels above III clamp to III");
    }

    @Test
    void oneSecondFloorSoTheHatchIsWitnessed() {
        assertEquals(HatchHaste.MIN_TIMER_TICKS, HatchHaste.scaledTimer(100, 3), "10 ticks would be instant — floor to 1s");
        assertEquals(HatchHaste.MIN_TIMER_TICKS, HatchHaste.scaledTimer(0, 1));
        assertEquals(HatchHaste.MIN_TIMER_TICKS, HatchHaste.scaledTimer(-50, 2), "garbage timers clamp sane");
    }

    @Test
    void labelsMatchTheFactors() {
        assertEquals("0.5", HatchHaste.factorLabel(1));
        assertEquals("0.25", HatchHaste.factorLabel(2));
        assertEquals("0.1", HatchHaste.factorLabel(3));
        assertEquals("1", HatchHaste.factorLabel(0));
    }
}
