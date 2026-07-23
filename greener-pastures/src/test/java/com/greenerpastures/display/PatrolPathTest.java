package com.greenerpastures.display;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The MC-free patrol state machine (Display Suite v2 §3). Drives the whole path headless: constructor
 * sanitizing, loop vs ping-pong stepping, dwell countdown, and the inert edge cases the MC goal leans on.
 */
class PatrolPathTest {

    private static PatrolPath path(boolean pingPong, int dwell, RelPos... pts) {
        return new PatrolPath(Arrays.asList(pts), pingPong, dwell, 0.3);
    }

    private static RelPos p(double x) {
        return new RelPos(x, 0, 0);
    }

    /** Walk a whole path to arrival at the current target, collecting the index visited each STOP. */
    private static List<Integer> visitOrder(PatrolPath path, int stops) {
        List<Integer> order = new ArrayList<>();
        PatrolPath.Progress prog = path.start();
        int guard = 0;
        while (order.size() < stops && guard++ < 10_000) {
            // arrive at the current target, then tick until we leave it (dwell drains, index steps)
            int at = Math.floorMod(prog.index(), path.size());
            order.add(at);
            prog = path.tick(prog, true);                 // register arrival
            while (prog.phase() == PatrolPath.Phase.DWELLING) {
                prog = path.tick(prog, true);             // drain the dwell
            }
        }
        return order;
    }

    // ── constructor sanitizing ──

    @Test
    void constructorCopiesClampsAndCaps() {
        List<RelPos> pts = new ArrayList<>();
        pts.add(p(1));
        pts.add(null);                                    // dropped
        pts.add(p(2));
        PatrolPath path = new PatrolPath(pts, false, -50, 9.0);
        assertEquals(2, path.size(), "null waypoint dropped");
        assertEquals(0, path.dwellTicks(), "negative dwell clamps to 0");
        assertEquals(PatrolPath.MAX_SPEED, path.speed(), "speed clamps to max");

        pts.add(p(3));                                    // mutate the source AFTER construction
        assertEquals(2, path.size(), "waypoint list is defensively copied");
    }

    @Test
    void constructorCapsWaypointCountAndDefaultsNaNSpeed() {
        List<RelPos> many = new ArrayList<>();
        for (int i = 0; i < PatrolPath.MAX_WAYPOINTS + 5; i++) many.add(p(i));
        PatrolPath path = new PatrolPath(many, false, 20, Double.NaN);
        assertEquals(PatrolPath.MAX_WAYPOINTS, path.size(), "list capped");
        assertEquals(PatrolPath.DEFAULT_SPEED, path.speed(), "NaN speed → default");
    }

    @Test
    void nullWaypointsBecomesEmptyInertPath() {
        PatrolPath path = new PatrolPath(null, false, 0, 0.3);
        assertTrue(path.isEmpty());
        assertNull(path.target(path.start()), "empty path has no target");
    }

    // ── target selection ──

    @Test
    void targetWrapsAStaleIndex() {
        PatrolPath path = path(false, 0, p(0), p(10));
        // a Progress whose index outran a since-shrunk list must not throw
        assertEquals(p(0), path.target(new PatrolPath.Progress(4, 1, PatrolPath.Phase.TRAVELING, 0)));
        assertEquals(p(10), path.target(new PatrolPath.Progress(5, 1, PatrolPath.Phase.TRAVELING, 0)));
    }

    // ── traveling ──

    @Test
    void staysPutUntilItArrives() {
        PatrolPath path = path(false, 5, p(0), p(1), p(2));
        PatrolPath.Progress prog = path.start();
        for (int i = 0; i < 100; i++) {
            prog = path.tick(prog, false);                // never arrives
            assertEquals(0, prog.index(), "no arrival = no advance");
            assertEquals(PatrolPath.Phase.TRAVELING, prog.phase());
        }
    }

    // ── loop ──

    @Test
    void loopCyclesForwardForever() {
        PatrolPath path = path(false, 0, p(0), p(1), p(2));
        assertEquals(List.of(0, 1, 2, 0, 1, 2, 0), visitOrder(path, 7));
    }

    // ── ping-pong ──

    @Test
    void pingPongBouncesAtBothEnds() {
        PatrolPath path = path(true, 0, p(0), p(1), p(2), p(3));
        assertEquals(List.of(0, 1, 2, 3, 2, 1, 0, 1, 2, 3), visitOrder(path, 10),
                "endpoints visited once per bounce, not doubled");
    }

    @Test
    void pingPongOnTwoWaypointsAlternates() {
        PatrolPath path = path(true, 0, p(0), p(1));
        assertEquals(List.of(0, 1, 0, 1, 0), visitOrder(path, 5));
    }

    // ── dwell ──

    @Test
    void dwellHoldsThenSteps() {
        PatrolPath path = path(false, 3, p(0), p(1));
        PatrolPath.Progress prog = path.start();

        prog = path.tick(prog, true);                     // arrive at 0 → begin dwell
        assertEquals(PatrolPath.Phase.DWELLING, prog.phase());
        assertEquals(3, prog.dwellRemaining());
        assertEquals(0, prog.index(), "still at 0 while dwelling");

        prog = path.tick(prog, true);                     // 3 → 2
        assertEquals(2, prog.dwellRemaining());
        prog = path.tick(prog, true);                     // 2 → 1
        assertEquals(1, prog.dwellRemaining());

        prog = path.tick(prog, true);                     // 1 → 0 → step
        assertEquals(1, prog.index(), "dwell elapsed, advanced to next waypoint");
        assertEquals(PatrolPath.Phase.TRAVELING, prog.phase());
    }

    // ── degenerate paths ──

    @Test
    void singleWaypointSitsStill() {
        PatrolPath path = path(false, 10, p(0));
        PatrolPath.Progress prog = path.tick(path.start(), true);
        assertEquals(0, prog.index());
        assertEquals(PatrolPath.Phase.DWELLING, prog.phase(), "greeter parks on its one spot");
        // and stays there tick after tick
        assertEquals(prog, path.tick(prog, true));
    }

    @Test
    void emptyPathHoldsStateSoTheGoalCanWander() {
        PatrolPath path = path(false, 0);
        PatrolPath.Progress start = path.start();
        assertEquals(start, path.tick(start, true), "inert - never advances");
        assertEquals(start, path.tick(start, false));
    }
}
