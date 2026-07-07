package com.greenerpastures.arcade;

import java.util.List;

/**
 * The <b>High Roller Room</b> (Deuce, 2026-07-07): the velvet-rope shelf under the Prize Counter.
 * FIXED catalog - no rotation, no refresh - because these are the long-term goals the arcade
 * grinds toward; they should stand there taunting you every visit. Prices are anchored to
 * post-nerf honest earn rates (~600-1200 Coins/hour): the Master Ball is a dedicated-week flex,
 * the Legend Disk is a season trophy. Coins still never convert to Data; legends leave as ITEMS
 * (tradeable Specimen Disks - yes, that makes them server currency; that is the fun part).
 */
public final class HighRoller {
    private HighRoller() {}

    public static final String MASTER_BALL_ID = "cobblemon:master_ball";
    public static final String PRIME_EGG_ID = "greenerpastures:prime_egg";
    public static final String LEGEND_DISK_ID = "greenerpastures:legend_disk";
    /** Prime Egg: the Mystery Egg's big sibling - this many guaranteed-perfect IVs (plus HA, always). */
    public static final int PRIME_EGG_PERFECT_IVS = 4;
    /** Legend disk mon level - a caught-legend feel, not a raid boss. */
    public static final int LEGEND_LEVEL = 50;

    public record Ware(String itemId, String name, long price) {}

    public static final List<Ware> CATALOG = List.of(
            new Ware(MASTER_BALL_ID, "Master Ball", 30_000),
            new Ware(PRIME_EGG_ID, "Prime Egg", 8_000),
            new Ware(LEGEND_DISK_ID, "Legend Specimen Disk", 100_000));

    /** Fixed shelves: the slot IS the ware, forever. Out-of-range buys nothing. */
    public static Ware wareAt(int slot) {
        return (slot >= 0 && slot < CATALOG.size()) ? CATALOG.get(slot) : null;
    }
}
