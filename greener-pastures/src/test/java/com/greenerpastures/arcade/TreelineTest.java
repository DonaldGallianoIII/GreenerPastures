package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Cabinet #2 - round law, sweep budget, snitch arrows, payout ladder. */
class TreelineTest {

    @Test
    void everyRoundHidesOneTargetAndThreeDecoysInDistinctTrees() {
        for (int seed = 0; seed < 50; seed++) {
            Treeline.Round r = Treeline.generate(new Random(seed));
            long targets = r.trees.stream().filter(t -> "target".equals(t.contains)).count();
            long decoys = r.trees.stream().filter(t -> t.contains != null && t.contains.startsWith("decoy")).count();
            assertEquals(1, targets, "seed " + seed);
            assertEquals(Treeline.DECOYS, decoys, "seed " + seed);
            assertEquals(26, r.trees.size(), "the 4-row grid deals 26 trees");
            assertEquals(Treeline.DECOYS + 1, r.critters.size());
            assertTrue(r.critters.get(0).isTarget());
        }
    }

    @Test
    void findingHerPaysBySweepsLeftFirstSweepMax() {
        Treeline.Round r = Treeline.generate(new Random(7));
        Treeline.Sweep s = Treeline.search(r, r.targetTreeId);
        assertEquals(Treeline.Outcome.FOUND, s.outcome());
        assertEquals(Treeline.PAY_PER_SWEEP * Treeline.CLICK_BUDGET, s.payout(), "first-sweep find = max pot 600");
        assertTrue(r.over && r.won);
    }

    @Test
    void decoysSnitchWithAnArrowThatActuallyPointsAtHer() {
        Treeline.Round r = Treeline.generate(new Random(3));
        Treeline.Tree decoy = r.trees.stream().filter(t -> t.contains != null && t.contains.startsWith("decoy")).findFirst().orElseThrow();
        Treeline.Sweep s = Treeline.search(r, decoy.id);
        assertEquals(Treeline.Outcome.DECOY, s.outcome());
        assertNotNull(s.arrow());
        Treeline.Tree target = r.tree(r.targetTreeId);
        assertEquals(Treeline.arrowToward(target.x - decoy.x, target.y - decoy.y), s.arrow());
    }

    @Test
    void arrowSnapsEightWays() {
        assertEquals("→", Treeline.arrowToward(10, 0));
        assertEquals("↓", Treeline.arrowToward(0, 10));    // screen-space: +y is down
        assertEquals("↑", Treeline.arrowToward(0, -10));
        assertEquals("↘", Treeline.arrowToward(7, 7));
        assertEquals("↖", Treeline.arrowToward(-7, -7));
    }

    @Test
    void budgetRunsOutHonestlyAndTheRoundLocks() {
        Treeline.Round r = Treeline.generate(new Random(11));
        int swept = 0;
        Treeline.Sweep last = null;
        for (Treeline.Tree t : r.trees) {
            if (t.id == r.targetTreeId) continue;   // deliberately never find her
            last = Treeline.search(r, t.id);
            swept++;
            if (last.outcome() == Treeline.Outcome.LOST) break;
        }
        assertEquals(Treeline.CLICK_BUDGET, swept, "exactly 10 sweeps then done");
        assertEquals(Treeline.Outcome.LOST, last.outcome());
        assertEquals(0, last.payout());
        assertTrue(r.over && !r.won);
        assertEquals(Treeline.Outcome.INVALID, Treeline.search(r, r.targetTreeId).outcome(), "locked rounds refuse sweeps");
    }

    @Test
    void reSweepingATreeCostsNothingAndDoesNothing() {
        Treeline.Round r = Treeline.generate(new Random(5));
        Treeline.Tree plain = r.trees.stream().filter(t -> t.contains == null).findFirst().orElseThrow();
        Treeline.search(r, plain.id);
        int left = r.clicksLeft;
        assertEquals(Treeline.Outcome.INVALID, Treeline.search(r, plain.id).outcome());
        assertEquals(left, r.clicksLeft, "no double-spend on a searched tree");
    }

    @Test
    void generationIsDeterministicPerSeed() {
        Treeline.Round a = Treeline.generate(new Random(42));
        Treeline.Round b = Treeline.generate(new Random(42));
        assertEquals(a.targetTreeId, b.targetTreeId);
        for (int i = 0; i < a.trees.size(); i++) {
            assertEquals(a.trees.get(i).x, b.trees.get(i).x, 1e-12);
            assertEquals(a.trees.get(i).contains, b.trees.get(i).contains);
        }
    }
}
