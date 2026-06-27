package com.greenerpastures.pasture.breeding.compiler;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A craftable augment "package" — slot it alongside a Kernel at a {@link CompilerBlock} and Compile
 * to install its effect onto the Kernel as data (consumed in the process). Carries its
 * {@link AugmentType}; the merge logic lives on the type so the server compile and the client preview
 * share one source of truth.
 */
public class AugmentItem extends Item {
    public final AugmentType type;

    public AugmentItem(AugmentType type, Settings settings) {
        super(settings);
        this.type = type;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType ttype) {
        super.appendTooltip(stack, context, tooltip, ttype);
        tooltip.add(Text.literal(type.pkg()).formatted(Formatting.AQUA));
        if (type == AugmentType.SHINY) {
            tooltip.add(Text.literal("✦ +" + type.value + "% shiny proc · bounded reroll").formatted(Formatting.GRAY));
        }
        tooltip.add(Text.literal("Compile onto a Kernel at a Compiler").formatted(Formatting.DARK_GRAY));
    }
}
