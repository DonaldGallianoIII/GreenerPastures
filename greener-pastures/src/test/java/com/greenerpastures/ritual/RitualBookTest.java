package com.greenerpastures.ritual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the ritual roster: enabled/satisfied filtering + the master toggle. */
class RitualBookTest {

    private static final Requirement FIRE = new Requirement(Map.of("fire", 1), 0, List.of());
    private static final Composition FIREMON = new Composition(Map.of("fire", 1), Set.of());

    private static Ritual r(String id, boolean enabled, Requirement req) {
        return new Ritual(id, id, enabled, req, "minecraft:stone", 1, 5.0, 10, 0);
    }

    @Test
    void activeReturnsSatisfiedEnabledRituals() {
        RitualBook book = new RitualBook(true, List.of(r("a", true, FIRE)));
        assertEquals(1, book.active(FIREMON).size());
        assertTrue(book.active(Composition.EMPTY).isEmpty(), "requirement not met");
    }

    @Test
    void disabledBookYieldsNothing() {
        RitualBook book = new RitualBook(false, List.of(r("a", true, FIRE)));
        assertTrue(book.active(FIREMON).isEmpty(), "master off");
    }

    @Test
    void disabledRitualIsSkipped() {
        RitualBook book = new RitualBook(true, List.of(r("a", false, FIRE), r("b", true, FIRE)));
        List<Ritual> active = book.active(FIREMON);
        assertEquals(1, active.size());
        assertEquals("b", active.get(0).id());
    }

    @Test
    void byIdFindsOrNull() {
        RitualBook book = new RitualBook(true, List.of(r("a", true, FIRE)));
        assertNotNull(book.byId("a"));
        assertNull(book.byId("nope"));
    }
}
