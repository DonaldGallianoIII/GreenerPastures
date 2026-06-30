package com.greenerpastures.pasture.breeding;

import java.util.Locale;

/**
 * The core "Pasture Upgrade" tiers — a slot-expander (Sophisticated-style) that also scales breeding.
 * Slotted into a pasture's base upgrade slot, it sets BOTH how many compatible pairs breed in parallel
 * AND how many functional-upgrade slots (Shiny/Speed/Yield/…) unlock. Vanilla-material progression;
 * {@link #GREENER} is the mod's own top tier.
 *
 * <p><b>Hard ceiling: 8 pairs.</b> A Cobblemon pasture tethers at most {@code defaultPasturedPokemonLimit}
 * = 16 Pokémon (its config default; we never override it), so 8 pairs saturates a full pasture. No tier
 * may exceed 8 — everything is built around the 16-mon pasture and does NOT grow it.
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

    /** Per-tier base drop-rate perk, in centipercent — SCALES with the tier: copper +0.25%, iron +0.50%, gold
     *  +0.75%, diamond +1.00%, netherite +1.25%, greener +1.50%. (BUG-001 fix: every tier previously shared one
     *  flat +0.25%.) The unit is {@link BreedingUpgradeItem#BASE_DROP_RATE}, a compile-time constant that inlines,
     *  so this enum stays MC-free and headless-testable. */
    public int baseDropRateCentipercent() {
        return BreedingUpgradeItem.BASE_DROP_RATE * (ordinal() + 1);
    }
}
