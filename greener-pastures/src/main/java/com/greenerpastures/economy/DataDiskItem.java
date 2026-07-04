package com.greenerpastures.economy;

import com.greenerpastures.core.GpLog;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * A <b>Data disk</b> — Data's physical form (NOTEBOOK_CONSOLE_SPEC §5c). The Notebook is the drive:
 * the console's Dashboard WRITES your balance onto a blank (choosing a denomination); RIGHT-CLICKING a
 * written disk READS it — its full value credits your balance and the media reverts to a blank (disks
 * are reusable floppies, never consumed). This makes Data craftable + tradeable: the GPU recipe eats a
 * kilobyte disk, which is how "a GPU costs Data" is paid at craft time.
 *
 * <p>Denominations are <b>baked constants</b> (binary ladder — it's a Data disk), same anti-p2w rule as
 * every other rate in the mod: no config. {@code value == 0} is blank media.
 */
public class DataDiskItem extends Item {
    /** Data credited when this disk is read; 0 = blank media (write target). */
    public final long value;

    public DataDiskItem(long value, Settings settings) {
        super(settings);
        this.value = value;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);
        if (value <= 0) {   // blank — point at the drive
            user.sendMessage(Text.literal("§7[Greener Pastures]§r Blank media — write Data onto it from the Notebook's Dashboard."), false);
            return TypedActionResult.pass(stack);
        }
        if (user instanceof ServerPlayerEntity player && player.getServer() != null) {
            DataStore.get(player.getServer()).credit(player.getUuid(), value);
            stack.decrement(1);
            player.getInventory().offerOrDrop(new ItemStack(GpItems.DISK_BLANK));   // the media survives the read
            player.sendMessage(Text.literal("§a[Greener Pastures]§r Read " + String.format("%,d", value)
                    + " Data from the disk (media kept as a blank)."), false);
            GpLog.i("disk", "read", "player", player.getUuid().toString(), "value", Long.toString(value));
        }
        return TypedActionResult.success(stack, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (value > 0) {
            tooltip.add(Text.literal("Holds " + String.format("%,d", value) + " Data").formatted(Formatting.AQUA));
            tooltip.add(Text.literal("Right-click to load it onto your balance").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.literal("Blank media").formatted(Formatting.GRAY));
            tooltip.add(Text.literal("Write Data onto it from the Notebook's Dashboard").formatted(Formatting.DARK_GRAY));
        }
    }
}
