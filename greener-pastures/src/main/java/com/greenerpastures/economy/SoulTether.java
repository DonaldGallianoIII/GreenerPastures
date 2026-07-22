package com.greenerpastures.economy;

/**
 * A Soul Tether - the rented amplifier that slots into a Kernel slot and multiplies that Kernel's
 * matching mod, burning Data while the Daemon is fed. Per-function (Shiny / IV / Speed / Enrichment …);
 * tiers I–III set both magnitude and burn (and double as the buff +1/+2/+3). Minecraft-free +
 * unit-tested; magnitudes are calibration (tune via config) - the SHAPE is what's pinned. Inscription
 * economics live in {@link TetherEconomics}.
 *
 * <p>{@code tier 0} = a blank (uninscribed) tether: inert, no amplification, no burn.
 */
public record SoulTether(String function, TetherClass cls, int tier) {
    public static final int MAX_TIER = 3;

    public SoulTether {
        tier = Math.max(0, Math.min(MAX_TIER, tier));
    }

    public static SoulTether blank() { return new SoulTether("", TetherClass.QUALITY, 0); }

    public boolean isBlank() { return tier <= 0 || function == null || function.isBlank(); }

    /** Tether LEVELS this adds to its Kernel's matching mod (= tier; 0 when blank). Flat + additive +
     *  stacking, each level worth the function's {@code tetherStep} - deliberately able to push PAST the
     *  augment's rollable max (Deuce, 2026-07-21: that's what the rent buys). */
    public int levelsAdded() { return isBlank() ? 0 : tier; }

    /** Data burned per breeding cycle while powered - quality is expensive (sets the grid), throughput is
     *  cheap (pays for itself). 0 when blank/inert (a starved Daemon also makes this irrelevant). */
    public long burnPerCycle() {
        if (isBlank()) return 0L;
        long perTier = (cls == TetherClass.QUALITY) ? 8L : 3L;
        return perTier * tier;
    }
}
