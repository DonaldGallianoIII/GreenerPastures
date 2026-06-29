package com.greenerpastures.goal;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * The {@code /gp goal} command — the player-facing way to set + inspect a breeding hunt (no GUI; RC-friendly). The
 * mod's first command. Usage:
 * <pre>
 *   /gp goal                                         show the current goal + progress
 *   /gp goal clear                                   clear it
 *   /gp goal set [species] [shiny] [minPerfectIvs] [count]
 * </pre>
 * {@code species} {@code "any"}/{@code "*"} = wildcard; trailing args are optional (a bare {@code set} = "any egg").
 * Goal-matching itself is the pure {@link BreedingGoal}; this just builds one + reads {@link GoalStore}.
 */
public final class GoalCommand {
    private GoalCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("gp").then(CommandManager.literal("goal")
                .executes(GoalCommand::show)
                .then(CommandManager.literal("clear").executes(GoalCommand::clear))
                .then(CommandManager.literal("set")
                    .executes(ctx -> set(ctx, null, null, 0, 1))
                    .then(CommandManager.argument("species", StringArgumentType.word())
                        .executes(ctx -> set(ctx, species(ctx), null, 0, 1))
                        .then(CommandManager.argument("shiny", BoolArgumentType.bool())
                            .executes(ctx -> set(ctx, species(ctx), BoolArgumentType.getBool(ctx, "shiny"), 0, 1))
                            .then(CommandManager.argument("minPerfectIvs", IntegerArgumentType.integer(0, 6))
                                .executes(ctx -> set(ctx, species(ctx), BoolArgumentType.getBool(ctx, "shiny"),
                                        IntegerArgumentType.getInteger(ctx, "minPerfectIvs"), 1))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                                    .executes(ctx -> set(ctx, species(ctx), BoolArgumentType.getBool(ctx, "shiny"),
                                            IntegerArgumentType.getInteger(ctx, "minPerfectIvs"),
                                            IntegerArgumentType.getInteger(ctx, "count")))))))))));
    }

    private static String species(CommandContext<ServerCommandSource> ctx) {
        String s = StringArgumentType.getString(ctx, "species");
        return (s == null || s.equalsIgnoreCase("any") || s.equals("*")) ? null : s;
    }

    private static int set(CommandContext<ServerCommandSource> ctx, String species, Boolean shiny, int minPerfectIvs, int count) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) {
            ctx.getSource().sendError(Text.literal("Only a player can set a breeding goal."));
            return 0;
        }
        BreedingGoal goal = new BreedingGoal(species, shiny, minPerfectIvs, 0, count);
        GoalStore.set(p.getUuid(), goal);
        ctx.getSource().sendFeedback(() -> Text.literal("🎯 Breeding goal set: " + goal.describe()), false);
        return 1;
    }

    private static int show(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) {
            ctx.getSource().sendError(Text.literal("Only a player has a breeding goal."));
            return 0;
        }
        UUID id = p.getUuid();
        BreedingGoal goal = GoalStore.goalOf(id);
        if (goal == null) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "No breeding goal set. Try: /gp goal set <species|any> <shiny> <minPerfectIvs> <count>"), false);
            return 1;
        }
        GoalProgress pr = GoalStore.progressOf(id);
        String msg = "🎯 " + goal.describe() + " — matched " + pr.matched() + "/" + goal.count()
                + " (" + pr.checked() + " eggs checked, best IV total " + pr.bestIvTotal() + ")"
                + (pr.reached(goal) ? " ✓ REACHED" : "");
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static int clear(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) {
            ctx.getSource().sendError(Text.literal("Only a player has a breeding goal."));
            return 0;
        }
        GoalStore.clear(p.getUuid());
        ctx.getSource().sendFeedback(() -> Text.literal("Breeding goal cleared."), false);
        return 1;
    }
}
