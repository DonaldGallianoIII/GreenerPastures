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
        assertEquals(300, BreedingTier.GREENER.baseDropRateCentipercent(),   "greener +3.00%");
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
