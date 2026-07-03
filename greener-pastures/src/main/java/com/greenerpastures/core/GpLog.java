package com.greenerpastures.core;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GpLog — the single observability seam for Greener Pastures (see {@code OBSERVABILITY.md}).
 *
 * <p>Every feature logs through here. Output is line-delimited JSON — one event per line,
 * {@code {"t","lvl","tag","ev",...payload}} — so a live {@code tail -f} is readable by eye AND the
 * file is {@code jq}-parseable. Writes run on a dedicated daemon thread (mirrors
 * {@link com.greenerpastures.analytics}'s {@code EventLog}) so logging never does disk I/O on the
 * game thread, and each line is flushed within ~1s so the tail is effectively live.
 *
 * <p><b>Location:</b> {@code <gameDir>/gp-logs/latest.log}. On the WSL test box the instance dir is
 * Windows-side, so symlink it to {@code ~/gp-logs} and {@code tail -F ~/gp-logs/latest.log}.
 *
 * <p><b>Never crashes gameplay:</b> every public method swallows its own failures. If the log can't
 * open, the mod runs fine — just without the debug trace.
 *
 * <p>Usage: {@code GpLog.d("breeder", "enqueue", "pos", pos, "queueSize", n, "shiny", false);}
 */
public final class GpLog {
    private GpLog() {}

    public enum Level { TRACE, DEBUG, INFO, WARN, ERROR }

    /** Calls below this level are dropped cheaply. Mutable knob; a config option can drive it later. */
    public static volatile Level minLevel = Level.DEBUG;

    /** True when {@code level} would actually log — guard hot-LOOP log calls with this so their argument
     *  strings (toString/format/varargs array) aren't built just to be dropped (perf-audit R3 #5). */
    public static boolean on(Level level) {
        return level.ordinal() >= minLevel.ordinal();
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int KEEP_ARCHIVES = 10;
    private static final int QUEUE_CAP = 100_000;   // bounded so a stalled disk can't OOM us (perf-audit M2)

    private static volatile boolean ready;
    private static BlockingQueue<String> queue;
    private static volatile Thread writer;
    private static volatile boolean running;
    private static final AtomicLong dropped = new AtomicLong();   // events lost to a full queue; surfaced inline by the writer (M2)

    /** Open the log + start the writer thread. Call FIRST in mod init. Idempotent; never throws. */
    public static synchronized void init() {
        if (ready) return;
        try {
            applyConfiguredLevel();   // honor -Dgp.log.level / GP_LOG_LEVEL before we log the session banner
            Path dir = FabricLoader.getInstance().getGameDir().resolve("gp-logs");
            Files.createDirectories(dir);
            Path latest = dir.resolve("latest.log");
            rollPrevious(dir, latest);

            queue = new LinkedBlockingQueue<>(QUEUE_CAP);
            running = true;
            writer = new Thread(() -> drainLoop(latest), "GreenerPastures-GpLog");
            writer.setDaemon(true);
            writer.start();
            ready = true;
            // Drain the queue + close the file cleanly on JVM exit so the tail keeps its last lines. This is a
            // JVM shutdown hook, NOT a SERVER_STOPPING hook: GpLog opens once for the whole JVM (mod-init), so
            // closing it on quit-to-title would silence the log for the next world loaded this session (perf-audit).
            Runtime.getRuntime().addShutdownHook(new Thread(GpLog::shutdown, "GreenerPastures-GpLog-shutdown"));

            i("gplog", "session_start",
                    "gameDir", FabricLoader.getInstance().getGameDir().toString(),
                    "minLevel", minLevel.name());
        } catch (Throwable t) {
            GreenerPastures.LOG.error("[gplog] init failed; debug log disabled", t);
        }
    }

    /** Honor a launch-time level override: {@code -Dgp.log.level=INFO} (or env {@code GP_LOG_LEVEL}); default DEBUG. */
    private static void applyConfiguredLevel() {
        String raw = System.getProperty("gp.log.level");
        if (raw == null || raw.isBlank()) raw = System.getenv("GP_LOG_LEVEL");
        if (raw == null || raw.isBlank()) return;
        try {
            minLevel = Level.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            GreenerPastures.LOG.warn("[gplog] ignoring unknown gp.log.level '{}' (want TRACE|DEBUG|INFO|WARN|ERROR)", raw);
        }
    }

    /** Drain the queue + close the file on JVM exit (registered as a shutdown hook by {@link #init}). Idempotent. */
    public static void shutdown() {
        if (!running) return;                 // not started, or already shutting down
        running = false;                      // the drain loop finishes the backlog, then its finally closes the writer
        Thread w = writer;
        if (w != null) {
            try { w.join(2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        ready = false;
    }

    public static void t(String tag, String ev, Object... kv) { log(Level.TRACE, tag, ev, kv); }
    public static void d(String tag, String ev, Object... kv) { log(Level.DEBUG, tag, ev, kv); }
    public static void i(String tag, String ev, Object... kv) { log(Level.INFO,  tag, ev, kv); }
    public static void w(String tag, String ev, Object... kv) { log(Level.WARN,  tag, ev, kv); }
    public static void e(String tag, String ev, Object... kv) { log(Level.ERROR, tag, ev, kv); }

    private static void log(Level lvl, String tag, String ev, Object... kv) {
        try {
            if (!ready || lvl.ordinal() < minLevel.ordinal()) return;
            StringBuilder sb = new StringBuilder(96);
            sb.append('{');
            key(sb, "t");   str(sb, LocalDateTime.now().format(TS)); sb.append(',');
            key(sb, "lvl"); str(sb, lvl.name());                     sb.append(',');
            key(sb, "tag"); str(sb, tag == null ? "?" : tag);        sb.append(',');
            key(sb, "ev");  str(sb, ev == null ? "?" : ev);
            for (int k = 0; k + 1 < kv.length; k += 2) {
                sb.append(',');
                key(sb, String.valueOf(kv[k]));
                val(sb, kv[k + 1]);
            }
            sb.append('}');
            if (!queue.offer(sb.toString())) dropped.incrementAndGet();   // full queue (stalled disk): count it, don't block the game thread
        } catch (Throwable ignored) {
            // logging must never break the caller
        }
    }

    // ── JSON helpers (hand-rolled: tiny, dependency-free, matches EventLog's pre-serialized lines) ──

    private static void key(StringBuilder sb, String name) { str(sb, name); sb.append(':'); }

    private static void val(StringBuilder sb, Object v) {
        if (v == null) sb.append("null");
        else if (v instanceof Number || v instanceof Boolean) sb.append(v);
        else str(sb, v.toString());
    }

    private static void str(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ── writer thread (mirrors analytics.EventLog#drainLoop) ──

    private static void drainLoop(Path file) {
        BufferedWriter w = null;
        try {
            while (running || !queue.isEmpty()) {
                String line;
                try {
                    line = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    continue;
                }
                long lost = dropped.get();                  // peek; cleared only after a durable flush
                if (line == null && lost == 0) continue;    // idle: nothing to write
                try {
                    if (w == null) w = Files.newBufferedWriter(file,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    if (lost > 0) {                          // surface flood gaps inline so the live tail shows the hole
                        w.write(droppedLine(lost));
                        w.write('\n');
                    }
                    if (line != null) {
                        w.write(line);
                        w.write('\n');
                        int batch = 0;
                        String more;
                        while ((more = queue.poll()) != null) { // drain the backlog under one flush
                            w.write(more);
                            w.write('\n');
                            if (++batch >= 512) break;
                        }
                    }
                    w.flush();                              // ≤1s latency ⇒ live tail
                    if (lost > 0) dropped.addAndGet(-lost); // marker durably written → clear only what we reported
                } catch (IOException io) {
                    // a transient disk error must not permanently kill the debug log — reopen on the next line.
                    // Back off so a *persistent* outage can't spin this thread (poll() returns instantly while
                    // the queue has a backlog); the drop count is preserved above for the next attempt.
                    GreenerPastures.LOG.error("[gplog] write failed for {}; will retry in 1s", file, io);
                    if (w != null) { try { w.close(); } catch (IOException ignored) { } w = null; }
                    try { Thread.sleep(1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        } finally {
            if (w != null) try { w.close(); } catch (IOException ignored) { }
        }
    }

    /** Synthetic gap marker: emitted by the writer when the bounded queue dropped events under flood. */
    private static String droppedLine(long n) {
        StringBuilder sb = new StringBuilder(96);
        sb.append('{');
        key(sb, "t");     str(sb, LocalDateTime.now().format(TS)); sb.append(',');
        key(sb, "lvl");   str(sb, "WARN");                         sb.append(',');
        key(sb, "tag");   str(sb, "gplog");                        sb.append(',');
        key(sb, "ev");    str(sb, "dropped");                      sb.append(',');
        key(sb, "count"); sb.append(n);                           sb.append(',');
        key(sb, "note");  str(sb, "gp-log queue full; events lost — raise QUEUE_CAP or minLevel");
        sb.append('}');
        return sb.toString();
    }

    // ── rotation: archive last session's latest.log, keep the newest KEEP_ARCHIVES ──

    private static void rollPrevious(Path dir, Path latest) {
        try {
            if (Files.exists(latest) && Files.size(latest) > 0) {
                String stamp = LocalDateTime.now().format(STAMP);
                Path archive = dir.resolve("gp-" + stamp + ".log");
                int n = 1;
                while (Files.exists(archive)) archive = dir.resolve("gp-" + stamp + "-" + (n++) + ".log");
                Files.move(latest, archive);
            }
            pruneArchives(dir);
        } catch (Throwable t) {
            GreenerPastures.LOG.warn("[gplog] could not roll previous log", t);
        }
    }

    private static void pruneArchives(Path dir) throws IOException {
        List<Path> archives = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir, "gp-*.log")) {
            for (Path p : stream) archives.add(p);
        }
        if (archives.size() <= KEEP_ARCHIVES) return;
        archives.sort(Comparator.comparingLong(p -> {
            try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0L; }
        }));
        for (int i = 0; i < archives.size() - KEEP_ARCHIVES; i++) {
            try { Files.deleteIfExists(archives.get(i)); } catch (IOException ignored) { }
        }
    }
}
