package com.greenerpastures.economy;

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
 * The <b>Illicit Data Disk</b> (Deuce 2026-07-04: "like a corruption orb from Path of Exile") - NOT a
 * currency denomination: it Vaal-rolls a Kernel through the Notebook's Augmenter (one of
 * blessed/wild/nothing/bricked, then corrupted forever). Sourced from the hidden Black Market ritual and,
 * whisper-rare, from the Renderer's void stream. Right-click just whispers the hint; the deed happens at
 * the Augmenter so the reveal lands in the console.
 */
public class IllicitDiskItem extends Item {
    public IllicitDiskItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            user.sendMessage(Text.literal("\u00a75\u26e7\u00a7r It hums against your palm. \u00a78Bring it and a Kernel to the Notebook\'s Augmenter.\u00a7r"), false);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("it shouldn\'t exist. someone will pay for it.").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC));
        tooltip.add(Text.literal("Corrupts a Kernel at the Augmenter \u2014 outcomes vary. Permanently.").formatted(Formatting.DARK_GRAY));
    }
}
