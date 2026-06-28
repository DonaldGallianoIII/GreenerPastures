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

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int KEEP_ARCHIVES = 10;

    private static volatile boolean ready;
    private static BlockingQueue<String> queue;
    private static Thread writer;
    private static volatile boolean running;

    /** Open the log + start the writer thread. Call FIRST in mod init. Idempotent; never throws. */
    public static synchronized void init() {
        if (ready) return;
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("gp-logs");
            Files.createDirectories(dir);
            Path latest = dir.resolve("latest.log");
            rollPrevious(dir, latest);

            queue = new LinkedBlockingQueue<>();
            running = true;
            writer = new Thread(() -> drainLoop(latest), "GreenerPastures-GpLog");
            writer.setDaemon(true);
            writer.start();
            ready = true;

            i("gplog", "session_start",
                    "gameDir", FabricLoader.getInstance().getGameDir().toString(),
                    "minLevel", minLevel.name());
        } catch (Throwable t) {
            GreenerPastures.LOG.error("[gplog] init failed; debug log disabled", t);
        }
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
            queue.offer(sb.toString());
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
        try (BufferedWriter w = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            while (running || !queue.isEmpty()) {
                String line;
                try {
                    line = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    continue;
                }
                if (line == null) continue;
                w.write(line);
                w.write('\n');
                int batch = 0;
                String more;
                while ((more = queue.poll()) != null) {     // drain the backlog under one flush
                    w.write(more);
                    w.write('\n');
                    if (++batch >= 512) break;
                }
                w.flush();                                  // ≤1s latency ⇒ live tail
            }
        } catch (IOException ex) {
            GreenerPastures.LOG.error("[gplog] writer failed for {}", file, ex);
        }
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
