package com.greenerpastures.pasture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.greenerpastures.core.GpLog;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-operator config for pasture progression pacing. Same fail-safe lazy-Gson pattern as
 * {@code NotifyConfig}/{@code BuffConfig} (missing → write defaults, corrupt → fall back, never crash).
 *
 * <p>This is deliberately a PACING knob, not a power knob (the anti-p2w line): it bounds how much
 * away-from-the-chunk time a pasture may bank for breed/harvest catch-up when its chunk reloads.
 * Offline time is already excluded by {@code OfflineProgress} regardless of this value.
 *
 * @param maxCatchupHours cap on banked away-time per catch-up, in real hours (default 12; clamped
 *                        0-168 - 0 disables catch-up entirely, chunks only progress while loaded)
 */
public record PastureConfig(double maxCatchupHours) {

    public PastureConfig {
        if (Double.isNaN(maxCatchupHours)) maxCatchupHours = 12;
        maxCatchupHours = Math.max(0, Math.min(168, maxCatchupHours));
    }

    public static PastureConfig defaults() {
        return new PastureConfig(12);
    }

    /** The cap in ticks - what the breeder/harvester actually compare gaps against. */
    public long maxCatchupTicks() {
        return (long) (maxCatchupHours * 60.0 * 60.0 * 20.0);
    }

    // ── persistence (fail-safe; Gson lazily loaded so the cores stay test-runnable) ──
    private static final class GsonHolder {
        static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public static PastureConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                PastureConfig c = GsonHolder.GSON.fromJson(Files.readString(file), PastureConfig.class);
                if (c != null) return c;
                GpLog.w("pasture", "config_empty", "file", file.toString());
            }
        } catch (Throwable t) {
            GpLog.w("pasture", "config_bad", "file", file.toString(), "err", String.valueOf(t));
            return defaults();
        }
        PastureConfig def = defaults();
        def.save(file);   // first run → drop the editable default file next to the others
        return def;
    }

    public void save(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GsonHolder.GSON.toJson(this));
        } catch (Throwable t) {
            GpLog.w("pasture", "config_save_fail", "file", file.toString(), "err", String.valueOf(t));
        }
    }
}
