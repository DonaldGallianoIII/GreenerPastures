package com.greenerpastures.notify;

import com.greenerpastures.economy.DataStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;

/**
 * The MC side of notifications - observes the analytics event stream (called from {@code Analytics.record} with
 * the stamped event row), runs the pure {@link NotifyRules}, and delivers the message + chime. Two delivery
 * shapes: a <b>broadcast</b> (the shiny ping, to {@code all} / {@code ops}) and an <b>owner-targeted</b> ping (the
 * Data milestone, only to the player whose Data crossed). Fully wrapped so a notification can never break event
 * recording or the server tick.
 */
public final class Notifier {
    private Notifier() {}

    /** Observe one recorded event (its stamped row) and fire any notifications {@link NotifyRules} decides on. */
    public static void observe(World world, Map<String, Object> row) {
        try {
            MinecraftServer server = world == null ? null : world.getServer();
            if (server == null || row == null) return;        // client world / no server → nothing to notify
            NotifyConfig cfg = NotifySystem.config();

            NotifyRules.evaluate(row, cfg).ifPresent(n -> broadcast(server, cfg, n));   // row-only triggers (shiny)

            if (cfg.enabled() && cfg.dataMilestone() && "egg_rendered".equals(String.valueOf(row.get("type")))) {
                maybeDataMilestone(server, cfg, row);          // owner-targeted Data milestone on a render credit
            }
        } catch (Throwable t) {
            // a notification must never break the event sink
        }
    }

    /** On a render credit, ping the owner if it pushed their Data into a new milestone block. */
    private static void maybeDataMilestone(MinecraftServer server, NotifyConfig cfg, Map<String, Object> row) {
        Object owner = row.get("player");
        if (owner == null || !(row.get("data") instanceof Number gainedNum)) return;
        long gained = gainedNum.longValue();
        if (gained <= 0) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(String.valueOf(owner));
        } catch (IllegalArgumentException e) {
            return;
        }
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        if (p == null) return;                                 // owner offline → nobody to ping
        long balance = DataStore.get(server).balanceOf(uuid);
        NotifyRules.dataMilestone(balance - gained, balance, cfg).ifPresent(n -> sendTo(p, cfg, n));
    }

    private static void broadcast(MinecraftServer server, NotifyConfig cfg, NotifyRules.Notification n) {
        boolean opsOnly = "ops".equalsIgnoreCase(cfg.target());
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (opsOnly && !server.getPlayerManager().isOperator(p.getGameProfile())) continue;
            sendTo(p, cfg, n);
        }
    }

    private static void sendTo(ServerPlayerEntity p, NotifyConfig cfg, NotifyRules.Notification n) {
        Text msg = Text.literal(n.message());
        String ch = cfg.channel();
        boolean actionbar = "actionbar".equalsIgnoreCase(ch) || "both".equalsIgnoreCase(ch);
        boolean chat = !actionbar || "both".equalsIgnoreCase(ch);   // default to chat unless explicitly actionbar-only
        if (chat) p.sendMessage(msg, false);
        if (actionbar) p.sendMessage(msg, true);
        if (n.sound()) {
            p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.7f, 1.5f);
        }
    }
}
