package com.greenerpastures.arcade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * <b>TOP DECK</b> - Game Corner cabinet #3 (Deuce's pitch, 2026-07-06): a deck of 20 random Pokemon
 * cards fans out face-up, slides back into the deck, and the house secretly draws one. The player
 * wagers Coins and picks {@link #PICKS} of the {@link #DECK} they think it is. A hit climbs a
 * multiplier ladder ({@link #MULT}: 2x / 6x / 20x - "you can tune it"); between stages the player
 * cashes out or lets it ride against a FRESH secret draw. Any miss loses the whole wager.
 *
 * <p>Minecraft-free and server-authoritative: the drawn card index lives only in {@link Round} on
 * the server - the client sees the 20 species (public: they fanned face-up) and nothing else until
 * the round ends. Per-stage odds are {@code PICKS/DECK} = 25%; expected value by stage: 50% / 37.5%
 * / 31.25% of wager - a house game, but an arcade-honest one.
 */
public final class TopDeck {
    private TopDeck() {}

    public static final int DECK = 20;
    public static final int PICKS = 5;
    public static final int MAX_STAGE = 3;
    public static final long[] MULT = {2, 6, 20};
    public static final long MIN_BET = 10;
    public static final long MAX_BET = 200;

    /** One live round. {@code target} = index into {@code species} of the current secret draw.
     *  {@code stage} = the rung being attempted (1-based). {@code banked} = multiplier already won. */
    public static final class Round {
        public final List<String> species;
        public long wager;
        public int target;
        public int stage = 1;
        public boolean over = false;
        public boolean won = false;
        public long payout = 0;
        public int revealTarget = -1;          // set when the round ends - the client may show it
        Round(List<String> species, long wager, int target) {
            this.species = species; this.wager = wager; this.target = target;
        }
    }

    public enum Outcome { INVALID, HIT, MISS, CASHED }

    /** Deal a round: {@code DECK} distinct species from the approved pool + the first secret draw.
     *  Returns null when the pool is too small or the wager is out of the house's range. */
    public static Round deal(List<String> pool, long wager, Random rng) {
        if (pool == null || pool.size() < DECK || wager < MIN_BET || wager > MAX_BET) return null;
        List<String> deck = new ArrayList<>(pool);
        Collections.shuffle(deck, rng);
        deck = List.copyOf(deck.subList(0, DECK));
        return new Round(deck, wager, rng.nextInt(DECK));
    }

    /** The player's five picks for the current stage. HIT advances the ladder (fresh secret draw);
     *  at {@link #MAX_STAGE} a hit auto-cashes. MISS ends the round, wager gone. */
    public static Outcome guess(Round r, int[] picks, Random rng) {
        if (r == null || r.over || picks == null || picks.length != PICKS) return Outcome.INVALID;
        boolean[] seen = new boolean[DECK];
        for (int p : picks) {
            if (p < 0 || p >= DECK || seen[p]) return Outcome.INVALID;
            seen[p] = true;
        }
        if (!seen[r.target]) {
            r.over = true;
            r.won = false;
            r.revealTarget = r.target;
            return Outcome.MISS;
        }
        if (r.stage >= MAX_STAGE) {
            r.revealTarget = r.target;
            settle(r);
            return Outcome.HIT;                // auto-cash at the top rung
        }
        r.stage++;
        r.target = rng.nextInt(DECK);          // fresh draw for the next rung
        return Outcome.HIT;
    }

    /** Cash out BETWEEN stages: pays the last COMPLETED rung. Only valid once at least one rung is
     *  won (stage has advanced past 1) and the round is still live. */
    public static Outcome cashout(Round r) {
        if (r == null || r.over || r.stage <= 1) return Outcome.INVALID;
        r.stage--;                              // pay the rung actually completed
        settle(r);
        return Outcome.CASHED;
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
