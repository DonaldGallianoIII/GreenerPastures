package com.greenerpastures.analytics;

/**
 * One bred-egg event, Minecraft-free — the row the Dashboard aggregates. Mirrors the {@code egg_laid}
 * fields written to {@code events.jsonl}. The file→event parser (an adapter) is kept separate so this
 * record and the aggregation stay unit-testable headless.
 */
public record EggEvent(String tier, String mode, boolean shiny, boolean procShiny) {}
