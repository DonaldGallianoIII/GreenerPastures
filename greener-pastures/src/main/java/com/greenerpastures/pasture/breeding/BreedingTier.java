package com.greenerpastures.pasture.breeding;

import java.util.Locale;

/**
 * The core "Pasture Upgrade" tiers - a slot-expander (Sophisticated-style) that also scales breeding.
 * Slotted into a pasture's base upgrade slot, it sets BOTH how many compatible pairs breed in parallel
 * AND how many functional-upgrade slots (Shiny/Speed/Yield/…) unlock. Vanilla-material progression;
 * {@link #GREENER} is the mod's own top tier.
 *
 * <p><b>Hard ceiling: 8 pairs.</b> A Cobblemon pasture tethers at most {@code defaultPasturedPokemonLimit}
 * = 16 Pokémon (its config default; we never override it), so 8 pairs saturates a full pasture. No tier
 * may exceed 8 - everything is built around the 16-mon pasture and does NOT grow it.
 */
public enum BreedingTier {
    COPPER(2, 2),
    IRON(3, 3),
    GOLD(4, 4),
    DIAMOND(5, 5),
    NETHERITE(6, 6),
    GREENER(8, 8); // top tier = fills the pasture: 8 pairs × 2 = 16 tethered (the cap)

    /** Max compatible pairs bred in parallel at this tier. */
    public final int maxPairs;
    /** Functional-upgrade slots this Pasture Upgrade unlocks (beyond the base slot it occupies). */
    public final int slots;

    BreedingTier(int maxPairs, int slots) {
        this.maxPairs = maxPairs;
        this.slots = slots;
    }

    /** Lowercase id, used for item ids / lookups (e.g. "copper"). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Per-tier base drop-rate perk, in centipercent - SCALES with the tier: copper +0.50% … netherite +2.50%,
     *  and GREENER breaks the line at ×2 (+6.00%, Deuce 2026-07-04: "double drops from where it currently is")
     *  - the top kernel is a jump, not a step, matching its 8-netherite/emerald-block recipe. The unit is
     *  {@link BreedingUpgradeItem#BASE_DROP_RATE}, a compile-time constant that inlines, so this enum stays
     *  MC-free and headless-testable. */
    public int baseDropRateCentipercent() {
        if (this == GREENER) return 500;   // +5.00% - the jump, tuned down 1% from ×2 (Deuce 2026-07-04)
        return BreedingUpgradeItem.BASE_DROP_RATE * (ordinal() + 1);
    }

    /** Per-tier EGG-SPEED perk (Deuce 2026-07-04: "base kernels carry a faster egg laying speed… like the
     *  drops"; jump halved same day): the breeding interval divides by this. +10% per tier straight up the
     *  line - copper ×1.1 … netherite ×1.5, greener ×1.6. STACKS with the Speed augment (×1.5/×2/×3); the
     *  breeder's ~2.5-min floor still backstops the server. MC-free, headless-tested. */
    public double baseSpeedFactor() {
        return 1.0 + 0.10 * (ordinal() + 1);
    }
}
