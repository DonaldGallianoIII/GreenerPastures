package com.greenerpastures.arcade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * <b>VIBE CHECK</b> - Game Corner cabinet #5 (Deuce, 2026-07-06): the free press-your-luck deck.
 * A {@link #DECK_SIZE}-card deck holds {@link #SOUR} sour faces; draw a HAPPY pokemon and the pot
 * doubles (2, 4, 8, ...), draw a sour one and the pot is gone. Cash out between draws; clearing
 * every happy card auto-cashes the max pot ({@code 2^(DECK_SIZE-SOUR)} = 256).
 *
 * <p>FREE to play (no wager) - it's a coin faucet by design, tuned small: the known deck
 * composition makes every draw a real odds decision (4 sour in 12 to start; the deck depletes,
 * so the sour density CLIMBS as the pot doubles - that's the whole game).
 *
 * <p>Minecraft-free and server-authoritative: the shuffled deck order lives only here.
 */
public final class VibeCheck {
    private VibeCheck() {}

    public static final int DECK_SIZE = 12;
    public static final int SOUR = 4;
    public static final List<String> HAPPY_EMOTIONS = List.of("happy", "joyous");
    public static final List<String> SOUR_EMOTIONS = List.of("angry", "sad", "crying", "pain");

    /** One drawn card the client may see. */
    public record Card(String species, String emotion, boolean happy) {}

    public static final class Round {
        final boolean[] order;                       // true = happy, in draw order (secret)
        public final List<Card> drawn = new ArrayList<>();
        public long pot = 0;
        public boolean over = false;
        public boolean won = false;
        public long payout = 0;
        Round(boolean[] order) { this.order = order; }
        public int remaining() { return order.length - drawn.size(); }
        public int sourRemaining() {
            int drawnSour = (int) drawn.stream().filter(c -> !c.happy()).count();
            return SOUR - drawnSour;
        }
    }

    public enum Outcome { INVALID, HAPPY, SOUR, CASHED }

    /** Shuffle a fresh deck. The catalog dresses cards at draw time. */
    public static Round deal(Random rng) {
        boolean[] order = new boolean[DECK_SIZE];
        for (int i = 0; i < DECK_SIZE - SOUR; i++) order[i] = true;
        for (int i = DECK_SIZE - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            boolean t = order[i]; order[i] = order[j]; order[j] = t;
        }
        return new Round(order);
    }

    /** Flip the next card: happy doubles the pot (1 first), sour torches it. Emptying the deck
     *  auto-cashes - there is nothing left to lose to. */
    public static Outcome draw(Round r, Map<String, List<String>> catalog, Random rng) {
        if (r == null || r.over || r.drawn.size() >= r.order.length) return Outcome.INVALID;
        boolean happy = r.order[r.drawn.size()];
        r.drawn.add(dress(happy, catalog, rng));
        if (!happy) {
            r.over = true;
            r.won = false;
            r.pot = 0;
            return Outcome.SOUR;
        }
        r.pot = r.pot == 0 ? 2 : r.pot * 2;   // Deuce 2026-07-07: "award a bit more" - base 1 -> 2
        if (allHappyDrawn(r)) settle(r);   // only sour cards remain - nothing left to win, bank it
        return Outcome.HAPPY;
    }

    private static boolean allHappyDrawn(Round r) {
        return r.drawn.stream().filter(Card::happy).count() == DECK_SIZE - SOUR;
    }

    /** Bank the pot between draws. */
    public static Outcome cashout(Round r) {
        if (r == null || r.over || r.pot <= 0) return Outcome.INVALID;
        settle(r);
        return Outcome.CASHED;
    }

    private static void settle(Round r) {
        r.over = true;
        r.won = true;
        r.payout = r.pot;
    }

    /** Dress a card in a random species wearing a mood from the right family. */
    private static Card dress(boolean happy, Map<String, List<String>> catalog, Random rng) {
        List<String> family = happy ? HAPPY_EMOTIONS : SOUR_EMOTIONS;
        List<String> pool = new ArrayList<>();
        for (var e : catalog.entrySet()) {
            for (String emo : family) {
                if (e.getValue().contains(emo)) { pool.add(e.getKey() + ":" + emo); }
            }
        }
        if (pool.isEmpty()) return new Card("voltorb", happy ? "happy" : "angry", happy);
        String[] pick = pool.get(rng.nextInt(pool.size())).split(":");
        return new Card(pick[0], pick[1], happy);
    }
}
