package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** VIBE CHECK - the free deck: doubling pot, sour torch, auto-cash on the last happy card. */
class VibeCheckTest {

    private static Map<String, List<String>> catalog() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("upmon", List.of("happy", "joyous", "normal"));
        m.put("downmon", List.of("sad", "angry", "crying"));
        m.put("bothmon", List.of("happy", "sad", "pain"));
        return m;
    }

    private static VibeCheck.Outcome drawOne(VibeCheck.Round r) {
        return VibeCheck.draw(r, catalog(), new Random(9));
    }

    @Test
    void deckHoldsExactlyTheAdvertisedMix() {
        for (long seed = 0; seed < 50; seed++) {
            VibeCheck.Round r = VibeCheck.deal(new Random(seed));
            int happy = 0;
            while (!r.over) {
                if (drawOne(r) == VibeCheck.Outcome.HAPPY) happy++;
            }
            int sour = (int) r.drawn.stream().filter(c -> !c.happy()).count();
            assertTrue(sour <= VibeCheck.SOUR);
            if (!r.won) assertEquals(1, sour, "a loss ends on the FIRST sour card");
            if (r.won) assertEquals(VibeCheck.DECK_SIZE - VibeCheck.SOUR, happy, "a full clear drew every happy card");
        }
    }

    @Test
    void potDoublesAndSourTorchesIt() {
        // find a seed whose first three cards are happy
        for (long seed = 0; ; seed++) {
            VibeCheck.Round r = VibeCheck.deal(new Random(seed));
            if (drawOne(r) != VibeCheck.Outcome.HAPPY) continue;
            assertEquals(1, r.pot);
            if (drawOne(r) != VibeCheck.Outcome.HAPPY) continue;
            assertEquals(2, r.pot);
            if (drawOne(r) != VibeCheck.Outcome.HAPPY) continue;
            assertEquals(4, r.pot);
            break;
        }
        // and a sour start torches instantly
        for (long seed = 0; ; seed++) {
            VibeCheck.Round r = VibeCheck.deal(new Random(seed));
            if (drawOne(r) == VibeCheck.Outcome.SOUR) {
                assertTrue(r.over);
                assertFalse(r.won);
                assertEquals(0, r.pot);
                assertEquals(0, r.payout);
                assertEquals(VibeCheck.Outcome.INVALID, drawOne(r), "dead round draws nothing");
                break;
            }
        }
    }

    @Test
    void cashoutBanksBetweenDrawsOnly() {
        for (long seed = 0; ; seed++) {
            VibeCheck.Round r = VibeCheck.deal(new Random(seed));
            assertEquals(VibeCheck.Outcome.INVALID, VibeCheck.cashout(r), "empty pot can't cash");
            if (drawOne(r) != VibeCheck.Outcome.HAPPY) continue;
            if (drawOne(r) != VibeCheck.Outcome.HAPPY) continue;
            assertEquals(VibeCheck.Outcome.CASHED, VibeCheck.cashout(r));
            assertTrue(r.over && r.won);
            assertEquals(2, r.payout);
            assertEquals(VibeCheck.Outcome.INVALID, VibeCheck.cashout(r), "no double-dip");
            break;
        }
    }

    @Test
    void clearingEveryHappyCardAutoCashesTheMaxPot() {
        long maxPot = 1L << (VibeCheck.DECK_SIZE - VibeCheck.SOUR - 1);
        boolean sawClear = false;
        for (long seed = 0; seed < 4000 && !sawClear; seed++) {
            VibeCheck.Round r = VibeCheck.deal(new Random(seed));
            while (!r.over) drawOne(r);
            if (r.won && r.drawn.stream().allMatch(VibeCheck.Card::happy)) {
                assertEquals(maxPot, r.payout, "8 straight happy = 128");
                assertEquals(VibeCheck.DECK_SIZE - VibeCheck.SOUR, r.drawn.size(), "auto-cash fires before a doomed draw");
                sawClear = true;
            }
        }
        assertTrue(sawClear, "no full clear in 4000 seeds - odds are 1/495, something is rigged");
    }

    @Test
    void cardsWearTheRightMoodFamily() {
        for (long seed = 0; seed < 30; seed++) {
            VibeCheck.Round r = VibeCheck.deal(new Random(seed));
            while (!r.over) drawOne(r);
            for (VibeCheck.Card c : r.drawn) {
                List<String> family = c.happy() ? VibeCheck.HAPPY_EMOTIONS : VibeCheck.SOUR_EMOTIONS;
                assertTrue(family.contains(c.emotion()), c.species() + " wore " + c.emotion());
                assertNotEquals("downmon", c.happy() ? c.species() : "", "downmon has no happy face to wear");
            }
        }
    }
}
