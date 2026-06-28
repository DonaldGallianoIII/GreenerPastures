package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Headless tests for the egg → Data valuation (income side of the loop). */
class RenderValuationTest {

    @Test
    void flatValuePerEgg() {
        assertEquals(50L, RenderValuation.dataFor(10, 5, 1.0));
    }

    @Test
    void enrichmentMultipliesIncome() {
        assertEquals(100L, RenderValuation.dataFor(10, 5, 2.0));
    }

    @Test
    void enrichmentBelowOneIsFlooredToOne() {
        assertEquals(50L, RenderValuation.dataFor(10, 5, 0.5), "income is never penalized below base");
    }

    @Test
    void zeroInputsYieldZeroData() {
        assertEquals(0L, RenderValuation.dataFor(0, 5, 2.0));
        assertEquals(0L, RenderValuation.dataFor(10, 0, 2.0));
        assertEquals(0L, RenderValuation.dataFor(-3, 5, 2.0));
    }
}
