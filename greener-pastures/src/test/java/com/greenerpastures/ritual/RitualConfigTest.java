package com.greenerpastures.ritual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the config DEFAULTS + master gating. Deliberately never calls {@code load/save} so Gson
 * (lazy in {@link RitualConfig}) is never loaded — the test JVM has no Gson on its runtime classpath. JSON
 * round-trip is verified in-game (QA).
 */
class RitualConfigTest {

    @Test
    void defaultsAreWellFormed() {
        RitualConfig c = RitualConfig.defaults();
        assertTrue(c.enabled());
        assertTrue(c.rituals().enabled());
        assertFalse(c.rituals().rituals().isEmpty(), "ships a ritual roster");
        for (Ritual r : c.rituals().rituals()) {
            assertTrue(r.outputItem().contains(":"), r.id() + " output should be a namespaced item id");
            assertTrue(r.hardPity() >= 1, r.id() + " needs a hard pity");
            assertTrue(r.baseChancePercent() >= 0 && r.baseChancePercent() <= 100, r.id() + " chance in range");
        }
        assertTrue(c.typeDrops().enabled());
        assertFalse(c.typeDrops().drops().isEmpty(), "ships type-drops");
    }

    @Test
    void masterToggleGatesBothTiersButKeepsTheData() {
        RitualConfig def = RitualConfig.defaults();
        RitualConfig off = new RitualConfig(false, true, 3.0, def.typeDrops(), def.rituals());
        assertFalse(off.activeRituals().enabled(), "master off ⇒ rituals inert");
        assertFalse(off.activeTypeDrops().enabled(), "master off ⇒ type-drops inert");
        assertFalse(off.rituals().rituals().isEmpty(), "data preserved so flipping master back on restores it");
    }

    @Test
    void signatureGateStillWorks() {
        // The signature ("any of these species present") gate — kept for future hand-designed rituals.
        Ritual r = new Ritual("sig", "Sig", true,
                new Requirement(Map.of("fairy", 1, "ghost", 1), 0, List.of("sableye")),
                "minecraft:totem_of_undying", 1, 4.0, 35, 0);
        Composition noSableye = new Composition(Map.of("fairy", 1, "ghost", 1), Set.of("gengar"));
        Composition withSableye = new Composition(Map.of("fairy", 1, "ghost", 1), Set.of("sableye"));
        assertFalse(r.requirement().satisfiedBy(noSableye), "no Sableye ⇒ no totem");
        assertTrue(r.requirement().satisfiedBy(withSableye), "Sableye unlocks the totem ritual");
    }

    @Test
    void feastOfTheBladeNeedsTheExactRetinue() {
        // Deuce's first hand-designed hidden ritual: Kartana + Xerneas + 8 Meowth in ONE pasture.
        Ritual feast = RitualConfig.defaults().rituals().byId("feast_of_the_blade");
        assertNotNull(feast);
        assertTrue(feast.outputItem().equals("minecraft:enchanted_golden_apple"));

        Composition sevenMeowths = new Composition(Map.of(), Set.of(),
                Map.of("kartana", 1, "xerneas", 1, "meowth", 7));
        Composition exact = new Composition(Map.of(), Set.of(),
                Map.of("kartana", 1, "xerneas", 1, "meowth", 8));
        Composition noXerneas = new Composition(Map.of(), Set.of(),
                Map.of("kartana", 1, "meowth", 8));
        Composition overfilled = new Composition(Map.of(), Set.of(),
                Map.of("kartana", 2, "xerneas", 1, "meowth", 9, "ditto", 3));

        assertFalse(feast.requirement().satisfiedBy(sevenMeowths), "7 Meowth is not 8");
        assertFalse(feast.requirement().satisfiedBy(noXerneas), "no Xerneas ⇒ no feast");
        assertTrue(feast.requirement().satisfiedBy(exact), "the exact retinue forms the ritual");
        assertTrue(feast.requirement().satisfiedBy(overfilled), "minimums — extras don't break it");
    }

    @Test
    void speciesCountsAreCaseInsensitiveAndDefaultEmpty() {
        Composition c = new Composition(Map.of(), Set.of(), Map.of("Meowth", 8));
        assertTrue(c.countOfSpecies("meowth") == 8 && c.countOfSpecies("MEOWTH") == 8);
        Composition legacy = new Composition(Map.of("fire", 1), Set.of("meowth"));   // 2-arg compat shape
        assertTrue(legacy.countOfSpecies("meowth") == 0, "legacy shape has no counts — count gates just fail closed");
        Requirement legacyReq = new Requirement(Map.of("fire", 1), 0, List.of());    // 3-arg compat shape
        assertTrue(legacyReq.satisfiedBy(legacy), "requirements without count gates are unaffected");
    }
}
