package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The Game Corner's one machine - board law, pot math, level ladder, and the daily kB fence. */
class VoltorbFlipTest {

    @Test
    void everyLevelDealsExactlyTwentyFiveTilesWithItsMix() {
        for (int lvl = 1; lvl <= VoltorbFlip.MAX_LEVEL; lvl++) {
            VoltorbFlip.Board b = VoltorbFlip.generate(lvl, new Random(42));
            int[] count = new int[4];
            for (int i = 0; i < VoltorbFlip.TILES; i++) count[b.tile(i)]++;
            int[] mix = VoltorbFlip.LEVELS[lvl - 1];
            assertEquals(mix[0], count[2], "level " + lvl + " twos");
            assertEquals(mix[1], count[3], "level " + lvl + " threes");
            assertEquals(mix[2], count[0], "level " + lvl + " voltorbs");
            assertEquals(VoltorbFlip.TILES - mix[0] - mix[1] - mix[2], count[1], "level " + lvl + " ones");
        }
    }

    @Test
    void chipsAlwaysAccountForEveryTile() {
        VoltorbFlip.Board b = VoltorbFlip.generate(4, new Random(7));
        int sumFromRows = 0, sumFromCols = 0, voltsFromRows = 0;
        int total = 0;
        for (int i = 0; i < VoltorbFlip.TILES; i++) total += b.tile(i);
        for (int r = 0; r < VoltorbFlip.SIZE; r++) { sumFromRows += b.rowSum(r); voltsFromRows += b.rowVoltorbs(r); }
        for (int c = 0; c < VoltorbFlip.SIZE; c++) sumFromCols += b.colSum(c);
        assertEquals(total, sumFromRows);
        assertEquals(total, sumFromCols);
        assertEquals(VoltorbFlip.LEVELS[3][2], voltsFromRows);
    }

    @Test
    void potMultipliesAndVoltorbTakesEverything() {
        VoltorbFlip.Board b = VoltorbFlip.generate(1, new Random(1));
        int volt = -1, two = -1, three = -1;
        for (int i = 0; i < VoltorbFlip.TILES; i++) {
            if (b.tile(i) == 0 && volt < 0) volt = i;
            if (b.tile(i) == 2 && two < 0) two = i;
            if (b.tile(i) == 3 && three < 0) three = i;
        }
        assertEquals(2, VoltorbFlip.flip(b, two).coins());
        assertEquals(6, VoltorbFlip.flip(b, three).coins());
        VoltorbFlip.Flip bust = VoltorbFlip.flip(b, volt);
        assertTrue(bust.bust());
        assertEquals(0, bust.coins(), "the pot is LOST on a bust, never banked");
        assertTrue(b.over);
        assertFalse(VoltorbFlip.flip(b, two == 0 ? 1 : 0).wasNew(), "finished boards refuse further flips");
    }

    @Test
    void clearingMeansEveryTwoAndThreeButOnesAreOptional() {
        VoltorbFlip.Board b = VoltorbFlip.generate(2, new Random(3));
        long expectedPot = 1;
        for (int i = 0; i < VoltorbFlip.TILES; i++) if (b.tile(i) >= 2) expectedPot *= b.tile(i);
        VoltorbFlip.Flip last = null;
        for (int i = 0; i < VoltorbFlip.TILES; i++) if (b.tile(i) >= 2) last = VoltorbFlip.flip(b, i);
        assertNotNull(last);
        assertTrue(last.cleared(), "all 2s+3s flipped = cleared, with every 1 still face-down");
        assertEquals(expectedPot, last.coins());
        assertEquals(2L * 2 * 2 * 2 * 3, expectedPot, "level 2 mix = x2^4 * x3 = 48");
    }

    @Test
    void doubleFlippingATileNeverDoubleCounts() {
        VoltorbFlip.Board b = VoltorbFlip.generate(1, new Random(9));
        int two = -1;
        for (int i = 0; i < VoltorbFlip.TILES; i++) if (b.tile(i) == 2) { two = i; break; }
        assertEquals(2, VoltorbFlip.flip(b, two).coins());
        VoltorbFlip.Flip again = VoltorbFlip.flip(b, two);
        assertFalse(again.wasNew());
        assertEquals(2, again.coins(), "re-flip is a no-op, not a pot doubler");
    }

    @Test
    void levelLadderClimbsOnClearDropsOnBustClampsAtEnds() {
        assertEquals(2, VoltorbFlip.nextLevel(1, true, false));
        assertEquals(1, VoltorbFlip.nextLevel(2, false, true));
        assertEquals(1, VoltorbFlip.nextLevel(1, false, true), "floor at 1");
        assertEquals(VoltorbFlip.MAX_LEVEL, VoltorbFlip.nextLevel(VoltorbFlip.MAX_LEVEL, true, false), "cap at 7");
        assertEquals(3, VoltorbFlip.nextLevel(3, false, false), "cashout holds the level");
    }

    @Test
    void theHousePaysAtMostOneKilobyteADay() {
        assertEquals(500, VoltorbFlip.payable(500, 0));
        assertEquals(1024, VoltorbFlip.payable(5184, 0), "a level-7 clear caps the day in one pot");
        assertEquals(24, VoltorbFlip.payable(500, 1000), "only the day's remainder is payable");
        assertEquals(0, VoltorbFlip.payable(500, 1024), "capped day pays zero");
        assertEquals(0, VoltorbFlip.payable(0, 0));
    }

    @Test
    void generationIsDeterministicPerSeedButVariesAcrossSeeds() {
        VoltorbFlip.Board a = VoltorbFlip.generate(3, new Random(123));
        VoltorbFlip.Board b = VoltorbFlip.generate(3, new Random(123));
        VoltorbFlip.Board c = VoltorbFlip.generate(3, new Random(456));
        boolean same = true, diff = false;
        for (int i = 0; i < VoltorbFlip.TILES; i++) {
            if (a.tile(i) != b.tile(i)) same = false;
            if (a.tile(i) != c.tile(i)) diff = true;
        }
        assertTrue(same, "same seed = same board (resume-safe)");
        assertTrue(diff, "different seed = different board");
    }
}
