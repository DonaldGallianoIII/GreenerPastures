package com.greenerpastures.ritual;

import java.util.function.DoubleSupplier;

/**
 * The gacha math, Minecraft-free + unit-tested: spend banked pulls at a base % with a PITY safety net, so a
 * carefully engineered pasture composition is never betrayed by variance. HARD pity forces a hit on the
 * {@code hardPity}-th pull since the last hit; optional SOFT pity ramps the chance from {@code softPityStart}
 * up to 100% at {@code hardPity}. Deterministic given the rng value, so the odds AND the pity guarantee are
 * tested exactly.
 */
public final class Gacha {
    private Gacha() {}

    /** Per-pasture, per-ritual gacha progress: pulls banked (earned by holding the composition) and the pity
     *  counter (consecutive misses since the last hit). Persisted with the pasture. */
    public record PullState(int bankedPulls, int pity) {
        public static final PullState EMPTY = new PullState(0, 0);

        public PullState {
            bankedPulls = Math.max(0, bankedPulls);
            pity = Math.max(0, pity);
        }

        public PullState bank(int n) { return new PullState(bankedPulls + Math.max(0, n), pity); }
    }

    /** The outcome of spending one pull: whether it hit, and the resulting state (pulls/pity updated). */
    public record Roll(boolean hit, PullState state) {}

    /** How many of {@code n} pulls HIT and the final state, given a per-pull rng supplier (∈ [0,1)). Powers
     *  the GUI "PULL ALL" and the simulator. */
    public record Session(int hits, PullState state) {}

    /** The chance (0..100) THIS pull resolves at, where {@code pullIndex} (1-based = pity+1) is how many
     *  attempts have passed since the last hit. Hard pity ⇒ 100 at/over {@code hardPity}; soft pity linearly
     *  ramps base→100 across [{@code softPityStart}, {@code hardPity}]; otherwise the flat base. */
    public static double effectiveChance(double base, int hardPity, int softPityStart, int pullIndex) {
        if (hardPity > 0 && pullIndex >= hardPity) return 100.0;
        if (softPityStart > 0 && hardPity > softPityStart && pullIndex >= softPityStart) {
            double frac = (double) (pullIndex - softPityStart) / (hardPity - softPityStart);
            return base + (100.0 - base) * frac;
        }
        return base;
    }

    /** Spend ONE banked pull. {@code rng} ∈ [0,1). No-op (miss, state unchanged) if nothing is banked. A hit
     *  resets pity to 0; a miss increments it. */
    public static Roll pull(PullState s, double baseChancePercent, int hardPity, int softPityStart, double rng) {
        if (s.bankedPulls() <= 0) return new Roll(false, s);
        int pullIndex = s.pity() + 1;
        double chance = effectiveChance(baseChancePercent, hardPity, softPityStart, pullIndex);
        boolean hit = rng * 100.0 < chance;
        return new Roll(hit, new PullState(s.bankedPulls() - 1, hit ? 0 : pullIndex));
    }

    /** Spend EVERY banked pull (the "PULL ALL" path), returning the hit count + final state. */
    public static Session pullAll(PullState s, double base, int hardPity, int softPityStart, DoubleSupplier rng) {
        int hits = 0;
        while (s.bankedPulls() > 0) {
            Roll r = pull(s, base, hardPity, softPityStart, rng.getAsDouble());
            if (r.hit()) hits++;
            s = r.state();
        }
        return new Session(hits, s);
    }
}
