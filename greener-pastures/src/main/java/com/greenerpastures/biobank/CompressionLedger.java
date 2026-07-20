package com.greenerpastures.biobank;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The <b>Compression press</b> ledger, Minecraft-free + unit-tested: how many eggs of each species a player
 * has fed to the press, and the permanent per-species drop bonus that buys. Every {@link #BATCH} eggs
 * sacrificed from the BioBank = one press = a further {@link #BONUS_PER_BATCH} on that species' drop proc
 * in the owner's pastures, forever - it MULTIPLIES the whole proc the Kernel/Tether stack already computed
 * ({@code (base + augments) × multiplier}), so it composes with every existing upgrade instead of replacing
 * any. Unbounded stacking is the point (the idle treadmill: farm Klink forever); the proc itself is clamped
 * to 1.0 at the roll.
 *
 * <p>Constants are <b>baked</b> (the anti-p2w rule - no config knob), and the ledger only ever grows:
 * presses are permanent, there is no refund path. Keys are normalized via {@link #normalize} so the egg
 * reader's bank keys ("mrmime") and Cobblemon's display names ("Mr. Mime") land on the same entry.
 */
public final class CompressionLedger {
    /** Eggs consumed per press - all-or-nothing. */
    public static final int BATCH = 100;
    /** Drop-proc bonus per press: +5%, additive per press (2 presses = ×1.10). */
    public static final double BONUS_PER_BATCH = 0.05;

    private final Map<String, Long> pressed = new HashMap<>();   // normalized species → total eggs fed

    /** Lowercase + strip everything outside [a-z0-9], so every spelling of a species collapses to one key
     *  ("Mr. Mime" / "mr-mime" / "mrmime" → "mrmime"). Empty for null/blank input. */
    public static String normalize(String species) {
        if (species == null) return "";
        return species.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Total eggs this species has fed to the press. */
    public long eggsOf(String species) {
        return pressed.getOrDefault(normalize(species), 0L);
    }

    /** Completed presses for a species (eggs / {@link #BATCH}). */
    public long pressesOf(String species) {
        return eggsOf(species) / BATCH;
    }

    /** The permanent drop-proc multiplier for a species: {@code 1 + 0.05 × presses}; 1.0 when never pressed. */
    public double multiplierOf(String species) {
        return 1.0 + BONUS_PER_BATCH * pressesOf(species);
    }

    /** Record eggs fed to the press (ignores non-positive / blank-species input). */
    public void record(String species, long eggs) {
        String key = normalize(species);
        if (key.isEmpty() || eggs <= 0) return;
        pressed.merge(key, eggs, Long::sum);
    }

    public boolean isEmpty() { return pressed.isEmpty(); }

    /** A defensive copy for persistence / the console push (normalized species → eggs). */
    public Map<String, Long> snapshot() { return new HashMap<>(pressed); }

    /** Rebuild from a persisted snapshot (drops null / non-positive entries, re-normalizes keys). */
    public static CompressionLedger fromSnapshot(Map<String, Long> data) {
        CompressionLedger l = new CompressionLedger();
        if (data != null) data.forEach((k, v) -> { if (v != null && v > 0) l.record(k, v); });
        return l;
    }
}
