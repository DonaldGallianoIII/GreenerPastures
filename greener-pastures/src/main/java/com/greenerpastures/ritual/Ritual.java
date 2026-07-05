package com.greenerpastures.ritual;

/**
 * One ritual definition (data, from config): a {@link Requirement} a pasture must hold, the item it can yield,
 * and the gacha tuning — base chance per pull, hard pity (a hit guaranteed by this many pulls since the last
 * one), and an optional soft-pity ramp start ({@code 0} = none). {@code enabled=false} lets an admin switch
 * off a single ritual without deleting it. Pure data; the rolling lives in {@link Gacha}.
 */
public record Ritual(String id, String name, boolean enabled, Requirement requirement,
                     String outputItem, int outputQty, double baseChancePercent, int hardPity, int softPityStart,
                     int pastureSpan, String hint, java.util.List<String> outputPool) {

    public Ritual {
        hint = hint == null ? "" : hint;                       // "" = fully hidden (no locked teaser card)
        outputPool = outputPool == null ? java.util.List.of() : java.util.List.copyOf(outputPool);   // non-empty = each hit rolls one of these instead of outputItem
        outputQty = Math.max(1, outputQty);
        baseChancePercent = Math.max(0.0, Math.min(100.0, baseChancePercent));
        hardPity = Math.max(1, hardPity);
        softPityStart = Math.max(0, softPityStart);   // 0 = no soft pity
        pastureSpan = Math.max(1, Math.min(4, pastureSpan));   // 1 = classic single-pasture; 2 = union of two
        // (missing in old JSON → Gson gives 0 → clamps to 1: every pre-span config reads back unchanged)
    }

    /** Compat shape (pre-span era): single-pasture ritual. */
    public Ritual(String id, String name, boolean enabled, Requirement requirement,
                  String outputItem, int outputQty, double baseChancePercent, int hardPity, int softPityStart) {
        this(id, name, enabled, requirement, outputItem, outputQty, baseChancePercent, hardPity, softPityStart, 1, "", java.util.List.of());
    }

    /** Compat shape (pre-hint era): spanning ritual without teaser/pool. */
    public Ritual(String id, String name, boolean enabled, Requirement requirement,
                  String outputItem, int outputQty, double baseChancePercent, int hardPity, int softPityStart,
                  int pastureSpan) {
        this(id, name, enabled, requirement, outputItem, outputQty, baseChancePercent, hardPity, softPityStart, pastureSpan, "", java.util.List.of());
    }

    /** The item a HIT pays: fixed output, or one uniform roll from the pool (the Music Disc jukebox). */
    public String rollOutput(java.util.function.DoubleSupplier rng) {
        if (outputPool.isEmpty()) return outputItem;
        return outputPool.get(Math.min(outputPool.size() - 1, (int) (rng.getAsDouble() * outputPool.size())));
    }
}
