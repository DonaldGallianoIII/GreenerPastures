package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the per-pasture FIFO egg-queue - pure data structure, no Minecraft. */
class EggQueueTest {

    @Test
    void pollIsFifo() {
        EggQueue<String> q = new EggQueue<>(5);
        q.offer("a"); q.offer("b"); q.offer("c");
        assertEquals("a", q.poll());
        assertEquals("b", q.poll());
        assertEquals("c", q.poll());
        assertNull(q.poll(), "empty queue polls null");
    }

    @Test
    void fullQueuePausesAndNeverEvictsTheHead() {
        EggQueue<String> q = new EggQueue<>(2);
        assertTrue(q.offer("first"));
        assertTrue(q.offer("second"));
        assertTrue(q.isFull());
        assertFalse(q.offer("third"), "full → offer returns false (producer pauses)");
        assertEquals(2, q.size(), "rejected egg is NOT added");
        assertEquals("first", q.peek(), "the oldest kept egg is never evicted");
    }

    @Test
    void offerRejectsNull() {
        assertFalse(new EggQueue<>(5).offer(null));
    }

    @Test
    void drainIntoTakesFifoUpToFreeSlots() {
        EggQueue<String> q = new EggQueue<>(10);
        q.offer("a"); q.offer("b"); q.offer("c"); q.offer("d");
        List<String> tray = new ArrayList<>();
        int moved = q.drainInto(2, tray::add);   // tray had 2 empty slots
        assertEquals(2, moved);
        assertEquals(List.of("a", "b"), tray, "drains oldest-first");
        assertEquals(2, q.size(), "the rest stay queued");
    }

    @Test
    void drainIntoStopsWhenQueueEmpties() {
        EggQueue<String> q = new EggQueue<>(10);
        q.offer("only");
        List<String> tray = new ArrayList<>();
        assertEquals(1, q.drainInto(5, tray::add), "drains at most what's queued");
        assertTrue(q.isEmpty());
    }

    @Test
    void snapshotRestoreRoundTripsInFifoOrder() {
        EggQueue<String> q = new EggQueue<>(10);
        q.offer("a"); q.offer("b"); q.offer("c");
        List<String> saved = q.snapshot();
        assertEquals(List.of("a", "b", "c"), saved);

        EggQueue<String> loaded = new EggQueue<>(10);
        loaded.restore(saved);
        assertEquals("a", loaded.poll());
        assertEquals("b", loaded.poll());
        assertEquals("c", loaded.poll());
    }

    @Test
    void shrinkingCapKeepsContentsThenPausesUntilDrained() {
        EggQueue<String> q = new EggQueue<>(5);
        for (String s : List.of("a", "b", "c", "d", "e")) q.offer(s);
        q.setCap(2);
        assertEquals(5, q.size(), "shrinking cap never drops eggs");
        assertFalse(q.offer("x"), "over cap → paused");
        q.poll(); q.poll(); q.poll();          // drain back under the new cap (size 2)
        assertFalse(q.offer("y"), "still at cap (2) → paused");
        q.poll();                              // size 1, now under cap
        assertTrue(q.offer("z"), "under cap again → accepts");
    }

    @Test
    void minCapIsTheDesignFloor() {
        assertEquals(24, EggQueue.MIN_CAP, "8 pairs × 3 eggs - the per-pasture queue floor");
    }
}
