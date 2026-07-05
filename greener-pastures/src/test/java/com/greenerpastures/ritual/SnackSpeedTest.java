package com.greenerpastures.ritual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Snack Overdrive pt.2 — the credit queue that makes bite-time stacking real. */
class SnackSpeedTest {

    @Test
    void noSeasoningsReproducesVanillaExactly() {
        // 0.5 credits per hit → spawn on every second random tick, exactly like the vanilla countdown.
        double c = 0;
        int spawns = 0;
        for (int hit = 1; hit <= 10; hit++) {
            SnackSpeed.CreditRoll r = SnackSpeed.onRandomTick(c, SnackSpeed.trueMultiplier(List.of()), true);
            spawns += r.spawns();
            c = r.remaining();
        }
        assertEquals(5, spawns, "10 hits at vanilla rate = 5 spawns");
    }

    @Test
    void everyCopyCountsMultiplicatively() {
        double sixEga = SnackSpeed.trueMultiplier(List.of(0.1, 0.1, 0.1, 0.1, 0.1, 0.1));
        assertEquals(Math.pow(0.9, 6), sixEga, 1e-9, "6 EGAs = ×0.9⁶ — stacking finally does something");
        double sixGold = SnackSpeed.trueMultiplier(List.of(0.25, 0.25, 0.25, 0.25, 0.25, 0.25));
        assertTrue(sixGold < sixEga, "golden apples (0.25) out-speed EGAs (0.1) per Cobblemon's own data");
        assertEquals(SnackSpeed.MIN_MULTIPLIER,
                SnackSpeed.trueMultiplier(java.util.Collections.nCopies(40, 0.25)), 1e-9,
                "the ×0.1 fence holds against absurd stacks");
    }

    @Test
    void expectedThroughputIsExactOverManyHits() {
        // interval multiplier 0.5 → 1 credit/hit → a spawn EVERY hit: 2× throughput, no drift.
        double c = 0;
        int spawns = 0;
        for (int hit = 1; hit <= 100; hit++) {
            SnackSpeed.CreditRoll r = SnackSpeed.onRandomTick(c, 0.5, true);
            spawns += r.spawns();
            c = r.remaining();
        }
        assertEquals(100, spawns);
        // multiplier 0.2 → 2.5 credits/hit → 250 spawns per 100 hits (fractions carry exactly).
        c = 0; spawns = 0;
        for (int hit = 1; hit <= 100; hit++) {
            SnackSpeed.CreditRoll r = SnackSpeed.onRandomTick(c, 0.2, true);
            spawns += r.spawns();
            c = r.remaining();
        }
        assertEquals(250, spawns);
    }

    @Test
    void noPlayerHoldsCreditsAndCapsTheBank() {
        double c = 0;
        for (int i = 0; i < 100; i++) c = SnackSpeed.onRandomTick(c, 0.1, false).remaining();
        assertEquals(SnackSpeed.CREDIT_CAP, c, 1e-9, "AFK-far banking fences at the cap");
        SnackSpeed.CreditRoll back = SnackSpeed.onRandomTick(c, 0.1, true);
        assertEquals(SnackSpeed.BURST_CAP, back.spawns(), "return → burst-capped release, remainder banked");
        assertTrue(back.remaining() > 0);
    }

    @Test
    void loreFactorMatchesThroughput() {
        assertEquals(1.0, SnackSpeed.throughputFactor(List.of()), 1e-9);
        assertEquals(1.0 / Math.pow(0.9, 6), SnackSpeed.throughputFactor(
                List.of(0.1, 0.1, 0.1, 0.1, 0.1, 0.1)), 1e-9);
    }
}
