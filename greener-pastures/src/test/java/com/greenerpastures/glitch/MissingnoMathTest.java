package com.greenerpastures.glitch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The 1M-lifetime MissingNo. odometer + the never-repeat sprite rotation. */
class MissingnoMathTest {

    @Test
    void onePerMillionForever() {
        assertEquals(0, MissingnoMath.claimable(999_999, 0), "999,999 is not a million");
        assertEquals(1, MissingnoMath.claimable(1_000_000, 0));
        assertEquals(0, MissingnoMath.claimable(1_000_000, 1), "claimed - wait for the next million");
        assertEquals(3, MissingnoMath.claimable(3_500_000, 0), "every million pays, retroactively");
        assertEquals(1, MissingnoMath.claimable(3_500_000, 2));
        assertEquals(0, MissingnoMath.claimable(-5, 0), "garbage in, zero out");
        assertEquals(0, MissingnoMath.claimable(2_000_000, 99), "over-claimed never goes negative");
    }

    @Test
    void progressBarIsTheRemainder() {
        assertEquals(0.0, MissingnoMath.progressToNext(0), 1e-9);
        assertEquals(0.5, MissingnoMath.progressToNext(500_000), 1e-9);
        assertEquals(0.5, MissingnoMath.progressToNext(2_500_000), 1e-9, "post-claim millions restart the bar");
    }

    @Test
    void rotationNeverRepeatsAndCoversAllSprites() {
        java.util.Random rng = new java.util.Random(151);   // the glitch's own dex number
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int cur = 0;
        for (int i = 0; i < 300; i++) {
            int next = MissingnoMath.pickNext(cur, 5, rng.nextDouble());
            assertNotEquals(cur, next, "the glitch must visibly glitch - never the same sprite twice");
            assertTrue(next >= 0 && next < 5);
            seen.add(next);
            cur = next;
        }
        assertEquals(5, seen.size(), "all five sprites appear");
        assertEquals(0, MissingnoMath.pickNext(0, 1, 0.5), "degenerate single-species list is safe");
    }
}
