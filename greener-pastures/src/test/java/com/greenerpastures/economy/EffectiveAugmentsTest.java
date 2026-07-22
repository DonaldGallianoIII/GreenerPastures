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
        assertEquals(35.0, e.magnitude(AugmentFunction.SHINY), 1e-9, "5 + 2 levels × 15 (tier II)");
        assertEquals(0.35, e.shinyProcChance(), 1e-9);
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
    void multipleMatchingTethersStackAdditively() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.SHINY, 10);
        EffectiveAugments e = EffectiveAugments.of(base,
                List.of(tether("shiny", TetherClass.QUALITY, 1), tether("shiny", TetherClass.QUALITY, 1)));
        assertEquals(40.0, e.magnitude(AugmentFunction.SHINY), 1e-9, "10 + (1+1) levels × 15 - flat, stacking");
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
        assertEquals(1.30, amped.enrichmentMultiplier(), 1e-9, "20 + 1 level × 10 = 30% → 1.30×");
    }

    @Test
    void noEnrichmentMeansNeutralMultiplier() {
        assertEquals(1.0, EffectiveAugments.of(Map.of(), List.of()).enrichmentMultiplier(), 1e-9);
    }

    @Test
    void speedIsDiscreteTetherAddsFlatLevels() {
        // Speed is a LEVELED mod: a tether ADDS +tier levels (never multiply-and-round, which ate tiers).
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.SPEED, 1);
        assertEquals(1, EffectiveAugments.of(base, List.of()).speedLevel());
        assertEquals(2, EffectiveAugments.of(base, List.of(tether("speed", TetherClass.THROUGHPUT, 1))).speedLevel(),
                "tier I is FELT even on a level-1 mod");
        assertEquals(3, EffectiveAugments.of(base, List.of(tether("speed", TetherClass.THROUGHPUT, 2))).speedLevel());
        assertEquals(4, EffectiveAugments.of(base, List.of(tether("speed", TetherClass.THROUGHPUT, 3))).speedLevel(),
                "tethers push PAST the rollable max (level 4 is tether-only territory)");
        assertEquals(6, EffectiveAugments.of(Map.of(AugmentFunction.SPEED, 3),
                List.of(tether("speed", TetherClass.THROUGHPUT, 3), tether("speed", TetherClass.THROUGHPUT, 3))).speedLevel(),
                "stacked tethers climb to the extended ladder's top (6)");
        assertEquals(0, EffectiveAugments.of(Map.of(), List.of(tether("speed", TetherClass.THROUGHPUT, 3))).speedLevel(),
                "no base mod → a tether adds nothing (base = free, amplification = rented)");
    }

    @Test
    void dropRateCentipercentBecomesAProcFraction() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.DROP_RATE, 25);   // 25 centipercent = 0.25%
        assertEquals(0.0025, EffectiveAugments.of(base, List.of()).dropRateFraction(), 1e-9);
        // a Drop Rate tether (throughput) tier II = +2 levels × 100 centipercent → 225 → 2.25%
        assertEquals(0.0225,
                EffectiveAugments.of(base, List.of(tether("drop_rate", TetherClass.THROUGHPUT, 2))).dropRateFraction(), 1e-9);
        assertEquals(0.0, EffectiveAugments.of(Map.of(), List.of()).dropRateFraction(), 1e-9, "no mod → no bonus");
    }

    @Test
    void ivFloorIsARetiredTetherTarget() {
        // IV Floor keeps its BASE behavior (guaranteed perfects, capped at 6) but a tether on it is inert
        // (Deuce, 2026-07-21: multiply-and-round jank wasn't worth keeping - the target is retired).
        assertEquals(2, EffectiveAugments.of(Map.of(AugmentFunction.IV_FLOOR, 2), List.of()).ivFloorCount(), "flat base");
        assertEquals(2, EffectiveAugments.of(Map.of(AugmentFunction.IV_FLOOR, 2),
                List.of(tether("iv_floor", TetherClass.QUALITY, 3))).ivFloorCount(), "tether contributes NOTHING");
        assertEquals(6, EffectiveAugments.of(Map.of(AugmentFunction.IV_FLOOR, 9), List.of()).ivFloorCount(),
                "base still caps at the 6 stats");
        assertEquals(0, EffectiveAugments.of(Map.of(), List.of()).ivFloorCount(), "no mod → no floor");
    }

    @Test
    void evIsARetiredTetherTarget() {
        // EV's flat floor has no consumers since the EV-spread rework (BUG-002) - a tether on it is inert.
        assertEquals(20, EffectiveAugments.of(Map.of(AugmentFunction.EV, 20), List.of()).evFloorPerStat(), "flat base");
        assertEquals(20, EffectiveAugments.of(Map.of(AugmentFunction.EV, 20),
                List.of(tether("ev", TetherClass.QUALITY, 3))).evFloorPerStat(), "tether contributes NOTHING");
        assertEquals(0, EffectiveAugments.of(Map.of(), List.of()).evFloorPerStat(), "no mod → no floor");
    }

    @Test
    void dropYieldIsDiscreteTetherAddsFlatBudget() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.DROP_YIELD, 2);   // +2 budget ceiling
        assertEquals(2, EffectiveAugments.of(base, List.of()).dropYieldBonus(), "flat base, no tether");
        // a LEVELED mod: tier I = +1, tier III = +3 - every tier is felt (the old ×1.1 rounded away to nothing)
        assertEquals(3,
                EffectiveAugments.of(base, List.of(tether("drop_yield", TetherClass.THROUGHPUT, 1))).dropYieldBonus());
        assertEquals(5,
                EffectiveAugments.of(base, List.of(tether("drop_yield", TetherClass.THROUGHPUT, 3))).dropYieldBonus());
        assertEquals(0, EffectiveAugments.of(Map.of(), List.of()).dropYieldBonus(), "no mod → no bonus");
    }

    @Test
    void hatchIsDiscreteTetherAddsFlatLevelsClampedAtThree() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.HATCH, 1);
        assertEquals(1, EffectiveAugments.of(base, List.of()).hatchLevel());
        assertEquals(2, EffectiveAugments.of(base, List.of(tether("hatch", TetherClass.THROUGHPUT, 1))).hatchLevel(),
                "tier I is FELT on a level-1 mod");
        assertEquals(4, EffectiveAugments.of(base, List.of(tether("hatch", TetherClass.THROUGHPUT, 3))).hatchLevel(),
                "tethers push past Hatch Haste III (the 1s timer floor still rules)");
    }

    @Test
    void natureSelectorReadsTheRawBaseIndex() {
        assertEquals(4, EffectiveAugments.of(Map.of(AugmentFunction.NATURE, 4), List.of()).natureIndex(),
                "level IS the 1-based catalog index");
        assertEquals(0, EffectiveAugments.of(Map.of(), List.of()).natureIndex(), "no Nature augment → 0 (no lock)");
    }

    @Test
    void natureSelectorIsNeverAmplifiedByATether() {
        // A selector encodes a CHOICE, not a magnitude - even a (hypothetical) Nature tether must not scale the
        // index, or Adamant (4) would silently become a different nature. The raw base index passes through.
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.NATURE, 4);
        EffectiveAugments amped = EffectiveAugments.of(base, List.of(tether("nature", TetherClass.QUALITY, 3)));
        assertEquals(4, amped.natureIndex(), "selector index stays exactly 4 despite a tier-III tether");
        assertEquals(4.0, amped.magnitude(AugmentFunction.NATURE), 1e-9, "magnitude unscaled for a selector");
    }

    @Test
    void ballSelectorReadsTheRawBaseIndex() {
        assertEquals(7, EffectiveAugments.of(Map.of(AugmentFunction.BALL, 7), List.of()).ballIndex());
        assertEquals(0, EffectiveAugments.of(Map.of(), List.of()).ballIndex(), "no Ball augment → 0 (no lock)");
    }

    @Test
    void hiddenAbilityIsABinaryToggle() {
        assertFalse(EffectiveAugments.of(Map.of(), List.of()).forceHiddenAbility(), "absent → off");
        assertTrue(EffectiveAugments.of(Map.of(AugmentFunction.ABILITY, 1), List.of()).forceHiddenAbility(), "level 1 → on");
        // a tether can't amplify the toggle into or out of existence (selector → unscaled)
        assertTrue(EffectiveAugments.of(Map.of(AugmentFunction.ABILITY, 1),
                List.of(tether("ability", TetherClass.QUALITY, 3))).forceHiddenAbility());
    }

    @Test
    void eggMovesIsABinaryToggle() {
        assertFalse(EffectiveAugments.of(Map.of(), List.of()).teachEggMoves(), "absent → off");
        assertTrue(EffectiveAugments.of(Map.of(AugmentFunction.EGG_MOVE, 1), List.of()).teachEggMoves(), "level 1 → on");
    }
}
