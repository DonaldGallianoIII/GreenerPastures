package com.greenerpastures.economy;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * <b>Snack Repel</b> (Snack Overdrive pt.1 - Deuce's sprite, 2026-07-04): a spray can crafted into an Ultra
 * Compressed Snack. Each can INVERTS one typed seasoning group - the type those berries would have attracted
 * is instead repelled (spawn weight ÷ the same summed magnitude the attract would have multiplied by). The
 * flip happens at craft time ({@code RepelFold}); this item is inert on its own.
 */
public class SnackRepelItem extends Item {

    public SnackRepelItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        if (stack.contains(com.greenerpastures.pasture.breeding.GpComponents.REPEL_TYPES)) return;   // charged - the ÷N lore says it all
        tooltip.add(Text.literal("empty - charge it with the berries of the type").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("you DON'T want (can + 1–6 berries in a grid),").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("then craft the charged can into an Ultra Snack").formatted(Formatting.DARK_GRAY));
    }
}
