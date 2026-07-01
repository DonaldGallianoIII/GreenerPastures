package com.greenerpastures.notebook;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A tiny in-memory ring of each player's recent egg-ingest outcomes (kept / voided + which filter) plus running
 * totals — the live feed the console's Log view renders. This is the <b>void-log trust feature</b>
 * (VISUAL_SCRIPTING_UI_IDEA.md) made player-facing: "produced N, voided N-1 by IV, kept 1". Not persisted — a
 * rolling recent-activity tail (the durable record is the {@code egg_voided} analytics event). Thread-safe:
 * appended from the server breeding tick, read from the networking thread.
 */
public final class EggLog {
    private EggLog() {}

    public record Entry(String species, boolean voided, String filter) {}

    private static final int CAP = 40;
    private static final Map<UUID, Deque<Entry>> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, long[]> COUNTS = new HashMap<>();   // [kept, voided]

    public static synchronized void record(UUID owner, String species, boolean voided, String filter) {
        if (owner == null) return;
        Deque<Entry> d = BY_PLAYER.computeIfAbsent(owner, k -> new ArrayDeque<>());
        d.addFirst(new Entry(species, voided, filter == null ? "" : filter));
        while (d.size() > CAP) d.removeLast();
        long[] c = COUNTS.computeIfAbsent(owner, k -> new long[2]);
        if (voided) c[1]++; else c[0]++;
    }

    public static synchronized List<Entry> recent(UUID owner) {
        Deque<Entry> d = BY_PLAYER.get(owner);
        return d == null ? List.of() : new ArrayList<>(d);
    }

    public static synchronized long kept(UUID owner) { long[] c = COUNTS.get(owner); return c == null ? 0 : c[0]; }
    public static synchronized long voided(UUID owner) { long[] c = COUNTS.get(owner); return c == null ? 0 : c[1]; }
}
