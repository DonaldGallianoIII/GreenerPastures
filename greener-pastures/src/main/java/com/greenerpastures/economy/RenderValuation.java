package com.greenerpastures.economy;

/**
 * How much Data a Renderer produces from culled eggs — the income side of the closed loop. Pure.
 *
 * <p>{@code baseValuePerEgg} is the ⭐ <b>balance constant</b>: tuned BELOW a trophy pasture's tether
 * burn so amplification requires dedicated fuel pastures (the whole economy's balance). An
 * <b>Enrichment</b> tether (the fuel-role amplifier) multiplies it.
 */
public final class RenderValuation {
    private RenderValuation() {}

    /** Data from rendering {@code eggsRendered} eggs at {@code baseValuePerEgg}, scaled by an
     *  Enrichment multiplier (≥1; values below 1 are floored to 1 — never negative income). */
    public static long dataFor(int eggsRendered, long baseValuePerEgg, double enrichmentMultiplier) {
        if (eggsRendered <= 0 || baseValuePerEgg <= 0) return 0L;
        // floor a NaN/sub-1 multiplier to 1× (NaN >= 1.0 is false) so income is never negative or garbage.
        double mult = (enrichmentMultiplier >= 1.0) ? enrichmentMultiplier : 1.0;
        // double→long narrowing already saturates to Long.MAX_VALUE for huge products (JLS 5.1.3) and
        // yields 0 for NaN — so this can't wrap negative; precision is lossy only above 2^53, acceptably.
        return (long) Math.floor((double) eggsRendered * (double) baseValuePerEgg * mult);
    }
}
