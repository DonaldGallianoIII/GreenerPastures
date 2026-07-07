package com.greenerpastures.arcade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * <b>TOP DECK</b> - Game Corner cabinet #3, v2 (Deuce's redesign, 2026-07-06): 20 cards fan
 * face-up (each a species wearing a RANDOM emotion portrait), slide back into the deck, and get
 * re-dealt face-down - but only ONE of the originals stays on the table; the rest are strangers.
 * The player flips up to {@link #FLIPS} cards hunting the survivor: a stranger face means keep
 * clicking, the original means the ladder rung is won ({@link #MULT}: 2x/6x/20x, cash out between
 * rungs, top rung auto-pays).
 *
 * <p><b>Mercy</b>: on a loss, one free chance - the house picks one of the original 20 and shows
 * EVERY emotion that species has; click the emotion it actually wore in the fan and the wager
 * comes back (never winnings - Mercy can't be farmed for profit, only remembered for a refund).
 *
 * <p>Minecraft-free and server-authoritative: the survivor's position and the stranger faces live
 * only here. Per-rung odds are FLIPS/DECK = 25%, identical to v1 - the redesign is presentation.
 */
public final class TopDeck {
    private TopDeck() {}

    public static final int DECK = 20;
    public static final int FLIPS = 5;
    public static final int MAX_STAGE = 3;
    public static final long[] MULT = {2, 6, 20};
    public static final long MIN_BET = 10;
    public static final long MAX_BET = 200;
    /** Mercy needs a real question - species with fewer approved emotions than this never deal. */
    public static final int MIN_EMOTIONS = 3;

    /** One live round. {@code target} = the survivor's position for the CURRENT rung. */
    public static final class Round {
        public final List<String> species;        // the og 20, in fan order
        public final List<String> ogEmotions;     // the emotion each og card wore in the fan
        public long wager;
        public int target;
        public int stage = 1;
        public boolean over = false;
        public boolean won = false;
        public long payout = 0;
        public int revealTarget = -1;
        public int flipsLeft = FLIPS;
        public final boolean[] flipped = new boolean[DECK];
        public final String[] strangerSpecies = new String[DECK];
        public final String[] strangerEmotion = new String[DECK];
        public boolean mercyAvailable = false;
        public boolean mercyUsed = false;
        public boolean mercyWon = false;
        public int mercyIndex = -1;
        public List<String> mercyOptions = List.of();
        Round(List<String> species, List<String> ogEmotions, long wager, int target) {
            this.species = species; this.ogEmotions = ogEmotions; this.wager = wager; this.target = target;
        }
    }

    public enum Outcome { INVALID, HIT, STRANGER, LOST, CASHED, MERCY_WON, MERCY_LOST }

    /** Deal: 20 distinct species (each with >= {@link #MIN_EMOTIONS} emotions), a random og emotion
     *  per card, and the first survivor position. Null when the catalog or wager can't support it. */
    public static Round deal(Map<String, List<String>> catalog, long wager, Random rng) {
        if (catalog == null || wager < MIN_BET || wager > MAX_BET) return null;
        List<String> eligible = new ArrayList<>();
        for (var e : catalog.entrySet()) {
            if (e.getValue() != null && e.getValue().size() >= MIN_EMOTIONS) eligible.add(e.getKey());
        }
        if (eligible.size() < DECK + 1) return null;      // need at least one stranger species too
        Collections.sort(eligible);                        // deterministic base order, then shuffle
        Collections.shuffle(eligible, rng);
        List<String> deck = List.copyOf(eligible.subList(0, DECK));
        List<String> emotions = new ArrayList<>(DECK);
        for (String sp : deck) {
            List<String> pool = catalog.get(sp);
            emotions.add(pool.get(rng.nextInt(pool.size())));
        }
        return new Round(deck, List.copyOf(emotions), wager, rng.nextInt(DECK));
    }

    /** Flip one face-down card. Survivor = rung won (advance or auto-cash); stranger = a random
     *  non-og face burns a flip; the fifth stranger loses the round and unlocks Mercy. */
    public static Outcome flip(Round r, int pos, Map<String, List<String>> catalog, Random rng) {
        if (r == null || r.over || pos < 0 || pos >= DECK || r.flipped[pos] || r.flipsLeft <= 0) return Outcome.INVALID;
        r.flipped[pos] = true;
        if (pos == r.target) {
            if (r.stage >= MAX_STAGE) {
                r.revealTarget = r.target;
                settle(r);
                return Outcome.HIT;
            }
            r.stage++;
            nextRung(r, rng);
            return Outcome.HIT;
        }
        List<String> strangers = new ArrayList<>(catalog.keySet());
        strangers.removeAll(r.species);
        String sp = strangers.isEmpty() ? r.species.get((pos + 1) % DECK)   // degenerate catalog - never in prod
                : strangers.get(rng.nextInt(strangers.size()));
        List<String> emos = catalog.getOrDefault(sp, List.of("normal"));
        r.strangerSpecies[pos] = sp;
        r.strangerEmotion[pos] = emos.get(rng.nextInt(emos.size()));
        r.flipsLeft--;
        if (r.flipsLeft <= 0) {
            r.over = true;
            r.won = false;
            r.revealTarget = r.target;
            r.mercyAvailable = true;
            return Outcome.LOST;
        }
        return Outcome.STRANGER;
    }

    private static void nextRung(Round r, Random rng) {
        r.flipsLeft = FLIPS;
        java.util.Arrays.fill(r.flipped, false);
        java.util.Arrays.fill(r.strangerSpecies, null);
        java.util.Arrays.fill(r.strangerEmotion, null);
        r.target = rng.nextInt(DECK);
    }

    /** Cash out BETWEEN rungs - pays the last COMPLETED rung; invalid before any rung is won. */
    public static Outcome cashout(Round r) {
        if (r == null || r.over || r.stage <= 1) return Outcome.INVALID;
        r.stage--;
        settle(r);
        return Outcome.CASHED;
    }

    /** Start Mercy: pick one og card, return its species' full emotion list shuffled. One shot. */
    public static List<String> mercyStart(Round r, Map<String, List<String>> catalog, Random rng) {
        if (r == null || !r.over || r.won || !r.mercyAvailable || r.mercyUsed || r.mercyIndex >= 0) return null;
        r.mercyIndex = rng.nextInt(DECK);
        List<String> options = new ArrayList<>(catalog.getOrDefault(r.species.get(r.mercyIndex), List.of()));
        if (options.size() < 2) {                          // guaranteed >= MIN_EMOTIONS at deal; belt+braces
            r.mercyIndex = -1;
            return null;
        }
        Collections.shuffle(options, rng);
        r.mercyOptions = List.copyOf(options);
        return r.mercyOptions;
    }

    /** The player's Mercy answer: the og emotion for the chosen card = wager refund earned. */
    public static Outcome mercyPick(Round r, String emotion) {
        if (r == null || r.mercyIndex < 0 || r.mercyUsed || emotion == null) return Outcome.INVALID;
        r.mercyUsed = true;
        r.mercyWon = emotion.equals(r.ogEmotions.get(r.mercyIndex));
        return r.mercyWon ? Outcome.MERCY_WON : Outcome.MERCY_LOST;
    }

    private static void settle(Round r) {
        r.over = true;
        r.won = true;
        r.payout = r.wager * MULT[r.stage - 1];
    }

    /** The multiplier paid for finishing {@code stage} rungs (1-based). */
    public static long multiplierFor(int stage) {
        return MULT[Math.max(0, Math.min(MULT.length - 1, stage - 1))];
    }
}
