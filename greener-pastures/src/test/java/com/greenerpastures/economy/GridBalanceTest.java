package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the fuel-vs-trophy grid balance readout. */
class GridBalanceTest {

    @Test
    void netAndPositivityWhenFuelOutearns() {
        GridBalance g = new GridBalance(100, 40);
        assertEquals(60L, g.netPerCycle());
        assertTrue(g.netPositive());
    }

    @Test
    void netNegativeWhenBurnExceedsIncome() {
        GridBalance g = new GridBalance(30, 80);
        assertEquals(-50L, g.netPerCycle());
        assertFalse(g.netPositive(), "a trophy line that out-burns its fuel is draining");
    }

    @Test
    void cyclesToEmptyWhenDraining() {
        GridBalance g = new GridBalance(0, 10);   // pure burn
        assertEquals(5L, g.cyclesToEmpty(50));
    }

    @Test
    void neverEmptiesWhenNetPositive() {
        GridBalance g = new GridBalance(100, 40);
        assertEquals(Long.MAX_VALUE, g.cyclesToEmpty(50));
    }
}
