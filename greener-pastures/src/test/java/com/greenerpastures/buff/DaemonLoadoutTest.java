package com.greenerpastures.buff;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the pure {@link DaemonLoadout} (BUG-004) - install/replace/remove, the ≤0 + entry-count
 * bounds, and the forward-compat "unknown id survives the wire but drops when typed to {@link BuffId}" contract.
 * Loads {@code DaemonLoadout} (which builds MC codecs at class-init), so it exercises the fabric-loader-junit
 * bootstrap exactly like {@code AugmentsTest}.
 */
class DaemonLoadoutTest {

    @Test
    void noneIsEmpty() {
        assertTrue(DaemonLoadout.NONE.isEmpty());
        assertTrue(DaemonLoadout.NONE.toLevels().isEmpty());
        assertEquals(0, DaemonLoadout.NONE.level(BuffId.FORTUNE));
    }

    @Test
    void withLevelInstallsAndRemoves() {
        DaemonLoadout l = DaemonLoadout.NONE.withLevel(BuffId.FORTUNE, 3);
        assertEquals(3, l.level(BuffId.FORTUNE));
        assertEquals(3, l.toLevels().get(BuffId.FORTUNE));
        DaemonLoadout cleared = l.withLevel(BuffId.FORTUNE, 0);   // ≤0 removes the entry
        assertEquals(0, cleared.level(BuffId.FORTUNE));
        assertTrue(cleared.isEmpty());
    }

    @Test
    void withLevelReplacesInPlace() {
        DaemonLoadout l = DaemonLoadout.NONE.withLevel(BuffId.HASTE, 1).withLevel(BuffId.HASTE, 2);
        assertEquals(2, l.level(BuffId.HASTE), "one entry per buff - replace, not stack");
        assertEquals(1, l.toLevels().size());
    }

    @Test
    void nonPositiveLevelsAreNeverStored() {
        Map<String, Integer> raw = new HashMap<>();
        raw.put(BuffId.FORTUNE.id, 0);
        raw.put(BuffId.HASTE.id, -2);
        raw.put(BuffId.MAGNET.id, 2);
        DaemonLoadout l = new DaemonLoadout(raw);
        assertEquals(1, l.levels().size(), "≤0 levels are dropped by the compact constructor");
        assertEquals(2, l.level(BuffId.MAGNET));
    }

    @Test
    void unknownIdsSurviveTheMapButDropWhenTyped() {
        Map<String, Integer> raw = new HashMap<>();
        raw.put("not_a_real_buff", 3);          // a key from a hypothetical newer version
        raw.put(BuffId.FORTUNE.id, 2);
        DaemonLoadout l = new DaemonLoadout(raw);
        assertEquals(2, l.levels().size(), "unknown key kept on the wire (forward-compat)");
        Map<BuffId, Integer> typed = l.toLevels();
        assertEquals(1, typed.size(), "but the unknown id is dropped when resolved to a BuffId");
        assertEquals(2, typed.get(BuffId.FORTUNE));
    }

    @Test
    void entryCountIsBounded() {
        Map<String, Integer> raw = new HashMap<>();
        for (int i = 0; i < 100; i++) raw.put("buff_" + i, 1);
        DaemonLoadout l = new DaemonLoadout(raw);
        assertTrue(l.levels().size() <= 32, "codec/wire entry count is bounded (defense in depth)");
    }
}
