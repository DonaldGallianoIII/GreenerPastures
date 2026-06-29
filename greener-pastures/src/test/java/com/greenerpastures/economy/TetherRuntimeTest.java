package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the per-cycle fed-vs-starved tether decision. */
class TetherRuntimeTest {

    private static SoulTether shiny(int tier) { return new SoulTether("shiny", TetherClass.QUALITY, tier); }
    private static SoulTether dropRate(int tier) { return new SoulTether("drop_rate", TetherClass.THROUGHPUT, tier); }
    private static final Map<AugmentFunction, Integer> SHINY5 = Map.of(AugmentFunction.SHINY, 5);

    @Test
    void fedAmplifiesAndDrains() {
        // shiny tier II = quality burn 8×2 = 16; balance 100 covers it
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(shiny(2)), 100);
        assertTrue(r.amplified());
        assertEquals(16L, r.drain());
        assertEquals(6.0, r.effective().magnitude(AugmentFunction.SHINY), 1e-9, "5 × 1.20 while fed");
    }

    @Test
    void starvedFallsBackToBaseWithNoDrain() {
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(shiny(2)), 10);   // 10 < 16
        assertFalse(r.amplified());
        assertEquals(0L, r.drain(), "can't pay → don't drain");
        assertEquals(5.0, r.effective().magnitude(AugmentFunction.SHINY), 1e-9, "base only when starved");
    }

    @Test
    void exactlyAffordableIsFed() {
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(shiny(2)), 16);
        assertTrue(r.amplified());
        assertEquals(16L, r.drain());
    }

    @Test
    void noTethersIsBaseAndNoDrain() {
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(), 1000);
        assertFalse(r.amplified());
        assertEquals(0L, r.drain());
        assertEquals(5.0, r.effective().magnitude(AugmentFunction.SHINY), 1e-9);
    }

    @Test
    void totalBurnSumsEveryTether() {
        // shiny tier II (quality 16) + speed tier I (throughput 3) = 19
        long burn = TetherRuntime.totalBurn(List.of(shiny(2), new SoulTether("speed", TetherClass.THROUGHPUT, 1)));
        assertEquals(19L, burn);
    }

    @Test
    void blankTetherBurnsNothingSoItStaysBase() {
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(SoulTether.blank()), 1000);
        assertEquals(0L, TetherRuntime.totalBurn(List.of(SoulTether.blank())));
        assertFalse(r.amplified());
        assertEquals(5.0, r.effective().magnitude(AugmentFunction.SHINY), 1e-9);
    }

    @Test
    void selectKeepsOnlyTheNamedFunctions() {
        List<SoulTether> all = List.of(shiny(2), dropRate(2), SoulTether.blank());
        List<SoulTether> drops = TetherRuntime.select(all, EnumSet.of(AugmentFunction.DROP_RATE));
        assertEquals(1, drops.size());
        assertEquals("drop_rate", drops.get(0).function(), "blanks + non-matching functions dropped");
    }

    @Test
    void resolveForChargesOnlyTheConsumersOwnTethers() {
        // a Kernel carrying BOTH a shiny mod+tether (the breeder's) and a drop-rate mod+tether (the Harvester's)
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.SHINY, 5, AugmentFunction.DROP_RATE, 25);
        List<SoulTether> tethers = List.of(shiny(2), dropRate(2));   // burns: shiny 16 (quality), drop_rate 6 (throughput)
        long balance = 1000;

        // Harvester resolves only DROP_RATE → drains 6, amplifies drop-rate (25×1.2=30), leaves shiny at base
        TetherRuntime.Resolution drops =
                TetherRuntime.resolveFor(base, tethers, balance, EnumSet.of(AugmentFunction.DROP_RATE));
        assertEquals(6L, drops.drain(), "only the drop-rate tether's burn");
        assertEquals(30.0, drops.effective().magnitude(AugmentFunction.DROP_RATE), 1e-9);
        assertEquals(5.0, drops.effective().magnitude(AugmentFunction.SHINY), 1e-9, "shiny is the breeder's — not amplified here");

        // breeder resolves only SHINY → drains 16, amplifies shiny, leaves drop-rate at base
        TetherRuntime.Resolution breed =
                TetherRuntime.resolveFor(base, tethers, balance, EnumSet.of(AugmentFunction.SHINY));
        assertEquals(16L, breed.drain(), "only the shiny tether's burn");
        assertEquals(6.0, breed.effective().magnitude(AugmentFunction.SHINY), 1e-9);
        assertEquals(25.0, breed.effective().magnitude(AugmentFunction.DROP_RATE), 1e-9);

        // disjoint sets ⇒ each tether billed exactly once across the two clocks (no double-charge, no gap)
        assertEquals(TetherRuntime.totalBurn(tethers), drops.drain() + breed.drain());
    }

    @Test
    void resolveForStarvesWhenItCantCoverItsOwnBurn() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.DROP_RATE, 25);
        TetherRuntime.Resolution r =
                TetherRuntime.resolveFor(base, List.of(dropRate(2)), 5, EnumSet.of(AugmentFunction.DROP_RATE)); // 5 < 6
        assertFalse(r.amplified());
        assertEquals(0L, r.drain());
        assertEquals(25.0, r.effective().magnitude(AugmentFunction.DROP_RATE), 1e-9, "starved → base drop rate, no drain");
    }

    @Test
    void resolveForAmplifiesEnrichmentMultiplierForTheRenderer() {
        // the Renderer's exact path: resolveFor({ENRICHMENT}) → effective().enrichmentMultiplier()
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.ENRICHMENT, 20);          // +20% base → 1.20×
        SoulTether enrich = new SoulTether("enrichment", TetherClass.THROUGHPUT, 1);          // ×1.10, burn 3
        TetherRuntime.Resolution r =
                TetherRuntime.resolveFor(base, List.of(enrich), 100, EnumSet.of(AugmentFunction.ENRICHMENT));
        assertEquals(3L, r.drain(), "only the enrichment tether's burn");
        assertEquals(1.22, r.effective().enrichmentMultiplier(), 1e-9, "20 × 1.10 = 22% → 1.22×");
    }
}
