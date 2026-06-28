package com.greenerpastures.analytics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated breeding stats for the analytics Dashboard — pure data-crunching over {@link EggEvent}s,
 * Minecraft-free + unit-tested. (CSV/HTML export and the {@code events.jsonl} reader come next, as
 * separate pieces, so each stays independently testable.)
 */
public record DashboardStats(int totalEggs, Map<String, Integer> byTier, Map<String, Integer> byMode,
                             int shinyTotal, int procShinyTotal) {

    /** Fraction of eggs that hatched shiny (0..1). */
    public double shinyRate() {
        return totalEggs == 0 ? 0.0 : (double) shinyTotal / totalEggs;
    }

    /** Of the shinies, the fraction OUR bonus proc is responsible for (0..1) — the "shinies this augment earned you" stat. */
    public double procShareOfShinies() {
        return shinyTotal == 0 ? 0.0 : (double) procShinyTotal / shinyTotal;
    }

    /** Roll a list of egg events up into the dashboard summary. Tier/mode order is first-seen. */
    public static DashboardStats summarize(List<EggEvent> events) {
        Map<String, Integer> byTier = new LinkedHashMap<>();
        Map<String, Integer> byMode = new LinkedHashMap<>();
        int shiny = 0, proc = 0;
        for (EggEvent e : events) {
            byTier.merge(e.tier() == null ? "?" : e.tier(), 1, Integer::sum);
            byMode.merge(e.mode() == null ? "?" : e.mode(), 1, Integer::sum);
            if (e.shiny()) shiny++;
            if (e.procShiny()) proc++;
        }
        return new DashboardStats(events.size(), byTier, byMode, shiny, proc);
    }
}
