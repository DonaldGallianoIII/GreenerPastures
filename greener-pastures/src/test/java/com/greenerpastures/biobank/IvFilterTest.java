package com.greenerpastures.biobank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the per-stat IV gate (Daemon FILTER node logic). */
class IvFilterTest {

    @Test
    void anyPassesEverything() {
        assertTrue(IvFilter.any().matches(new int[]{0, 0, 0, 0, 0, 0}));
        assertTrue(IvFilter.any().matches(new int[]{31, 31, 31, 31, 31, 31}));
    }

    @Test
    void minGateRejectsTooLowStats() {
        IvFilter atLeast20Hp = new IvFilter(new int[]{20, 0, 0, 0, 0, 0}, new int[]{31, 31, 31, 31, 31, 31});
        assertTrue(atLeast20Hp.matches(new int[]{20, 0, 0, 0, 0, 0}));
        assertFalse(atLeast20Hp.matches(new int[]{19, 31, 31, 31, 31, 31}), "HP below the floor fails the gate");
    }

    @Test
    void maxGateRejectsTooHighStats() {
        IvFilter atMost10Atk = new IvFilter(new int[]{0, 0, 0, 0, 0, 0}, new int[]{31, 10, 31, 31, 31, 31});
        assertTrue(atMost10Atk.matches(new int[]{31, 10, 31, 31, 31, 31}));
        assertFalse(atMost10Atk.matches(new int[]{0, 11, 0, 0, 0, 0}));
    }

    @Test
    void wrongShapedInputNeverMatches() {
        assertFalse(IvFilter.any().matches(new int[]{31, 31, 31}));
        assertFalse(IvFilter.any().matches(null));
    }

    @Test
    void constructorRequiresSixStats() {
        assertThrows(IllegalArgumentException.class, () -> new IvFilter(new int[]{0}, new int[]{31}));
    }
}
