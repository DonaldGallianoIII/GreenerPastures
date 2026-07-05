package com.greenerpastures.pasture.breeding;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bounded FIFO buffer of bred eggs that feeds a pasture's small visible tray - the runtime for the
 * COLLECTION node in {@code EGG_STORAGE_DESIGN.md}. <b>Minecraft-free</b> and generic over the egg
 * type {@code T} (the adapter uses {@code EggQueue<ItemStack>}), so all of this logic is unit-tested
 * headless.
 *
 * <p><b>Key invariant - pause, don't evict.</b> When the queue is full, {@link #offer} returns
 * {@code false} (the producer pauses breeding) rather than dropping the head, so a kept egg is never
 * lost. FIFO is the order; a future <b>Soul Tether</b> can reorder via a separate seam (not here).
 *
 * <p>Cap is configurable; the design floor is {@link #MIN_CAP} (= 8 pairs × 3 eggs), enforced by the
 * config layer that sets the cap - the core accepts any non-negative cap so it stays trivially testable.
 */
public final class EggQueue<T> {
    /** Per-pasture queue floor from the design: 8 pairs × 3 eggs/cycle. Config clamps to at least this. */
    public static final int MIN_CAP = 24;

    private final ArrayDeque<T> q = new ArrayDeque<>();
    private int cap;

    public EggQueue(int cap) { this.cap = Math.max(0, cap); }

    public int size() { return q.size(); }
    public int cap() { return cap; }
    public boolean isEmpty() { return q.isEmpty(); }
    public boolean isFull() { return q.size() >= cap; }

    /**
     * Add an egg to the tail if there's room. Returns {@code false} when full (the producer should
     * pause) - the queue never evicts, so the oldest kept egg is safe.
     */
    public boolean offer(T egg) {
        if (egg == null || q.size() >= cap) return false;
        q.addLast(egg);
        return true;
    }

    /** Remove + return the oldest egg (FIFO head), or {@code null} if empty. */
    public T poll() { return q.pollFirst(); }

    /** Look at the oldest egg without removing it, or {@code null} if empty. */
    public T peek() { return q.peekFirst(); }

    /**
     * Drain up to {@code freeSlots} eggs (FIFO order) into {@code sink} - the "while the tray has an
     * empty slot, pop the queue into it" step. Returns how many were moved.
     */
    public int drainInto(int freeSlots, Consumer<? super T> sink) {
        int moved = 0;
        while (moved < freeSlots && !q.isEmpty()) {
            sink.accept(q.pollFirst());
            moved++;
        }
        return moved;
    }

    /**
     * Reconfigure the cap. If the new cap is below the current size, existing eggs are kept
     * (over-capacity) and {@link #offer} simply pauses until the queue drains back under cap.
     */
    public void setCap(int newCap) { this.cap = Math.max(0, newCap); }

    /** Iterate in FIFO order (oldest first) without copying - for in-place persistence (perf-audit H6). */
    public void forEach(java.util.function.Consumer<? super T> action) {
        for (T t : q) action.accept(t);
    }

    /** FIFO-ordered snapshot (oldest first) for persistence / inspection. */
    public List<T> snapshot() { return new ArrayList<>(q); }

    /** Replace contents with a FIFO-ordered collection (oldest first). Restore may exceed the cap. */
    public void restore(Collection<? extends T> eggs) {
        q.clear();
        if (eggs != null) q.addAll(eggs);
    }
}
