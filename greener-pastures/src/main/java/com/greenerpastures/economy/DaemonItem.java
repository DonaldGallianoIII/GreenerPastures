package com.greenerpastures.economy;

import com.greenerpastures.buff.BuffSetting;
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
 * The Daemon — the player's handle to their <b>Data</b> account (the dark economy's currency) and the seat of
 * its level (Mk I/II/III), which is the ceiling on the "root" buffs it grants while held + fed (see
 * {@code com.greenerpastures.buff}). Data is player-bound and lives server-side, so the balance survives losing
 * the item; the <i>level</i> rides on the stack as the {@code greenerpastures:daemon_level} component.
 *
 * <p>Right-click in the air reads your live balance + the Daemon's Mk tier. <b>Sneak</b>-right-click <b>in
 * creative</b> cycles the level Mk I→II→III→I — a no-UI testing affordance so all three buff tiers are QA-able
 * now; in survival the level will come from an upgrade recipe (publish-phase) that writes the same component.
 */
public class DaemonItem extends Item {
    public DaemonItem(Settings settings) {
        super(settings);
    }

    /** The Daemon's Mk tier (1–3); an un-upgraded Daemon is Mk I. Clamped to the buff ceiling. */
    public static int levelOf(ItemStack stack) {
        int v = stack.getOrDefault(DarkEconomy.DAEMON_LEVEL, 1);
        return Math.max(1, Math.min(BuffSetting.TIER_CEILING, v));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack held = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp && sp.getServer() != null) {
            if (sp.isSneaking() && sp.isCreative()) {
                int next = levelOf(held) >= BuffSetting.TIER_CEILING ? 1 : levelOf(held) + 1;
                held.set(DarkEconomy.DAEMON_LEVEL, next);
                sp.sendMessage(Text.literal("§5⌬ Daemon§r — level set to §dMk " + roman(next)), true);
                GpLog.i("daemon", "level_set", "player", sp.getUuid().toString(), "level", next);
            } else {
                long balance = DataStore.get(sp.getServer()).balanceOf(sp.getUuid());
                sp.sendMessage(Text.literal("§5⌬ Daemon Mk " + roman(levelOf(held))
                        + "§r — Data: §d" + balance), false);
                GpLog.i("daemon", "balance_check", "player", sp.getUuid().toString(),
                        "data", balance, "level", levelOf(held));
            }
        }
        return TypedActionResult.success(held, world.isClient);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }
}
