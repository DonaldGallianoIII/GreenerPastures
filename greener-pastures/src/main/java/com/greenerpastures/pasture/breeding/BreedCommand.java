package com.greenerpastures.pasture.breeding;

import com.greenerpastures.core.GpLog;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * {@code /gp breed} - a QA/admin handle on the breeding cadence so a tester doesn't wait minutes per egg. Sets
 * {@link MultiPairBreeder#testIntervalTicks}, an in-memory override that fixes every pasture's breed interval,
 * bypassing both Cobbreeding's configured time and the 2.5-min speed floor. <b>Op-gated (level 2)</b>; resets on
 * restart (so a fast test rate can never ship in a world save). Usage:
 * <pre>
 *   /gp breed                      show the current cadence
 *   /gp breed interval &lt;seconds&gt;    breed every N seconds per pair (e.g. 15)
 *   /gp breed default              clear the override (back to normal)
 * </pre>
 */
public final class BreedCommand {
    private BreedCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("gp").then(CommandManager.literal("breed")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(BreedCommand::show)
                .then(CommandManager.literal("interval")
                    .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1))
                        .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(CommandManager.literal("default").executes(BreedCommand::clear)))));
    }

    private static int show(CommandContext<ServerCommandSource> ctx) {
        long t = MultiPairBreeder.testIntervalTicks;
        String msg = t > 0
                ? "🥚 breeding override: every " + (t / 20.0) + "s per pair (" + t + " ticks, floor bypassed)"
                : "🥚 breeding: normal cadence - Cobbreeding's configured time, floored at 2.5 min (3000 ticks). "
                        + "Speed it up for testing: /gp breed interval <seconds>";
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static int set(CommandContext<ServerCommandSource> ctx, int seconds) {
        long ticks = seconds * 20L;
        MultiPairBreeder.testIntervalTicks = ticks;
        MultiPairBreeder.restampSchedules(ctx.getSource().getServer());
        GpLog.i("breeder", "test_interval", "seconds", seconds, "ticks", ticks);
        ctx.getSource().sendFeedback(() -> Text.literal("🥚 breeding override ON - every " + seconds
                + "s per pair (" + ticks + " ticks; floor + Speed augment bypassed). /gp breed default to restore."), false);
        return 1;
    }

    private static int clear(CommandContext<ServerCommandSource> ctx) {
        MultiPairBreeder.testIntervalTicks = 0L;
        MultiPairBreeder.restampSchedules(ctx.getSource().getServer());
        GpLog.i("breeder", "test_interval", "seconds", 0, "ticks", 0L);
        ctx.getSource().sendFeedback(() -> Text.literal("🥚 breeding override cleared - back to normal cadence."), false);
        return 1;
    }
}
