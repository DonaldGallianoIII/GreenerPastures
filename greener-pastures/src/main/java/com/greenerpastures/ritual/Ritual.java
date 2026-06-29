package com.greenerpastures.ritual;

/**
 * One ritual definition (data, from config): a {@link Requirement} a pasture must hold, the item it can yield,
 * and the gacha tuning — base chance per pull, hard pity (a hit guaranteed by this many pulls since the last
 * one), and an optional soft-pity ramp start ({@code 0} = none). {@code enabled=false} lets an admin switch
 * off a single ritual without deleting it. Pure data; the rolling lives in {@link Gacha}.
 */
public record Ritual(String id, String name, boolean enabled, Requirement requirement,
                     String outputItem, int outputQty, double baseChancePercent, int hardPity, int softPityStart) {

    public Ritual {
        outputQty = Math.max(1, outputQty);
        baseChancePercent = Math.max(0.0, Math.min(100.0, baseChancePercent));
        hardPity = Math.max(1, hardPity);
        softPityStart = Math.max(0, softPityStart);   // 0 = no soft pity
    }
}
