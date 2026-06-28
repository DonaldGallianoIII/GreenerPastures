package com.greenerpastures.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the Dashboard's stat aggregation. */
class DashboardStatsTest {

    private static EggEvent egg(String tier, String mode, boolean shiny, boolean proc) {
        return new EggEvent(tier, mode, shiny, proc);
    }

    @Test
    void countsTotalsAndBreakdowns() {
        DashboardStats s = DashboardStats.summarize(List.of(
                egg("COPPER", "buckets", false, false),
                egg("COPPER", "buckets", true, true),
                egg("GREENER", "auto", false, false)));
        assertEquals(3, s.totalEggs());
        assertEquals(2, s.byTier().get("COPPER"));
        assertEquals(1, s.byTier().get("GREENER"));
        assertEquals(2, s.byMode().get("buckets"));
        assertEquals(1, s.byMode().get("auto"));
        assertEquals(1, s.shinyTotal());
        assertEquals(1, s.procShinyTotal());
    }

    @Test
    void shinyRateAndProcShare() {
        DashboardStats s = DashboardStats.summarize(List.of(
                egg("IRON", "auto", true, false),
                egg("IRON", "auto", true, true),
                egg("IRON", "auto", false, false),
                egg("IRON", "auto", false, false)));
        assertEquals(0.5, s.shinyRate(), 1e-9, "2 of 4 eggs were shiny");
        assertEquals(0.5, s.procShareOfShinies(), 1e-9, "1 of the 2 shinies came from our proc");
    }

    @Test
    void emptyIsAllZero() {
        DashboardStats s = DashboardStats.summarize(List.of());
        assertEquals(0, s.totalEggs());
        assertEquals(0.0, s.shinyRate(), 1e-9);
        assertEquals(0.0, s.procShareOfShinies(), 1e-9);
        assertTrue(s.byTier().isEmpty());
    }

    @Test
    void nullTierOrModeBucketsUnderQuestionMark() {
        DashboardStats s = DashboardStats.summarize(List.of(egg(null, null, false, false)));
        assertEquals(1, s.byTier().get("?"));
        assertEquals(1, s.byMode().get("?"));
    }
}
