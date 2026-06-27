package com.greenerpastures.analytics;

import com.greenerpastures.GreenerPastures;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Append-only JSONL writer for analytics events, one per server/save. Lines are queued from the
 * server thread and flushed on a dedicated daemon thread in batches, so event recording never does
 * disk I/O on the tick thread — that's how "excessive" collection stays cheap on TPS.
 */
final class EventLog {
    private final Path file;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Thread writer;
    private volatile boolean running = true;

    EventLog(Path file) {
        this.file = file;
        this.writer = new Thread(this::drainLoop, "GreenerPastures-EventLog");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    /** Enqueue a pre-serialized JSON line. Non-blocking; safe from the server thread. */
    void append(String jsonLine) {
        queue.offer(jsonLine);
    }

    private void drainLoop() {
        try (BufferedWriter w = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            while (running || !queue.isEmpty()) {
                String line;
                try {
                    line = queue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    continue;   // close() interrupts us; the while-condition drains then exits
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
                w.flush();
            }
        } catch (IOException e) {
            GreenerPastures.LOG.error("[analytics] event-log writer failed for {}", file, e);
        }
    }

    /** Stop accepting writes, drain the backlog, and close the file. */
    void close() {
        running = false;
        writer.interrupt();
        try {
            writer.join(3000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
