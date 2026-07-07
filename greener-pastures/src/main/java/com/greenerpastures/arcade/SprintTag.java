package com.greenerpastures.arcade;

import java.util.List;
import java.util.Random;

/**
 * <b>QUICK CLAW</b> - Game Corner cabinet #6 (Deuce, 2026-07-06): a meadow of ambling decoys,
 * a wanted poster, and the target mon SPRINTING across the field trying to reach the far edge
 * before you click it. Free to play; payout decays with reaction time.
 *
 * <p>Anti-spoof by construction: the client never reports its own timing - the server timestamps
 * the round start and judges the CLICK PACKET'S ARRIVAL against the target's transit window
 * ({@link #judge}). A pre-cognitive click (before the target could plausibly be on screen plus
 * human reaction time) pays zero, as does a click after she's off the far edge. Decoys are pure
 * client theater; misclicking one just locks the client's cursor briefly.
 */
public final class SprintTag {
    private SprintTag() {}

    public static final long PAY_MAX = 15;
    public static final long PAY_MIN = 3;
    /** No human tags anything this fast after it becomes visible; earlier clicks pay nothing. */
    public static final long REACTION_FLOOR_MS = 150;
    /** Latency grace past the theoretical exit before we call it escaped. */
    public static final long EXIT_GRACE_MS = 250;

    public static final class Round {
        public final String species;
        public final boolean fromLeft;
        public final int yStartPct, yEndPct;        // path endpoints, % of field height
        public final long spawnDelayMs;             // theater time before she enters
        public final long crossMs;                  // time on screen edge-to-edge
        public boolean over = false;
        public long paid = 0;
        public boolean escaped = false;
        Round(String species, boolean fromLeft, int yStartPct, int yEndPct, long spawnDelayMs, long crossMs) {
            this.species = species; this.fromLeft = fromLeft;
            this.yStartPct = yStartPct; this.yEndPct = yEndPct;
            this.spawnDelayMs = spawnDelayMs; this.crossMs = crossMs;
        }
    }

    /** Deal a chase: random target from the crowd pool, random edge, slanted path, speed tier. */
    public static Round deal(List<String> pool, Random rng) {
        if (pool == null || pool.isEmpty()) return null;
        String sp = pool.get(rng.nextInt(pool.size()));
        boolean fromLeft = rng.nextBoolean();
        int y0 = 25 + rng.nextInt(55);
        int y1 = Math.max(18, Math.min(88, y0 + rng.nextInt(41) - 20));
        long spawn = 900 + rng.nextInt(1800);        // she waits just long enough to make you blink
        long cross = 2400 + rng.nextInt(1900);       // 2.4s..4.3s edge to edge
        return new Round(sp, fromLeft, y0, y1, spawn, cross);
    }

    /** Judge a click by SERVER-measured elapsed ms since the round started. */
    public static long judge(Round r, long elapsedMs) {
        if (r == null || r.over) return -1;
        r.over = true;
        long visibleAt = r.spawnDelayMs + REACTION_FLOOR_MS;
        long gone = r.spawnDelayMs + r.crossMs + EXIT_GRACE_MS;
        if (elapsedMs < visibleAt || elapsedMs > gone) {
            r.escaped = elapsedMs > gone;
            r.paid = 0;
            return 0;
        }
        double progress = Math.min(1.0, Math.max(0.0,
                (double) (elapsedMs - r.spawnDelayMs) / r.crossMs));
        r.paid = Math.max(PAY_MIN, Math.round(PAY_MAX - progress * (PAY_MAX - PAY_MIN)));
        return r.paid;
    }

    /** No click ever came (player dealt a new round / walked away): she got away, pays nothing. */
    public static void forfeit(Round r) {
        if (r != null && !r.over) { r.over = true; r.escaped = true; r.paid = 0; }
    }
}
