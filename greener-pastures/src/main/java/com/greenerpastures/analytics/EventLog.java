package com.greenerpastures.analytics;

import com.google.gson.Gson;
import com.greenerpastures.GreenerPastures;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Append-only JSONL writer for analytics events, one per server/save. Rows are queued from the server
 * thread and BOTH serialized (Gson) AND flushed on a dedicated daemon thread — so the tick thread does
 * no Gson reflection and no disk I/O (perf-audit H3). The queue is <b>bounded</b>: under an event flood
 * or a stalled disk, {@link #append} drops rather than growing without bound (perf-audit M2).
 */
final class EventLog {
    private static final int CAPACITY = 100_000;

    private final Path file;
    private final BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>(CAPACITY);
    private final Gson gson = new Gson();
    private final Thread writer;
    private volatile boolean running = true;
    private final AtomicLong dropped = new AtomicLong();   // queue-full drops (multi-producer safe)

    EventLog(Path file) {
        this.file = file;
        this.writer = new Thread(this::drainLoop, "GreenerPastures-EventLog");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    /** Enqueue a row to be serialized + written off-thread. Non-blocking; drops if the queue is full. */
    void append(Map<String, Object> row) {
        if (!queue.offer(row)) dropped.incrementAndGet();
    }

    private void drainLoop() {
        BufferedWriter w = null;
        try {
            while (running || !queue.isEmpty()) {
                Map<String, Object> row;
                try {
                    row = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    continue;
                }
                if (row == null) continue;
                try {
                    if (w == null) w = Files.newBufferedWriter(file,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    w.write(gson.toJson(row));               // serialize off the tick thread
                    w.write('\n');
                    int batch = 0;
                    Map<String, Object> more;
                    while ((more = queue.poll()) != null) {   // drain the backlog under one flush
                        w.write(gson.toJson(more));
                        w.write('\n');
                        if (++batch >= 512) break;
                    }
                    w.flush();
                } catch (IOException io) {
                    // a transient disk error (full, handle revoked) must NOT permanently kill logging —
                    // close, null the writer, and reopen on the next row so it recovers (bug-hunt #9).
                    // Back off first: poll() returns instantly while the queue has a backlog, so without a
                    // pause a *persistent* outage would spin a core + flood this very log (review follow-up).
                    GreenerPastures.LOG.error("[analytics] write failed for {}; will retry in 1s", file, io);
                    closeQuietly(w);
                    w = null;
                    try { Thread.sleep(1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        } finally {
            closeQuietly(w);
        }
    }

    private static void closeQuietly(BufferedWriter w) {
        if (w != null) try { w.close(); } catch (IOException ignored) { }
    }

    void close() {
        running = false;
        writer.interrupt();
        try {
            writer.join(3000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        long d = dropped.get();
        if (d > 0) GreenerPastures.LOG.warn("[analytics] dropped {} events under load (queue full)", d);
    }
}
