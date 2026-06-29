package com.greenerpastures.ritual;

import org.junit.jupiter.api.Test;

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
            assertTrue(r.outputItem().startsWith("minecraft:"), r.id() + " output should be a real item id");
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
    void signatureRitualNeedsItsSignatureMon() {
        Ritual lastStand = RitualConfig.defaults().rituals().byId("last_stand");
        assertNotNull(lastStand);
        Composition noSableye = new Composition(Map.of("fairy", 1, "ghost", 1), Set.of("gengar"));
        Composition withSableye = new Composition(Map.of("fairy", 1, "ghost", 1), Set.of("sableye"));
        assertFalse(lastStand.requirement().satisfiedBy(noSableye), "no Sableye ⇒ no totem");
        assertTrue(lastStand.requirement().satisfiedBy(withSableye), "Sableye unlocks the totem ritual");
    }
}
