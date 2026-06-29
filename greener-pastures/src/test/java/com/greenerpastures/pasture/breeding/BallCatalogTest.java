package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-core tests for the Ball selector's id table (the Ball-lock augment maps level → namespaced ball id). */
class BallCatalogTest {

    @Test
    void allIdsAreNamespacedLowercaseAndUnique() {
        Set<String> seen = new HashSet<>();
        for (String b : BallCatalog.BALLS) {
            assertTrue(b.startsWith("cobblemon:"), "ball id must be fully namespaced: " + b);
            assertEquals(b.toLowerCase(), b, "ball id must be lowercase: " + b);
            assertTrue(b.endsWith("_ball"), "ball id looks wrong: " + b);
            assertFalse(b.contains("ancient_"), "ancient balls are intentionally omitted from v1: " + b);
            assertTrue(seen.add(b), "duplicate ball id " + b);
        }
        assertTrue(BallCatalog.size() >= 20, "a useful spread of balls");
    }

    @Test
    void byIndexIsOneBasedAndBounded() {
        assertEquals("cobblemon:poke_ball", BallCatalog.byIndex(1), "poke_ball is the default first entry");
        assertEquals("cobblemon:great_ball", BallCatalog.byIndex(2));
        assertNull(BallCatalog.byIndex(0), "0 = off (no lock)");
        assertNull(BallCatalog.byIndex(-1), "negative = off");
        assertNull(BallCatalog.byIndex(BallCatalog.size() + 1), "past the catalog = no lock, not a crash");
    }

    @Test
    void indexOfRoundTripsAndAcceptsBarePaths() {
        for (int i = 1; i <= BallCatalog.size(); i++) {
            assertEquals(i, BallCatalog.indexOf(BallCatalog.byIndex(i)), "round-trip @ " + i);
        }
        assertEquals(1, BallCatalog.indexOf("poke_ball"), "bare path is namespaced for the lookup");
        assertEquals(1, BallCatalog.indexOf("COBBLEMON:POKE_BALL"), "case-insensitive");
        assertEquals(0, BallCatalog.indexOf("not_a_ball"));
        assertEquals(0, BallCatalog.indexOf(null));
    }

    @Test
    void orderIsStableAtItsAnchor() {
        // poke_ball must stay index 1 (the default); a reorder would silently repoint every stored augment.
        assertEquals("cobblemon:poke_ball", BallCatalog.BALLS.get(0));
    }
}
