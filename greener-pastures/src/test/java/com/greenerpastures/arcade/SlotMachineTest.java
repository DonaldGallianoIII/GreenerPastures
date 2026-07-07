package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** SLOTS - the paytable (pairs 1.5x era), and the whole 512-outcome space enumerated exactly. */
class SlotMachineTest {

    @Test
    void paytableRungs() {
        long bet = 10;
        assertEquals(100 * bet, SlotMachine.payout(new int[]{0, 0, 0}, bet), "three Voltorb");
        assertEquals(15 * bet, SlotMachine.payout(new int[]{4, 4, 4}, bet), "three of a kind");
        assertEquals(3 * bet, SlotMachine.payout(new int[]{0, 0, 5}, bet), "two Voltorb");
        assertEquals(3 * bet, SlotMachine.payout(new int[]{0, 5, 0}, bet), "two Voltorb split");
        assertEquals(15, SlotMachine.payout(new int[]{3, 3, 7}, bet), "plain pair pays 1.5x");
        assertEquals(15, SlotMachine.payout(new int[]{3, 7, 3}, bet), "outer pair");
        assertEquals(15, SlotMachine.payout(new int[]{0, 3, 3}, bet), "pair with one Voltorb pays as pair");
        assertEquals(0, SlotMachine.payout(new int[]{1, 2, 3}, bet), "bust");
        assertEquals(0, SlotMachine.payout(new int[]{0, 2, 3}, bet), "single Voltorb is nothing");
        assertEquals(0, SlotMachine.payout(null, bet));
        assertEquals(0, SlotMachine.payout(new int[]{1, 2}, bet));
    }

    @Test
    void pairPayoutFloorsToWholeCoins() {
        assertEquals(7, SlotMachine.payout(new int[]{2, 2, 5}, 5), "1.5 x 5 floors to 7");
        assertEquals(37, SlotMachine.payout(new int[]{2, 2, 5}, 25), "1.5 x 25 floors to 37");
        assertEquals(150, SlotMachine.payout(new int[]{2, 2, 5}, 100));
    }

    @Test
    void houseEdgeIsExactlyEnumerable() {
        int n = SlotMachine.SYMBOLS.size();
        long bet = 2;                                    // pair 1.5x is integer-exact at bet 2
        long totalPaid = 0;
        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++)
                for (int c = 0; c < n; c++)
                    totalPaid += SlotMachine.payout(new int[]{a, b, c}, bet);
        assertEquals(977, totalPaid, "the paytable sums to 977/1024 at bet 2 - change it ON PURPOSE only");
        double rtp = totalPaid / (Math.pow(n, 3) * bet);
        assertTrue(rtp > 0.90 && rtp < 0.99, "generous-casino but still the house's game (" + rtp + ")");
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
