package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** TOP DECK - deal validation, the pick rules, the 2x/6x/20x ladder, cashout timing. */
class TopDeckTest {

    private static List<String> pool(int n) {
        return IntStream.range(0, n).mapToObj(i -> "mon" + i).toList();
    }

    private static int[] hitting(TopDeck.Round r) {
        int[] picks = new int[TopDeck.PICKS];
        picks[0] = r.target;
        int k = 1;
        for (int i = 0; i < TopDeck.DECK && k < TopDeck.PICKS; i++) if (i != r.target) picks[k++] = i;
        return picks;
    }

    private static int[] missing(TopDeck.Round r) {
        int[] picks = new int[TopDeck.PICKS];
        int k = 0;
        for (int i = 0; i < TopDeck.DECK && k < TopDeck.PICKS; i++) if (i != r.target) picks[k++] = i;
        return picks;
    }

    @Test
    void dealsTwentyDistinctSpeciesAndGuardsTheWager() {
        TopDeck.Round r = TopDeck.deal(pool(30), 50, new Random(7));
        assertNotNull(r);
        assertEquals(TopDeck.DECK, r.species.size());
        assertEquals(TopDeck.DECK, r.species.stream().distinct().count());
        assertTrue(r.target >= 0 && r.target < TopDeck.DECK);
        assertNull(TopDeck.deal(pool(TopDeck.DECK - 1), 50, new Random(7)), "pool too small");
        assertNull(TopDeck.deal(pool(30), TopDeck.MIN_BET - 1, new Random(7)), "bet under the house minimum");
        assertNull(TopDeck.deal(pool(30), TopDeck.MAX_BET + 1, new Random(7)), "bet over the house maximum");
    }

    @Test
    void invalidPicksNeverTouchTheRound() {
        TopDeck.Round r = TopDeck.deal(pool(25), 20, new Random(1));
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.guess(r, null, new Random(2)));
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.guess(r, new int[]{1, 2, 3}, new Random(2)), "too few");
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.guess(r, new int[]{1, 2, 3, 4, 4}, new Random(2)), "duplicate");
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.guess(r, new int[]{1, 2, 3, 4, TopDeck.DECK}, new Random(2)), "out of range");
        assertFalse(r.over);
        assertEquals(1, r.stage);
    }

    @Test
    void missEndsTheRoundAndRevealsTheCard() {
        TopDeck.Round r = TopDeck.deal(pool(25), 20, new Random(3));
        int drawn = r.target;
        assertEquals(TopDeck.Outcome.MISS, TopDeck.guess(r, missing(r), new Random(4)));
        assertTrue(r.over);
        assertFalse(r.won);
        assertEquals(0, r.payout, "wager is gone");
        assertEquals(drawn, r.revealTarget, "the house shows its card");
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.guess(r, hitting(r), new Random(4)), "dead round takes no picks");
    }

    @Test
    void ladderClimbsAndAutoCashesAtTheTop() {
        TopDeck.Round r = TopDeck.deal(pool(25), 100, new Random(5));
        assertEquals(TopDeck.Outcome.HIT, TopDeck.guess(r, hitting(r), new Random(6)));
        assertEquals(2, r.stage);
        assertFalse(r.over);
        assertEquals(TopDeck.Outcome.HIT, TopDeck.guess(r, hitting(r), new Random(7)));
        assertEquals(3, r.stage);
        assertEquals(TopDeck.Outcome.HIT, TopDeck.guess(r, hitting(r), new Random(8)));
        assertTrue(r.over, "stage 3 hit auto-cashes");
        assertTrue(r.won);
        assertEquals(100 * 20, r.payout, "top rung pays 20x");
    }

    @Test
    void cashoutPaysTheCompletedRungOnly() {
        TopDeck.Round r = TopDeck.deal(pool(25), 40, new Random(9));
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.cashout(r), "nothing banked yet - no cashout");
        assertEquals(TopDeck.Outcome.HIT, TopDeck.guess(r, hitting(r), new Random(10)));
        assertEquals(TopDeck.Outcome.CASHED, TopDeck.cashout(r));
        assertTrue(r.over && r.won);
        assertEquals(40 * 2, r.payout, "one rung completed pays 2x");
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.cashout(r), "no double-dip");
    }

    @Test
    void secondRungCashoutPaysSix() {
        TopDeck.Round r = TopDeck.deal(pool(25), 40, new Random(11));
        assertEquals(TopDeck.Outcome.HIT, TopDeck.guess(r, hitting(r), new Random(12)));
        assertEquals(TopDeck.Outcome.HIT, TopDeck.guess(r, hitting(r), new Random(13)));
        assertEquals(TopDeck.Outcome.CASHED, TopDeck.cashout(r));
        assertEquals(40 * 6, r.payout, "two rungs completed pays 6x");
    }

    @Test
    void ladderConstantsStayHouseHonest() {
        assertEquals(TopDeck.MAX_STAGE, TopDeck.MULT.length);
        assertEquals(2, TopDeck.multiplierFor(1));
        assertEquals(6, TopDeck.multiplierFor(2));
        assertEquals(20, TopDeck.multiplierFor(3));
        for (int s = 1; s <= TopDeck.MAX_STAGE; s++) {
            double odds = Math.pow((double) TopDeck.PICKS / TopDeck.DECK, s);
            double ev = odds * TopDeck.multiplierFor(s);
            assertTrue(ev < 1.0, "rung " + s + " must favor the house (ev=" + ev + ")");
            assertTrue(ev > 0.25, "rung " + s + " must not be a scam (ev=" + ev + ")");
        }
    }
}
