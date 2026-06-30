package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.greenerpastures.pasture.breeding.BreedingCompat.Gender.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for the breeding-compatibility rules (BUG-006) — no Minecraft needed. */
class BreedingCompatTest {

    private static BreedingCompat.Parent mon(BreedingCompat.Gender g, String... groups) {
        return new BreedingCompat.Parent(Set.of(groups), g, false);
    }
    private static final BreedingCompat.Parent DITTO =
            new BreedingCompat.Parent(Set.of("ditto"), GENDERLESS, true);

    @Test
    void theActualBug_drilburTimesPidgey_cannotBreed() {   // Field × Flying, no shared group
        var drilbur = mon(MALE, "field");
        var pidgey = mon(FEMALE, "flying");
        assertFalse(BreedingCompat.canBreed(drilbur, pidgey));
        assertEquals("no shared egg group", BreedingCompat.reason(drilbur, pidgey));
    }

    @Test
    void sharedGroupAndOppositeGenders_canBreed() {
        assertTrue(BreedingCompat.canBreed(mon(MALE, "flying"), mon(FEMALE, "flying")));
        assertNull(BreedingCompat.reason(mon(FEMALE, "field", "fairy"), mon(MALE, "fairy")));
    }

    @Test
    void sameGender_cannotBreed() {
        assertFalse(BreedingCompat.canBreed(mon(MALE, "flying"), mon(MALE, "flying")));
        assertEquals("need one ♂ + one ♀ (or a Ditto)",
                BreedingCompat.reason(mon(MALE, "flying"), mon(MALE, "flying")));
    }

    @Test
    void dittoBreedsWithAnyBreedable_eitherOrder() {
        assertTrue(BreedingCompat.canBreed(DITTO, mon(MALE, "field")));
        assertTrue(BreedingCompat.canBreed(mon(FEMALE, "flying"), DITTO));
        assertTrue(BreedingCompat.canBreed(DITTO, mon(GENDERLESS, "mineral")));   // ditto rescues genderless
    }

    @Test
    void twoDitto_cannotBreed() {
        assertFalse(BreedingCompat.canBreed(DITTO, DITTO));
        assertEquals("two Ditto can't breed", BreedingCompat.reason(DITTO, DITTO));
    }

    @Test
    void genderlessNonDitto_needsADitto() {
        var magnemite = mon(GENDERLESS, "mineral");
        assertFalse(BreedingCompat.canBreed(magnemite, magnemite), "two genderless can't pair");
        assertTrue(BreedingCompat.canBreed(magnemite, DITTO));
    }

    @Test
    void undiscoveredGroup_neverBreeds_evenWithDitto() {
        var legendary = new BreedingCompat.Parent(Set.of("undiscovered"), GENDERLESS, false);
        assertFalse(BreedingCompat.canBreed(legendary, DITTO));
        assertFalse(BreedingCompat.canBreed(legendary, mon(FEMALE, "field")));
        assertEquals("an Undiscovered-group mon can't breed", BreedingCompat.reason(legendary, DITTO));
    }

    @Test
    void emptyEggGroups_treatedAsUnbreedable() {
        var blank = new BreedingCompat.Parent(Set.of(), MALE, false);
        assertFalse(BreedingCompat.canBreed(blank, mon(FEMALE, "field")));
    }
}
