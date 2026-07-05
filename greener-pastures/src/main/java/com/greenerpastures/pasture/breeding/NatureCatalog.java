package com.greenerpastures.pasture.breeding;

import java.util.List;

/**
 * The 25 vanilla Pokémon natures, in a fixed canonical order, as the index table behind the <b>Nature</b>
 * selector augment. The {@code NATURE} augment stores a 1-based index as its "level" ({@code 0} = off); this
 * maps that index to the Cobblemon nature id string the egg spec wants. Pure data (lowercase id strings only) so
 * the cores/tests stay MC-free - Cobblemon validates the string at hatch, so an out-of-range/unknown value just
 * lapses to vanilla nature inheritance rather than corrupting the egg.
 *
 * <p>Order is the standard nature table (Hardy…Quirky) and is <b>stable</b> - a stored index must always mean the
 * same nature, so new entries (there won't be any; there are exactly 25) would only ever append.
 */
public final class NatureCatalog {
    private NatureCatalog() {}

    /** The 25 nature ids, canonical order. A NATURE augment of level N (1-based) picks {@code NATURES.get(N-1)}. */
    public static final List<String> NATURES = List.of(
            "hardy", "lonely", "brave", "adamant", "naughty",
            "bold", "docile", "relaxed", "impish", "lax",
            "timid", "hasty", "serious", "jolly", "naive",
            "modest", "mild", "quiet", "bashful", "rash",
            "calm", "gentle", "sassy", "careful", "quirky");

    public static int size() {
        return NATURES.size();
    }

    /**
     * The nature id for a 1-based augment level, or {@code null} if the level is off ({@code ≤0}) or past the
     * catalog. Null means "no nature lock" - the egg keeps vanilla nature inheritance.
     */
    public static String byIndex(int level) {
        if (level <= 0 || level > NATURES.size()) return null;
        return NATURES.get(level - 1);
    }

    /** The 1-based index of a nature id (case-insensitive), or {@code 0} if unknown - the inverse of {@link #byIndex}. */
    public static int indexOf(String natureId) {
        if (natureId == null) return 0;
        String want = natureId.trim().toLowerCase();
        for (int i = 0; i < NATURES.size(); i++) {
            if (NATURES.get(i).equals(want)) return i + 1;
        }
        return 0;
    }
}
