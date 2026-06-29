package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-core tests for the Nature selector's index table (the Nature-lock augment maps level → nature id). */
class NatureCatalogTest {

    @Test
    void hasExactlyTheTwentyFiveNatures() {
        assertEquals(25, NatureCatalog.size());
        assertEquals(25, NatureCatalog.NATURES.size());
    }

    @Test
    void allIdsAreLowercaseNonBlankAndUnique() {
        Set<String> seen = new HashSet<>();
        for (String n : NatureCatalog.NATURES) {
            assertEquals(n.toLowerCase(), n, "id must be lowercase: " + n);
            assertFalse(n.isBlank(), "id must be non-blank");
            assertTrue(seen.add(n), "duplicate nature id " + n);
        }
    }

    @Test
    void byIndexIsOneBasedAndBounded() {
        assertEquals("hardy", NatureCatalog.byIndex(1));
        assertEquals("adamant", NatureCatalog.byIndex(4));
        assertEquals("quirky", NatureCatalog.byIndex(25));
        assertNull(NatureCatalog.byIndex(0), "0 = off (no lock)");
        assertNull(NatureCatalog.byIndex(-3), "negative = off");
        assertNull(NatureCatalog.byIndex(26), "past the catalog = no lock, not a crash");
    }

    @Test
    void indexOfRoundTripsAndIsCaseInsensitive() {
        for (int i = 1; i <= NatureCatalog.size(); i++) {
            assertEquals(i, NatureCatalog.indexOf(NatureCatalog.byIndex(i)), "round-trip @ " + i);
        }
        assertEquals(4, NatureCatalog.indexOf("ADAMANT"), "case-insensitive");
        assertEquals(4, NatureCatalog.indexOf("  adamant "), "trims whitespace");
        assertEquals(0, NatureCatalog.indexOf("notanature"), "unknown → 0");
        assertEquals(0, NatureCatalog.indexOf(null), "null → 0");
    }

    @Test
    void orderIsStableAtItsAnchors() {
        // A stored augment index must always mean the same nature — pin anchors so a reorder is caught by CI.
        assertEquals("hardy", NatureCatalog.NATURES.get(0));
        assertEquals("adamant", NatureCatalog.NATURES.get(3));
        assertEquals("modest", NatureCatalog.NATURES.get(15));
        assertEquals("quirky", NatureCatalog.NATURES.get(24));
    }
}
