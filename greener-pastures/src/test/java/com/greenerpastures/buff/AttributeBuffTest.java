package com.greenerpastures.buff;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-core tests for the attribute-delivered Daemon enchant buffs (Respiration / Swift Sneak / Feather Falling). */
class AttributeBuffTest {

    @Test
    void valueScalesLinearlyWithTier() {
        assertEquals(1.0,  AttributeBuff.RESPIRATION.value(1),     1e-9);
        assertEquals(3.0,  AttributeBuff.RESPIRATION.value(3),     1e-9);
        assertEquals(0.15, AttributeBuff.SWIFT_SNEAK.value(1),     1e-9);
        assertEquals(0.45, AttributeBuff.SWIFT_SNEAK.value(3),     1e-9);
        assertEquals(-0.15, AttributeBuff.FEATHER_FALLING.value(1), 1e-9);
        assertEquals(-0.45, AttributeBuff.FEATHER_FALLING.value(3), 1e-9);
    }

    @Test
    void tierZeroOrNegativeIsNoBuff() {
        for (AttributeBuff a : AttributeBuff.values()) {
            assertEquals(0.0, a.value(0),  1e-9, a + " @0");
            assertEquals(0.0, a.value(-7), 1e-9, a + " @-7");
        }
    }

    @Test
    void reductionCanNeverInvertAMultiplier() {
        // Feather Falling scales a (1 + Σmultiplied) fall-damage factor. Even at an absurd tier the modifier must
        // stay > -1 so the factor stays positive — a non-positive factor would HEAL the player on a fall.
        assertEquals(AttributeBuff.FALL_FLOOR, AttributeBuff.FEATHER_FALLING.value(1_000), 1e-9);
        assertTrue(1.0 + AttributeBuff.FEATHER_FALLING.value(1_000) > 0.0, "fall-damage factor must stay positive");
    }

    @Test
    void everyAttributeBuffIsANonCombatNonGatheringEnchant() {
        for (AttributeBuff a : AttributeBuff.values()) {
            assertNotNull(a.buff, "buff link");
            assertSame(BuffCategory.ENCHANT, a.buff.category, a.buff.id + " must be an ENCHANT buff");
            // attribute modifiers can't be individually capped like Fortune, so we only deliver QOL/movement here —
            // never a gathering economy lever.
            assertFalse(a.buff.gathering, a.buff.id + " must not be a gathering buff");
        }
    }

    @Test
    void modifierPathsAreNamespacedAndUnique() {
        Set<String> paths = new HashSet<>();
        for (AttributeBuff a : AttributeBuff.values()) {
            assertTrue(a.modifierPath().startsWith("buff/"), a.modifierPath());
            assertTrue(paths.add(a.modifierPath()), "duplicate modifier path " + a.modifierPath());
        }
    }

    @Test
    void forBuffMapsOnlyTheAttributeDeliveredBuffs() {
        assertSame(AttributeBuff.RESPIRATION, AttributeBuff.forBuff(BuffId.RESPIRATION));
        assertSame(AttributeBuff.FEATHER_FALLING, AttributeBuff.forBuff(BuffId.FEATHER_FALLING));
        assertNull(AttributeBuff.forBuff(BuffId.FORTUNE),  "Fortune is mixin-delivered, not an attribute");
        assertNull(AttributeBuff.forBuff(BuffId.HASTE),    "Haste is an EFFECT, not an attribute");
        assertNull(AttributeBuff.forBuff(BuffId.SOUL_SPEED), "Soul Speed is intentionally not delivered");
    }
}
