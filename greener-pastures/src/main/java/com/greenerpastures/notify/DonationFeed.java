package com.greenerpastures.notify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The <b>server-press donation feed</b> (Deuce, 2026-07-19: "don't need a server console chirp, rather a
 * separate inbox message area for donations"). One GLOBAL list - every player sees the same feed in the
 * Inbox tab - so the communal press stays visible without a chat broadcast. Entries <b>expire after 24h</b>
 * ({@link #TTL_MS}) and the deque is hard-capped ({@link #CAP}) as the RAM backstop; nothing here is
 * dismissible or persisted - it's a rolling window, not a log (the JSONL has the permanent record).
 * Thread-safe like {@link Inbox}: pushed from networking, read from networking.
 */
public final class DonationFeed {
    private DonationFeed() {}

    /** One donation: who fed the pool, what, and where the communal multiplier stood after.
     *  {@code tierUp} marks the pull that crossed a 1000-egg boundary (+1% for everyone). */
    public record Entry(long id, long atMs, String who, String species, int eggs, double mult, boolean tierUp) {}

    /** Entries older than this are pruned (Deuce: expire after 24h so it doesn't clutter up RAM). */
    public static final long TTL_MS = 24L * 60L * 60L * 1000L;
    /** Hard backstop so a donation-spam session can't outgrow the window before it ages out. */
    static final int CAP = 200;

    private static final AtomicLong IDS = new AtomicLong(1);
    private static final Deque<Entry> FEED = new ArrayDeque<>();

    public static void push(String who, String species, int eggs, double mult, boolean tierUp) {
        push(who, species, eggs, mult, tierUp, System.currentTimeMillis());
    }

    /** Clock-injected for the headless tests. */
    static synchronized void push(String who, String species, int eggs, double mult, boolean tierUp, long nowMs) {
        if (who == null || species == null) return;
        FEED.addFirst(new Entry(IDS.getAndIncrement(), nowMs, who, species, eggs, mult, tierUp));
        while (FEED.size() > CAP) FEED.removeLast();
        prune(nowMs);
    }

    /** The live window, newest first (expired entries pruned on every read). */
    public static List<Entry> entries() {
        return entries(System.currentTimeMillis());
    }

    static synchronized List<Entry> entries(long nowMs) {
        prune(nowMs);
        return new ArrayList<>(FEED);
    }

    private static void prune(long nowMs) {
        // newest-first deque → expired entries cluster at the tail; pop until the tail is young enough
        while (!FEED.isEmpty() && nowMs - FEED.peekLast().atMs() > TTL_MS) FEED.removeLast();
    }

    /** SERVER_STARTED hygiene - mirrors {@link Inbox#clearAll()}. */
    public static synchronized void clearAll() {
        FEED.clear();
    }
}
