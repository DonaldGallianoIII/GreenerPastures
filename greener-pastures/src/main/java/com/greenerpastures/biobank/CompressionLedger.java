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
 * <p>Two instances exist per world (Deuce, 2026-07-19): each player's PERSONAL ledger ({@link #BATCH} eggs
 * = +{@link #BONUS_PER_BATCH}) and one communal SERVER ledger ({@link #SERVER_BATCH} eggs = +
 * {@link #SERVER_BONUS_PER_BATCH}, fed by anyone, boosting everyone). The server ledger is a "more"
 * multiplier: the harvest multiplies the two ({@code personal × server}), never adds them.
 *
 * <p>Constants are <b>baked</b> (the anti-p2w rule - no config knob), and the ledger only ever grows:
 * presses are permanent, there is no refund path. Keys are normalized via {@link #normalize} so the egg
 * reader's bank keys ("mrmime") and Cobblemon's display names ("Mr. Mime") land on the same entry.
 */
public final class CompressionLedger {
    /** Eggs consumed per press - all-or-nothing (also the size of one server DONATION). */
    public static final int BATCH = 100;
    /** Personal drop-proc bonus per press: +5%, additive per press (2 presses = ×1.10). */
    public static final double BONUS_PER_BATCH = 0.05;
    /** Server pool: every 1000 communal eggs of a species = a further +1% for EVERY player. */
    public static final int SERVER_BATCH = 1000;
    public static final double SERVER_BONUS_PER_BATCH = 0.01;

    private final int batch;
    private final double bonus;
    private final Map<String, Long> pressed = new HashMap<>();   // normalized species → total eggs fed

    /** A personal ledger (100 eggs = +5%). */
    public CompressionLedger() { this(BATCH, BONUS_PER_BATCH); }

    private CompressionLedger(int batch, double bonus) {
        this.batch = batch;
        this.bonus = bonus;
    }

    /** The communal server ledger (1000 eggs = +1%, for everyone). */
    public static CompressionLedger server() { return new CompressionLedger(SERVER_BATCH, SERVER_BONUS_PER_BATCH); }

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

    /** Completed presses for a species (eggs / this ledger's batch size). */
    public long pressesOf(String species) {
        return eggsOf(species) / batch;
    }

    /** The permanent drop-proc multiplier for a species: {@code 1 + bonus × presses}; 1.0 when never pressed. */
    public double multiplierOf(String species) {
        return 1.0 + bonus * pressesOf(species);
    }

    /** Eggs still needed to complete the NEXT press tier (a full batch when the pool sits on a boundary). */
    public long toNextPress(String species) {
        return batch - (eggsOf(species) % batch);
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

    /** Rebuild a PERSONAL ledger from a persisted snapshot (drops null / non-positive entries, re-normalizes keys). */
    public static CompressionLedger fromSnapshot(Map<String, Long> data) {
        return fill(new CompressionLedger(), data);
    }

    /** Rebuild the SERVER ledger from a persisted snapshot. */
    public static CompressionLedger serverFromSnapshot(Map<String, Long> data) {
        return fill(server(), data);
    }

    private static CompressionLedger fill(CompressionLedger l, Map<String, Long> data) {
        if (data != null) data.forEach((k, v) -> { if (v != null && v > 0) l.record(k, v); });
        return l;
    }
}
