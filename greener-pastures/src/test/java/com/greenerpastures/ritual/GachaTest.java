package com.greenerpastures.ritual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the gacha pull/pity math — odds and the pity guarantee, exactly. */
class GachaTest {

    @Test
    void emptyBankIsANoOpMiss() {
        Gacha.Roll r = Gacha.pull(Gacha.PullState.EMPTY, 100.0, 10, 0, 0.0);
        assertFalse(r.hit());
        assertEquals(0, r.state().bankedPulls());
    }

    @Test
    void flatChanceHitsStrictlyBelow() {
        Gacha.PullState s = Gacha.PullState.EMPTY.bank(2);
        assertTrue(Gacha.pull(s, 50.0, 100, 0, 0.49).hit(), "0.49 < 0.50");
        assertFalse(Gacha.pull(s, 50.0, 100, 0, 0.50).hit(), "0.50 is not < 0.50");
    }

    @Test
    void aHitSpendsAPullAndResetsPity() {
        Gacha.Roll r = Gacha.pull(new Gacha.PullState(3, 7), 100.0, 50, 0, 0.0);
        assertTrue(r.hit());
        assertEquals(2, r.state().bankedPulls());
        assertEquals(0, r.state().pity(), "pity resets on a hit");
    }

    @Test
    void aMissSpendsAPullAndIncrementsPity() {
        Gacha.Roll r = Gacha.pull(new Gacha.PullState(3, 7), 0.0, 50, 0, 0.99);
        assertFalse(r.hit());
        assertEquals(2, r.state().bankedPulls());
        assertEquals(8, r.state().pity());
    }

    @Test
    void hardPityGuaranteesAHitByN() {
        // base 0% (never hits by chance), hard pity 10 → the 10th pull MUST hit
        Gacha.PullState s = Gacha.PullState.EMPTY.bank(10);
        int hits = 0, lastHitPull = -1;
        for (int i = 1; i <= 10; i++) {
            Gacha.Roll r = Gacha.pull(s, 0.0, 10, 0, 0.999);
            if (r.hit()) { hits++; lastHitPull = i; }
            s = r.state();
        }
        assertEquals(1, hits, "exactly one forced hit");
        assertEquals(10, lastHitPull, "lands on the 10th pull");
        assertEquals(0, s.pity(), "pity reset after the forced hit");
    }

    @Test
    void softPityRampsBaseToHundred() {
        assertEquals(10.0, Gacha.effectiveChance(10, 10, 5, 4), 1e-9, "before soft start = flat base");
        assertEquals(10.0, Gacha.effectiveChance(10, 10, 5, 5), 1e-9, "at soft start = base");
        assertEquals(46.0, Gacha.effectiveChance(10, 10, 5, 7), 1e-9, "10 + 90*(2/5)");
        assertEquals(100.0, Gacha.effectiveChance(10, 10, 5, 10), 1e-9, "hard pity = 100");
    }

    @Test
    void pullAllSpendsEveryBankedPull() {
        Gacha.PullState s = Gacha.PullState.EMPTY.bank(10);
        // base 0, hard pity 5 → a forced hit every 5 pulls → 10 pulls = 2 hits, bank emptied
        Gacha.Session sess = Gacha.pullAll(s, 0.0, 5, 0, () -> 0.999);
        assertEquals(0, sess.state().bankedPulls(), "all spent");
        assertEquals(2, sess.hits(), "two forced hits across 10 pulls at pity 5");
    }
}
