package com.greenerpastures.buff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the buff config DEFAULTS + the worker-not-fighter invariant. Never calls {@code load/save}
 * so Gson (lazy in {@link BuffConfig}) is never loaded - the test JVM has no Gson on its runtime classpath. JSON
 * round-trip is verified in-game (QA).
 */
class BuffConfigTest {

    @Test
    void defaultsCoverEveryBuffEnabledAtTheCeiling() {
        BuffConfig c = BuffConfig.defaults();
        assertTrue(c.enabled(), "ships enabled");
        assertEquals(BuffId.values().length, c.buffs().size(), "a setting for every catalogued buff");
        for (BuffId b : BuffId.values()) {
            BuffSetting s = c.settingOf(b);
            assertNotNull(s, b.id + " has a default setting");
            assertTrue(s.enabled(), b.id + " enabled by default");
            assertEquals(BuffSetting.TIER_CEILING, s.maxTier(), b.id + " rides to +3 by default");
            assertTrue(s.costPerSec() > 0, b.id + " has a positive Data cost");
        }
    }

    @Test
    void gatheringBuffsCostMoreThanQol() {
        BuffConfig c = BuffConfig.defaults();
        double fortune = c.settingOf(BuffId.FORTUNE).costPerSec();          // economy-impacting
        double feather = c.settingOf(BuffId.FEATHER_FALLING).costPerSec();  // pure QOL
        assertTrue(fortune > feather, "economy-impacting buffs are priced higher per tier");
    }

    @Test
    void settingLookupIsByStableId() {
        BuffConfig c = BuffConfig.defaults();
        assertSame(c.buffs().get(BuffId.LOOTING.id), c.settingOf(BuffId.LOOTING));
        assertNull(c.settingOf(null));
    }

    @Test
    void catalogHoldsTheWorkerNotFighterInvariant() {
        // The catalog IS the allow-list - assert no combat / binary-capped enchant ever sneaks in (a +N on
        // Sharpness/Protection or on Silk Touch/Mending/Infinity would break the PvP-neutral guarantee).
        for (BuffId b : BuffId.values()) {
            String id = b.id;
            assertFalse(id.contains("sharpness") || id.contains("smite") || id.contains("bane")
                            || id.contains("protection") || id.contains("power") || id.contains("punch")
                            || id.contains("knockback") || id.contains("fire_aspect") || id.contains("thorns")
                            || id.contains("sweeping") || id.contains("flame") || id.contains("channeling")
                            || id.contains("riptide") || id.contains("loyalty") || id.contains("impaling"),
                    id + " must not be a combat enchant");
            assertFalse(id.equals("silk_touch") || id.equals("mending") || id.equals("infinity")
                            || id.equals("aqua_affinity") || id.equals("depth_strider"),
                    id + " is binary/already-capped - a +N is meaningless, must be auto-skipped");
        }
    }
}
