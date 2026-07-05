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
 * totals - the live feed the console's Log view renders. This is the <b>void-log trust feature</b>
 * (VISUAL_SCRIPTING_UI_IDEA.md) made player-facing: "produced N, voided N-1 by IV, kept 1". Not persisted - a
 * rolling recent-activity tail (the durable record is the {@code egg_voided} analytics event). Thread-safe:
 * appended from the server breeding tick, read from the networking thread.
 */
public final class EggLog {
    private EggLog() {}

    public record Entry(String species, boolean voided, String filter) {}

    private static final int CAP = 40;
    private static final Map<UUID, Deque<Entry>> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, long[]> COUNTS = new HashMap<>();   // [kept, voided]
    private static final Map<UUID, long[]> TOTALS = new HashMap<>();          // [laid, shiny, procShiny, dataEarned]
    private static final Map<UUID, Map<String, Integer>> BY_TIER = new HashMap<>();
    private static final Map<UUID, Map<Long, Integer>> SPARK = new HashMap<>(); // eggs per world-minute bucket

    /** Drop one player's session stats - DISCONNECT pruning so a 24/7 server's maps stay bounded by the
     *  ONLINE player count, not everyone who ever joined (perf-audit R3 #5). Dashboard stats are per-login. */
    public static synchronized void forget(UUID owner) {
        BY_PLAYER.remove(owner); COUNTS.remove(owner); TOTALS.remove(owner);
        BY_TIER.remove(owner); SPARK.remove(owner);
    }

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

    // ── dashboard totals (laid / shiny / proc-shiny / Data earned + by-tier + a per-minute sparkline) ─────────
    /** Record one laid egg for the dashboard totals + the per-minute sparkline (nowTicks = world time). */
    public static synchronized void recordLaid(UUID owner, String tier, boolean shiny, boolean procShiny, long nowTicks) {
        if (owner == null) return;
        long[] t = TOTALS.computeIfAbsent(owner, k -> new long[4]);
        t[0]++; if (shiny) t[1]++; if (procShiny) t[2]++;
        BY_TIER.computeIfAbsent(owner, k -> new HashMap<>()).merge(tier == null || tier.isEmpty() ? "?" : tier, 1, Integer::sum);
        java.util.TreeMap<Long, Integer> s = (java.util.TreeMap<Long, Integer>) SPARK.computeIfAbsent(owner, k -> new java.util.TreeMap<>());
        s.merge(nowTicks / 1200L, 1, Integer::sum);
        while (s.size() > 48) s.remove(s.firstKey());
    }

    /** Credit Data earned from a rendered (voided) egg - folds into the dashboard's "Data earned" total. */
    public static synchronized void addData(UUID owner, long amt) {
        if (owner != null) TOTALS.computeIfAbsent(owner, k -> new long[4])[3] += amt;
    }

    public static synchronized long[] totals(UUID owner) { return TOTALS.getOrDefault(owner, new long[4]).clone(); }
    public static synchronized Map<String, Integer> byTier(UUID owner) { return new java.util.LinkedHashMap<>(BY_TIER.getOrDefault(owner, Map.of())); }

    /** Wipe everything - called on SERVER_STARTED so a new world (same JVM in singleplayer) never inherits the
     *  previous world's session stats (Deuce hit exactly this: a fresh world showing the old world's numbers). */
    public static synchronized void clearAll() {
        BY_PLAYER.clear();
        COUNTS.clear();
        TOTALS.clear();
        BY_TIER.clear();
        SPARK.clear();
    }

    /** The last 12 world-minute egg counts (oldest→newest, ending at the current minute) - the eggs/min sparkline. */
    public static synchronized int[] spark(UUID owner, long nowTicks) {
        int[] out = new int[12];
        Map<Long, Integer> s = SPARK.get(owner);
        if (s == null) return out;
        long cur = nowTicks / 1200L;
        for (int i = 0; i < 12; i++) out[i] = s.getOrDefault(cur - (11 - i), 0);
        return out;
    }
}
