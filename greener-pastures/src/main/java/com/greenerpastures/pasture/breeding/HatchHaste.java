package com.greenerpastures.pasture.breeding;

/**
 * Hatch Haste math (Deuce, 2026-07-05 - "hatching is the un-automated bottleneck"): Cobbreeding eggs carry
 * a {@code cobbreeding:timer} component (ticks, ~20 drained per second in a player inventory, 40 with an
 * incubator-ability mon in the party). The augment scales that timer at EGG BUILD: level I halves it,
 * II quarters it, III leaves a tenth. Floor of one second so a hatch is never instant-on-pickup (the pop
 * should still be witnessed). Eggs already laid keep the timer they were born with. Pure + tested.
 */
public final class HatchHaste {
    private HatchHaste() {}

    public static final int MIN_TIMER_TICKS = 20;

    /** Timer multiplier per augment level; 0/garbage = ×1 (no augment). Levels 4-6 are TETHER-ONLY
     *  (Deuce, 2026-07-21: tethers stack past the rollable max) - each keeps halving; the 1-second
     *  {@link #MIN_TIMER_TICKS} floor still guarantees a witnessed hatch. Above 6 clamps. */
    public static double factor(int level) {
        return switch (Math.max(0, Math.min(6, level))) {
            case 1 -> 0.5;
            case 2 -> 0.25;
            case 3 -> 0.1;
            case 4 -> 0.05;
            case 5 -> 0.025;
            case 6 -> 0.0125;
            default -> 1.0;
        };
    }

    /** The lore/summary label for a level ("0.5" · "0.25" · "0.1"). */
    public static String factorLabel(int level) {
        double f = factor(level);
        return f == Math.floor(f) ? String.valueOf((int) f) : String.valueOf(f);
    }

    /** The egg's new timer: current × factor, floored at {@link #MIN_TIMER_TICKS}; untouched at level 0. */
    public static int scaledTimer(int currentTicks, int level) {
        if (level <= 0) return currentTicks;
        return Math.max(MIN_TIMER_TICKS, (int) Math.round(Math.max(0, currentTicks) * factor(level)));
    }
}
