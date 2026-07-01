package com.greenerpastures.notebook;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Notebook's <b>digital item storage</b> — the harvested-loot warehouse that replaces chest farms
 * (see {@code NOTEBOOK_CONSOLE_SPEC.md} §3). Minecraft-free + unit-tested; the MC layer
 * ({@code NotebookStore}) is a per-player {@link net.minecraft.world.PersistentState} wrapper around this,
 * mirroring {@code DataAccount}/{@code DataStore}.
 *
 * <p>Items are keyed by their registry-id string and counted as {@code long}, saturating at a per-item
 * {@link #capacity} that defaults to the integer limit ({@link #INT_LIMIT}) — "stack to the int limit as
 * long as you upgrade enough". Capacity is the stand-in for the upgrade-gated cap (a later economy phase);
 * for now every player is effectively fully upgraded. The {@code >0} invariant holds: a stack that reaches
 * 0 is dropped, so {@link #types()} is exactly the non-empty item ids.
 */
public final class NotebookStorage {
    /** The design ceiling — a single item id stacks up to the 32-bit signed limit. */
    public static final long INT_LIMIT = Integer.MAX_VALUE;

    private final Map<String, Long> counts = new HashMap<>();
    private long capacity;

    public NotebookStorage() { this(INT_LIMIT); }

    public NotebookStorage(long capacity) { this.capacity = clampCapacity(capacity); }

    private static long clampCapacity(long c) { return c < 0 ? 0 : Math.min(c, INT_LIMIT); }

    /** Per-item ceiling (upgrade-gated; defaults to the int limit). */
    public long capacity() { return capacity; }

    /** Raise/lower the per-item ceiling; existing overfull stacks are trimmed to the new cap. */
    public void setCapacity(long c) {
        capacity = clampCapacity(c);
        counts.replaceAll((k, v) -> Math.min(v, capacity));
        counts.values().removeIf(v -> v <= 0);
    }

    /**
     * Deposit up to {@code n} of {@code item}, saturating at {@link #capacity}. Returns the amount
     * actually stored (0 if the stack is already full, or {@code n <= 0}) — the caller keeps any overflow.
     */
    public long add(String item, long n) {
        if (item == null || n <= 0) return 0;
        long have = counts.getOrDefault(item, 0L);
        long room = capacity - have;
        if (room <= 0) return 0;
        long stored = Math.min(room, n);
        counts.put(item, have + stored);
        return stored;
    }

    /**
     * Withdraw up to {@code n} of {@code item}. Returns the amount actually removed (min of held and
     * {@code n}); the stack is dropped from the map when it hits 0.
     */
    public long withdraw(String item, long n) {
        if (item == null || n <= 0) return 0;
        long have = counts.getOrDefault(item, 0L);
        if (have <= 0) return 0;
        long taken = Math.min(have, n);
        long left = have - taken;
        if (left <= 0) counts.remove(item); else counts.put(item, left);
        return taken;
    }

    public long count(String item) { return item == null ? 0 : counts.getOrDefault(item, 0L); }

    public long total() {
        long t = 0;
        for (long v : counts.values()) t += v;
        return t;
    }

    /** The stored item ids (unmodifiable). */
    public Set<String> types() { return Collections.unmodifiableSet(counts.keySet()); }

    public boolean isEmpty() { return counts.isEmpty(); }

    /** A defensive copy for persistence. */
    public Map<String, Long> snapshot() { return new HashMap<>(counts); }

    /** Rebuild from a persisted snapshot (drops null/non-positive entries, trims to capacity). */
    public static NotebookStorage fromSnapshot(Map<String, Long> data, long capacity) {
        NotebookStorage s = new NotebookStorage(capacity);
        if (data != null) {
            data.forEach((k, v) -> {
                if (k != null && v != null && v > 0) s.counts.put(k, Math.min(v, s.capacity));
            });
        }
        return s;
    }
}
