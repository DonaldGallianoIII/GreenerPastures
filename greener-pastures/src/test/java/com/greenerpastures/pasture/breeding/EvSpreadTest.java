package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for the EV allocation spread (BUG-002) — no Minecraft needed. */
class EvSpreadTest {

    @Test
    void targetedSpreadIsKeptAsIs() {
        EvSpread s = new EvSpread(0, 252, 0, 0, 4, 252);   // a legal Atk/Spe sweeper spread
        assertEquals(252, s.atk());
        assertEquals(4, s.spd());
        assertEquals(252, s.spe());
        assertEquals(508, s.total());
        assertFalse(s.isEmpty());
    }

    @Test
    void perStatClampedTo252() {
        assertEquals(252, new EvSpread(999, 0, 0, 0, 0, 0).hp());
    }

    @Test
    void negativesFloorToZero() {
        assertEquals(0, new EvSpread(-5, 0, 0, 0, 0, 0).hp());
    }

    @Test
    void totalTrimmedTo510_fromTheLastStatBack() {
        EvSpread s = new EvSpread(252, 252, 252, 0, 0, 0);   // 756 → must trim to 510
        assertEquals(510, s.total());
        assertEquals(252, s.hp(), "earliest stat preserved");
        assertEquals(252, s.atk(), "second stat preserved");
        assertEquals(6, s.def(), "last allocated stat trimmed (252+252+6 = 510)");
    }

    @Test
    void noneIsEmpty() {
        assertTrue(EvSpread.NONE.isEmpty());
        assertEquals(0, EvSpread.NONE.total());
    }
}
