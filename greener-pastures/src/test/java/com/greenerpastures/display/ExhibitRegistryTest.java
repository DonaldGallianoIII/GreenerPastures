package com.greenerpastures.display;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Headless tests for the "My Exhibits" directory - the safety net that makes disguised blocks findable (§2.1). */
class ExhibitRegistryTest {

    private static ExhibitEntry pen(int x, int y, int z, String name) {
        return new ExhibitEntry("minecraft:overworld", x, y, z, "Exhibit Pen", name);
    }

    @Test
    void defaultNameStampsTheCoords() {
        ExhibitEntry e = pen(12, 64, -8, "");
        assertTrue(e.unnamed());
        assertEquals("Exhibit Pen @12,64,-8", e.displayName(), "an unnamed block is still findable by coords");
        assertEquals("Statue @1,2,3", ExhibitEntry.defaultName("Statue", 1, 2, 3));
    }

    @Test
    void registerDedupesByPosition() {
        ExhibitRegistry r = new ExhibitRegistry();
        r.register(pen(0, 70, 0, "Front Desk"));
        r.register(pen(0, 70, 0, "Front Desk v2"));   // same spot re-registered → replace, not duplicate
        assertEquals(1, r.size());
        assertEquals("Front Desk v2", r.at("minecraft:overworld", 0, 70, 0).name());
    }

    @Test
    void removeAndRename() {
        ExhibitRegistry r = new ExhibitRegistry();
        r.register(pen(5, 64, 5, ""));
        assertEquals("Gym Leader", r.rename("minecraft:overworld", 5, 64, 5, "Gym Leader").name());
        assertEquals("Gym Leader", r.at("minecraft:overworld", 5, 64, 5).displayName());
        assertNull(r.rename("minecraft:overworld", 9, 9, 9, "Nowhere"), "renaming an empty spot is a no-op");
        assertNotNull(r.removeAt("minecraft:overworld", 5, 64, 5));
        assertTrue(r.isEmpty());
        assertNull(r.removeAt("minecraft:overworld", 5, 64, 5), "double-break removes nothing");
    }

    @Test
    void searchMatchesNameAndCoordDefault() {
        ExhibitRegistry r = new ExhibitRegistry();
        r.register(pen(100, 64, 0, "Fire Gym"));
        r.register(pen(0, 64, 200, ""));               // unnamed → "Exhibit Pen @0,64,200"
        assertEquals(1, r.search("fire").size(), "case-insensitive name match");
        assertEquals(1, r.search("@0,64,200").size(), "unnamed block found by its coord default");
        assertEquals(2, r.search("").size(), "blank query returns all");
        assertEquals(1, r.search("exhibit").size(), "'Fire Gym' has no 'exhibit'; only the unnamed's default matches");
    }

    @Test
    void sortByDistanceIsNearestFirstThenOtherDimensions() {
        ExhibitRegistry r = new ExhibitRegistry();
        r.register(pen(0, 64, 100, "far"));
        r.register(pen(0, 64, 10, "near"));
        r.register(new ExhibitEntry("minecraft:the_nether", 0, 64, 1, "Exhibit Pen", "other-dim"));
        List<ExhibitEntry> sorted = r.sortedByDistance("minecraft:overworld", 0, 64, 0);
        assertEquals("near", sorted.get(0).name());
        assertEquals("far", sorted.get(1).name());
        assertEquals("other-dim", sorted.get(2).name(), "cross-dimension sorts last");
    }

    @Test
    void snapshotRoundTrips() {
        ExhibitRegistry r = new ExhibitRegistry();
        r.register(pen(1, 1, 1, "A"));
        r.register(pen(2, 2, 2, ""));
        ExhibitRegistry restored = ExhibitRegistry.fromSnapshot(r.snapshot());
        assertEquals(2, restored.size());
        assertEquals("A", restored.at("minecraft:overworld", 1, 1, 1).name());
        assertTrue(restored.at("minecraft:overworld", 2, 2, 2).unnamed());
    }
}
