package com.greenerpastures.economy;

import com.greenerpastures.core.GpLog;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The Daemon — the player's handle to their <b>Data</b> account (the dark economy's currency).
 *
 * <p>v1 / min-slice: right-click in the air to read your live Data balance from {@link DataStore}.
 * Data is player-bound and lives server-side, so the balance survives losing the item. The global
 * "root" buffs, Soul-Tether crafting, and the Notebook UI come later — this just makes the
 * feed → Data → see-it loop visible.
 */
public class DaemonItem extends Item {
    public DaemonItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack held = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp && sp.getServer() != null) {
            long balance = DataStore.get(sp.getServer()).balanceOf(sp.getUuid());
            sp.sendMessage(Text.literal("§5⌬ Daemon§r — Data: §d" + balance), false);
            GpLog.i("daemon", "balance_check", "player", sp.getUuid().toString(), "data", balance);
        }
        return TypedActionResult.success(held, world.isClient);
    }
}
