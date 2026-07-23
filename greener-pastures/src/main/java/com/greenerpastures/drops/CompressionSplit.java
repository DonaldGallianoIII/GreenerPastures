package com.greenerpastures.drops;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Splits a Compression multiplier across TWO drop levers instead of pouring it all into frequency (Deuce,
 * 2026-07-22). Half the bonus amplifies the proc CADENCE (as before, still clamped at certainty), the other
 * half amplifies YIELD - a per-species multiplier on the rolled drop counts that NEVER caps, so compression
 * keeps paying after a species already procs every sweep. Minecraft-free + unit-tested.
 *
 * <p>For a compression multiplier {@code M}, each lever gets {@code 1 + (M-1)×SHARE}: a 2× press → ×1.5
 * frequency AND ×1.5 yield. Below the frequency cap total output is the product (≈2.25× at 2×); above it,
 * only the uncapped yield half keeps growing. This is a reallocation of the SAME bonus, not a new one.
 */
public final class CompressionSplit {
    private CompressionSplit() {}

    /** Fraction of the compression bonus routed to EACH lever (frequency + yield). 0.5 = an even split. */
    public static final double SHARE = 0.5;

    /** The per-lever multiplier for a compression multiplier {@code M} - used for BOTH the frequency half and
     *  the yield half. {@code M ≤ 1} (no compression) → 1.0 (no-op). */
    public static double lever(double compMult) {
        return 1.0 + Math.max(0.0, compMult - 1.0) * SHARE;
    }

    /** Expected-value rounding: {@code floor(value)}, then +1 with probability equal to the fraction
     *  ({@code roll} in [0,1)). Keeps {@code E[out] == value}, so fractional yield is fair over many procs -
     *  a ×1.5 on a single quick_claw averages 1.5, never silently floors to 1. */
    public static int evRound(double value, double roll) {
        if (value <= 0.0) return 0;
        int base = (int) Math.floor(value);
        return roll < (value - base) ? base + 1 : base;
    }

    /** Multiply every drop count by {@code mult} (>1), EV-rounded per entry so the average equals
     *  {@code count × mult}. {@code rolls} supplies one [0,1) draw per entry. {@code mult ≤ 1} is a no-op. */
    public static Map<String, Integer> inflate(Map<String, Integer> counts, double mult, DoubleSupplier rolls) {
        if (mult <= 1.0 || counts.isEmpty()) return counts;
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            int n = evRound(e.getValue() * mult, rolls.getAsDouble());
            if (n > 0) out.put(e.getKey(), n);
        }
        return out;
    }
}
