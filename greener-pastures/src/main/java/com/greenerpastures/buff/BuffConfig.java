package com.greenerpastures.buff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.greenerpastures.core.GpLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The admin-editable config for the Daemon "root" buffs: a master {@code enabled} toggle plus a per-buff
 * {@link BuffSetting} map (keyed by {@link BuffId#id}). Serialized as JSON so a server admin can disable the
 * whole system, turn individual buffs on/off, cap a buff's tier (the Fortune/Looting "shop-economy" lever),
 * or re-price a buff's Data drain - all without touching code or rebuilding.
 *
 * <p>{@link #defaults()} ships every buff enabled at the full +3 ceiling, with economy-impacting "gathering"
 * buffs priced higher per tier than pure QOL ones (these are <i>tuning</i> - edit the JSON / the eventual sim
 * dials them in). {@link #load} is <b>fail-safe</b>, exactly mirroring {@code RitualConfig}: a missing file is
 * written from defaults; a corrupt file logs and falls back to defaults without overwriting the admin's file;
 * it never crashes the server. Gson is loaded lazily (a holder) so the pure cores + {@code defaults()} stay
 * usable in the headless test JVM, which has no Gson on its runtime classpath.
 */
public record BuffConfig(boolean enabled, Map<String, BuffSetting> buffs) {

    public BuffConfig {
        if (buffs == null) buffs = Map.of();
    }

    /** This buff's settings (looked up by its stable id); {@code null} if the admin deleted it from the file. */
    public BuffSetting settingOf(BuffId id) {
        return id == null ? null : buffs.get(id.id);
    }

    // ── persistence (fail-safe; Gson lazily loaded so the cores stay test-runnable) ──
    private static final class GsonHolder {
        static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public static BuffConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                BuffConfig c = GsonHolder.GSON.fromJson(Files.readString(file), BuffConfig.class);
                if (c != null) return mergeNewBuffs(c, file);
                GpLog.w("buff", "config_empty", "file", file.toString());
            }
        } catch (Throwable t) {
            GpLog.w("buff", "config_bad", "file", file.toString(), "err", String.valueOf(t));
            return defaults();
        }
        BuffConfig def = defaults();
        def.save(file);   // first run → drop the editable default file next to the others
        return def;
    }

    public void save(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GsonHolder.GSON.toJson(this));
        } catch (Throwable t) {
            GpLog.w("buff", "config_save_fail", "file", file.toString(), "err", String.valueOf(t));
        }
    }

    // ── built-in defaults (every buff on at +3; gathering buffs priced higher - all tunable via JSON) ──
    public static BuffConfig defaults() {
        Map<String, BuffSetting> m = new LinkedHashMap<>();
        for (BuffId b : BuffId.values()) {
            m.put(b.id, new BuffSetting(true, BuffSetting.TIER_CEILING, defaultCostPerSec(b)));
        }
        return new BuffConfig(true, m);
    }

    /** A buff added to the CATALOG after an admin's file was written must still appear (with its default
     *  setting) - merge + persist, preserving every admin-tuned entry untouched. */
    private static BuffConfig mergeNewBuffs(BuffConfig c, Path file) {
        Map<String, BuffSetting> merged = null;
        for (BuffId b : BuffId.values()) {
            if (c.buffs().containsKey(b.id)) continue;
            if (merged == null) merged = new LinkedHashMap<>(c.buffs());
            merged.put(b.id, new BuffSetting(true, BuffSetting.TIER_CEILING, defaultCostPerSec(b)));
            GpLog.i("buff", "config_merged_new", "buff", b.id);
        }
        if (merged == null) return c;
        BuffConfig out = new BuffConfig(c.enabled(), merged);
        try { Files.writeString(file, GsonHolder.GSON.toJson(out)); } catch (Throwable ignored) { }
        return out;
    }

    /** Data/sec/tier defaults: gathering (economy-impacting) buffs cost more than pure QOL ones. */
    private static double defaultCostPerSec(BuffId b) {
        return b.gathering ? 0.5 : 0.25;
    }
}
