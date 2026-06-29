package com.greenerpastures.goal;

import com.greenerpastures.biobank.EggSummary;

/**
 * Live progress toward a {@link BreedingGoal}: how many eggs have been checked, how many matched, and the best
 * IV-total seen so far (a "closest yet" hint). Immutable — each checked egg returns a new snapshot — so it's
 * trivially unit-tested and safe to recompute. The goal is {@link #reached} once matches hit the goal's count.
 */
public record GoalProgress(int checked, int matched, int bestIvTotal) {

    public static final GoalProgress START = new GoalProgress(0, 0, 0);

    /** Fold one egg into the progress, measured against {@code goal}; returns a new snapshot. */
    public GoalProgress check(BreedingGoal goal, EggSummary egg) {
        if (goal == null || egg == null) return this;
        boolean hit = goal.matches(egg);
        return new GoalProgress(checked + 1, matched + (hit ? 1 : 0), Math.max(bestIvTotal, egg.ivTotal()));
    }

    public boolean reached(BreedingGoal goal) {
        return goal != null && matched >= goal.count();
    }

    /** Matching eggs still wanted (0 once reached). */
    public int remaining(BreedingGoal goal) {
        return goal == null ? 0 : Math.max(0, goal.count() - matched);
    }
}
