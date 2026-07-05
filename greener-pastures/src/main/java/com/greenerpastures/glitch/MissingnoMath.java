package com.greenerpastures.glitch;

/**
 * MissingNo. entitlement + rotation math (Deuce, 2026-07-05): one summon per {@link #DATA_PER_SUMMON}
 * lifetime Data EARNED (rendered eggs only — balance-neutral flows never count), forever. The rotation
 * pick always moves to a DIFFERENT sprite (a glitch that visibly glitches). Pure + tested.
 */
public final class MissingnoMath {
    private MissingnoMath() {}

    public static final long DATA_PER_SUMMON = 1_000_000L;

    /** Summons this player is still owed: floor(lifetime/1M) − alreadyClaimed, never negative. */
    public static int claimable(long lifetimeEarned, int claimed) {
        long entitled = Math.max(0, lifetimeEarned) / DATA_PER_SUMMON;
        return (int) Math.max(0, entitled - Math.max(0, claimed));
    }

    /** Progress toward the NEXT summon, 0..1 (for the Dashboard bar). */
    public static double progressToNext(long lifetimeEarned) {
        long into = Math.max(0, lifetimeEarned) % DATA_PER_SUMMON;
        return (double) into / DATA_PER_SUMMON;
    }

    /** Next sprite index: uniform over the others — never the same twice in a row. */
    public static int pickNext(int currentIndex, int speciesCount, double roll) {
        if (speciesCount <= 1) return 0;
        int step = 1 + (int) (Math.min(0.999999, Math.max(0, roll)) * (speciesCount - 1));
        return ((currentIndex + step) % speciesCount + speciesCount) % speciesCount;
    }
}
