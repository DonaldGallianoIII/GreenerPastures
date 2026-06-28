package com.greenerpastures.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for parsing events.jsonl back into EggEvents, plus the full read→aggregate pipeline. */
class EventReaderTest {

    private static final String EGG =
            "{\"type\":\"egg_laid\",\"t\":1719600000000,\"gameTime\":12345,\"dimension\":\"minecraft:overworld\","
          + "\"source\":\"multipair\",\"tier\":\"COPPER\",\"pair\":1,\"mode\":\"buckets\",\"shiny\":false,\"proc_shiny\":true}";

    @Test
    void parsesARealEggLaidLine() {
        EggEvent e = EventReader.parseEggEvent(EGG);
        assertEquals("COPPER", e.tier());
        assertEquals("buckets", e.mode());
        assertEquals(false, e.shiny());
        assertEquals(true, e.procShiny());
    }

    @Test
    void ignoresNonEggAndGarbageLines() {
        assertNull(EventReader.parseEggEvent("{\"type\":\"pasture_toggle\",\"on\":true}"), "different event type");
        assertNull(EventReader.parseEggEvent("not json at all"));
        assertNull(EventReader.parseEggEvent("{\"type\":"), "truncated json");
        assertNull(EventReader.parseEggEvent(""));
        assertNull(EventReader.parseEggEvent(null));
    }

    @Test
    void readsAndSkipsAcrossAMixedLog() {
        List<String> log = List.of(
                EGG,
                "{\"type\":\"egg_laid\",\"tier\":\"GREENER\",\"mode\":\"auto\",\"shiny\":true,\"proc_shiny\":false}",
                "{\"type\":\"pasture_toggle\",\"on\":true}",
                "oops");
        List<EggEvent> events = EventReader.readEggEvents(log);
        assertEquals(2, events.size(), "only the two egg_laid lines survive");
    }

    @Test
    void fullPipelineLinesToDashboardStats() {
        List<String> log = List.of(
                EGG,                                                                                   // COPPER, proc-shiny
                "{\"type\":\"egg_laid\",\"tier\":\"COPPER\",\"mode\":\"buckets\",\"shiny\":true,\"proc_shiny\":true}",
                "{\"type\":\"egg_laid\",\"tier\":\"COPPER\",\"mode\":\"buckets\",\"shiny\":false,\"proc_shiny\":false}",
                "garbage");
        DashboardStats stats = DashboardStats.summarize(EventReader.readEggEvents(log));
        assertEquals(3, stats.totalEggs());
        assertEquals(3, stats.byTier().get("COPPER"));
        assertEquals(1, stats.shinyTotal());
        assertEquals(2, stats.procShinyTotal());
        assertTrue(stats.shinyRate() > 0);
    }
}
