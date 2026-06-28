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
}
