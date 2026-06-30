package com.greenerpastures.economy;

import com.greenerpastures.buff.BuffConfig;
import com.greenerpastures.buff.BuffId;
import com.greenerpastures.buff.BuffSetting;
import com.greenerpastures.buff.BuffSystem;
import com.greenerpastures.buff.DaemonBuffs;
import com.greenerpastures.buff.DaemonLoadout;
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

import java.util.Map;

/**
 * {@code /gp daemon} — the no-UI <b>compile</b> path for a held Daemon's buff loadout (BUG-004). Mirrors
 * {@code /gp augment} (Kernels): set ANY supported buff to ANY level, toggle the Daemon on/off, then keep it
 * anywhere in your inventory. The eventual Compiler GUI is the pretty version of this; the command is the
 * functional core, so the whole redesign is testable before the UI exists.
 * <pre>
 *   /gp daemon list                 show the held Daemon's loadout + on/off + Data balance
 *   /gp daemon set &lt;buff&gt; &lt;level&gt;   compile one buff (level 0 removes it; capped to the server's per-buff max)
 *   /gp daemon clear                strip the whole loadout
 *   /gp daemon on | off             toggle it (same as right-click) — ON shows the enchant glint
 * </pre>
 */
public final class DaemonCommand {
    private DaemonCommand() {}

    private static final SuggestionProvider<ServerCommandSource> BUFFS = (ctx, b) -> {
        for (BuffId id : DaemonBuffs.supported()) b.suggest(id.id);
        return b.buildFuture();
    };

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("gp").then(CommandManager.literal("daemon")
                .executes(DaemonCommand::list)
                .then(CommandManager.literal("list").executes(DaemonCommand::list))
                .then(CommandManager.literal("clear").executes(DaemonCommand::clear))
                .then(CommandManager.literal("on").executes(c -> toggle(c, true)))
                .then(CommandManager.literal("off").executes(c -> toggle(c, false)))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("buff", StringArgumentType.word()).suggests(BUFFS)
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(0))
                            .executes(DaemonCommand::set)))))));
    }

    /** The held Daemon stack, or null (with feedback) if the player isn't holding one. */
    private static ItemStack daemon(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) {
            ctx.getSource().sendError(Text.literal("Run this as a player holding a Daemon."));
            return null;
        }
        ItemStack held = p.getMainHandStack();
        if (!held.isOf(DarkEconomy.DAEMON)) {
            ctx.getSource().sendError(Text.literal("Hold a Daemon in your main hand first."));
            return null;
        }
        return held;
    }

    private static DaemonLoadout loadout(ItemStack daemon) {
        DaemonLoadout l = daemon.get(DarkEconomy.DAEMON_LOADOUT);
        return l == null ? DaemonLoadout.NONE : l;
    }

    private static int set(CommandContext<ServerCommandSource> ctx) {
        ItemStack held = daemon(ctx);
        if (held == null) return 0;
        String buffId = StringArgumentType.getString(ctx, "buff");
        BuffId id = BuffId.byId(buffId);
        if (id == null || !DaemonBuffs.supported().contains(id)) {
            ctx.getSource().sendError(Text.literal("Unknown / undeliverable buff '" + buffId
                    + "' — tab-complete or /gp daemon list."));
            return 0;
        }
        int level = IntegerArgumentType.getInteger(ctx, "level");
        int cap = capOf(id);
        if (cap <= 0) {
            ctx.getSource().sendError(Text.literal(id.label + " is disabled on this server."));
            return 0;
        }
        if (level > cap) {
            ctx.getSource().sendError(Text.literal(id.label + " caps at +" + cap + " on this server."));
            return 0;
        }
        held.set(DarkEconomy.DAEMON_LOADOUT, loadout(held).withLevel(id, level));
        String msg = level <= 0
                ? "Removed " + id.label + " from the Daemon."
                : "Compiled " + id.label + " +" + level + " onto the Daemon (" + fmt(costOf(id, level)) + " Data/s).";
        ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ItemStack held = daemon(ctx);
        if (held == null) return 0;
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        DaemonLoadout l = loadout(held);
        boolean on = DaemonItem.isOn(held);
        long balance = DataStore.get(ctx.getSource().getServer()).balanceOf(p.getUuid());
        Map<BuffId, Integer> m = l.toLevels();
        StringBuilder sb = new StringBuilder("Daemon [" + (on ? "ON" : "OFF") + "] · Data: " + balance + "\nLoadout:");
        if (m.isEmpty()) {
            sb.append(" (empty — /gp daemon set <buff> <level>)");
        } else {
            double total = 0.0;
            for (Map.Entry<BuffId, Integer> e : m.entrySet()) {
                double c = costOf(e.getKey(), e.getValue());
                total += c;
                sb.append("\n  ").append(e.getKey().label).append(" +").append(e.getValue())
                  .append(" (").append(fmt(c)).append(" Data/s)");
            }
            sb.append("\n  = ").append(fmt(total)).append(" Data/s total while ON");
        }
        String out = sb.toString();
        ctx.getSource().sendFeedback(() -> Text.literal(out), false);
        return 1;
    }

    private static int clear(CommandContext<ServerCommandSource> ctx) {
        ItemStack held = daemon(ctx);
        if (held == null) return 0;
        held.set(DarkEconomy.DAEMON_LOADOUT, DaemonLoadout.NONE);
        ctx.getSource().sendFeedback(() -> Text.literal("Cleared the Daemon's loadout."), false);
        return 1;
    }

    private static int toggle(CommandContext<ServerCommandSource> ctx, boolean on) {
        ItemStack held = daemon(ctx);
        if (held == null) return 0;
        DaemonItem.setOn(held, on);
        ctx.getSource().sendFeedback(() -> Text.literal(on
                ? "Daemon ON — glint shown; installed buffs active while it's in your inventory."
                : "Daemon OFF — inert, no Data drain."), false);
        return 1;
    }

    /** This buff's effective tier cap on this server: {@code min(maxTier, +3)}, or 0 if disabled. */
    private static int capOf(BuffId id) {
        BuffSetting s = BuffSystem.config().settingOf(id);
        if (s == null || !s.enabled()) return 0;
        return Math.max(0, Math.min(BuffSetting.TIER_CEILING, s.maxTier()));
    }

    /** Data/sec a compiled buff would drain at {@code level} (clamped like the resolver), for the feedback lines. */
    private static double costOf(BuffId id, int level) {
        BuffConfig cfg = BuffSystem.config();
        BuffSetting s = cfg.settingOf(id);
        if (s == null) return 0.0;
        int tier = Math.max(0, Math.min(level, capOf(id)));
        return tier * Math.max(0.0, s.costPerSec());
    }

    private static String fmt(double d) {
        return String.format("%.2f", d);
    }
}
