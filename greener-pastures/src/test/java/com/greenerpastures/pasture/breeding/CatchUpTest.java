package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Headless tests for the offline breeding catch-up math. */
class CatchUpTest {

    @Test
    void noTimeElapsedMeansNoCycles() {
        assertEquals(0, CatchUp.cyclesElapsed(1000, 1000, 20, 24), "same tick = nothing");
        assertEquals(0, CatchUp.cyclesElapsed(1000, 999, 20, 24), "clock going backwards = nothing");
    }

    @Test
    void countsWholeIntervals() {
        assertEquals(3, CatchUp.cyclesElapsed(0, 65, 20, 24), "65/20 = 3 whole cycles");
        assertEquals(0, CatchUp.cyclesElapsed(0, 19, 20, 24), "under one interval = 0");
    }

    @Test
    void cyclesAreCappedAtMax() {
        assertEquals(24, CatchUp.cyclesElapsed(0, 1_000_000, 20, 24), "a week away still caps at maxCycles");
    }

    @Test
    void degenerateInputsAreSafe() {
        assertEquals(0, CatchUp.cyclesElapsed(0, 100, 0, 24), "zero interval = no divide-by-zero, 0");
        assertEquals(0, CatchUp.cyclesElapsed(0, 100, 20, 0), "zero cap = 0");
    }

    @Test
    void offlineEggsIsCyclesTimesPairsUnderTheBuffer() {
        // 5 cycles × 2 pairs = 10 eggs, queue has room for 24
        assertEquals(10, CatchUp.offlineEggs(0, 5 * 20, 20, 2, 24));
    }

    @Test
    void offlineEggsIsBoundedByQueueRoom() {
        // 8 pairs over a long absence would be hundreds, but the queue only has room for 24
        assertEquals(24, CatchUp.offlineEggs(0, 1_000_000, 20, 8, 24), "banks ~24, then waits");
    }

    @Test
    void noPairsOrNoRoomMeansNoEggs() {
        assertEquals(0, CatchUp.offlineEggs(0, 10_000, 20, 0, 24));
        assertEquals(0, CatchUp.offlineEggs(0, 10_000, 20, 4, 0));
    }
}
