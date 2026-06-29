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
    /** Every Kernel ships with this base drop-rate perk, in centipercent ({@code 25} = +0.25%). Added to
     *  the Harvester's per-mon proc; a base augment, so a Drop Rate tether amplifies it. Set as the item's
     *  default {@code augments} component in {@code BetterPasture.registerItems}. */
    public static final int BASE_DROP_RATE = 25;

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
        }
    }
}
