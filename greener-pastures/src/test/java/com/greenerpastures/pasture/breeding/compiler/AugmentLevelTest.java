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
        assertEquals(4, AugmentType.IV_FLOOR.valueAt(2), "4.5 rounds DOWN - perfection stays a chase");
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
        assertEquals(3, AugmentType.slotsForLevel(2), "Deuce: 3 slots total - not even possible on a Copper Kernel");
    }

    @Test
    void levelDetectionIsBaseRelative() {
        // pure levelOf: effective magnitude vs the three tier values (III = corruption-only)
        assertEquals(0, AugmentType.levelOf(0, 30, 45, 60, 2));
        assertEquals(1, AugmentType.levelOf(30, 30, 45, 60, 2));
        assertEquals(1, AugmentType.levelOf(44, 30, 45, 60, 2));
        assertEquals(2, AugmentType.levelOf(45, 30, 45, 60, 2));
        assertEquals(3, AugmentType.levelOf(60, 30, 45, 60, 2), "a blessed kernel reads TIER III");
        assertEquals(3, AugmentType.levelOf(99, 30, 45, 60, 2), "QA-set overshoot reads the corrupt ceiling");
        assertEquals(1, AugmentType.levelOf(99, 30, 45, 60, 1), "single-level types never report II+");
        // the Greener birth-quirk: base 500cp drop rate is 0 EFFECTIVE magnitude → not installed, no phantom slots
        assertEquals(0, AugmentType.levelOf(500 - 500, 200, 300, 400, 2));
        assertEquals(1, AugmentType.levelOf(700 - 500, 200, 300, 400, 2), "base + one install = level I");
        assertEquals(2, AugmentType.levelOf(800 - 500, 200, 300, 400, 2), "base + upgrade = level II");
    }

    @Test
    void dropYieldLevelTwoIsNotMisreadAsCorruptedTierThree() {
        // Drop Yield value 1 → valueAt(2)=round(1.5)=2 collides with valueAt(3)=1×2=2. A legit level-II
        // install (magnitude 2) must read as II, NOT the corruption-only III (Deuce's "shows corrupted" bug).
        assertEquals(2, AugmentType.DROP_YIELD.valueAt(2));
        assertEquals(2, AugmentType.DROP_YIELD.valueAt(3), "the collision that caused the bug");
        int v1 = AugmentType.DROP_YIELD.value, v2 = AugmentType.DROP_YIELD.valueAt(2), v3 = AugmentType.DROP_YIELD.valueAt(3);
        assertEquals(0, AugmentType.levelOf(0, v1, v2, v3, 2));
        assertEquals(1, AugmentType.levelOf(1, v1, v2, v3, 2), "one install = level I");
        assertEquals(2, AugmentType.levelOf(2, v1, v2, v3, 2), "level II must NOT read as corrupted III");
    }

    @Test
    void tierThreeStillReadsWhenGenuinelyHigher() {
        // guard: the v3>v2 fix must not suppress III for augments whose curve DOES separate (shiny 45→60)
        assertEquals(3, AugmentType.levelOf(60, 30, 45, 60, 2), "shiny III still detected");
        assertEquals(3, AugmentType.levelOf(400, 200, 300, 400, 2), "drop-rate III still detected");
    }

    @Test
    void tierThreeIsCorruptionOnlyAndDoubleBase() {
        assertEquals(60, AugmentType.SHINY.valueAt(3), "III = 2× base");
        assertEquals(400, AugmentType.DROP_RATE.valueAt(3));
        assertEquals(5, AugmentType.IV_FLOOR.valueAt(3), "even the glitch never grants a 6th perfect");
        assertEquals(3, AugmentType.SPEED.valueAt(3), "native curve: ×3 cadence");
        assertEquals(3, AugmentType.HATCH.valueAt(3), "native curve: ×0.1 hatch");
        assertEquals(2, AugmentType.SHINY.maxLevel(), "the Augmenter can NEVER reach III - corruption only");
        assertEquals(3, AugmentType.CORRUPT_MAX_LEVEL);
    }
}
