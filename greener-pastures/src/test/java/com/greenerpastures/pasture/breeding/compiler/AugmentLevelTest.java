package com.greenerpastures.pasture.breeding.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Two-tier augments (Deuce 2026-07-05): level II = 1.5× effect, THREE slots total. Pure math only. */
class AugmentLevelTest {

    @Test
    void levelTwoIsOnePointFiveTimes() {
        assertEquals(45, AugmentType.SHINY.valueAt(2), "30% → 45%");
        assertEquals(300, AugmentType.DROP_RATE.valueAt(2), "+2.00% → +3.00%");
        assertEquals(2, AugmentType.DROP_YIELD.valueAt(2));
        assertEquals(30, AugmentType.ENRICHMENT.valueAt(2), "+20% → +30% render");
        assertEquals(4, AugmentType.IV_FLOOR.valueAt(2), "4.5 rounds DOWN — perfection stays a chase");
        assertEquals(2, AugmentType.SPEED.valueAt(2), "native curve: level 2 = ×2 cadence");
        assertEquals(2, AugmentType.HATCH.valueAt(2), "native curve: level 2 = ×0.25 hatch");
    }

    @Test
    void selectorsAndBinariesStaySingleLevel() {
        assertEquals(1, AugmentType.NATURE.maxLevel());
        assertEquals(1, AugmentType.BALL.maxLevel());
        assertEquals(1, AugmentType.ABILITY.maxLevel());
        assertEquals(1, AugmentType.EGG_MOVES.maxLevel());
        assertEquals(1, AugmentType.EV.maxLevel());
        assertEquals(2, AugmentType.SHINY.maxLevel());
        assertEquals(2, AugmentType.HATCH.maxLevel());
    }

    @Test
    void levelTwoOccupiesThreeSlots() {
        assertEquals(0, AugmentType.slotsForLevel(0));
        assertEquals(1, AugmentType.slotsForLevel(1));
        assertEquals(3, AugmentType.slotsForLevel(2), "Deuce: 3 slots total — not even possible on a Copper Kernel");
    }

    @Test
    void levelDetectionIsBaseRelative() {
        // pure levelOf: effective magnitude vs the two tier values
        assertEquals(0, AugmentType.levelOf(0, 30, 45, 2));
        assertEquals(1, AugmentType.levelOf(30, 30, 45, 2));
        assertEquals(1, AugmentType.levelOf(44, 30, 45, 2));
        assertEquals(2, AugmentType.levelOf(45, 30, 45, 2));
        assertEquals(2, AugmentType.levelOf(99, 30, 45, 2), "QA-set overshoot still reads II");
        assertEquals(1, AugmentType.levelOf(99, 30, 45, 1), "single-level types never report II");
        // the Greener birth-quirk: base 500cp drop rate is 0 EFFECTIVE magnitude → not installed, no phantom slots
        assertEquals(0, AugmentType.levelOf(500 - 500, 200, 300, 2));
        assertEquals(1, AugmentType.levelOf(700 - 500, 200, 300, 2), "base + one install = level I");
        assertEquals(2, AugmentType.levelOf(800 - 500, 200, 300, 2), "base + upgrade = level II");
    }
}
