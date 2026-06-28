package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the bounded bonus shiny-reroll math (extracted from CobbreedingBridge). */
class ShinyOddsTest {

    @Test
    void baseOddsWithNoMultipliers() {
        assertEquals(8192.0, ShinyOdds.effectiveOdds(8192, null, null, null, false, false, false), 1e-9);
    }

    @Test
    void alwaysMultiplierDividesOdds() {
        assertEquals(2048.0, ShinyOdds.effectiveOdds(8192, 4f, null, null, false, false, false), 1e-9);
    }

    @Test
    void crystalAppliesOncePerShinyParent() {
        assertEquals(4096.0, ShinyOdds.effectiveOdds(8192, null, 2f, null, true, false, false), 1e-9);
        assertEquals(2048.0, ShinyOdds.effectiveOdds(8192, null, 2f, null, true, true, false), 1e-9);
        assertEquals(8192.0, ShinyOdds.effectiveOdds(8192, null, 2f, null, false, false, false), 1e-9);
    }

    @Test
    void masudaOnlyAppliesWithDifferentTrainers() {
        assertEquals(2048.0, ShinyOdds.effectiveOdds(8192, null, null, 4f, false, false, true), 1e-9);
        assertEquals(8192.0, ShinyOdds.effectiveOdds(8192, null, null, 4f, false, false, false), 1e-9);
    }

    @Test
    void zeroMultiplierMeansNeverShiny() {
        assertTrue(Double.isInfinite(ShinyOdds.effectiveOdds(8192, 0f, null, null, false, false, false)));
        assertTrue(Double.isInfinite(ShinyOdds.effectiveOdds(8192, null, 0f, null, true, false, false)),
                "zero crystal only kills it when a parent is actually shiny");
        assertEquals(8192.0, ShinyOdds.effectiveOdds(8192, null, 0f, null, false, false, false), 1e-9,
                "zero crystal with no shiny parent is harmless");
    }

    @Test
    void shinyProbabilityIsOneOverOddsOrGuaranteed() {
        assertEquals(1.0 / 8192, ShinyOdds.shinyProbability(8192), 1e-12);
        assertEquals(1.0, ShinyOdds.shinyProbability(0.5), 1e-12, "odds < 1 ⇒ guaranteed shiny");
        assertEquals(1.0, ShinyOdds.shinyProbability(1.0), 1e-12);
    }

    @Test
    void procFiresStrictlyBelowItsChance() {
        assertTrue(ShinyOdds.procFires(0.4, 0.39));
        assertFalse(ShinyOdds.procFires(0.4, 0.40), "roll == chance does not fire");
        assertFalse(ShinyOdds.procFires(0.0, 0.0), "no augment = never fires");
    }

    @Test
    void procMakesShinyOnlyWhenBothRollsFavour() {
        // effective odds 2.0 ⇒ shiny probability 0.5
        assertTrue(ShinyOdds.procMakesShiny(false, 0.5, 2.0, 0.4, 0.4), "proc fires AND shiny hits");
        assertFalse(ShinyOdds.procMakesShiny(false, 0.5, 2.0, 0.6, 0.4), "proc didn't fire");
        assertFalse(ShinyOdds.procMakesShiny(false, 0.5, 2.0, 0.4, 0.6), "proc fired but shiny missed");
    }

    @Test
    void alreadyShinyNeverDoubleCounts() {
        assertFalse(ShinyOdds.procMakesShiny(true, 1.0, 0.5, 0.0, 0.0), "already shiny ⇒ proc adds nothing");
    }

    @Test
    void zeroOrNaNBaseRateMeansNeverShinyNotGuaranteed() {
        // server disabled shinies (shinyRate=0): odds must be infinite ⇒ probability 0, NOT guaranteed.
        assertTrue(Double.isInfinite(ShinyOdds.effectiveOdds(0, 2f, 2f, 2f, true, true, true)),
                "base 0 ⇒ infinite odds even with every multiplier stacked");
        assertTrue(Double.isInfinite(ShinyOdds.effectiveOdds(Double.NaN, null, null, null, false, false, false)),
                "a NaN base rate is treated as never-shiny, not guaranteed");
        double odds = ShinyOdds.effectiveOdds(0, null, null, null, false, false, false);
        assertFalse(ShinyOdds.procMakesShiny(false, 1.0, odds, 0.0, 0.0),
                "with shinies disabled, a firing proc must NOT force a guaranteed shiny");
    }
}
