package com.greenerpastures.analytics;

import java.util.Locale;
import java.util.Map;

/**
 * Renders {@link DashboardStats} to shareable artifacts — a CSV (for spreadsheets) and a self-contained
 * dark-theme HTML page. Pure string generation, Minecraft-free + unit-tested; the command/GUI that writes
 * these to a file is the thin adapter on top.
 */
public final class DashboardExport {
    private DashboardExport() {}

    // ── CSV (long format: section,key,value) ──────────────────────────────────────────────────────
    public static String toCsv(DashboardStats s) {
        StringBuilder b = new StringBuilder();
        b.append("section,key,value\n");
        b.append("summary,total_eggs,").append(s.totalEggs()).append('\n');
        b.append("summary,shiny_total,").append(s.shinyTotal()).append('\n');
        b.append("summary,proc_shiny_total,").append(s.procShinyTotal()).append('\n');
        b.append("summary,shiny_rate,").append(num(s.shinyRate())).append('\n');
        b.append("summary,proc_share_of_shinies,").append(num(s.procShareOfShinies())).append('\n');
        s.byTier().forEach((k, v) -> b.append("tier,").append(csv(k)).append(',').append(v).append('\n'));
        s.byMode().forEach((k, v) -> b.append("mode,").append(csv(k)).append(',').append(v).append('\n'));
        return b.toString();
    }

    // ── HTML (self-contained, dark, mod palette) ──────────────────────────────────────────────────
    public static String toHtml(DashboardStats s) {
        StringBuilder b = new StringBuilder();
        b.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>Greener Pastures — Dashboard</title><style>")
                .append("body{background:#0c0f14;color:#e7eef6;font-family:'JetBrains Mono',Consolas,monospace;margin:24px}")
                .append("h1{color:#4fd6a0}h2{color:#5cc8ff;margin-top:24px}")
                .append("table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid #2a3543;padding:4px 12px;text-align:left}")
                .append("th{background:#161c25;color:#8593a4}.num{text-align:right;color:#d56bff}")
                .append("</style></head><body>");
        b.append("<h1>🌲 Greener Pastures — Breeding Dashboard</h1>");
        b.append("<table>");
        row(b, "Total eggs", String.valueOf(s.totalEggs()));
        row(b, "Shiny", s.shinyTotal() + " (" + pct(s.shinyRate()) + ")");
        row(b, "From your proc", s.procShinyTotal() + " (" + pct(s.procShareOfShinies()) + " of shinies)");
        b.append("</table>");
        b.append("<h2>By Kernel tier</h2>").append(breakdown(s.byTier()));
        b.append("<h2>By pairing mode</h2>").append(breakdown(s.byMode()));
        b.append("</body></html>");
        return b.toString();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────
    private static void row(StringBuilder b, String k, String v) {
        b.append("<tr><th>").append(esc(k)).append("</th><td class=\"num\">").append(esc(v)).append("</td></tr>");
    }

    private static String breakdown(Map<String, Integer> m) {
        if (m.isEmpty()) return "<p>(none yet)</p>";
        StringBuilder b = new StringBuilder("<table><tr><th>key</th><th>eggs</th></tr>");
        m.forEach((k, v) -> b.append("<tr><td>").append(esc(k)).append("</td><td class=\"num\">").append(v).append("</td></tr>"));
        return b.append("</table>").toString();
    }

    private static String num(double d) { return String.format(Locale.ROOT, "%.4f", d); }
    private static String pct(double d) { return String.format(Locale.ROOT, "%.2f%%", d * 100); }

    /** CSV-escape: quote + double inner quotes when the value has a comma/quote/newline. */
    private static String csv(String s) {
        if (s == null) return "";
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
