package com.greenerpastures.notify;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Runtime holder for the loaded {@link NotifyConfig} - the single point {@link Notifier} reads from. {@link #init()}
 * loads (and on first run writes) {@code config/greenerpastures/notifications.json}; until then, and if loading
 * ever fails, {@link NotifyConfig#defaults()} are used so notifications are never in a broken state. Mirrors
 * {@code BuffSystem}.
 */
public final class NotifySystem {
    private NotifySystem() {}

    private static volatile NotifyConfig config = NotifyConfig.defaults();

    public static void init() {
        reload();
        NotifyConfig c = config;
        GreenerPastures.LOG.info("[notify] {} - shiny:{} sound:{} channel:{} target:{}",
                c.enabled() ? "enabled" : "disabled", c.shiny(), c.sound(), c.channel(), c.target());
    }

    public static void reload() {
        try {
            config = NotifyConfig.load(configPath());
        } catch (Throwable t) {
            config = NotifyConfig.defaults();
            GreenerPastures.LOG.warn("[notify] config load failed; using defaults", t);
        }
    }

    public static NotifyConfig config() {
        return config;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("greenerpastures").resolve("notifications.json");
    }
}
