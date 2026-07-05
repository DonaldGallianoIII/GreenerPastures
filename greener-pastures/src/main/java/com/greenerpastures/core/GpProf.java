package com.greenerpastures.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * The mod's built-in <b>section profiler</b> - "A Data Science Mod" profiles itself. Wrap a hot section in
 * {@code try (var s = GpProf.begin("harvest.sweep")) { … }} and it lands in a global timing tree: count,
 * total/avg/max ms, with parent/child nesting per thread (spans opened inside a span become its children).
 * {@code /gp perf} prints the table; {@code /gp perf flame} renders the tree as a self-contained flame-graph
 * HTML you can open in any browser (and screenshot for the mod listing).
 *
 * <p><b>Minecraft-free + near-zero overhead:</b> a span is two {@code System.nanoTime()} calls, one
 * {@link ConcurrentHashMap} child lookup and two {@link LongAdder} adds - always-on safe at our call rates
 * (hundreds/sec). Thread-safe by construction: the tree is concurrent, the open-span stack is thread-local,
 * so server-thread and render-thread sections coexist as separate branches. Mis-nesting is impossible with
 * try-with-resources; a missed close only orphans that thread's stack until {@link #reset()}.
 */
public final class GpProf {
    private GpProf() {}

    /** Kill-switch: {@code false} makes {@link #begin} return a shared no-op span (nothing recorded). */
    public static volatile boolean enabled = true;

    /** One section in the timing tree. Counters are concurrent; children materialize on first use. */
    public static final class Node {
        public final String name;
        final Map<String, Node> children = new ConcurrentHashMap<>();
        final LongAdder count = new LongAdder();
        final LongAdder totalNs = new LongAdder();
        final AtomicLong maxNs = new AtomicLong();

        Node(String name) { this.name = name; }

        Node child(String n) { return children.computeIfAbsent(n, Node::new); }

        void record(long ns) {
            count.increment();
            totalNs.add(ns);
            long prev;
            while (ns > (prev = maxNs.get()) && !maxNs.compareAndSet(prev, ns)) { /* retry */ }
        }
    }

    /** An open section - {@link #close()} stamps the elapsed time onto its node. */
    public static final class Span implements AutoCloseable {
        private final Node node;
        private final long start;

        private Span(Node node, long start) { this.node = node; this.start = start; }

        @Override
        public void close() {
            if (node == null) return;                      // the shared NOOP span
            node.record(System.nanoTime() - start);
            ArrayDeque<Node> stack = STACK.get();
            if (stack.peek() == node) stack.pop();         // tolerate a corrupt stack instead of throwing
        }
    }

    private static final Span NOOP = new Span(null, 0L);
    private static volatile Node root = new Node("session");
    private static final ThreadLocal<ArrayDeque<Node>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile long since = System.currentTimeMillis();

    /** Open a section under the current thread's innermost open section (or the root). */
    public static Span begin(String name) {
        if (!enabled) return NOOP;
        ArrayDeque<Node> stack = STACK.get();
        Node parent = stack.isEmpty() ? root : stack.peek();
        Node node = parent.child(name);
        stack.push(node);
        return new Span(node, System.nanoTime());
    }

    /** Drop all recorded timings and start a fresh window (open spans on other threads re-root cleanly). */
    public static synchronized void reset() {
        root = new Node("session");
        since = System.currentTimeMillis();
    }

    public static long sinceMs() { return System.currentTimeMillis() - since; }

    // ── reporting ──────────────────────────────────────────────────────────────────────────────────────────

    /** One flattened row of the table view. */
    public record Row(String path, long count, double totalMs, double avgMs, double maxMs) {}

