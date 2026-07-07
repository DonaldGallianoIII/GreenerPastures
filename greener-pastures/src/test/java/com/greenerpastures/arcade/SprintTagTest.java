package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** QUICK CLAW - path dealing, the server-clock judge, spoof floors, escape edges. */
class SprintTagTest {

    private static final List<String> POOL = List.of("lechonk", "pawmi", "wooloo");

    @Test
    void dealsSanePathsFromThePool() {
        Random rng = new Random(4);
        for (int i = 0; i < 100; i++) {
            SprintTag.Round r = SprintTag.deal(POOL, rng);
            assertTrue(POOL.contains(r.species));
            assertTrue(r.yStartPct >= 18 && r.yStartPct <= 88);
            assertTrue(r.yEndPct >= 18 && r.yEndPct <= 88);
            assertTrue(r.spawnDelayMs >= 900 && r.spawnDelayMs < 2700);
            assertTrue(r.crossMs >= 2400 && r.crossMs < 4300);
        }
        assertNull(SprintTag.deal(List.of(), rng));
    }

    @Test
    void fastTagsPayMoreAndTheCurveIsClamped() {
        SprintTag.Round r = new SprintTag.Round("lechonk", true, 40, 50, 1000, 3000);
        long fast = SprintTag.judge(r, 1000 + SprintTag.REACTION_FLOOR_MS + 50);
        assertTrue(fast >= SprintTag.PAY_MAX - 2, "near-instant tag pays near max: " + fast);
        SprintTag.Round r2 = new SprintTag.Round("lechonk", true, 40, 50, 1000, 3000);
        long slow = SprintTag.judge(r2, 1000 + 2900);
        assertTrue(slow <= SprintTag.PAY_MIN + 1, "buzzer-beater pays near min: " + slow);
        assertTrue(fast > slow);
    }

    @Test
    void precognitionAndEscapesPayZero() {
        SprintTag.Round early = new SprintTag.Round("pawmi", true, 40, 50, 1500, 3000);
        assertEquals(0, SprintTag.judge(early, 1500 + SprintTag.REACTION_FLOOR_MS - 1),
                "clicking before human-possible = suspicious = zero");
        assertFalse(early.escaped);
        SprintTag.Round late = new SprintTag.Round("pawmi", true, 40, 50, 1500, 3000);
        assertEquals(0, SprintTag.judge(late, 1500 + 3000 + SprintTag.EXIT_GRACE_MS + 1));
        assertTrue(late.escaped, "past the far edge = she got away");
    }

    @Test
    void oneJudgementPerRoundAndForfeitEscapes() {
        SprintTag.Round r = new SprintTag.Round("wooloo", false, 30, 60, 1000, 3000);
        assertTrue(SprintTag.judge(r, 2000) > 0);
        assertEquals(-1, SprintTag.judge(r, 2000), "a settled round takes no second click");
        SprintTag.Round f = new SprintTag.Round("wooloo", false, 30, 60, 1000, 3000);
        SprintTag.forfeit(f);
        assertTrue(f.over && f.escaped);
        assertEquals(0, f.paid);
        SprintTag.forfeit(f);   // idempotent
        assertEquals(0, f.paid);
    }
}
