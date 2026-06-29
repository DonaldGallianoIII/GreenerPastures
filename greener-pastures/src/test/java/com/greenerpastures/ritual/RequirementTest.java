package com.greenerpastures.ritual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for ritual composition-matching (type counts · distinct types · signature species). */
class RequirementTest {

    private static Composition comp(Map<String, Integer> types, String... species) {
        return new Composition(types, Set.of(species));
    }

    @Test
    void emptyRequirementIsAlwaysSatisfied() {
        assertTrue(new Requirement(Map.of(), 0, List.of()).satisfiedBy(Composition.EMPTY));
    }

    @Test
    void typeMinCountsMustAllBeMet() {
        Requirement r = new Requirement(Map.of("fire", 1, "dark", 1, "ghost", 1), 0, List.of());
        assertTrue(r.satisfiedBy(comp(Map.of("fire", 2, "dark", 1, "ghost", 1))));
        assertFalse(r.satisfiedBy(comp(Map.of("fire", 2, "dark", 1))), "missing ghost");
    }

    @Test
    void minDistinctTypesGatesDiversityRituals() {
        Requirement r = new Requirement(Map.of(), 5, List.of());
        assertFalse(r.satisfiedBy(comp(Map.of("a", 1, "b", 1, "c", 1, "d", 1))), "only 4 distinct");
        assertTrue(r.satisfiedBy(comp(Map.of("a", 1, "b", 1, "c", 1, "d", 1, "e", 1))), "5 distinct");
    }

    @Test
    void signatureSpeciesNeedsAnyOneCaseInsensitive() {
        Requirement r = new Requirement(Map.of("ghost", 1), 0, List.of("sableye", "mismagius"));
        assertFalse(r.satisfiedBy(comp(Map.of("ghost", 1), "Gengar")), "no signature present");
        assertTrue(r.satisfiedBy(comp(Map.of("ghost", 1), "Sableye")), "Sableye matches (case-insensitive)");
    }
}
