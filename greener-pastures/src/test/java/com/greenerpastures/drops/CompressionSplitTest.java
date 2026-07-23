package com.greenerpastures.drops;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Headless tests for the 50/50 compression split (Deuce, 2026-07-22): half the bonus → frequency, half → yield. */
class CompressionSplitTest {

    @Test
    void leverHalvesTheBonus() {
        assertEquals(1.0, CompressionSplit.lever(1.0), 1e-9, "no compression → no lever");
        assertEquals(1.5, CompressionSplit.lever(2.0), 1e-9, "2× press → ×1.5 on each half");
        assertEquals(2.0, CompressionSplit.lever(3.0), 1e-9, "3× press → ×2.0 on each half");
        assertEquals(1.025, CompressionSplit.lever(1.05), 1e-9, "one press → a sliver each way");
        assertEquals(1.0, CompressionSplit.lever(0.5), 1e-9, "below 1 clamps - never a penalty");
    }

    @Test
    void evRoundHitsTheBoundaries() {
        assertEquals(3, CompressionSplit.evRound(3.0, 0.99), "whole number → exact, rng irrelevant");
        assertEquals(2, CompressionSplit.evRound(1.5, 0.4), "1.5, roll 0.4 < .5 → rounds UP to 2");
        assertEquals(1, CompressionSplit.evRound(1.5, 0.6), "1.5, roll 0.6 ≥ .5 → floors to 1");
        assertEquals(0, CompressionSplit.evRound(0.0, 0.0), "nothing stays nothing");
    }

    @Test
    void evRoundPreservesExpectedValue() {
        long sum = 0; int n = 200_000;
        Random r = new Random(42);
        for (int i = 0; i < n; i++) sum += CompressionSplit.evRound(1.5, r.nextDouble());
        assertEquals(1.5, sum / (double) n, 0.01, "×1.5 on a qty-1 drop must AVERAGE 1.5, not floor to 1");
    }

    @Test
    void inflateScalesEachCountEvRounded() {
        Map<String, Integer> in = new LinkedHashMap<>();
        in.put("minecraft:gold_ingot", 2);   // 2 × 1.5 = 3.0 exact
        in.put("cobblemon:quick_claw", 1);   // 1 × 1.5 = 1.5 → roll 0.4 → 2
        Iterator<Double> rolls = List.of(0.9, 0.4).iterator();
        Map<String, Integer> out = CompressionSplit.inflate(in, 1.5, rolls::next);
        assertEquals(3, out.get("minecraft:gold_ingot"));
        assertEquals(2, out.get("cobblemon:quick_claw"), "a 5%-single quick_claw now scales with compression too");
    }

    @Test
    void inflateIsANoOpAtOrBelowOne() {
        Map<String, Integer> in = Map.of("x", 5);
        assertSame(in, CompressionSplit.inflate(in, 1.0, () -> 0.0), "no yield lever → untouched");
        Map<String, Integer> empty = Map.of();
        assertSame(empty, CompressionSplit.inflate(empty, 2.0, () -> 0.0), "empty → untouched");
    }

    @Test
    void splitBeatsPureFrequencyBelowTheCapAndSurvivesAboveIt() {
        // Below cap: total output = freq × yield = 1.5 × 1.5 = 2.25 > the old flat 2.0.
        double lever = CompressionSplit.lever(2.0);
        assertEquals(2.25, lever * lever, 1e-9, "2× split yields 2.25× total below the cap");
        // Above cap: frequency is clamped to 1.0, but the yield half still multiplies output.
        double cappedFreq = Math.min(1.0, 0.9 * lever);   // already near-certain proc
        assertEquals(1.0, cappedFreq, 1e-9, "frequency clamps at certainty");
        assertTrue(lever > 1.0, "...but yield keeps paying past the cap");
    }
}
