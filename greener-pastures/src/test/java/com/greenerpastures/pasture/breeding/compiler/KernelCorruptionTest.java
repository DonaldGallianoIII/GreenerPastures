package com.greenerpastures.pasture.breeding.compiler;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the Vaal-roll weight table - the gamble must match its advertised odds. */
class KernelCorruptionTest {

    @Test
    void weightsSumToExactlyOneHundred() {
        assertEquals(100, KernelCorruption.BLESSED_PCT + KernelCorruption.WILD_PCT
                + KernelCorruption.NOTHING_PCT + KernelCorruption.BRICKED_PCT);
    }

    @Test
    void observedFrequenciesMatchTheTable() {
        Random rng = new Random(2026_07_04L);
        Map<KernelCorruption.Outcome, Integer> counts = new EnumMap<>(KernelCorruption.Outcome.class);
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            KernelCorruption.Roll r = KernelCorruption.roll(rng);
            counts.merge(r.outcome(), 1, Integer::sum);
            assertTrue(r.variant() == 0 || r.variant() == 1);
        }
        assertEquals(KernelCorruption.BLESSED_PCT / 100.0, counts.get(KernelCorruption.Outcome.BLESSED) / (double) n, 0.01);
        assertEquals(KernelCorruption.WILD_PCT / 100.0, counts.get(KernelCorruption.Outcome.WILD) / (double) n, 0.01);
        assertEquals(KernelCorruption.NOTHING_PCT / 100.0, counts.get(KernelCorruption.Outcome.NOTHING) / (double) n, 0.01);
        assertEquals(KernelCorruption.BRICKED_PCT / 100.0, counts.get(KernelCorruption.Outcome.BRICKED) / (double) n, 0.01);
    }

    @Test
    void revealLinesCoverEveryOutcome() {
        for (KernelCorruption.Outcome o : KernelCorruption.Outcome.values()) {
            String line = KernelCorruption.reveal(new KernelCorruption.Roll(o, 0), "x");
            assertTrue(line.contains("⛧"), o + " reveal must carry the mark");
        }
    }
}
