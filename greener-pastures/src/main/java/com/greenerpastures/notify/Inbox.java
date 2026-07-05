package com.greenerpastures.notify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-player <b>dismissible notifications</b> for the Notebook console's Inbox tab (Deuce, 2026-07-03: catch-up
 * pings out of chat, into the console, dismissible so they never stack unboundedly). In-memory for the server
 * session (cleared on SERVER_STARTED like the other session stores); capped per player so an absent owner's
 * inbox can't grow forever - oldest notes fall off. Thread-safe: pushed from world ticks, read from networking.
 */
public final class Inbox {
    private Inbox() {}

    public record Note(long id, String icon, String text, long atMs) {}

    private static final int CAP = 50;
    private static final AtomicLong IDS = new AtomicLong(1);
    private static final Map<UUID, Deque<Note>> NOTES = new HashMap<>();

    /** Add a note to {@code player}'s inbox (works whether they're online or not - they see it next open). */
    public static synchronized void push(UUID player, String icon, String text) {
        if (player == null || text == null || text.isEmpty()) return;
        Deque<Note> d = NOTES.computeIfAbsent(player, k -> new ArrayDeque<>());
        long id = IDS.getAndIncrement();
        d.addFirst(new Note(id, icon == null ? "" : icon, text, System.currentTimeMillis()));
        while (d.size() > CAP) d.removeLast();
        com.greenerpastures.core.GpLog.i("inbox", "note_push", "player", player, "id", id, "text", text);
    }

    public static synchronized List<Note> notesOf(UUID player) {
        Deque<Note> d = NOTES.get(player);
        return d == null ? List.of() : new ArrayList<>(d);
    }

    public static synchronized void dismiss(UUID player, long id) {
        Deque<Note> d = NOTES.get(player);
        if (d != null && d.removeIf(n -> n.id() == id)) {
            com.greenerpastures.core.GpLog.d("inbox", "note_dismiss", "player", player, "id", id);
        }
    }

    public static synchronized void dismissAll(UUID player) {
        Deque<Note> d = NOTES.remove(player);
        if (d != null && !d.isEmpty()) {
            com.greenerpastures.core.GpLog.d("inbox", "note_dismiss", "player", player, "id", "all", "n", d.size());
        }
    }

    /** SERVER_STARTED hygiene - a new world (same JVM in singleplayer) starts with empty inboxes. */
    public static synchronized void clearAll() {
        NOTES.clear();
    }
}
