package com.greenerpastures.notebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotebookStorageTest {

    @Test void addAndCount() {
        NotebookStorage s = new NotebookStorage();
        assertEquals(10, s.add("minecraft:diamond", 10));
        assertEquals(10, s.count("minecraft:diamond"));
        assertEquals(5, s.add("minecraft:diamond", 5));
        assertEquals(15, s.count("minecraft:diamond"));
    }

    @Test void addRejectsNonPositiveAndNull() {
        NotebookStorage s = new NotebookStorage();
        assertEquals(0, s.add("x", 0));
        assertEquals(0, s.add("x", -3));
        assertEquals(0, s.add(null, 5));
        assertTrue(s.isEmpty());
    }

    @Test void addSaturatesAtCapacity() {
        NotebookStorage s = new NotebookStorage(100);
        assertEquals(100, s.add("x", 100));
        assertEquals(0, s.add("x", 50));        // full
        assertEquals(100, s.count("x"));
    }

    @Test void addReturnsPartialWhenNearCap() {
        NotebookStorage s = new NotebookStorage(100);
        s.add("x", 90);
        assertEquals(10, s.add("x", 40));       // only 10 room; caller keeps 30
        assertEquals(100, s.count("x"));
    }

    @Test void addNearIntLimitDoesNotOverflow() {
        NotebookStorage s = new NotebookStorage();           // cap = INT_LIMIT
        long huge = NotebookStorage.INT_LIMIT - 5;
        assertEquals(huge, s.add("x", huge));
        assertEquals(5, s.add("x", 1_000_000));              // saturates to INT_LIMIT, no overflow
        assertEquals(NotebookStorage.INT_LIMIT, s.count("x"));
    }

    @Test void withdrawRemovesAndReturns() {
        NotebookStorage s = new NotebookStorage();
        s.add("x", 20);
        assertEquals(8, s.withdraw("x", 8));
        assertEquals(12, s.count("x"));
    }

    @Test void withdrawMoreThanHeldEmptiesAndReturnsHeld() {
        NotebookStorage s = new NotebookStorage();
        s.add("x", 7);
        assertEquals(7, s.withdraw("x", 99));
        assertEquals(0, s.count("x"));
        assertFalse(s.types().contains("x"));   // key dropped at zero
        assertTrue(s.isEmpty());
    }

    @Test void withdrawUnknownOrNonPositiveIsZero() {
        NotebookStorage s = new NotebookStorage();
        assertEquals(0, s.withdraw("nope", 5));
        s.add("x", 3);
        assertEquals(0, s.withdraw("x", 0));
        assertEquals(0, s.withdraw("x", -1));
        assertEquals(3, s.count("x"));
    }

    @Test void totalAndTypes() {
        NotebookStorage s = new NotebookStorage();
        s.add("a", 3);
        s.add("b", 4);
        assertEquals(7, s.total());
        assertEquals(2, s.types().size());
    }

    @Test void loweringCapacityTrimsStacks() {
        NotebookStorage s = new NotebookStorage();
        s.add("x", 500);
        s.setCapacity(100);
        assertEquals(100, s.count("x"));
        assertEquals(100, s.capacity());
    }

    @Test void snapshotRoundTrips() {
        NotebookStorage s = new NotebookStorage();
        s.add("a", 3);
        s.add("b", 4);
        NotebookStorage r = NotebookStorage.fromSnapshot(s.snapshot(), s.capacity());
        assertEquals(3, r.count("a"));
        assertEquals(4, r.count("b"));
        assertEquals(2, r.types().size());
    }

    @Test void fromSnapshotDropsBadEntries() {
        java.util.Map<String, Long> raw = new java.util.HashMap<>();
        raw.put("good", 5L);
        raw.put("zero", 0L);
        raw.put("neg", -2L);
        NotebookStorage r = NotebookStorage.fromSnapshot(raw, NotebookStorage.INT_LIMIT);
        assertEquals(5, r.count("good"));
        assertEquals(1, r.types().size());
    }

    @Test void typesViewIsUnmodifiable() {
        NotebookStorage s = new NotebookStorage();
        s.add("a", 1);
        assertThrows(UnsupportedOperationException.class, () -> s.types().add("z"));
    }
}
