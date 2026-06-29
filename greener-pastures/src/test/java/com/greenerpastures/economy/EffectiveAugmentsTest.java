package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the base-augment × Soul-Tether amplification (the rented-amplification mechanic). */
class EffectiveAugmentsTest {

    private static SoulTether tether(String fn, TetherClass cls, int tier) {
        return new SoulTether(fn, cls, tier);
    }

    @Test
    void tetherAmplifiesOnlyItsMatchingBase() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.SHINY, 5);
        EffectiveAugments e = EffectiveAugments.of(base, List.of(tether("shiny", TetherClass.QUALITY, 2)));
        assertEquals(6.0, e.magnitude(AugmentFunction.SHINY), 1e-9, "5 × 1.20 (tier II)");
        assertEquals(0.06, e.shinyProcChance(), 1e-9);
    }

    @Test
    void tetherWithNoMatchingBaseAmplifiesNothing() {
        EffectiveAugments e = EffectiveAugments.of(Map.of(), List.of(tether("shiny", TetherClass.QUALITY, 3)));
        assertEquals(0.0, e.magnitude(AugmentFunction.SHINY), 1e-9, "no base mod → nothing to scale");
        assertFalse(e.has(AugmentFunction.SHINY));
    }

    @Test
    void tethersDoNotBleedAcrossFunctions() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.SHINY, 5);
        EffectiveAugments e = EffectiveAugments.of(base, List.of(tether("speed", TetherClass.THROUGHPUT, 3)));
        assertEquals(5.0, e.magnitude(AugmentFunction.SHINY), 1e-9, "a Speed Tether leaves Shiny untouched");
        assertEquals(0.0, e.magnitude(AugmentFunction.SPEED), 1e-9, "and Speed has no base to amplify");
    }

    @Test
    void multipleMatchingTethersStackMultiplicatively() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.SHINY, 10);
        EffectiveAugments e = EffectiveAugments.of(base,
                List.of(tether("shiny", TetherClass.QUALITY, 1), tether("shiny", TetherClass.QUALITY, 1)));
        assertEquals(12.1, e.magnitude(AugmentFunction.SHINY), 1e-9, "10 × 1.10 × 1.10");
    }

    @Test
    void blankAndStarvedTethersContributeNothing() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.SHINY, 5);
        assertEquals(5.0, EffectiveAugments.of(base, List.of(SoulTether.blank())).magnitude(AugmentFunction.SHINY), 1e-9);
        assertEquals(5.0, EffectiveAugments.of(base, List.of()).magnitude(AugmentFunction.SHINY), 1e-9,
                "no tethers ⇒ effective == base (rented amplification, free base)");
    }

    @Test
    void enrichmentBecomesARenderDataMultiplier() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.ENRICHMENT, 20);
        assertEquals(1.20, EffectiveAugments.of(base, List.of()).enrichmentMultiplier(), 1e-9, "20% base → 1.20×");
        EffectiveAugments amped = EffectiveAugments.of(base, List.of(tether("enrichment", TetherClass.THROUGHPUT, 1)));
        assertEquals(1.22, amped.enrichmentMultiplier(), 1e-9, "20 × 1.10 = 22% → 1.22×");
    }

    @Test
    void noEnrichmentMeansNeutralMultiplier() {
        assertEquals(1.0, EffectiveAugments.of(Map.of(), List.of()).enrichmentMultiplier(), 1e-9);
    }

    @Test
    void speedLevelRoundsTheAmplifiedMagnitude() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.SPEED, 2);
        assertEquals(2, EffectiveAugments.of(base, List.of()).speedLevel());
        // 2 × 1.30 = 2.6 → rounds to 3
        assertEquals(3, EffectiveAugments.of(base, List.of(tether("speed", TetherClass.THROUGHPUT, 3))).speedLevel());
    }

    @Test
    void dropRateCentipercentBecomesAProcFraction() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.DROP_RATE, 25);   // 25 centipercent = 0.25%
        assertEquals(0.0025, EffectiveAugments.of(base, List.of()).dropRateFraction(), 1e-9);
        // a Drop Rate tether (throughput) tier II ×1.20 → 30 → 0.30%
        assertEquals(0.0030,
                EffectiveAugments.of(base, List.of(tether("drop_rate", TetherClass.THROUGHPUT, 2))).dropRateFraction(), 1e-9);
        assertEquals(0.0, EffectiveAugments.of(Map.of(), List.of()).dropRateFraction(), 1e-9, "no mod → no bonus");
    }

    @Test
    void dropYieldIsAFlatBudgetBonusAmplifiedByItsTether() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.DROP_YIELD, 2);   // +2 budget ceiling
        assertEquals(2, EffectiveAugments.of(base, List.of()).dropYieldBonus(), "flat base, no tether");
        // a Drop Yield tether (throughput) tier III ×1.30 → 2.6 → rounds to 3
        assertEquals(3,
                EffectiveAugments.of(base, List.of(tether("drop_yield", TetherClass.THROUGHPUT, 3))).dropYieldBonus());
        // tier I ×1.10 → 2.2 → rounds back down to 2 (integer budget)
        assertEquals(2,
                EffectiveAugments.of(base, List.of(tether("drop_yield", TetherClass.THROUGHPUT, 1))).dropYieldBonus());
        assertEquals(0, EffectiveAugments.of(Map.of(), List.of()).dropYieldBonus(), "no mod → no bonus");
    }
}
