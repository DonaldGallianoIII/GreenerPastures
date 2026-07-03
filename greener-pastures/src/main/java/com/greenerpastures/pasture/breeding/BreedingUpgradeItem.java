package com.greenerpastures.pasture.breeding;

import com.greenerpastures.economy.AugmentFunction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A "Pasture Upgrade" item (copper→greener) — slotted into a pasture via the wand GUI's slot 0. It
 * scales the pasture's breeding pairs and unlocks functional-upgrade slots (see {@link BreedingTier}).
 * It also carries the player's compiled {@link Augments} as the {@code greenerpastures:augments} data
 * component (this is the "Kernel" in the locked lexicon). No right-click behaviour: installation is
 * handled entirely through the Pasture Wand GUI.
 */
public class BreedingUpgradeItem extends Item {
    /** The per-tier base drop-rate INCREMENT, in centipercent ({@code 50} = +0.50%/tier): a Kernel's base drop
     *  rate is {@code BASE_DROP_RATE × tier-level} (copper +0.50% … greener +3.00% — see
     *  {@link BreedingTier#baseDropRateCentipercent()}). Added to the Harvester's per-mon proc; a base augment,
     *  so a Drop Rate tether amplifies it. Set as the item's default {@code augments} component in
     *  {@code BetterPasture.registerItems}. Doubled from 25 (Deuce, 2026-07-03 — drop-rate QA pass).
     *  <b>NB:</b> a Kernel the Augmenter has touched carries an explicit component with the OLD value baked in —
     *  use a freshly crafted Kernel to see the new base. */
    public static final int BASE_DROP_RATE = 50;

    private final BreedingTier tier;

    public BreedingUpgradeItem(BreedingTier tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    public BreedingTier tier() {
        return tier;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.literal(tier.maxPairs + " pairs · " + tier.slots + " slots").formatted(Formatting.GRAY));
        Augments a = stack.get(GpComponents.AUGMENTS);
        if (a != null) {
            if (a.shinyProcPercent() > 0) {
                tooltip.add(Text.literal("✦ +" + a.shinyProcPercent() + "% shiny proc").formatted(Formatting.AQUA));
            }
            int dr = a.level(AugmentFunction.DROP_RATE);
            if (dr > 0) {
                tooltip.add(Text.literal("⛏ +" + String.format("%.2f", dr / 100.0) + "% drop rate").formatted(Formatting.GREEN));
            }
            int dy = a.level(AugmentFunction.DROP_YIELD);
            if (dy > 0) {
                tooltip.add(Text.literal("⛏ +" + dy + " drop yield").formatted(Formatting.GREEN));
            }
        }
    }
}
