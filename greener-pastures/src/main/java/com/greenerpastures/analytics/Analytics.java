package com.greenerpastures.analytics;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local-only analytics facade — the "data science" core. Modules call {@link #record} with an
 * {@link Event}; we stamp it with time / dimension and append it to a per-save JSONL log at
 * {@code <save>/greenerpastures/events.jsonl}. Nothing leaves the machine — players and server
 * admins read or export it themselves. Aggregation, charts, and CSV/HTML export build on this log.
 */
public final class Analytics {
    private Analytics() {}

    private static volatile EventLog log;

    /** Opens the per-save event log on server start and closes it on stop. Call from common init. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Path dir = server.getSavePath(WorldSavePath.ROOT).resolve("greenerpastures");
            try {
                Files.createDirectories(dir);
                log = new EventLog(dir.resolve("events.jsonl"));
                GreenerPastures.LOG.info("[analytics] recording events to {}", dir.resolve("events.jsonl"));
            } catch (IOException e) {
                GreenerPastures.LOG.error("[analytics] could not open event log in {}", dir, e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            EventLog l = log;
            log = null;
            if (l != null) l.close();
        });
    }

    /** Record an event. No-op on client worlds and before the log is open. */
    public static void record(World world, Event event) {
        EventLog l = log;
        if (l == null || event == null || world == null || world.isClient) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", event.type);
        row.put("t", System.currentTimeMillis());
        row.put("gameTime", world.getTime());
        row.put("dimension", world.getRegistryKey().getValue().toString());
        row.putAll(event.fields);
        l.append(row);   // serialized off-thread by EventLog (perf-audit H3)
    }
}
