package com.greenerpastures.drops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the GP species-drops overlay (gold for the gold Pokémon; drops audit 2026-07-22). */
class SpeciesDropOverlayTest {

    private static final String GOLD = "minecraft:gold_ingot";

    @Test
    void defaultsCarryGoldForTheGoldPokemonAndPersian() {
        SpeciesDropOverlay o = SpeciesDropOverlay.defaults();
        assertTrue(o.has("Gholdengo"));
        assertTrue(o.has("Gimmighoul"));
        assertTrue(o.has("Persian"));
        assertFalse(o.has("Pikachu"), "unlisted species carry no overlay");
    }

    @Test
    void speciesLookupIsCaseInsensitive() {
        assertTrue(SpeciesDropOverlay.defaults().has("gImMiGhOuL"));
    }

    @Test
    void gimmighoulAlwaysGivesOneOrTwoGold() {
        SpeciesDropOverlay o = SpeciesDropOverlay.defaults();
        // 1-2 range, chance 1.0 → every event yields gold, never zero, never > 2.
        Random rng = new Random(123);
        for (int i = 0; i < 500; i++) {
            int gold = o.roll("Gimmighoul", rng).getOrDefault(GOLD, 0);
            assertTrue(gold == 1 || gold == 2, "Gimmighoul gold must be 1 or 2, got " + gold);
        }
    }

    @Test
    void gholdengoAndPersianRangeZeroToOne() {
        SpeciesDropOverlay o = SpeciesDropOverlay.defaults();
        Random rng = new Random(7);
        boolean sawZero = false, sawOne = false;
        for (int i = 0; i < 500; i++) {
            int g = o.roll("Gholdengo", rng).getOrDefault(GOLD, 0);
            int p = o.roll("Persian", rng).getOrDefault(GOLD, 0);
            assertTrue(g >= 0 && g <= 1 && p >= 0 && p <= 1, "0-1 gold only");
            if (g == 0) sawZero = true;
            if (g == 1) sawOne = true;
        }
        assertTrue(sawZero, "a 0-1 range must sometimes drop nothing (the 'chance')");
        assertTrue(sawOne, "a 0-1 range must sometimes drop one");
    }

    @Test
    void unlistedSpeciesAndNullRollEmpty() {
        SpeciesDropOverlay o = SpeciesDropOverlay.defaults();
        assertTrue(o.roll("Magikarp", new Random(1)).isEmpty());
        Map<String, Integer> nullRoll = o.roll(null, new Random(1));
        assertNotNull(nullRoll);
        assertTrue(nullRoll.isEmpty(), "null species → empty, never throws");
    }

    @Test
    void emptyTablesAreDroppedFromTheOverlay() {
        SpeciesDropOverlay o = new SpeciesDropOverlay(Map.of("Foo", DropTable.EMPTY));
        assertFalse(o.has("Foo"), "an empty overlay table is not carried");
        assertTrue(o.isEmpty());
    }

    @Test
    void customOverlayRollsItsEntries() {
        SpeciesDropOverlay o = new SpeciesDropOverlay(Map.of(
                "Bar", new DropTable(List.of(new DropEntry("minecraft:emerald", 1.0, 2, 2)))));
        assertEquals(2, o.roll("Bar", new Random(9)).get("minecraft:emerald"));
    }

    @Test
    void dropYieldWidensTheOverlayCeiling() {
        SpeciesDropOverlay o = SpeciesDropOverlay.defaults();
        // Gimmighoul native overlay is 1-2; yieldBonus 2 → 1-4. Floor never drops below the base min.
        Random rng = new Random(555);
        int max = 0, min = 99;
        for (int i = 0; i < 2000; i++) {
            int gold = o.roll("Gimmighoul", rng, 2).get(GOLD);
            max = Math.max(max, gold);
            min = Math.min(min, gold);
        }
        assertEquals(4, max, "yieldBonus 2 raises the 1-2 ceiling to 4");
        assertEquals(1, min, "floor is untouched by Drop Yield - only ever a chance at MORE");
    }

    @Test
    void dropYieldZeroOrNegativeIsANoOp() {
        SpeciesDropOverlay o = SpeciesDropOverlay.defaults();
        Random rng = new Random(3);
        for (int i = 0; i < 1000; i++) {
            assertTrue(o.roll("Gimmighoul", rng, 0).get(GOLD) <= 2, "no bonus → native 1-2 ceiling");
            assertTrue(o.roll("Gimmighoul", rng, -5).get(GOLD) <= 2, "negative bonus is a no-op");
        }
    }
}
