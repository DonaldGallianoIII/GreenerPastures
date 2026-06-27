package com.greenerpastures.pasture.breeding;

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
        if (a != null && a.shinyProcPercent() > 0) {
            tooltip.add(Text.literal("✦ +" + a.shinyProcPercent() + "% shiny proc").formatted(Formatting.AQUA));
        }
    }
}
