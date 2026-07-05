package com.greenerpastures.economy;

/**
 * Soul Tether inscription economics - the Data costs that keep <b>re-inscribing</b> from being broken.
 *
 * <p>Book-style (Deuce, 2026-06-28): you CAN wipe a tether clean, but you only get <b>part</b> of the
 * Data back, and inscribing costs Data <b>upfront</b> - so experimenting always has a real cost and you
 * can never profit by wiping. Magnitudes are calibration (config-tunable); the shape is pinned.
 */
public final class TetherEconomics {
    private TetherEconomics() {}

    /** Inscribe cost = tier² × this → 100 / 400 / 900 (tier III is a Data pile). Config-tunable. */
    public static final long INSCRIBE_BASE = 100L;
    /** A wipe refunds this fraction of what was paid - you LOSE the rest, so a wipe is never profitable. */
    public static final double REFUND_RATE = 0.5;

    /** Upfront Data to inscribe a blank tether to {@code tier} (0 for a blank). */
    public static long inscribeCost(int tier) {
        int t = clamp(tier);
        return (long) t * t * INSCRIBE_BASE;
    }

    /** Data returned when wiping a {@code tier} tether back to blank - always strictly less than it cost. */
    public static long wipeRefund(int tier) {
        return (long) Math.floor(inscribeCost(tier) * REFUND_RATE);
    }

    private static int clamp(int tier) { return Math.max(0, Math.min(SoulTether.MAX_TIER, tier)); }
}
