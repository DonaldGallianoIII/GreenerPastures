package com.greenerpastures.core;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The <b>Field Guide</b> — the ship-with awareness book (#18, "A Data Science Mod"). Right-click opens the
 * Notebook console straight onto its <b>Guide</b> tab (the in-console handbook: the loop, Kernels, the Daemon,
 * BioBank, rituals). Given once on first join ({@link FirstJoinGift}) so a new player's first steps are
 * self-explaining; craftable/held after that like any item.
 *
 * <p>Client-side the open is delegated through {@link #OPENER} (set by the client entrypoint — the common jar
 * must not touch client classes). On a dedicated server the item does nothing server-side; the client handles it.
 */
public class FieldGuideItem extends Item {
    /** Client hook: open the console on the Guide tab. Null on a dedicated server. */
    public static Runnable OPENER = null;

    public FieldGuideItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient && OPENER != null) OPENER.run();
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("A Data Science Mod — the handbook").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click to open the Guide").formatted(Formatting.DARK_GRAY));
    }
}
