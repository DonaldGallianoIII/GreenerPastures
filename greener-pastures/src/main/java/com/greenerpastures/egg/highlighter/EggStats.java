package com.greenerpastures.egg.highlighter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent lifetime tally of eggs scanned and shinies found — the empirical shiny-rate
 * dataset. Saved to config/shinyegghighlighter_stats.json so it survives restarts and
 * accumulates across worlds/servers.
 */
public final class EggStats {
    public long eggsScanned = 0;
    public long shiniesFound = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("shinyegghighlighter_stats.json");
    private static EggStats instance;

    public static EggStats get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static EggStats load() {
        try {
            if (Files.exists(FILE)) {
                EggStats s = GSON.fromJson(Files.readString(FILE), EggStats.class);
                if (s != null) return s;
            }
        } catch (Exception e) {
            ShinyEggHighlighterClient.LOGGER.warn("[ShinyEgg] stats load failed", e);
        }
        return new EggStats();
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            ShinyEggHighlighterClient.LOGGER.warn("[ShinyEgg] stats save failed", e);
        }
    }

    public void add(long eggs, long shinies) {
        eggsScanned += eggs;
        shiniesFound += shinies;
        save();
    }

    public void reset() {
        eggsScanned = 0;
        shiniesFound = 0;
        save();
    }

    /** Empirical "1 in N" shiny rate, or a placeholder before any shinies are seen. */
    public String rateString() {
        if (shiniesFound <= 0) return "no shinies counted yet";
        return "1 in " + (eggsScanned / shiniesFound);
    }
}
