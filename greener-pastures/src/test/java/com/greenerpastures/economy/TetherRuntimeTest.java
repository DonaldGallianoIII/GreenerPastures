package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the per-cycle fed-vs-starved tether decision. */
class TetherRuntimeTest {

    private static SoulTether shiny(int tier) { return new SoulTether("shiny", TetherClass.QUALITY, tier); }
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
}
