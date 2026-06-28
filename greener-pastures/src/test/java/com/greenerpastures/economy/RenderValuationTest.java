package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void nanMultiplierFloorsToBaseNotZero() {
        assertEquals(50L, RenderValuation.dataFor(10, 5, Double.NaN),
                "a NaN enrichment must floor to 1×, never silently zero income");
        assertEquals(50L, RenderValuation.dataFor(10, 5, -2.0), "negative floors to 1× too");
    }

    @Test
    void hugeProductSaturatesNonNegative() {
        long v = RenderValuation.dataFor(Integer.MAX_VALUE, Long.MAX_VALUE, 1e9);
        assertTrue(v > 0, "double→long narrowing saturates to MAX_VALUE; it must never wrap negative");
    }
}
