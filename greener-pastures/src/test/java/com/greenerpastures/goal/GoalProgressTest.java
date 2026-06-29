package com.greenerpastures.goal;

import com.greenerpastures.biobank.EggSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the pure goal-progress fold. */
class GoalProgressTest {

    private static EggSummary egg(boolean shiny, int ivTotal, int perfect) {
        return new EggSummary("gible", shiny, ivTotal, perfect);
    }

    @Test
    void checkCountsCheckedAndMatched() {
        BreedingGoal g = new BreedingGoal("gible", Boolean.TRUE, 0, 0, 1);
        GoalProgress p = GoalProgress.START
                .check(g, egg(false, 90, 2))      // checked, not matched (not shiny)
                .check(g, egg(true, 100, 3));     // checked + matched
        assertEquals(2, p.checked());
        assertEquals(1, p.matched());
    }

    @Test
    void bestIvTotalTracksTheMax() {
        BreedingGoal g = new BreedingGoal(null, null, 0, 0, 1);
        GoalProgress p = GoalProgress.START
                .check(g, egg(false, 90, 1)).check(g, egg(false, 150, 4)).check(g, egg(false, 120, 3));
        assertEquals(150, p.bestIvTotal());
    }

    @Test
    void reachedWhenMatchesHitCount() {
        BreedingGoal g = new BreedingGoal(null, Boolean.TRUE, 0, 0, 2);
        GoalProgress p = GoalProgress.START;
        assertFalse(p.reached(g));
        p = p.check(g, egg(true, 0, 0));
        assertFalse(p.reached(g));
        assertEquals(1, p.remaining(g));
        p = p.check(g, egg(true, 0, 0));
        assertTrue(p.reached(g));
        assertEquals(0, p.remaining(g));
    }

    @Test
    void nullsAreNoOps() {
        BreedingGoal g = new BreedingGoal(null, null, 0, 0, 1);
        assertSame(GoalProgress.START, GoalProgress.START.check(g, null), "null egg → unchanged");
        assertSame(GoalProgress.START, GoalProgress.START.check(null, egg(true, 0, 0)), "null goal → unchanged");
    }
}
