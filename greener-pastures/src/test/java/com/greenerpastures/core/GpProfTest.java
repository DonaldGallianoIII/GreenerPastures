package com.greenerpastures.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the section profiler - tree shape, counters, reset, and report rendering. */
class GpProfTest {

    @BeforeEach
    void fresh() {
        GpProf.enabled = true;
        GpProf.reset();
    }

    private static GpProf.Row row(String path) {
        return GpProf.rows().stream().filter(r -> r.path().equals(path)).findFirst().orElse(null);
    }

    @Test
    void nestingBuildsParentChildPaths() {
        try (var a = GpProf.begin("scan")) {
            try (var b = GpProf.begin("sweep")) { }
            try (var c = GpProf.begin("sweep")) { }
        }
        GpProf.Row scan = row("scan");
        GpProf.Row sweep = row("scan.sweep");
        assertEquals(1, scan.count());
        assertEquals(2, sweep.count());
        assertTrue(scan.totalMs() >= sweep.totalMs(), "parent time includes child time");
    }

    @Test
    void siblingsStaySeparate() {
        try (var a = GpProf.begin("breeder")) { }
        try (var b = GpProf.begin("harvest")) { }
        assertEquals(1, row("breeder").count());
        assertEquals(1, row("harvest").count());
        assertEquals(null, row("breeder.harvest"));
    }

    @Test
    void countersAccumulateAcrossCalls() {
        for (int i = 0; i < 5; i++) {
            try (var s = GpProf.begin("tick")) { }
        }
        GpProf.Row r = row("tick");
        assertEquals(5, r.count());
        assertTrue(r.totalMs() >= 0 && r.maxMs() >= r.avgMs());
    }

    @Test
    void resetDropsEverything() {
        try (var s = GpProf.begin("x")) { }
        assertFalse(GpProf.rows().isEmpty());
        GpProf.reset();
        assertTrue(GpProf.rows().isEmpty());
    }

    @Test
    void disabledRecordsNothing() {
        GpProf.enabled = false;
        try (var s = GpProf.begin("ghost")) { }
        assertTrue(GpProf.rows().isEmpty());
    }

    @Test
    void reportsRenderTheTree() {
        try (var a = GpProf.begin("net")) {
            try (var b = GpProf.begin("biobank")) { }
        }
        String table = GpProf.table(10);
        assertTrue(table.contains("net") && table.contains("net.biobank"));
        String html = GpProf.flameHtml();
        assertTrue(html.startsWith("<!doctype html>") && html.contains("net") && html.contains("biobank"));
        assertTrue(html.contains("flame graph"));
    }
}
