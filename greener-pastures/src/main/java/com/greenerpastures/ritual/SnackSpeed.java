package com.greenerpastures.ritual;

import java.util.List;

/**
 * Snack spawn-speed credit queue (Snack Overdrive pt.2 — Deuce, 2026-07-04): vanilla snack speed is
 * {@code max(2 × biteTimeMultiplier, 1)} RANDOM TICKS between spawns, where the multiplier comes from ONE
 * randomly-picked bite_time entry — a hard 2× throughput cap, stacking does nothing, and plain golden
 * apples (0.25) beat enchanted (0.1). Decompile-verified rot.
 *
 * <p>Replacement semantics: every bite_time copy counts, MULTIPLICATIVELY — 6 EGAs = ×0.9⁶ interval — and a
 * fractional-credit accumulator (the catch-up-brood pattern) converts the true rate into spawns with exact
 * expected value: each random-tick hit banks {@code 1/interval} credits; whole credits spend as spawns
 * (burst-capped), the remainder carries. With no bite seasonings this reproduces vanilla exactly
 * (0.5 credits/hit → a spawn every 2 hits). Pure + tested.
 */
public final class SnackSpeed {
    private SnackSpeed() {}

    /** Vanilla's base: 2 random ticks between spawns. */
    public static final double BASE_TICKS = 2.0;
    /** Fence: never faster than ×0.1 interval (20× vanilla base throughput) no matter the stack. */
    public static final double MIN_MULTIPLIER = 0.1;
    /** Max spawns released per random-tick hit — extras stay banked (no entity bursts). */
    public static final int BURST_CAP = 3;
    /** Credits stop accruing past this while no player is in range — no AFK-remote banking. */
    public static final double CREDIT_CAP = 8.0;

    /** The TRUE interval multiplier: every bite_time value counts, multiplicatively ({@code Π(1-v)}),
     *  floored at {@link #MIN_MULTIPLIER}. Empty/garbage → 1.0 (vanilla). */
    public static double trueMultiplier(List<Double> biteTimeValues) {
        double m = 1.0;
        if (biteTimeValues != null) {
            for (Double v : biteTimeValues) {
                if (v == null || v <= 0 || v >= 1) continue;   // 0/negative = junk; ≥1 would reverse time
                m *= (1.0 - v);
            }
        }
        return Math.max(MIN_MULTIPLIER, m);
    }

    /** One random-tick hit: bank {@code 1/(BASE_TICKS × multiplier)} credits, release whole ones. */
    public record CreditRoll(int spawns, double remaining) {}

    public static CreditRoll onRandomTick(double credits, double multiplier, boolean playerInRange) {
        double m = Math.max(MIN_MULTIPLIER, multiplier);
        double c = Math.min(CREDIT_CAP, Math.max(0, credits) + 1.0 / (BASE_TICKS * m));
        if (!playerInRange) return new CreditRoll(0, c);   // hold — spawns need a player, credits keep
        int spawns = Math.min(BURST_CAP, (int) c);
        return new CreditRoll(spawns, c - spawns);
    }

    /** The truthful lore multiplier ("⚡ spawn speed ×N.N") — throughput vs vanilla base. */
    public static double throughputFactor(List<Double> biteTimeValues) {
        return 1.0 / trueMultiplier(biteTimeValues);
    }
}
