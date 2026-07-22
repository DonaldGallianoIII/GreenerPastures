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

    /** Multiplier applied to the Kernel's matching CONTINUOUS mod: ×1.5 / ×2.0 / ×2.5 by tier (1.0 when
     *  blank). Bumped from +10/20/30% (Deuce QA 2026-07-21): a rented amplifier must be FELT. Discrete
     *  (leveled) mods don't use this - they get flat +tier levels in {@code EffectiveAugments}. */
    public double amplification() { return isBlank() ? 1.0 : 1.0 + 0.50 * tier; }

    /** Data burned per breeding cycle while powered - quality is expensive (sets the grid), throughput is
     *  cheap (pays for itself). 0 when blank/inert (a starved Daemon also makes this irrelevant). */
    public long burnPerCycle() {
        if (isBlank()) return 0L;
        long perTier = (cls == TetherClass.QUALITY) ? 8L : 3L;
        return perTier * tier;
    }
}
