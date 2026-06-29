package com.greenerpastures.notify;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Map;

/**
 * The MC side of notifications — observes the analytics event stream (called from {@code Analytics.record} with
 * the stamped event row), runs the pure {@link NotifyRules}, and delivers the message + chime to the configured
 * targets ({@code all} players or {@code ops} only; chat / actionbar / both). Fully wrapped so a notification can
 * never break event recording or the server tick.
 */
public final class Notifier {
    private Notifier() {}

    /** Observe one recorded event (its stamped row) and fire a notification if {@link NotifyRules} says so. */
    public static void observe(World world, Map<String, Object> row) {
        try {
            MinecraftServer server = world == null ? null : world.getServer();
            if (server == null) return;                       // client world / no server → nothing to notify
            NotifyConfig cfg = NotifySystem.config();
            NotifyRules.evaluate(row, cfg).ifPresent(n -> send(server, cfg, n));
        } catch (Throwable t) {
            // a notification must never break the event sink
        }
    }

    private static void send(MinecraftServer server, NotifyConfig cfg, NotifyRules.Notification n) {
        Text msg = Text.literal(n.message());
        boolean opsOnly = "ops".equalsIgnoreCase(cfg.target());
        String ch = cfg.channel();
        boolean actionbar = "actionbar".equalsIgnoreCase(ch) || "both".equalsIgnoreCase(ch);
        boolean chat = !actionbar || "both".equalsIgnoreCase(ch);   // default to chat unless explicitly actionbar-only
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (opsOnly && !server.getPlayerManager().isOperator(p.getGameProfile())) continue;
            if (chat) p.sendMessage(msg, false);
            if (actionbar) p.sendMessage(msg, true);
            if (n.sound()) {
                p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.7f, 1.5f);
            }
        }
    }
}
