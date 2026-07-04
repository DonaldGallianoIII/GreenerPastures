package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for the Kernel tiers — no Minecraft needed. Runs headless via {@code ./gradlew test}. */
class BreedingTierTest {

    @Test
    void greenerFillsAFullPasture() {
        assertEquals(8, BreedingTier.GREENER.maxPairs, "top tier breeds 8 pairs = fills a 16-mon pasture");
    }

    @Test
    void noTierExceedsTheEightPairCeiling() {
        for (BreedingTier t : BreedingTier.values()) {
            assertTrue(t.maxPairs <= 8, t + " must not exceed the 8-pair pasture ceiling");
            assertTrue(t.maxPairs >= 1, t + " should breed at least one pair");
        }
    }

    @Test
    void tiersNeverBreedFewerPairsAsTheyGoUp() {
        BreedingTier[] v = BreedingTier.values();
        for (int i = 1; i < v.length; i++) {
            assertTrue(v[i].maxPairs >= v[i - 1].maxPairs, v[i] + " should not breed fewer pairs than " + v[i - 1]);
        }
    }

    @Test
    void idIsTheLowercaseName() {
        assertEquals("copper", BreedingTier.COPPER.id());
        assertEquals("greener", BreedingTier.GREENER.id());
    }

    @Test
    void baseDropRateScalesPerTier() {   // BUG-001: every tier used to share a flat rate; doubled 2026-07-03 (Deuce)
        assertEquals(50,  BreedingTier.COPPER.baseDropRateCentipercent(),    "copper +0.50%");
        assertEquals(100, BreedingTier.IRON.baseDropRateCentipercent(),      "iron +1.00%");
        assertEquals(150, BreedingTier.GOLD.baseDropRateCentipercent(),      "gold +1.50%");
        assertEquals(200, BreedingTier.DIAMOND.baseDropRateCentipercent(),   "diamond +2.00%");
        assertEquals(250, BreedingTier.NETHERITE.baseDropRateCentipercent(), "netherite +2.50%");
        // Greener jumps off the line (Deuce 2026-07-04: doubled, then tuned down 1% the same day) —
        // the top kernel is a JUMP, priced like one (8 netherite/emerald blocks).
        assertEquals(500, BreedingTier.GREENER.baseDropRateCentipercent(),   "greener +5.00%");
    }

    @Test
    void baseSpeedFactorScalesPerTierWithTheGreenerJump() {   // Deuce 2026-07-04: kernels carry egg speed like drops
        assertEquals(1.1, BreedingTier.COPPER.baseSpeedFactor(),    1e-9, "copper ×1.1");
        assertEquals(1.2, BreedingTier.IRON.baseSpeedFactor(),      1e-9, "iron ×1.2");
        assertEquals(1.3, BreedingTier.GOLD.baseSpeedFactor(),      1e-9, "gold ×1.3");
        assertEquals(1.4, BreedingTier.DIAMOND.baseSpeedFactor(),   1e-9, "diamond ×1.4");
        assertEquals(1.5, BreedingTier.NETHERITE.baseSpeedFactor(), 1e-9, "netherite ×1.5");
        assertEquals(1.6, BreedingTier.GREENER.baseSpeedFactor(),   1e-9, "greener ×1.6 (jump halved 2026-07-04)");
        BreedingTier prev = null;
        for (BreedingTier t : BreedingTier.values()) {
            if (prev != null) assertTrue(t.baseSpeedFactor() > prev.baseSpeedFactor(), "speed must climb per tier");
            prev = t;
        }
    }

    @Test
    void baseDropRateStrictlyIncreasesPerTier() {   // BUG-001 regression guard
        BreedingTier[] v = BreedingTier.values();
        for (int i = 1; i < v.length; i++) {
            assertTrue(v[i].baseDropRateCentipercent() > v[i - 1].baseDropRateCentipercent(),
                    v[i] + " base drop rate must exceed " + v[i - 1]);
        }
    }
}
