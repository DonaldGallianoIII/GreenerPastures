package com.greenerpastures.pasture.breeding.compiler;

import java.util.Random;

/**
 * The <b>corruption roll</b> (Deuce 2026-07-04: "like a corruption orb from Path of Exile") — applying an
 * Illicit Data Disk to a Kernel rolls exactly one outcome and marks the Kernel CORRUPTED forever (the
 * Augmenter refuses corrupted Kernels; permanent consequences are the whole flavor). <b>Minecraft-free</b>
 * so the weight table + variants are headless-tested; the MC adapter in {@code NotebookNet} applies them.
 *
 * <p>Weights (baked, no config — same anti-p2w rule as every rate in the mod):
 * <ul>
 *   <li><b>BLESSED 30%</b> — a random augment force-installed, IGNORING slot capacity (the beyond-cap gift).</li>
 *   <li><b>WILD 25%</b> — variant 0: the Kernel's drop-rate mod DOUBLES · variant 1: +1 breeding pair.</li>
 *   <li><b>NOTHING 25%</b> — just corrupted. Wear the scar.</li>
 *   <li><b>BRICKED 20%</b> — variant 0: every augment + EV spread wiped · variant 1: the tier drops one rung.</li>
 * </ul>
 */
public final class KernelCorruption {
    private KernelCorruption() {}

    public enum Outcome { BLESSED, WILD, NOTHING, BRICKED }

    /** One resolved roll: the outcome bucket + a 0/1 variant selector (meaning depends on the bucket). */
    public record Roll(Outcome outcome, int variant) {}

    public static final int BLESSED_PCT = 30;
    public static final int WILD_PCT = 25;
    public static final int NOTHING_PCT = 25;
    public static final int BRICKED_PCT = 20;

    public static Roll roll(Random rng) {
        int r = rng.nextInt(100);
        int variant = rng.nextInt(2);
        if (r < BLESSED_PCT) return new Roll(Outcome.BLESSED, variant);
        if (r < BLESSED_PCT + WILD_PCT) return new Roll(Outcome.WILD, variant);
        if (r < BLESSED_PCT + WILD_PCT + NOTHING_PCT) return new Roll(Outcome.NOTHING, variant);
        return new Roll(Outcome.BRICKED, variant);
    }

    /** The player-facing reveal line per roll (chat + Inbox — the gamble deserves a moment). */
    public static String reveal(Roll r, String detail) {
        return switch (r.outcome()) {
            case BLESSED -> "§5⛧§r The disk hums… §aBLESSED§r — " + detail;
            case WILD    -> "§5⛧§r The disk crackles… §dWILD§r — " + detail;
            case NOTHING -> "§5⛧§r The disk goes quiet. Nothing happens — but the Kernel is §8corrupted§r.";
            case BRICKED -> "§5⛧§r The disk shrieks! §cBRICKED§r — " + detail;
        };
    }
}
