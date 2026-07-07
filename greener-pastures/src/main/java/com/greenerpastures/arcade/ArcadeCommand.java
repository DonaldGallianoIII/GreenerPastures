package com.greenerpastures.arcade;

import com.greenerpastures.core.GpLog;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * {@code /gp coins} - a QA/admin handle on the Game Corner ledger (the Coins twin of {@code /gp data}).
 * Credits touch {@code coins} only, never {@code earnedToday} - test money can't trip the daily fence,
 * and outside QA mode this class is never registered, so Coins stay a closed loop in public builds.
 * <b>Op-gated (level 2)</b>. Usage:
 * <pre>
 *   /gp coins              show your balance
 *   /gp coins add &lt;n&gt;      credit n Coins (n &lt; 0 subtracts, floored at 0)
 * </pre>
 */
public final class ArcadeCommand {
    private ArcadeCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("gp").then(CommandManager.literal("coins")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(ArcadeCommand::show)
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("amount", LongArgumentType.longArg())
                        .executes(ctx -> add(ctx, LongArgumentType.getLong(ctx, "amount"))))))));
    }

    private static String today() {
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
    }

    private static int show(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) { ctx.getSource().sendError(Text.literal("Only a player has a Coin purse.")); return 0; }
        long bal = ArcadeStore.get(ctx.getSource().getServer()).of(p.getUuid(), today()).coins;
        ctx.getSource().sendFeedback(() -> Text.literal("🪙 Game Corner Coins: " + bal), false);
        return 1;
    }

    private static int add(CommandContext<ServerCommandSource> ctx, long amount) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) { ctx.getSource().sendError(Text.literal("Only a player has a Coin purse.")); return 0; }
        ArcadeStore store = ArcadeStore.get(ctx.getSource().getServer());
        ArcadeStore.Ledger lgr = store.of(p.getUuid(), today());
        long delta = Math.max(-lgr.coins, amount);   // floor the purse at 0
        lgr.coins += delta;                          // direct: refund() clamps negatives away
        store.markDirty();
        long bal = lgr.coins;
        GpLog.i("arcade", "coins_admin", "player", p.getUuid().toString(), "delta", Long.toString(delta), "coins", Long.toString(bal));
        ctx.getSource().sendFeedback(() -> Text.literal("🪙 " + (delta >= 0 ? "+" : "") + delta + " Coins → " + bal), false);
        return 1;
    }
}
