package com.greenerpastures.pasture.breeding;

/**
 * Offline breeding catch-up math — Minecraft-free. Chunks don't tick while unloaded, so "away progress"
 * is computed lazily on return from a persisted {@code lastBred} timestamp (mirrors Cobbreeding's own
 * timestamp catch-up). Per {@code DAEMON_AND_TETHERS.md} the offline payout is <b>bounded</b> ("banks
 * ~24, then waits"): you can be gone an hour or a week and the result is the same once the buffer fills.
 */
public final class CatchUp {
    private CatchUp() {}

    /** Whole breeding cycles elapsed since {@code lastBredTick}, capped at {@code maxCycles}. */
    public static int cyclesElapsed(long lastBredTick, long nowTick, long intervalTicks, int maxCycles) {
        if (intervalTicks <= 0 || maxCycles <= 0 || nowTick <= lastBredTick) return 0;
        long elapsed = (nowTick - lastBredTick) / intervalTicks;
        if (elapsed <= 0) return 0;
        return (int) Math.min(maxCycles, elapsed);
    }

    /**
     * Eggs produced while away = cycles × pairs, never more than the queue's remaining room (the
     * "banks ~24, then waits" bound). {@code queueRoom} caps both the cycles counted and the eggs
     * returned, so a week-long absence yields the same as a buffer-fill.
     */
    public static int offlineEggs(long lastBredTick, long nowTick, long intervalTicks, int pairs, int queueRoom) {
        if (pairs <= 0 || queueRoom <= 0) return 0;
        int cycles = cyclesElapsed(lastBredTick, nowTick, intervalTicks, queueRoom);
        long eggs = (long) cycles * pairs;
        return (int) Math.min(queueRoom, Math.max(0, eggs));
    }
}