    /** All rows (path = dotted ancestry), sorted by total time descending. */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        flatten(root, "", out);
        out.sort(Comparator.comparingDouble(Row::totalMs).reversed());
        return out;
    }

    private static void flatten(Node n, String prefix, List<Row> out) {
        for (Node c : n.children.values()) {
            String path = prefix.isEmpty() ? c.name : prefix + "." + c.name;
            long cnt = c.count.sum();
            double total = c.totalNs.sum() / 1e6;
            out.add(new Row(path, cnt, total, cnt == 0 ? 0 : total / cnt, c.maxNs.get() / 1e6));
            flatten(c, path, out);
        }
    }

    /** A fixed-width text table of the top {@code n} sections (for chat / log). */
    public static String table(int n) {
        StringBuilder sb = new StringBuilder(String.format("%-34s %9s %9s %9s %10s%n", "section", "count", "avg ms", "max ms", "total ms"));
        List<Row> rows = rows();
        for (int i = 0; i < Math.min(n, rows.size()); i++) {
            Row r = rows.get(i);
            sb.append(String.format("%-34s %9d %9.3f %9.2f %10.1f%n", r.path(), r.count(), r.avgMs(), r.maxMs(), r.totalMs()));
        }
        if (rows.isEmpty()) sb.append("(no sections recorded yet)\n");
        return sb.toString();
    }

    /**
     * Render the timing tree as a <b>self-contained flame-graph HTML</b> - no external assets, opens anywhere.
     * Width = share of the parent's total time; hover shows count/avg/max/total. Sections from different
     * threads sit side-by-side under the session root.
     */
    public static String flameHtml() {
        long rootTotal = 0;
        for (Node c : root.children.values()) rootTotal += c.totalNs.sum();
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Greener Pastures - flame graph</title><style>")
          .append("body{background:#0b0e14;color:#c8d3e0;font:12px 'JetBrains Mono',monospace;margin:24px}")
          .append("h1{font-size:16px;color:#7ee0a3} .sub{color:#5b6b7c;margin-bottom:16px}")
          .append(".frame{box-sizing:border-box;height:26px;line-height:24px;border:1px solid #0b0e14;border-radius:3px;")
          .append("overflow:hidden;white-space:nowrap;text-overflow:ellipsis;padding:0 6px;font-size:11px;color:#08131c;cursor:default}")
          .append(".row{display:flex} .kids{margin:0} .lane{min-width:0}")
          .append("</style></head><body><h1>🌱 Greener Pastures - section flame graph</h1>")
          .append("<div class=\"sub\">window: ").append(sinceMs() / 1000).append("s · total sampled: ")
          .append(String.format("%.1f", rootTotal / 1e6)).append(" ms · generated by /gp perf flame</div>");
        if (rootTotal == 0) {
            sb.append("<div class=\"sub\">(no sections recorded yet)</div>");
        } else {
            sb.append("<div style=\"width:100%\">");
            appendFlame(sb, root.children.values(), rootTotal);
            sb.append("</div>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static void appendFlame(StringBuilder sb, Iterable<Node> nodes, long parentTotal) {
        List<Node> sorted = new ArrayList<>();
        nodes.forEach(sorted::add);
        sorted.sort(Comparator.comparingLong((Node n) -> n.totalNs.sum()).reversed());
        sb.append("<div class=\"row\">");
        for (Node n : sorted) {
            long total = n.totalNs.sum();
            if (total <= 0) continue;
            double pct = 100.0 * total / parentTotal;
            if (pct < 0.05) continue;                                  // sub-pixel frames just add noise
            long cnt = n.count.sum();
            String tip = String.format("%s - %d calls · avg %.3f ms · max %.2f ms · total %.1f ms (%.1f%%)",
                    n.name, cnt, cnt == 0 ? 0 : total / 1e6 / cnt, n.maxNs.get() / 1e6, total / 1e6, pct);
            sb.append("<div class=\"lane\" style=\"width:").append(String.format(java.util.Locale.ROOT, "%.3f", pct)).append("%\">")
              .append("<div class=\"frame\" style=\"background:").append(color(n.name)).append("\" title=\"").append(tip.replace("\"", "&quot;")).append("\">")
              .append(n.name).append(" · ").append(String.format(java.util.Locale.ROOT, "%.1f", total / 1e6)).append("ms")
              .append("</div>");
            if (!n.children.isEmpty()) {
                sb.append("<div class=\"kids\">");
                appendFlame(sb, n.children.values(), total);
                sb.append("</div>");
            }
            sb.append("</div>");
        }
        sb.append("</div>");
    }

    /** Deterministic warm palette per section name (flame-graph convention). */
    private static String color(String name) {
        int h = name.hashCode();
        int r = 205 + (h & 0x1F);              // 205..236
        int g = 90 + ((h >> 5) & 0x5F);        // 90..184
        int b = 40 + ((h >> 10) & 0x1F);       // 40..71
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
