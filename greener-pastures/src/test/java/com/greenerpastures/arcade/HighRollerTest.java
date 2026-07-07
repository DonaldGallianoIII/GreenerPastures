package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The High Roller Room - a fixed shelf of long-term goals. */
class HighRollerTest {

    @Test
    void fixedShelvesNeverMoveAndGuardTheirSlots() {
        assertEquals(3, HighRoller.CATALOG.size());
        assertEquals(HighRoller.MASTER_BALL_ID, HighRoller.wareAt(0).itemId());
        assertEquals(HighRoller.PRIME_EGG_ID, HighRoller.wareAt(1).itemId());
        assertEquals(HighRoller.LEGEND_DISK_ID, HighRoller.wareAt(2).itemId());
        assertNull(HighRoller.wareAt(-1));
        assertNull(HighRoller.wareAt(3));
    }

    @Test
    void pricesStayAspirational() {
        for (HighRoller.Ware w : HighRoller.CATALOG) {
            assertTrue(w.price() >= 5_000, w.name() + " must stay out of impulse-buy range");
        }
        assertEquals(100_000, HighRoller.wareAt(2).price(), "the Legend Disk is the season trophy");
        assertTrue(HighRoller.PRIME_EGG_PERFECT_IVS > 2, "Prime must beat the 1,200-Coin Mystery Egg");
    }
}
