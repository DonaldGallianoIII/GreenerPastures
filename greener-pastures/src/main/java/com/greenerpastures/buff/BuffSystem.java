package com.greenerpastures.buff;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Runtime holder for the loaded {@link BuffConfig} - the single point the Daemon tick-adapter reads the buff
 * rules from. {@link #init()} loads (and on first run writes) {@code config/greenerpastures/buffs.json}; until
 * then, and if loading ever fails, the built-in {@link BuffConfig#defaults()} are used so the feature is never
 * in a broken state. Hot-reloadable later via {@link #reload()}. Mirrors {@code RitualSystem}.
 */
public final class BuffSystem {
    private BuffSystem() {}

    private static volatile BuffConfig config = BuffConfig.defaults();

    public static void init() {
        reload();
        BuffConfig c = config;
        long active = c.buffs().values().stream().filter(BuffSetting::enabled).count();
        GreenerPastures.LOG.info("[buffs] {} - {} of {} buffs enabled",
                c.enabled() ? "enabled" : "disabled", active, c.buffs().size());
    }

    public static void reload() {
        try {
            config = BuffConfig.load(configPath());
        } catch (Throwable t) {
            config = BuffConfig.defaults();
            GreenerPastures.LOG.warn("[buffs] config load failed; using defaults", t);
        }
    }

    public static BuffConfig config() {
        return config;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("greenerpastures").resolve("buffs.json");
    }
}
