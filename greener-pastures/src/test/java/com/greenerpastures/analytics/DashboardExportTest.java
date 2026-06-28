package com.greenerpastures.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the Dashboard CSV / HTML export — pure string generation. */
class DashboardExportTest {

    private static DashboardStats sample() {
        return DashboardStats.summarize(List.of(
                new EggEvent("COPPER", "buckets", false, false),
                new EggEvent("COPPER", "buckets", true, true),
                new EggEvent("GREENER", "auto", false, false)));
    }

    @Test
    void csvHasHeaderAndRows() {
        String csv = DashboardExport.toCsv(sample());
        assertTrue(csv.startsWith("section,key,value\n"));
        assertTrue(csv.contains("summary,total_eggs,3"), csv);
        assertTrue(csv.contains("tier,COPPER,2"), csv);
        assertTrue(csv.contains("tier,GREENER,1"), csv);
        assertTrue(csv.contains("mode,buckets,2"), csv);
        assertTrue(csv.contains("summary,shiny_total,1"), csv);
    }

    @Test
    void csvQuotesValuesWithCommas() {
        String csv = DashboardExport.toCsv(DashboardStats.summarize(List.of(
                new EggEvent("WE,IRD", "auto", false, false))));
        assertTrue(csv.contains("tier,\"WE,IRD\",1"), csv);
    }

    @Test
    void htmlIsAWellFormedPageWithTheStats() {
        String html = DashboardExport.toHtml(sample());
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("</html>"));
        assertTrue(html.contains("Greener Pastures"));
        assertTrue(html.contains("Total eggs"));
        assertTrue(html.contains("COPPER"), "tier breakdown shows up");
        assertTrue(html.contains("GREENER"));
    }

    @Test
    void htmlEscapesAngleBrackets() {
        String html = DashboardExport.toHtml(DashboardStats.summarize(List.of(
                new EggEvent("<script>", "auto", false, false))));
        assertTrue(html.contains("&lt;script&gt;"), "tier name is HTML-escaped");
        assertTrue(!html.contains("<script>"), "no raw injection");
    }
}
