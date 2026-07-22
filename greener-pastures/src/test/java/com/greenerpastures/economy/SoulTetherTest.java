package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the Soul Tether amplifier model. */
class SoulTetherTest {

    @Test
    void tierIsTheLevelsAdded() {
        // Flat additive model (Deuce, 2026-07-21): a Tier-N tether adds N levels on top of its Kernel mod.
        assertEquals(1, new SoulTether("shiny", TetherClass.QUALITY, 1).levelsAdded());
        assertEquals(2, new SoulTether("shiny", TetherClass.QUALITY, 2).levelsAdded());
        assertEquals(3, new SoulTether("shiny", TetherClass.QUALITY, 3).levelsAdded());
    }

    @Test
    void blankTetherIsInert() {
        SoulTether b = SoulTether.blank();
        assertTrue(b.isBlank());
        assertEquals(0, b.levelsAdded(), "no levels added");
        assertEquals(0L, b.upkeepCentiPerSecond(), "no rent");
    }

    @Test
    void qualityRentsMoreThanThroughput() {
        // Rent in centi-Data/SECOND (Deuce, 2026-07-21: "9 data per second is insanely high" - per-cycle
        // burn replaced by per-second rent): quality 0.5/tier, throughput 0.2/tier.
        assertEquals(150L, new SoulTether("shiny", TetherClass.QUALITY, 3).upkeepCentiPerSecond(), "shiny III = 1.5 Data/s");
        assertEquals(60L, new SoulTether("speed", TetherClass.THROUGHPUT, 3).upkeepCentiPerSecond(), "throughput III = 0.6 Data/s");
        assertTrue(new SoulTether("iv", TetherClass.QUALITY, 2).upkeepCentiPerSecond()
                > new SoulTether("speed", TetherClass.THROUGHPUT, 2).upkeepCentiPerSecond());
    }

    @Test
    void tierIsClampedToTheLadder() {
        assertEquals(3, new SoulTether("shiny", TetherClass.QUALITY, 9).tier(), "tier caps at III");
        assertTrue(new SoulTether("shiny", TetherClass.QUALITY, -2).isBlank(), "negative tier = blank");
    }
}
