package com.greenerpastures.goal;

import com.greenerpastures.biobank.EggSummary;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the per-player goal store (each test uses a fresh UUID to isolate the static maps). */
class GoalStoreTest {

    @Test
    void setThenTrackToReached() {
        UUID u = UUID.randomUUID();
        GoalStore.set(u, new BreedingGoal("gible", Boolean.TRUE, 0, 0, 1));
        assertNotNull(GoalStore.goalOf(u));
        assertEquals(0, GoalStore.progressOf(u).checked());
        GoalStore.recordEgg(u, new EggSummary("gible", false, 90, 2));    // checked, not matched
        GoalStore.recordEgg(u, new EggSummary("gible", true, 100, 3));    // matched
        assertEquals(2, GoalStore.progressOf(u).checked());
        assertEquals(1, GoalStore.progressOf(u).matched());
        assertTrue(GoalStore.progressOf(u).reached(GoalStore.goalOf(u)));
        GoalStore.clear(u);
    }

    @Test
    void settingANewGoalResetsProgress() {
        UUID u = UUID.randomUUID();
        GoalStore.set(u, new BreedingGoal(null, null, 0, 0, 1));
        GoalStore.recordEgg(u, new EggSummary("x", false, 0, 0));
        assertEquals(1, GoalStore.progressOf(u).checked());
        GoalStore.set(u, new BreedingGoal("ditto", null, 0, 0, 1));
        assertEquals(0, GoalStore.progressOf(u).checked(), "a fresh goal zeroes progress");
        GoalStore.clear(u);
    }

    @Test
    void recordingWithNoGoalIsANoOp() {
        UUID u = UUID.randomUUID();
        assertSame(GoalProgress.START, GoalStore.recordEgg(u, new EggSummary("x", true, 0, 0)));
        assertNull(GoalStore.goalOf(u));
    }

    @Test
    void clearRemovesGoalAndProgress() {
        UUID u = UUID.randomUUID();
        GoalStore.set(u, new BreedingGoal(null, null, 0, 0, 1));
        GoalStore.clear(u);
        assertNull(GoalStore.goalOf(u));
        assertEquals(GoalProgress.START, GoalStore.progressOf(u));
    }
}
