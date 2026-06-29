package com.greenerpastures.pasture.breeding;

import com.greenerpastures.economy.AugmentFunction;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * {@code /gp augment} — the no-UI install path for breeding augments onto a held <b>Kernel</b>
 * ({@link BreedingUpgradeItem}). RC-friendly: set ANY function to ANY level/index, then slot the Kernel into a
 * pasture. It covers the selector augments (Nature/Ball — level = a {@link NatureCatalog}/{@link BallCatalog}
 * index), the binary ones (Hidden Ability / Egg Moves — level 1 = on), and the original magnitude augments. The
 * eventual Compiler UI is the pretty version of this; the command is the functional core.
 * <pre>
 *   /gp augment list                    show the held Kernel's augments
 *   /gp augment set <function> <level>  set one (level 0 removes it)
 *   /gp augment clear                   strip them all
 * </pre>
 */
public final class AugmentCommand {
    private AugmentCommand() {}

    private static final SuggestionProvider<ServerCommandSource> FUNCTIONS = (ctx, b) -> {
        for (AugmentFunction f : AugmentFunction.values()) b.suggest(f.id);
        return b.buildFuture();
    };

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("gp").then(CommandManager.literal("augment")
                .executes(AugmentCommand::list)
                .then(CommandManager.literal("list").executes(AugmentCommand::list))
                .then(CommandManager.literal("clear").executes(AugmentCommand::clear))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("function", StringArgumentType.word()).suggests(FUNCTIONS)
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(0))
                            .executes(AugmentCommand::set)))))));
    }

    /** The held Kernel stack, or null (with feedback) if the player isn't holding one. */
    private static ItemStack kernel(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) {
            ctx.getSource().sendError(Text.literal("Run this as a player holding a Kernel."));
            return null;
        }
        ItemStack held = p.getMainHandStack();
        if (!(held.getItem() instanceof BreedingUpgradeItem)) {
            ctx.getSource().sendError(Text.literal("Hold a Kernel (a breeding upgrade) in your main hand first."));
            return null;
        }
        return held;
    }

    private static Augments augments(ItemStack kernel) {
        Augments a = kernel.get(GpComponents.AUGMENTS);
        return a == null ? Augments.NONE : a;
    }

    private static int set(CommandContext<ServerCommandSource> ctx) {
        ItemStack held = kernel(ctx);
        if (held == null) return 0;
        String fnId = StringArgumentType.getString(ctx, "function");
        AugmentFunction fn = AugmentFunction.byId(fnId);
        if (fn == null) {
            ctx.getSource().sendError(Text.literal("Unknown augment '" + fnId + "' — tab-complete or /gp augment list."));
            return 0;
        }
        int level = IntegerArgumentType.getInteger(ctx, "level");
        held.set(GpComponents.AUGMENTS, augments(held).withLevel(fn, level));
        String msg = level <= 0
                ? "Removed " + fn.label + " from the Kernel."
                : "Set " + fn.label + " → " + level + selectorNote(fn, level) + " on the Kernel.";
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ItemStack held = kernel(ctx);
        if (held == null) return 0;
        Augments a = augments(held);
        StringBuilder sb = new StringBuilder("Kernel augments:");
        boolean any = false;
        for (AugmentFunction f : AugmentFunction.values()) {
            int lvl = a.level(f);
            if (lvl > 0) {
                any = true;
                sb.append("\n  ").append(f.label).append(" = ").append(lvl).append(selectorNote(f, lvl));
            }
        }
        if (!any) sb.append(" (none)");
        String out = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal(out), false);
        return 1;
    }

    private static int clear(CommandContext<ServerCommandSource> ctx) {
        ItemStack held = kernel(ctx);
        if (held == null) return 0;
        held.set(GpComponents.AUGMENTS, Augments.NONE);
        ctx.getSource().sendFeedback(() -> Text.literal("Cleared all augments on the Kernel."), false);
        return 1;
    }

    /** For a selector augment, append what the index maps to (e.g. {@code " (adamant)"}); empty otherwise. */
    private static String selectorNote(AugmentFunction f, int level) {
        if (level <= 0) return "";
        if (f == AugmentFunction.NATURE) {
            String n = NatureCatalog.byIndex(level);
            return n == null ? " (index out of range)" : " (" + n + ")";
        }
        if (f == AugmentFunction.BALL) {
            String b = BallCatalog.byIndex(level);
            return b == null ? " (index out of range)" : " (" + b + ")";
        }
        return "";
    }
}
