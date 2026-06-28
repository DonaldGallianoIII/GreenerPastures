package com.greenerpastures.pasture.breeding;

/**
 * The bounded bonus shiny-reroll math — Greener Pastures' ONLY shiny contribution — lifted out of the
 * Minecraft-bound {@code CobbreedingBridge} so it's unit-tested headless. Pure: the RNG rolls are passed
 * in.
 *
 * <p>Per egg the boost is ×(1+procChance), and because each egg sees only its own pasture's augment the
 * aggregate across any number of pastures is also ×(1+procChance) — it is mathematically incapable of
 * exploding. {@link #effectiveOdds} replicates Cobbreeding's {@code calcShiny} from plain config values.
 */
public final class ShinyOdds {
    private ShinyOdds() {}

    /**
     * Effective shiny denominator for a pair: base ÷ always ÷ crystal (once per shiny parent) ÷ masuda
     * (when the parents have different OTs). A multiplier of {@code 0} = the server set "never shiny" for
     * that path → returns {@link Double#POSITIVE_INFINITY}. Pass {@code null} for an unconfigured multiplier.
     */
    public static double effectiveOdds(double baseRate, Float always, Float crystal, Float masuda,
                                       boolean parentAShiny, boolean parentBShiny, boolean differentOT) {
        double odds = baseRate;
        if (always != null) {
            if (always == 0f) return Double.POSITIVE_INFINITY;
            odds /= always;
        }
        if (crystal != null) {
            if (parentAShiny) { if (crystal == 0f) return Double.POSITIVE_INFINITY; odds /= crystal; }
            if (parentBShiny) { if (crystal == 0f) return Double.POSITIVE_INFINITY; odds /= crystal; }
        }
        if (masuda != null && differentOT) {
            if (masuda == 0f) return Double.POSITIVE_INFINITY;
            odds /= masuda;
        }
        return odds;
    }

    /** Shiny probability at the given effective odds: 1/odds, or a guaranteed 1.0 when odds &lt; 1. */
    public static double shinyProbability(double effectiveOdds) {
        return (effectiveOdds < 1.0) ? 1.0 : 1.0 / effectiveOdds;
    }

    /** Does the augment's proc fire? Probability {@code procChance}; never when procChance ≤ 0. */
    public static boolean procFires(double procChance, double rollProc) {
        return procChance > 0.0 && rollProc < procChance;
    }

    /** Given the proc fired, does the bonus reroll land shiny at the effective rate? */
    public static boolean shinyHits(double effectiveOdds, double rollShiny) {
        return rollShiny < shinyProbability(effectiveOdds);
    }

    /**
     * The whole decision: true iff OUR proc is what makes the egg shiny — never when it's already shiny
     * (no double-count) or procChance ≤ 0. {@code rollProc}/{@code rollShiny} are uniform [0,1), injected
     * so this is deterministic + testable. (The live bridge calls {@link #procFires} then {@link #shinyHits}
     * directly, to roll the shiny die only when the proc actually fires.)
     */
    public static boolean procMakesShiny(boolean alreadyShiny, double procChance, double effectiveOdds,
                                         double rollProc, double rollShiny) {
        if (alreadyShiny) return false;
        if (!procFires(procChance, rollProc)) return false;
        return shinyHits(effectiveOdds, rollShiny);
    }
}
