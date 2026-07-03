package com.greenerpastures.core;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code /gp perf} — the player-facing face of {@link GpProf} ("A Data Science Mod" profiles itself).
 * Op-gated (level 2), works in singleplayer and on servers:
 * <pre>
 *   /gp perf           top sections table in chat (count · avg · max · total ms)
 *   /gp perf flame     write gp-logs/perf-flame.html (self-contained flame graph) + say where
 *   /gp perf reset     start a fresh timing window
 * </pre>
 */
public final class PerfCommand {
    private PerfCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("gp").then(CommandManager.literal("perf")
                .requires(src -> src.hasPermissionLevel(2))
                .executes(PerfCommand::table)
                .then(CommandManager.literal("flame").executes(PerfCommand::flame))
                .then(CommandManager.literal("reset").executes(PerfCommand::reset)))));
    }

    private static int table(CommandContext<ServerCommandSource> ctx) {
        String head = "§a[Greener Pastures]§r perf — window " + (GpProf.sinceMs() / 1000) + "s";
        ctx.getSource().sendFeedback(() -> Text.literal(head), false);
        for (String line : GpProf.table(12).split("\n")) {
            ctx.getSource().sendFeedback(() -> Text.literal("§7" + line), false);
        }
        return 1;
    }

    private static int flame(CommandContext<ServerCommandSource> ctx) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("gp-logs");
            Files.createDirectories(dir);
            Path out = dir.resolve("perf-flame.html");
            Files.writeString(out, GpProf.flameHtml());
            GpLog.i("perf", "flame_written", "path", out.toString(), "windowMs", GpProf.sinceMs());
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "§a[Greener Pastures]§r flame graph written → §b" + out + "§r (open it in any browser)"), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("[Greener Pastures] could not write the flame graph: " + e));
            return 0;
        }
    }

    private static int reset(CommandContext<ServerCommandSource> ctx) {
        GpProf.reset();
        ctx.getSource().sendFeedback(() -> Text.literal("§a[Greener Pastures]§r perf window reset — timings start fresh now."), false);
        return 1;
    }
}
