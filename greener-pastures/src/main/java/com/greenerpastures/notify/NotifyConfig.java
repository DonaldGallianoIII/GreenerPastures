package com.greenerpastures.notify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.greenerpastures.core.GpLog;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Admin/player config for notifications: a master toggle, which triggers fire, and how they're delivered. Same
 * fail-safe lazy-Gson pattern as {@code BuffConfig} (missing → write defaults, corrupt → fall back, never crash;
 * Gson via a holder so the pure cores stay test-runnable on the headless JVM).
 *
 * @param enabled        master on/off
 * @param shiny          ping when a shiny egg is laid (broadcast per {@code target})
 * @param dataMilestone  ping the owner each time their Data crosses another {@code dataMilestoneStep}
 * @param dataMilestoneStep the Data interval between milestone pings (e.g. 1000 → ping at 1000, 2000, …)
 * @param sound          also play a chime
 * @param channel        {@code "chat"} | {@code "actionbar"} | {@code "both"}
 * @param target         {@code "all"} | {@code "ops"} (shiny broadcast only; milestones always go to the owner)
 */
public record NotifyConfig(boolean enabled, boolean shiny, boolean dataMilestone, long dataMilestoneStep,
                           boolean sound, String channel, String target) {

    public NotifyConfig {
        if (channel == null || channel.isBlank()) channel = "chat";
        if (target == null || target.isBlank()) target = "all";
        if (dataMilestoneStep <= 0) dataMilestoneStep = 1000;
    }

    public static NotifyConfig defaults() {
        return new NotifyConfig(true, true, true, 1000, true, "chat", "all");
    }

    // ── persistence (fail-safe; Gson lazily loaded so the cores stay test-runnable) ──
    private static final class GsonHolder {
        static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public static NotifyConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                NotifyConfig c = GsonHolder.GSON.fromJson(Files.readString(file), NotifyConfig.class);
                if (c != null) return c;
                GpLog.w("notify", "config_empty", "file", file.toString());
            }
        } catch (Throwable t) {
            GpLog.w("notify", "config_bad", "file", file.toString(), "err", String.valueOf(t));
            return defaults();
        }
        NotifyConfig def = defaults();
        def.save(file);   // first run → drop the editable default file next to the others
        return def;
    }

    public void save(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GsonHolder.GSON.toJson(this));
        } catch (Throwable t) {
            GpLog.w("notify", "config_save_fail", "file", file.toString(), "err", String.valueOf(t));
        }
    }
}
