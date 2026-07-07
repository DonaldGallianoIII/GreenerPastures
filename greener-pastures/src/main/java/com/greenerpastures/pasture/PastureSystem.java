package com.greenerpastures.pasture;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Runtime holder for the loaded {@link PastureConfig} - the single point the breeder/harvester read pacing
 * from. {@link #init()} loads (and on first run writes) {@code config/greenerpastures/pastures.json}; until
 * then, and if loading ever fails, {@link PastureConfig#defaults()} apply. Mirrors {@code NotifySystem}.
 */
public final class PastureSystem {
    private PastureSystem() {}

    private static volatile PastureConfig config = PastureConfig.defaults();

    public static void init() {
        reload();
        GreenerPastures.LOG.info("[pasture] catch-up cap {}h ({} ticks)",
                config.maxCatchupHours(), config.maxCatchupTicks());
    }

    public static void reload() {
        try {
            config = PastureConfig.load(configPath());
        } catch (Throwable t) {
            config = PastureConfig.defaults();
            GreenerPastures.LOG.warn("[pasture] config load failed; using defaults", t);
        }
    }

    public static PastureConfig config() {
        return config;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("greenerpastures").resolve("pastures.json");
    }
}
