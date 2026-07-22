package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the fed-vs-starved tether decision. Billing lives on the TetherUpkeep ticker
 *  (per-second rent, Deuce 2026-07-21); resolve() only answers "can the owner cover a second of rent". */
class TetherRuntimeTest {

    private static SoulTether shiny(int tier) { return new SoulTether("shiny", TetherClass.QUALITY, tier); }
    private static SoulTether dropRate(int tier) { return new SoulTether("drop_rate", TetherClass.THROUGHPUT, tier); }
    private static final Map<AugmentFunction, Integer> SHINY5 = Map.of(AugmentFunction.SHINY, 5);

    @Test
    void fedAmplifiesWhenASecondOfRentIsCovered() {
        // shiny tier II = quality 100 centi/s → gate = 1 whole Data; balance 100 covers it
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(shiny(2)), 100);
        assertTrue(r.amplified());
        assertEquals(1L, r.upkeep(), "one second of rent, rounded up to whole Data");
        assertEquals(35.0, r.effective().magnitude(AugmentFunction.SHINY), 1e-9, "5 + 2 levels × 15 while fed");
    }

    @Test
    void starvedFallsBackToBase() {
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(shiny(2)), 0);   // broke
        assertFalse(r.amplified());
        assertEquals(0L, r.upkeep());
        assertEquals(5.0, r.effective().magnitude(AugmentFunction.SHINY), 1e-9, "base only when starved");
    }

    @Test
    void exactlyAffordableIsFed() {
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(shiny(2)), 1);   // gate is 1
        assertTrue(r.amplified());
    }

    @Test
    void noTethersIsBaseAndNoRent() {
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(), 1000);
        assertFalse(r.amplified());
        assertEquals(0L, r.upkeep());
        assertEquals(5.0, r.effective().magnitude(AugmentFunction.SHINY), 1e-9);
    }

    @Test
    void upkeepSumsEveryTetherInCentiPerSecond() {
        // shiny tier II (quality 100) + speed tier I (throughput 20) = 120 centi/s
        long centi = TetherRuntime.upkeepCentiPerSecond(List.of(shiny(2), new SoulTether("speed", TetherClass.THROUGHPUT, 1)));
        assertEquals(120L, centi);
    }

    @Test
    void blankTetherRentsNothingSoItStaysBase() {
        TetherRuntime.Resolution r = TetherRuntime.resolve(SHINY5, List.of(SoulTether.blank()), 1000);
        assertEquals(0L, TetherRuntime.upkeepCentiPerSecond(List.of(SoulTether.blank())));
        assertFalse(r.amplified());
        assertEquals(5.0, r.effective().magnitude(AugmentFunction.SHINY), 1e-9);
    }

    @Test
    void rentForPricesAwayWindowsCeilNeverFloor() {
        // The pre-paid catch-up contract (Deuce, 2026-07-21): away windows are priced by the second and
        // checked BEFORE the boost applies. Ceil - the house never rounds in the renter's favor.
        assertEquals(2160L, TetherRuntime.rentFor(3600,
                List.of(new SoulTether("drop_yield", TetherClass.THROUGHPUT, 3))), "1h of Yield III = 2160");
        assertEquals(1L, TetherRuntime.rentFor(1, List.of(shiny(1))), "50 centi rounds UP to 1 whole Data");
        assertEquals(0L, TetherRuntime.rentFor(0, List.of(shiny(3))), "no window, no rent");
        assertEquals(0L, TetherRuntime.rentFor(3600, List.of(SoulTether.blank())), "blanks never rent");
    }

    @Test
    void selectKeepsOnlyTheNamedFunctions() {
        List<SoulTether> all = List.of(shiny(2), dropRate(2), SoulTether.blank());
        List<SoulTether> drops = TetherRuntime.select(all, EnumSet.of(AugmentFunction.DROP_RATE));
        assertEquals(1, drops.size());
        assertEquals("drop_rate", drops.get(0).function(), "blanks + non-matching functions dropped");
    }

    @Test
    void resolveForAmplifiesOnlyTheConsumersOwnFunctions() {
        // a Kernel carrying BOTH a shiny mod+tether (the breeder's) and a drop-rate mod+tether (the
        // Harvester's) - each consumer amplifies only its own functions; nobody bills here (the rent
        // clock does), so overlap is a display concern, not a double-charge.
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.SHINY, 5, AugmentFunction.DROP_RATE, 25);
        List<SoulTether> tethers = List.of(shiny(2), dropRate(2));
        long balance = 1000;

        TetherRuntime.Resolution drops =
                TetherRuntime.resolveFor(base, tethers, balance, EnumSet.of(AugmentFunction.DROP_RATE));
        assertEquals(225.0, drops.effective().magnitude(AugmentFunction.DROP_RATE), 1e-9, "25 + 2lv × 100");
        assertEquals(5.0, drops.effective().magnitude(AugmentFunction.SHINY), 1e-9, "shiny is the breeder's - not amplified here");

        TetherRuntime.Resolution breed =
                TetherRuntime.resolveFor(base, tethers, balance, EnumSet.of(AugmentFunction.SHINY));
        assertEquals(35.0, breed.effective().magnitude(AugmentFunction.SHINY), 1e-9);
        assertEquals(25.0, breed.effective().magnitude(AugmentFunction.DROP_RATE), 1e-9);
    }

    @Test
    void resolveForStarvesABrokeOwner() {
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.DROP_RATE, 25);
        TetherRuntime.Resolution r =
                TetherRuntime.resolveFor(base, List.of(dropRate(2)), 0, EnumSet.of(AugmentFunction.DROP_RATE));
        assertFalse(r.amplified());
        assertEquals(25.0, r.effective().magnitude(AugmentFunction.DROP_RATE), 1e-9, "broke → base drop rate");
    }

    @Test
    void resolveForAmplifiesEnrichmentMultiplierForTheRenderer() {
        // the Renderer's exact path: resolveFor({ENRICHMENT}) → effective().enrichmentMultiplier()
        Map<AugmentFunction, Integer> base = Map.of(AugmentFunction.ENRICHMENT, 20);          // +20% base → 1.20×
        SoulTether enrich = new SoulTether("enrichment", TetherClass.THROUGHPUT, 1);          // +10%/lv, rent 0.2/s
        TetherRuntime.Resolution r =
                TetherRuntime.resolveFor(base, List.of(enrich), 100, EnumSet.of(AugmentFunction.ENRICHMENT));
        assertTrue(r.amplified());
        assertEquals(1.30, r.effective().enrichmentMultiplier(), 1e-9, "20 + 1lv × 10 = 30% → 1.30×");
    }
}
