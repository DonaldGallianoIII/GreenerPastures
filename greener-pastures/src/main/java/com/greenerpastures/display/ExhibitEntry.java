package com.greenerpastures.display;

/**
 * One row in the "My Exhibits" directory (Display Suite v2, §2.1): a placed display block a player owns -
 * its world position, dimension, block type, and player-given name. Minecraft-free + unit-tested; the MC
 * layer builds these from {@code BlockPos} / dimension keys and persists a list of them per owner.
 *
 * <p>This directory is the <b>source of truth</b> for where a block is, independent of what it visually
 * looks like - because a {@code disguise}d block (§2.2) is otherwise unfindable. Name it, look it up here.
 */
public record ExhibitEntry(String dimension, int x, int y, int z, String type, String name) {

    public ExhibitEntry {
        dimension = dimension == null ? "" : dimension;
        type = type == null ? "" : type;
        name = name == null ? "" : name.strip();
    }

    /** Stable identity: exactly one exhibit per world-position, so re-placing dedups instead of duplicating. */
    public String key() {
        return dimension + " " + x + " " + y + " " + z;
    }

    public boolean unnamed() {
        return name.isBlank();
    }

    /** What the directory shows: the player's name, or a coord default so a fresh block is still findable. */
    public String displayName() {
        return unnamed() ? defaultName(type, x, y, z) : name;
    }

    /** The coord-stamped fallback label, e.g. {@code "Exhibit Pen @12,64,-8"}. */
    public static String defaultName(String type, int x, int y, int z) {
        String label = (type == null || type.isBlank()) ? "Exhibit" : type;
        return label + " @" + x + "," + y + "," + z;
    }

    public ExhibitEntry withName(String newName) {
        return new ExhibitEntry(dimension, x, y, z, type, newName);
    }

    /** Squared world distance to a point in the SAME dimension; {@link Long#MAX_VALUE} across dimensions
     *  (so cross-dimension entries always sort last). Squared to stay integer + avoid a sqrt. */
    public long distanceSqTo(String dim, int px, int py, int pz) {
        if (!dimension.equals(dim)) return Long.MAX_VALUE;
        long dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
