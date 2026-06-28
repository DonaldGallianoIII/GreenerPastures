package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the re-inscribable tether economics (the anti-exploit rules). */
class TetherEconomicsTest {

    @Test
    void inscribeCostScalesWithTierSquared() {
        assertEquals(0L, TetherEconomics.inscribeCost(0));
        assertEquals(100L, TetherEconomics.inscribeCost(1));
        assertEquals(400L, TetherEconomics.inscribeCost(2));
        assertEquals(900L, TetherEconomics.inscribeCost(3));
        assertEquals(900L, TetherEconomics.inscribeCost(9), "clamped to tier III");
    }

    @Test
    void wipeRefundsHalf() {
        assertEquals(50L, TetherEconomics.wipeRefund(1));
        assertEquals(200L, TetherEconomics.wipeRefund(2));
        assertEquals(450L, TetherEconomics.wipeRefund(3));
    }

    @Test
    void aWipeNeverProfits() {
        for (int t = 1; t <= SoulTether.MAX_TIER; t++) {
            assertTrue(TetherEconomics.wipeRefund(t) < TetherEconomics.inscribeCost(t),
                    "tier " + t + ": refund must be strictly less than the inscribe cost (no free re-rolls)");
        }
    }

    @Test
    void inscribeThenWipeLeavesYouDown() {
        // inscribe tier III (pay 900), then wipe (get 450 back) → net -450, never a gain
        long net = TetherEconomics.wipeRefund(3) - TetherEconomics.inscribeCost(3);
        assertTrue(net < 0, "round-tripping a tether costs Data — it's a sink, not a faucet");
    }
}
