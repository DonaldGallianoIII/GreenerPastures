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
    private volatile long dropped = 0L;

    EventLog(Path file) {
        this.file = file;
        this.writer = new Thread(this::drainLoop, "GreenerPastures-EventLog");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    /** Enqueue a row to be serialized + written off-thread. Non-blocking; drops if the queue is full. */
    void append(Map<String, Object> row) {
        if (!queue.offer(row)) dropped++;
    }

    private void drainLoop() {
        try (BufferedWriter w = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            while (running || !queue.isEmpty()) {
                Map<String, Object> row;
                try {
                    row = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    continue;
                }
                if (row == null) continue;
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
            }
        } catch (IOException e) {
            GreenerPastures.LOG.error("[analytics] event-log writer failed for {}", file, e);
        }
    }

    void close() {
        running = false;
        writer.interrupt();
        try {
            writer.join(3000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (dropped > 0) GreenerPastures.LOG.warn("[analytics] dropped {} events under load (queue full)", dropped);
    }
}
