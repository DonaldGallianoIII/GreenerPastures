package com.greenerpastures.notify;

import java.util.Map;
import java.util.Optional;

/**
 * Pure decision core for player notifications: given a recorded analytics event (its stamped row of flat fields)
 * and the {@link NotifyConfig}, decide whether to ping and with what message. MC-free so it unit-tests headless;
 * {@link Notifier} does the actual sending.
 *
 * <p>v1 fires the high-value ping for a shiny-breeding farm — a <b>shiny egg laid</b> (the {@code egg_laid} event
 * with {@code shiny=true}). It's written as an observer over the whole event stream, so more triggers
 * (Data-threshold crossings, ritual pulls ready) slot in as their signals land — see {@code FEATURES_V2.md}.
 */
public final class NotifyRules {
    private NotifyRules() {}

    /** A decided notification: the line to show + whether to also chime. */
    public record Notification(String message, boolean sound) {}

    /** Decide a notification for one recorded event row, or empty if nothing should fire. */
    public static Optional<Notification> evaluate(Map<String, Object> row, NotifyConfig cfg) {
        if (cfg == null || !cfg.enabled() || row == null) return Optional.empty();
        String type = str(row.get("type"));
        if (cfg.shiny() && "egg_laid".equals(type) && truthy(row.get("shiny"))) {
            String at = coords(row);
            String msg = truthy(row.get("proc_shiny"))
                    ? "✨ Shiny egg laid" + at + " — Daemon bonus proc!"
                    : "✨ Shiny egg laid" + at + "!";
            return Optional.of(new Notification(msg, cfg.sound()));
        }
        return Optional.empty();
    }

    /**
     * Decide a <b>Data milestone</b> ping: fires when a credit moves a player from one {@code step}-sized block of
     * Data into a higher one (e.g. step 1000 → a ping each time they pass 1000, 2000, …). Stateless — the caller
     * passes the balance before and after this credit, so dips-and-recrosses aside, no per-player state is kept.
     * Returns the highest milestone newly reached.
     */
    public static Optional<Notification> dataMilestone(long prevBalance, long newBalance, NotifyConfig cfg) {
        if (cfg == null || !cfg.enabled() || !cfg.dataMilestone()) return Optional.empty();
        long step = cfg.dataMilestoneStep();
        if (step <= 0 || newBalance <= prevBalance) return Optional.empty();
        long prevBlock = Math.max(0, prevBalance) / step;
        long newBlock = Math.max(0, newBalance) / step;
        if (newBlock <= prevBlock) return Optional.empty();
        long milestone = newBlock * step;
        return Optional.of(new Notification("💾 Data milestone: " + milestone + "!", cfg.sound()));
    }

    private static String coords(Map<String, Object> row) {
        Object x = row.get("x"), y = row.get("y"), z = row.get("z");
        if (x == null || y == null || z == null) return "";
        return " at " + asLong(x) + ", " + asLong(y) + ", " + asLong(z);
    }

    private static boolean truthy(Object v) {
        return v == Boolean.TRUE || "true".equalsIgnoreCase(String.valueOf(v));
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String asLong(Object v) {
        if (v instanceof Number n) return Long.toString(n.longValue());
        try {
            return Long.toString((long) Double.parseDouble(String.valueOf(v)));
        } catch (NumberFormatException e) {
            return String.valueOf(v);
        }
    }
}
