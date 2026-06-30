package com.greenerpastures.economy;

import com.greenerpastures.core.GpLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * The {@code /gp data} command — a QA/admin handle on the player's <b>Data</b> balance (the dark-economy
 * currency). Earning Data normally requires a Renderer culling eggs; but the Daemon "root" buffs only run while
 * the Daemon is <i>fed</i> (balance &gt; 0), so testing them straight from a fresh world is impossible without a
 * grant. This mints/sets a balance directly. <b>Op-gated (level 2)</b> since it creates currency. Usage:
 * <pre>
 *   /gp data            show your Data balance
 *   /gp data set &lt;n&gt;    set your balance to exactly n
 *   /gp data add &lt;n&gt;    add n to your balance
 * </pre>
 * Writes through {@link DataStore} (the same persistent store a Renderer credits), so the balance survives relog.
 */
public final class DataCommand {
    private DataCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("gp").then(CommandManager.literal("data")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(DataCommand::show)
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(0))
                        .executes(ctx -> setTo(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))));
    }

    private static int show(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) { ctx.getSource().sendError(Text.literal("Only a player has a Data balance.")); return 0; }
        long bal = DataStore.get(ctx.getSource().getServer()).balanceOf(p.getUuid());
        ctx.getSource().sendFeedback(() -> Text.literal("💾 Data balance: " + bal), false);
        return 1;
    }

    private static int setTo(CommandContext<ServerCommandSource> ctx, int target) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) { ctx.getSource().sendError(Text.literal("Only a player has a Data balance.")); return 0; }
        DataStore store = DataStore.get(ctx.getSource().getServer());
        UUID id = p.getUuid();
        long cur = store.balanceOf(id);
        if (target > cur) store.credit(id, target - cur);
        else if (target < cur) store.tryDebit(id, cur - target);
        GpLog.i("data", "debug_set", "player", p.getName().getString(), "from", cur, "to", target);
        ctx.getSource().sendFeedback(() -> Text.literal("💾 Data balance set to " + target), false);
        return 1;
    }

    private static int add(CommandContext<ServerCommandSource> ctx, int amount) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) { ctx.getSource().sendError(Text.literal("Only a player has a Data balance.")); return 0; }
        DataStore store = DataStore.get(ctx.getSource().getServer());
        store.credit(p.getUuid(), amount);
        long bal = store.balanceOf(p.getUuid());
        GpLog.i("data", "debug_add", "player", p.getName().getString(), "added", amount, "balance", bal);
        ctx.getSource().sendFeedback(() -> Text.literal("💾 +" + amount + " Data → " + bal), false);
        return 1;
    }
}
