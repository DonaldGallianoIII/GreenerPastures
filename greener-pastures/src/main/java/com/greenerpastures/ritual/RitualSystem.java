package com.greenerpastures.ritual;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Runtime holder for the loaded {@link RitualConfig} — the single point the Harvester reads the custom-drop
 * rules from. {@link #init()} loads (and on first run writes) {@code config/greenerpastures/rituals.json};
 * until then, and if loading ever fails, the built-in {@link RitualConfig#defaults()} are used so the feature
 * is never in a broken state. Hot-reloadable later via {@link #reload()}.
 */
public final class RitualSystem {
    private RitualSystem() {}

    private static volatile RitualConfig config = RitualConfig.defaults();

    public static void init() {
        reload();
        RitualConfig c = config;
        GreenerPastures.LOG.info("[rituals] {} — {} rituals, {} type-drops (auto-pull {})",
                c.enabled() ? "enabled" : "disabled",
                c.rituals().rituals().size(), c.typeDrops().drops().size(), c.autoPull());
    }

    public static void reload() {
        try {
            config = RitualConfig.load(configPath());
        } catch (Throwable t) {
            config = RitualConfig.defaults();
            GreenerPastures.LOG.warn("[rituals] config load failed; using defaults", t);
        }
    }

    public static RitualConfig config() {
        return config;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("greenerpastures").resolve("rituals.json");
    }
}
