package com.greenerpastures.buff;

import java.util.Map;

/**
 * The outcome of {@link BuffResolver#resolve}: which buffs are active and at what effective tier (≥1), plus the
 * total Data to drain <i>per second</i> to sustain them all. An immutable snapshot the MC adapter applies on a
 * server tick - apply the {@link #tiers}, then debit {@link #dataPerSec} of Data (lose the fuel → the next
 * resolve sees a starved Daemon and returns {@link #NONE}).
 */
public record ResolvedBuffs(Map<BuffId, Integer> tiers, double dataPerSec, int daemonLevel) {

    public static final ResolvedBuffs NONE = new ResolvedBuffs(Map.of(), 0.0, 0);

    public boolean isEmpty() {
        return tiers.isEmpty();
    }

    /** Effective tier of a buff (0 = inactive). */
    public int tier(BuffId id) {
        return tiers.getOrDefault(id, 0);
    }

    /** Whole Data for one second of these buffs, rounded up - a convenience for whole-Data debiting. */
    public long dataPerSecCeil() {
        return (long) Math.ceil(dataPerSec);
    }
}
