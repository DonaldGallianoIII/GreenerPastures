package com.greenerpastures.arcade;

import java.util.Random;

/**
 * <b>Voltorb Flip</b> - the Game Corner's one machine (Deuce, 2026-07-06: "an arcade game inside the
 * Notebook to farm Data"). The HGSS deduction classic: a 5×5 board of hidden ×1/×2/×3 multipliers and
 * Voltorbs; row/column chips show each line's value sum + Voltorb count; flip multipliers to grow the
 * pot, cash out any time, flip a Voltorb and the round busts (pot lost, level drops).
 *
 * <p><b>Minecraft-free and server-authoritative by design</b>: the server owns the {@link Board}; the
 * client only ever sees flipped tiles + the line chips, so there is nothing to sniff and no score claim
 * to fake (the WS port is localhost-open - a reflex game's "I scored N" would be a Data printer).
 *
 * <p>Economy fences (baked constants, no config): payouts go through {@code DataStore.credit} - the
 * NEUTRAL path, NEVER {@code creditEarned} - so arcade winnings cannot move the MissingNo. odometer.
 * {@link #DAILY_CAP} is the daily fence (currently disarmed - see its javadoc).
 */
public final class VoltorbFlip {
    private VoltorbFlip() {}

    public static final int SIZE = 5;
    public static final int TILES = SIZE * SIZE;
    public static final int MAX_LEVEL = 7;
    /** Daily payout fence. {@code 0} = UNCAPPED (Deuce, 2026-07-06: "no cap until I know exactly what
     *  I'm going to do with it completely") - the ledger still tracks earnedToday for telemetry, and the
     *  fence returns by setting this back to a positive number (the old value was 1,024 - one kB a day). */
    public static final long DAILY_CAP = 0;

    /** Per-level board mix {twos, threes, voltorbs} - a clear ramp; full-clear pots: 36 · 72 · 216 ·
     *  432 · 1,296 · 2,592 · 7,776. (Deuce 2026-07-07: "award a bit more" - one 2 swapped for a 3 per
     *  level, so every pot is exactly 1.5x the old curve with IDENTICAL tile counts and difficulty.) */
    static final int[][] LEVELS = {
            {2, 2, 6},
            {3, 2, 7},
            {3, 3, 8},
            {4, 3, 8},
            {4, 4, 9},
            {5, 4, 10},
            {5, 5, 10},
    };

    /** One live round. Tiles: 0 = Voltorb · 1/2/3 = multiplier. */
    public static final class Board {
        public final int level;
        final int[] tiles = new int[TILES];
        final boolean[] flipped = new boolean[TILES];
        public long coins = 0;
        public boolean over = false;      // bust OR cleared - no further flips
        public boolean cleared = false;   // all 2s & 3s found

        Board(int level) { this.level = level; }

        public int tile(int i) { return tiles[i]; }
        public boolean isFlipped(int i) { return flipped[i]; }

        public int rowSum(int r) { int s = 0; for (int c = 0; c < SIZE; c++) s += tiles[r * SIZE + c]; return s; }
        public int colSum(int c) { int s = 0; for (int r = 0; r < SIZE; r++) s += tiles[r * SIZE + c]; return s; }
        public int rowVoltorbs(int r) { int n = 0; for (int c = 0; c < SIZE; c++) if (tiles[r * SIZE + c] == 0) n++; return n; }
        public int colVoltorbs(int c) { int n = 0; for (int r = 0; r < SIZE; r++) if (tiles[r * SIZE + c] == 0) n++; return n; }
    }

    /** A fresh board for {@code level} (clamped 1..{@link #MAX_LEVEL}). */
    public static Board generate(int level, Random rng) {
        int lvl = Math.max(1, Math.min(MAX_LEVEL, level));
        int[] mix = LEVELS[lvl - 1];
        Board b = new Board(lvl);
        int i = 0;
        for (int n = 0; n < mix[0]; n++) b.tiles[i++] = 2;
        for (int n = 0; n < mix[1]; n++) b.tiles[i++] = 3;
        for (int n = 0; n < mix[2]; n++) b.tiles[i++] = 0;
        while (i < TILES) b.tiles[i++] = 1;
        for (int j = TILES - 1; j > 0; j--) {   // Fisher-Yates
            int k = rng.nextInt(j + 1);
            int t = b.tiles[j]; b.tiles[j] = b.tiles[k]; b.tiles[k] = t;
        }
        return b;
    }

    public record Flip(int value, boolean bust, boolean cleared, long coins, boolean wasNew) {}

    /** Flip tile {@code index}. No-op (wasNew=false) on out-of-range, already-flipped, or finished boards. */
    public static Flip flip(Board b, int index) {
        if (b.over || index < 0 || index >= TILES || b.flipped[index]) {
            return new Flip(-1, false, b.cleared, b.coins, false);
        }
        b.flipped[index] = true;
        int v = b.tiles[index];
        if (v == 0) {
            b.over = true;
            b.coins = 0;   // the pot is lost, not banked
            return new Flip(0, true, false, 0, true);
        }
        b.coins = (b.coins == 0 ? 1 : b.coins) * v;
        if (allMultipliersFound(b)) {
            b.over = true;
            b.cleared = true;
        }
        return new Flip(v, false, b.cleared, b.coins, true);
    }

    /** Cleared = every 2 and 3 flipped (1s are safe filler; finding them is never required). */
    static boolean allMultipliersFound(Board b) {
        for (int i = 0; i < TILES; i++) {
            if (b.tiles[i] >= 2 && !b.flipped[i]) return false;
        }
        return true;
    }

    /** Level after a round: clear climbs, bust drops, cashing out mid-round holds. Always 1..MAX. */
    public static int nextLevel(int level, boolean cleared, boolean busted) {
        int next = cleared ? level + 1 : busted ? level - 1 : level;
        return Math.max(1, Math.min(MAX_LEVEL, next));
    }

    /** What a cashout/clear actually pays today: the pot clamped into the day's remaining allowance
     *  under {@code cap}; {@code cap <= 0} = uncapped. */
    public static long payable(long coins, long earnedToday, long cap) {
        if (cap <= 0) return Math.max(0, coins);
        long remaining = Math.max(0, cap - earnedToday);
        return Math.max(0, Math.min(coins, remaining));
    }

    public static long payable(long coins, long earnedToday) {
        return payable(coins, earnedToday, DAILY_CAP);
    }
}
