package com.greenerpastures.biobank;

import java.util.Arrays;

/**
 * Per-stat IV gate — the logic behind the Daemon's <b>FILTER</b> node (6 stats, each a {@code [min,max]}
 * range). Minecraft-free + unit-tested; the MC adapter feeds it an egg's 6 IVs. Stat order matches
 * Cobblemon: HP, Atk, Def, SpA, SpD, Spe.
 */
public final class IvFilter {
    public static final int STATS = 6;

    private final int[] min, max;

    public IvFilter(int[] min, int[] max) {
        if (min == null || max == null || min.length != STATS || max.length != STATS) {
            throw new IllegalArgumentException("IvFilter needs exactly " + STATS + " min and max values");
        }
        this.min = min.clone();
        this.max = max.clone();
    }

    /** A filter that passes any egg (0..31 on every stat). */
    public static IvFilter any() {
        int[] lo = new int[STATS], hi = new int[STATS];
        Arrays.fill(hi, 31);
        return new IvFilter(lo, hi);
    }

    /** True if all 6 IVs fall within their ranges. A wrong-length / null input never matches. */
    public boolean matches(int[] ivs) {
        if (ivs == null || ivs.length != STATS) return false;
        for (int i = 0; i < STATS; i++) {
            if (ivs[i] < min[i] || ivs[i] > max[i]) return false;
        }
        return true;
    }

    public int[] min() { return min.clone(); }
    public int[] max() { return max.clone(); }
}
