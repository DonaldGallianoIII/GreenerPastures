package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the Soul Tether amplifier model. */
class SoulTetherTest {

    @Test
    void amplificationRisesByTier() {
        // ×1.5 / ×2.0 / ×2.5 - bumped from +10/20/30% (Deuce QA 2026-07-21: rented power must be FELT)
        assertEquals(1.50, new SoulTether("shiny", TetherClass.QUALITY, 1).amplification(), 1e-9);
        assertEquals(2.00, new SoulTether("shiny", TetherClass.QUALITY, 2).amplification(), 1e-9);
        assertEquals(2.50, new SoulTether("shiny", TetherClass.QUALITY, 3).amplification(), 1e-9);
    }

    @Test
    void blankTetherIsInert() {
        SoulTether b = SoulTether.blank();
        assertTrue(b.isBlank());
        assertEquals(1.0, b.amplification(), 1e-9, "no amplification");
        assertEquals(0L, b.burnPerCycle(), "no burn");
    }

    @Test
    void qualityBurnsMoreThanThroughput() {
        assertEquals(24L, new SoulTether("shiny", TetherClass.QUALITY, 3).burnPerCycle(), "quality tier III = expensive");
        assertEquals(9L, new SoulTether("speed", TetherClass.THROUGHPUT, 3).burnPerCycle(), "throughput tier III = cheap");
        assertTrue(new SoulTether("iv", TetherClass.QUALITY, 2).burnPerCycle()
                > new SoulTether("speed", TetherClass.THROUGHPUT, 2).burnPerCycle());
    }

    @Test
    void tierIsClampedToTheLadder() {
        assertEquals(3, new SoulTether("shiny", TetherClass.QUALITY, 9).tier(), "tier caps at III");
        assertTrue(new SoulTether("shiny", TetherClass.QUALITY, -2).isBlank(), "negative tier = blank");
    }
}
