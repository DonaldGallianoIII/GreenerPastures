package com.greenerpastures.goal;

import com.greenerpastures.biobank.EggSummary;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-player store of the active {@link BreedingGoal} and its running {@link GoalProgress}. v1 lives only
 * for the server session — a hunt is set, tracked, and cleared within a run; persistence across restarts is a
 * follow-on (a {@code PersistentState} twin of {@code DataStore}). MC-free (so it's unit-tested); server-thread
 * access in practice, but a ConcurrentHashMap keeps it safe regardless.
 */
public final class GoalStore {
    private GoalStore() {}

    private static final Map<UUID, BreedingGoal> goals = new ConcurrentHashMap<>();
    private static final Map<UUID, GoalProgress> progress = new ConcurrentHashMap<>();

    /** Set (or replace) a player's goal — resets progress to zero. */
    public static void set(UUID player, BreedingGoal goal) {
        if (player == null || goal == null) return;
        goals.put(player, goal);
        progress.put(player, GoalProgress.START);
    }

    public static void clear(UUID player) {
        if (player == null) return;
        goals.remove(player);
        progress.remove(player);
    }

    /** Wipe every player's goal — called on SERVER_STARTED so a new world (same JVM in singleplayer) never
     *  inherits the previous world's hunts. */
    public static void clearAll() {
        goals.clear();
        progress.clear();
    }

    public static BreedingGoal goalOf(UUID player) {
        return player == null ? null : goals.get(player);
    }

    public static GoalProgress progressOf(UUID player) {
        return player == null ? GoalProgress.START : progress.getOrDefault(player, GoalProgress.START);
    }

    /** Fold one egg into a player's progress (no-op if they have no goal); returns the resulting progress. */
    public static GoalProgress recordEgg(UUID player, EggSummary egg) {
        BreedingGoal g = goalOf(player);
        if (g == null) return GoalProgress.START;
        GoalProgress p = progressOf(player).check(g, egg);
        progress.put(player, p);
        return p;
    }
}
