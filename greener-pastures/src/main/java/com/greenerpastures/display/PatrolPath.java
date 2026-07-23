package com.greenerpastures.display;

import java.util.ArrayList;
import java.util.List;

/**
 * The MC-free heart of Phase B patrol pathing (Display Suite v2 §3). Holds a resident's authored
 * waypoints + timing, and is the pure state machine the MC goal asks "where do I walk next?" every tick.
 * The goal owns only the entity + distance check; ALL sequencing (which waypoint, dwell countdown,
 * loop vs ping-pong reversal) lives here so it is fully headless-testable - the {@link StatueTransform}
 * lesson applied to motion.
 *
 * <p>Contract: the goal moves the projection toward {@link #target(Progress)}, decides each tick whether
 * it has {@code atTarget} (arrival radius is an MC concern), and feeds that into {@link #tick} to get the
 * next {@link Progress}. A path with no waypoints is inert - the goal wanders instead (spec §3 fallback).
 *
 * <p>Immutable + sanitizing like the rest of the suite: the canonical constructor drops null waypoints,
 * caps the list, and clamps timing, so the goal can trust any instance decoded from NBT.
 */
public record PatrolPath(List<RelPos> waypoints, boolean pingPong, int dwellTicks, double speed) {

    /** Enough for a room-sized loop; bounded so a hostile NBT list can't blow up per-tick work or save size. */
    public static final int MAX_WAYPOINTS = 16;
    /** 5 minutes - a greeter can pause a good while, but not forever. */
    public static final int MAX_DWELL_TICKS = 20 * 300;
    public static final double MIN_SPEED = 0.10;
    public static final double MAX_SPEED = 1.00;
    public static final double DEFAULT_SPEED = 0.35;

    /** An empty, inert path - the resident wanders until waypoints are authored. */
    public static final PatrolPath EMPTY = new PatrolPath(List.of(), false, 0, DEFAULT_SPEED);

    public PatrolPath {
        List<RelPos> cleaned = new ArrayList<>();
        if (waypoints != null) {
            for (RelPos p : waypoints) {
                if (p == null) continue;
                cleaned.add(p);
                if (cleaned.size() >= MAX_WAYPOINTS) break;
            }
        }
        waypoints = List.copyOf(cleaned);
        dwellTicks = Math.max(0, Math.min(MAX_DWELL_TICKS, dwellTicks));
        speed = Double.isNaN(speed) ? DEFAULT_SPEED : Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    public boolean isEmpty() {
        return waypoints.isEmpty();
    }

    public int size() {
        return waypoints.size();
    }

    /** Traveling toward a waypoint, or standing at it counting down the dwell. */
    public enum Phase { TRAVELING, DWELLING }

    /**
     * Where a resident is along its path RIGHT NOW - the transient cursor the MC goal carries between
     * ticks (never persisted; a re-projection restarts at {@link #start()}, spec §3 "path is config").
     * {@code dir} is the ping-pong travel direction (+1 forward, -1 back); ignored in loop mode.
     */
    public record Progress(int index, int dir, Phase phase, int dwellRemaining) {
        public Progress {
            dir = dir >= 0 ? 1 : -1;
            phase = phase == null ? Phase.TRAVELING : phase;
            dwellRemaining = Math.max(0, dwellRemaining);
        }
    }

    /** Fresh cursor: heading to the first waypoint, forward, no dwell pending. */
    public Progress start() {
        return new Progress(0, 1, Phase.TRAVELING, 0);
    }

    /** The waypoint the goal should currently walk toward. Never throws - a stale index wraps into range;
     *  an empty path returns null (the goal wanders). */
    public RelPos target(Progress p) {
        int n = waypoints.size();
        if (n == 0) return null;
        return waypoints.get(Math.floorMod(p.index(), n));
    }

    /**
     * Advance one tick. {@code atTarget} is the goal's arrival check for the CURRENT target. Returns the
     * next cursor: still traveling if not yet arrived; begins/continues the dwell on arrival; steps to the
     * next waypoint (loop or ping-pong) once the dwell elapses. A dwell of 0 steps on immediately.
     */
    public Progress tick(Progress p, boolean atTarget) {
        int n = waypoints.size();
        if (n == 0) return p;                       // inert - the goal wanders, we hold state
        int idx = Math.floorMod(p.index(), n);
        if (n == 1) {                               // one waypoint = a fixed spot; sit on it
            return new Progress(0, 1, Phase.DWELLING, 0);
        }

        return switch (p.phase()) {
            case TRAVELING -> {
                if (!atTarget) yield new Progress(idx, p.dir(), Phase.TRAVELING, 0);
                if (dwellTicks <= 0) yield step(idx, p.dir(), n);   // no pause - straight to the next
                yield new Progress(idx, p.dir(), Phase.DWELLING, dwellTicks);
            }
            case DWELLING -> {
                int rem = p.dwellRemaining() - 1;
                if (rem > 0) yield new Progress(idx, p.dir(), Phase.DWELLING, rem);
                yield step(idx, p.dir(), n);
            }
        };
    }

    /** Move the cursor to the next waypoint, reversing at the ends in ping-pong mode. Always TRAVELING. */
    private Progress step(int idx, int dir, int n) {
        if (!pingPong) return new Progress((idx + 1) % n, 1, Phase.TRAVELING, 0);
        int next = idx + dir;
        if (next >= n) {            // bounced off the far end
            next = n - 2;
            dir = -1;
        } else if (next < 0) {      // bounced off the near end
            next = 1;
            dir = 1;
        }
        return new Progress(next, dir, Phase.TRAVELING, 0);
    }
}
