package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** TOP DECK v2 - flip-the-survivor rules, the 2x/6x/20x ladder, cashout timing, and Mercy. */
class TopDeckTest {

    private static Map<String, List<String>> catalog(int n) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) m.put("mon" + (char) ('a' + i / 10) + (i % 10), List.of("normal", "happy", "sad", "angry"));
        return m;
    }

    private static TopDeck.Outcome flipSurvivor(TopDeck.Round r, Map<String, List<String>> c, Random rng) {
        return TopDeck.flip(r, r.target, c, rng);
    }

    private static TopDeck.Outcome flipStranger(TopDeck.Round r, Map<String, List<String>> c, Random rng) {
        for (int i = 0; i < TopDeck.DECK; i++) {
            if (i != r.target && !r.flipped[i]) return TopDeck.flip(r, i, c, rng);
        }
        return TopDeck.Outcome.INVALID;
    }

    @Test
    void dealsTwentyDistinctDressedCardsAndGuardsTheWager() {
        var c = catalog(30);
        TopDeck.Round r = TopDeck.deal(c, 50, new Random(7));
        assertNotNull(r);
        assertEquals(TopDeck.DECK, r.species.size());
        assertEquals(TopDeck.DECK, r.species.stream().distinct().count());
        assertEquals(TopDeck.DECK, r.ogEmotions.size());
        for (int i = 0; i < TopDeck.DECK; i++) {
            assertTrue(c.get(r.species.get(i)).contains(r.ogEmotions.get(i)), "og emotion must be from the species' set");
        }
        assertEquals(TopDeck.FLIPS, r.flipsLeft);
        assertNull(TopDeck.deal(catalog(TopDeck.DECK), 50, new Random(7)), "needs a stranger left over");
        assertNull(TopDeck.deal(c, TopDeck.MIN_BET - 1, new Random(7)));
        assertNull(TopDeck.deal(c, TopDeck.MAX_BET + 1, new Random(7)));
    }

    @Test
    void thinEmotionSpeciesNeverDeal() {
        Map<String, List<String>> c = catalog(25);
        c.put("flatmon", List.of("normal"));               // < MIN_EMOTIONS
        for (long seed = 0; seed < 30; seed++) {
            TopDeck.Round r = TopDeck.deal(c, 20, new Random(seed));
            assertNotNull(r);
            assertFalse(r.species.contains("flatmon"), "seed " + seed + " dealt a mercy-proof species");
        }
    }

    @Test
    void strangersBurnFlipsAndTheFifthLosesWithMercy() {
        var c = catalog(30);
        TopDeck.Round r = TopDeck.deal(c, 20, new Random(3));
        Random rng = new Random(4);
        for (int k = 1; k <= TopDeck.FLIPS - 1; k++) {
            assertEquals(TopDeck.Outcome.STRANGER, flipStranger(r, c, rng));
            assertEquals(TopDeck.FLIPS - k, r.flipsLeft);
        }
        assertEquals(TopDeck.Outcome.LOST, flipStranger(r, c, rng));
        assertTrue(r.over);
        assertFalse(r.won);
        assertTrue(r.mercyAvailable);
        assertEquals(r.target, r.revealTarget, "the house shows the survivor");
        for (int i = 0; i < TopDeck.DECK; i++) {
            if (r.flipped[i] && i != r.target) {
                assertNotNull(r.strangerSpecies[i]);
                assertFalse(r.species.contains(r.strangerSpecies[i]), "strangers are never og species");
            }
        }
        assertEquals(TopDeck.Outcome.INVALID, flipStranger(r, c, rng), "dead round takes no flips");
    }

    @Test
    void invalidFlipsNeverTouchTheRound() {
        var c = catalog(25);
        TopDeck.Round r = TopDeck.deal(c, 20, new Random(1));
        Random rng = new Random(2);
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.flip(r, -1, c, rng));
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.flip(r, TopDeck.DECK, c, rng));
        assertEquals(TopDeck.Outcome.STRANGER, flipStranger(r, c, rng));
        int burnt = firstFlipped(r);
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.flip(r, burnt, c, rng), "same card twice");
        assertEquals(TopDeck.FLIPS - 1, r.flipsLeft);
    }

    private static int firstFlipped(TopDeck.Round r) {
        for (int i = 0; i < TopDeck.DECK; i++) if (r.flipped[i]) return i;
        return -1;
    }

    @Test
    void ladderClimbsResetsTheTableAndAutoCashesAtTheTop() {
        var c = catalog(30);
        TopDeck.Round r = TopDeck.deal(c, 100, new Random(5));
        Random rng = new Random(6);
        assertEquals(TopDeck.Outcome.STRANGER, flipStranger(r, c, rng));
        assertEquals(TopDeck.Outcome.HIT, flipSurvivor(r, c, rng));
        assertEquals(2, r.stage);
        assertFalse(r.over);
        assertEquals(TopDeck.FLIPS, r.flipsLeft, "fresh rung, fresh flips");
        for (boolean f : r.flipped) assertFalse(f, "fresh rung, fresh table");
        assertEquals(TopDeck.Outcome.HIT, flipSurvivor(r, c, rng));
        assertEquals(3, r.stage);
        assertEquals(TopDeck.Outcome.HIT, flipSurvivor(r, c, rng));
        assertTrue(r.over, "top rung auto-cashes");
        assertTrue(r.won);
        assertEquals(100 * 20, r.payout);
    }

    @Test
    void cashoutPaysTheCompletedRungOnly() {
        var c = catalog(25);
        TopDeck.Round r = TopDeck.deal(c, 40, new Random(9));
        Random rng = new Random(10);
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.cashout(r), "nothing banked yet");
        assertEquals(TopDeck.Outcome.HIT, flipSurvivor(r, c, rng));
        assertEquals(TopDeck.Outcome.CASHED, TopDeck.cashout(r));
        assertEquals(40 * 2, r.payout, "one rung = 2x");
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.cashout(r), "no double-dip");
        TopDeck.Round r2 = TopDeck.deal(c, 40, new Random(11));
        assertEquals(TopDeck.Outcome.HIT, flipSurvivor(r2, c, rng));
        assertEquals(TopDeck.Outcome.HIT, flipSurvivor(r2, c, rng));
        assertEquals(TopDeck.Outcome.CASHED, TopDeck.cashout(r2));
        assertEquals(40 * 6, r2.payout, "two rungs = 6x");
    }

    @Test
    void mercyIsOneFreeMemoryCheckThatOnlyRefunds() {
        var c = catalog(30);
        Random rng = new Random(13);
        TopDeck.Round r = TopDeck.deal(c, 60, new Random(12));
        for (int k = 0; k < TopDeck.FLIPS; k++) flipStranger(r, c, rng);
        assertTrue(r.over && r.mercyAvailable);
        List<String> options = TopDeck.mercyStart(r, c, rng);
        assertNotNull(options);
        String og = r.ogEmotions.get(r.mercyIndex);
        assertTrue(options.contains(og), "the true answer is always on the table");
        assertNull(TopDeck.mercyStart(r, c, rng), "mercy starts once");
        assertEquals(TopDeck.Outcome.MERCY_WON, TopDeck.mercyPick(r, og));
        assertTrue(r.mercyWon);
        assertEquals(0, r.payout, "mercy never pays winnings - the caller refunds the wager");
        assertEquals(TopDeck.Outcome.INVALID, TopDeck.mercyPick(r, og), "one answer only");
    }

    @Test
    void mercyWrongAnswerAndWrongTimingRefuse() {
        var c = catalog(30);
        Random rng = new Random(15);
        TopDeck.Round live = TopDeck.deal(c, 60, new Random(14));
        assertNull(TopDeck.mercyStart(live, c, rng), "no mercy while the round lives");
        flipSurvivor(live, c, rng);
        TopDeck.cashout(live);
        assertNull(TopDeck.mercyStart(live, c, rng), "no mercy for winners");
        TopDeck.Round lost = TopDeck.deal(c, 60, new Random(16));
        for (int k = 0; k < TopDeck.FLIPS; k++) flipStranger(lost, c, rng);
        TopDeck.mercyStart(lost, c, rng);
        String wrong = lost.mercyOptions.stream()
                .filter(e -> !e.equals(lost.ogEmotions.get(lost.mercyIndex))).findFirst().orElseThrow();
        assertEquals(TopDeck.Outcome.MERCY_LOST, TopDeck.mercyPick(lost, wrong));
        assertFalse(lost.mercyWon);
    }

    @Test
    void ladderConstantsStayHouseHonest() {
        assertEquals(TopDeck.MAX_STAGE, TopDeck.MULT.length);
        assertEquals(2, TopDeck.multiplierFor(1));
        assertEquals(6, TopDeck.multiplierFor(2));
        assertEquals(20, TopDeck.multiplierFor(3));
        for (int s = 1; s <= TopDeck.MAX_STAGE; s++) {
            double odds = Math.pow((double) TopDeck.FLIPS / TopDeck.DECK, s);
            double ev = odds * TopDeck.multiplierFor(s);
            assertTrue(ev < 1.0, "rung " + s + " must favor the house (ev=" + ev + ")");
            assertTrue(ev > 0.25, "rung " + s + " must not be a scam (ev=" + ev + ")");
        }
    }
}
