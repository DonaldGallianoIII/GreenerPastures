package com.greenerpastures.notebook;

import com.greenerpastures.core.GpLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * {@code /gp harvest} - a QA/admin handle on the Notebook-harvest cadence so a tester can watch drop rates live
 * instead of waiting a minute per sweep. Sets {@link PastureHarvest#testIntervalTicks}; <b>op-gated (level 2)</b>;
 * reset on server start (a fast test rate can never ship in a world save). Mirrors {@code /gp breed}. Usage:
 * <pre>
 *   /gp harvest                       show the current cadence
 *   /gp harvest interval &lt;seconds&gt;    sweep every N seconds (procs are per sweep → faster = more rolls/min)
 *   /gp harvest default               clear the override (back to the 1-min clock)
 * </pre>
 */
public final class HarvestCommand {
    private HarvestCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("gp").then(CommandManager.literal("harvest")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(HarvestCommand::show)
                .then(CommandManager.literal("interval")
                    .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1))
                        .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(CommandManager.literal("default").executes(HarvestCommand::clear)))));
    }

    private static int show(CommandContext<ServerCommandSource> ctx) {
        long t = PastureHarvest.testIntervalTicks;
        String msg = t > 0
                ? "⛏ harvest override: sweep every " + (t / 20.0) + "s (" + t + " ticks)"
                : "⛏ harvest: normal cadence - one sweep per IRL minute (1200 ticks). "
                        + "Speed it up for testing: /gp harvest interval <seconds>";
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static int set(CommandContext<ServerCommandSource> ctx, int seconds) {
        long ticks = seconds * 20L;
        PastureHarvest.testIntervalTicks = ticks;
        GpLog.i("notebook_harvest", "test_interval", "seconds", seconds, "ticks", ticks);
        ctx.getSource().sendFeedback(() -> Text.literal("⛏ harvest override ON - sweep every " + seconds
                + "s (" + ticks + " ticks). Procs are per sweep, so rates scale with speed. /gp harvest default to restore."), false);
        return 1;
    }

    private static int clear(CommandContext<ServerCommandSource> ctx) {
        PastureHarvest.testIntervalTicks = 0L;
        GpLog.i("notebook_harvest", "test_interval", "seconds", 0, "ticks", 0L);
        ctx.getSource().sendFeedback(() -> Text.literal("⛏ harvest override cleared - back to one sweep per minute."), false);
        return 1;
    }
}
