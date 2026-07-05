package com.greenerpastures.specimen;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.GpItems;
import com.greenerpastures.pasture.breeding.GpComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * <b>Specimen Disk</b> (mon compression v1 - Deuce, 2026-07-05): a Pokémon as data, on media. Blank disks
 * are charged from the Notebook's Specimens tab (party mon → lossless {@code Pokemon.saveToNBT} payload on
 * {@code gp:specimen}); right-clicking a written disk releases the mon back (party first, PC overflow) and
 * hands back a blank - the same media-survives-the-read contract as data disks. Solves Cobblemon box
 * clutter: your farm's keepers live on a shelf, not in the PC. Deferred art: placeholder green disk until
 * Deuce's sprite.
 */
public class SpecimenDiskItem extends Item {

    public SpecimenDiskItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);
        NbtCompound nbt = stack.get(GpComponents.SPECIMEN);
        if (nbt == null) {
            user.sendMessage(Text.literal("§7[Greener Pastures]§r Blank media - archive a party mon from the Notebook's Specimens tab."), false);
            return TypedActionResult.pass(stack);
        }
        if (user instanceof ServerPlayerEntity player && player.getServer() != null) {
            try {
                Pokemon mon = new Pokemon().loadFromNBT(world.getRegistryManager(), nbt);
                boolean accepted = PlayerExtensionsKt.party(player).add(mon);
                if (!accepted) accepted = PlayerExtensionsKt.pc(player).add(mon);
                String err = SpecimenRules.releaseRefusal(true, accepted);
                if (err != null) {
                    player.sendMessage(Text.literal("§c[Greener Pastures]§r " + err), false);
                    return TypedActionResult.pass(stack);
                }
                SpecimenSummary sum = stack.get(GpComponents.SPECIMEN_SUMMARY);
                stack.decrement(1);
                player.getInventory().offerOrDrop(new ItemStack(GpItems.SPECIMEN_DISK));   // media survives the read
                player.sendMessage(Text.literal("§a[Greener Pastures]§r Released "
                        + (sum == null ? "the specimen" : cap(sum.species())) + " (media kept as a blank)."), false);
                GpLog.i("specimen", "release", "player", player.getUuid().toString(),
                        "species", sum == null ? "?" : sum.species());
            } catch (Throwable t) {
                // NEVER destroy the disk on a failed load - the mon data stays intact on the item
                player.sendMessage(Text.literal("§c[Greener Pastures]§r The specimen would not decompress - disk kept."), false);
                GpLog.w("specimen", "release_fail", "err", String.valueOf(t));
            }
        }
        return TypedActionResult.success(stack, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        SpecimenSummary s = stack.get(GpComponents.SPECIMEN_SUMMARY);
        if (s == null) {
            tooltip.add(Text.literal("blank - a Pokémon, as data, on media").formatted(Formatting.GRAY));
            tooltip.add(Text.literal("archive from the Notebook's Specimens tab").formatted(Formatting.DARK_GRAY));
            return;
        }
        tooltip.add(Text.literal((s.shiny() ? "✨ " : "") + cap(s.species()) + " · Lv." + s.level()
                + (s.gender().isEmpty() ? "" : " · " + s.gender().toLowerCase()))
                .formatted(s.shiny() ? Formatting.GOLD : Formatting.AQUA));
        tooltip.add(Text.literal("right-click to release (party → PC)").formatted(Formatting.DARK_GRAY));
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "?" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
