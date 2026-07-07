package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** SLOTS - the paytable, and the whole 512-outcome space enumerated for the exact house edge. */
class SlotMachineTest {

    @Test
    void paytableRungs() {
        assertEquals(SlotMachine.PAY_JACKPOT, SlotMachine.multiplier(new int[]{0, 0, 0}), "three Voltorb");
        assertEquals(SlotMachine.PAY_TRIPLE, SlotMachine.multiplier(new int[]{4, 4, 4}), "three of a kind");
        assertEquals(SlotMachine.PAY_TWO_VOLT, SlotMachine.multiplier(new int[]{0, 0, 5}), "two Voltorb");
        assertEquals(SlotMachine.PAY_TWO_VOLT, SlotMachine.multiplier(new int[]{0, 5, 0}), "two Voltorb split");
        assertEquals(SlotMachine.PAY_PAIR, SlotMachine.multiplier(new int[]{3, 3, 7}), "plain pair");
        assertEquals(SlotMachine.PAY_PAIR, SlotMachine.multiplier(new int[]{3, 7, 3}), "outer pair");
        assertEquals(SlotMachine.PAY_PAIR, SlotMachine.multiplier(new int[]{0, 3, 3}), "pair with one Voltorb pays as pair");
        assertEquals(0, SlotMachine.multiplier(new int[]{1, 2, 3}), "bust");
        assertEquals(0, SlotMachine.multiplier(new int[]{0, 2, 3}), "single Voltorb is nothing");
        assertEquals(0, SlotMachine.multiplier(null));
        assertEquals(0, SlotMachine.multiplier(new int[]{1, 2}));
    }

    @Test
    void houseEdgeIsExactlyEnumerable() {
        int n = SlotMachine.SYMBOLS.size();
        long totalPaid = 0;
        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++)
                for (int c = 0; c < n; c++)
                    totalPaid += SlotMachine.multiplier(new int[]{a, b, c});
        assertEquals(457, totalPaid, "the paytable sums to 457/512 - change the table, change this number ON PURPOSE");
        double rtp = totalPaid / Math.pow(n, 3);
        assertTrue(rtp > 0.80 && rtp < 0.95, "return-to-player stays honest-casino (" + rtp + ")");
    }

    @Test
    void spinsAreInRangeAndBetsAreGuarded() {
        Random rng = new Random(42);
        for (int i = 0; i < 200; i++) {
            int[] r = SlotMachine.spin(rng);
            assertEquals(3, r.length);
            for (int f : r) assertTrue(f >= 0 && f < SlotMachine.SYMBOLS.size());
        }
        assertFalse(SlotMachine.betValid(SlotMachine.MIN_BET - 1));
        assertTrue(SlotMachine.betValid(SlotMachine.MIN_BET));
        assertTrue(SlotMachine.betValid(SlotMachine.MAX_BET));
        assertFalse(SlotMachine.betValid(SlotMachine.MAX_BET + 1));
        assertEquals(2500, SlotMachine.payout(new int[]{0, 0, 0}, 25), "jackpot at 25 = 2,500");
    }
}
