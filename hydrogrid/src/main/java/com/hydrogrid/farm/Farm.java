package com.hydrogrid.farm;

/**
 * Shared state for the client-side auto harvest/plant engine ({@link AutoFarm}).
 *
 * Vanilla reach only — by design. Every action {@link AutoFarm} performs is an ordinary interaction
 * packet a normal player could send within their own reach, so a vanilla server accepts it with no
 * mixin and no anticheat trouble. Turn it on with ',' and walk your field.
 */
public final class Farm {
    private Farm() {}

    public enum Mode {
        REAP("REAP"),            // harvest mature crops + replant from a hotbar seed
        TILL_PLANT("TILL+PLANT"); // till dirt/grass/path + seed it
        private final String label;
        Mode(String label) { this.label = label; }
        public String label() { return label; }
    }

    /** Minimum gap between actions (ms). ~8/s — brisk but well within normal play. */
    public static final long INTERVAL_MS = 120;

    public static boolean active = false;
    public static Mode mode = Mode.REAP;

    /** Hotbar slot held when you started — the harvest tool (e.g. your Fortune pickaxe) breaks restore to. */
    public static int homeSlot = 0;

    // run stats (reset each time you start)
    public static int harvested = 0, planted = 0, tilled = 0, picked = 0;
    /** Transient one-liner for the HUD (e.g. "hold seeds"); cleared on the next successful action. */
    public static String lastWarn = null;

    public static void toggle() {
        active = !active;
        if (active) { harvested = planted = tilled = picked = 0; lastWarn = null; }
    }

    public static void cycleMode() {
        mode = (mode == Mode.REAP) ? Mode.TILL_PLANT : Mode.REAP;
    }
}
