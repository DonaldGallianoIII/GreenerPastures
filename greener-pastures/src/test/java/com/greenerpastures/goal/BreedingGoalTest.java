package com.greenerpastures.goal;

import com.greenerpastures.biobank.EggSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the pure breeding-goal matcher. */
class BreedingGoalTest {

    private static EggSummary egg(String sp, boolean shiny, int ivTotal, int perfect) {
        return new EggSummary(sp, shiny, ivTotal, perfect);
    }

    @Test
    void emptyGoalMatchesAnything() {
        BreedingGoal g = new BreedingGoal(null, null, 0, 0, 1);
        assertTrue(g.matches(egg("gible", false, 90, 1)));
        assertTrue(g.matches(egg("ditto", true, 186, 6)));
    }

    @Test
    void speciesIsCaseInsensitiveAndExact() {
        BreedingGoal g = new BreedingGoal("Gible", null, 0, 0, 1);
        assertTrue(g.matches(egg("gible", false, 0, 0)));
        assertTrue(g.matches(egg("GIBLE", true, 0, 0)));
        assertFalse(g.matches(egg("gabite", false, 186, 6)));
    }

    @Test
    void shinyIsTriState() {
        assertTrue(new BreedingGoal(null, Boolean.TRUE, 0, 0, 1).matches(egg("x", true, 0, 0)));
        assertFalse(new BreedingGoal(null, Boolean.TRUE, 0, 0, 1).matches(egg("x", false, 0, 0)));
        assertTrue(new BreedingGoal(null, Boolean.FALSE, 0, 0, 1).matches(egg("x", false, 0, 0)));
        assertFalse(new BreedingGoal(null, Boolean.FALSE, 0, 0, 1).matches(egg("x", true, 0, 0)));
        assertTrue(new BreedingGoal(null, null, 0, 0, 1).matches(egg("x", true, 0, 0)), "null = either");
    }

    @Test
    void ivFloorsAreMinimums() {
        BreedingGoal g = new BreedingGoal(null, null, 4, 120, 1);
        assertTrue(g.matches(egg("x", false, 120, 4)));
        assertTrue(g.matches(egg("x", false, 150, 5)));
        assertFalse(g.matches(egg("x", false, 119, 4)), "below IV total");
        assertFalse(g.matches(egg("x", false, 186, 3)), "below perfect count");
    }

    @Test
    void compactCtorClampsAndNormalizes() {
        BreedingGoal g = new BreedingGoal("  GIBLE  ", null, 9, -5, 0);
        assertEquals("gible", g.species(), "trimmed + lowercased");
        assertEquals(6, g.minPerfectIvs(), "clamped to 6");
        assertEquals(0, g.minIvTotal(), "negative → 0");
        assertEquals(1, g.count(), "count floored to 1");
        assertNull(new BreedingGoal("   ", null, 0, 0, 1).species(), "blank species → any");
    }

    @Test
    void describeReadsBackTheCriteria() {
        assertEquals("any species", new BreedingGoal(null, null, 0, 0, 1).describe());
        String d = new BreedingGoal("garchomp", Boolean.TRUE, 6, 0, 3).describe();
        assertTrue(d.contains("shiny") && d.contains("garchomp") && d.contains("6") && d.contains("×3"), d);
    }

    @Test
    void nullEggNeverMatches() {
        assertFalse(new BreedingGoal(null, null, 0, 0, 1).matches(null));
    }
}
