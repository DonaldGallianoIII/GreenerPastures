package com.greenerpastures.display;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "My Exhibits" directory (Display Suite v2, §2.1) - one player's placed display blocks, so a
 * {@code disguise}d or forgotten block is always locatable by name + exact coords. Minecraft-free +
 * unit-tested; the MC layer wraps one of these per owner in a {@code PersistentState} and pushes
 * {@link #all()} to the Notebook.
 *
 * <p>Keyed by world-position ({@link ExhibitEntry#key()}), so registering the same spot twice REPLACES
 * (a block is placed once); breaking removes it. Insertion order is preserved for a stable list.
 */
public final class ExhibitRegistry {

    private final Map<String, ExhibitEntry> byPos = new LinkedHashMap<>();

    /** Register (or update) an exhibit at its position. Returns the stored entry. Null/blank-key ignored. */
    public ExhibitEntry register(ExhibitEntry entry) {
        if (entry == null) return null;
        byPos.put(entry.key(), entry);
        return entry;
    }

    /** Deregister the exhibit at a position (block broken). Returns the removed entry, or null if none. */
    public ExhibitEntry removeAt(String dimension, int x, int y, int z) {
        return byPos.remove(key(dimension, x, y, z));
    }

    /** Rename the exhibit at a position; no-op (returns null) if nothing is registered there. */
    public ExhibitEntry rename(String dimension, int x, int y, int z, String name) {
        ExhibitEntry cur = byPos.get(key(dimension, x, y, z));
        if (cur == null) return null;
        ExhibitEntry renamed = cur.withName(name);
        byPos.put(renamed.key(), renamed);
        return renamed;
    }

    public ExhibitEntry at(String dimension, int x, int y, int z) {
        return byPos.get(key(dimension, x, y, z));
    }

    /** Every exhibit, in registration order. */
    public List<ExhibitEntry> all() {
        return new ArrayList<>(byPos.values());
    }

    /** Case-insensitive substring match against the DISPLAY name (given name, else the coord default), so a
     *  search finds unnamed blocks by their coords too. Blank query returns all. */
    public List<ExhibitEntry> search(String query) {
        if (query == null || query.isBlank()) return all();
        String q = query.strip().toLowerCase(Locale.ROOT);
        List<ExhibitEntry> out = new ArrayList<>();
        for (ExhibitEntry e : byPos.values()) {
            if (e.displayName().toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        }
        return out;
    }

    /** All exhibits sorted nearest-first from a point (same-dimension first; other dimensions sort last). */
    public List<ExhibitEntry> sortedByDistance(String dimension, int px, int py, int pz) {
        List<ExhibitEntry> out = all();
        out.sort(Comparator.comparingLong(e -> e.distanceSqTo(dimension, px, py, pz)));
        return out;
    }

    public int size() {
        return byPos.size();
    }

    public boolean isEmpty() {
        return byPos.isEmpty();
    }

    /** Flat snapshot for persistence (registration order preserved). */
    public List<ExhibitEntry> snapshot() {
        return all();
    }

    /** Rebuild from a persisted snapshot (last-writer-wins on a duplicate position; nulls skipped). */
    public static ExhibitRegistry fromSnapshot(List<ExhibitEntry> entries) {
        ExhibitRegistry r = new ExhibitRegistry();
        if (entries != null) for (ExhibitEntry e : entries) r.register(e);
        return r;
    }

    private static String key(String dimension, int x, int y, int z) {
        return (dimension == null ? "" : dimension) + " " + x + " " + y + " " + z;
    }
}
