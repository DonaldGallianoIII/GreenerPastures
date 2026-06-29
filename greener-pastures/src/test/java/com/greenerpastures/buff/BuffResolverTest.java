package com.greenerpastures.buff;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the pure buff resolver: Daemon level ⇒ tier, per-buff caps, and the tier-scaled summed
 * Data drain. No Gson, no MC — only {@code defaults()}/{@code resolve}.
 */
class BuffResolverTest {

    @Test
    void noDaemonMeansNoBuffs() {
        assertSame(ResolvedBuffs.NONE, BuffResolver.resolve(BuffConfig.defaults(), 0));
        assertTrue(BuffResolver.resolve(BuffConfig.defaults(), -1).isEmpty());
    }

    @Test
    void masterOffMeansNoBuffsAtAnyLevel() {
        BuffConfig off = new BuffConfig(false, BuffConfig.defaults().buffs());
        assertTrue(BuffResolver.resolve(off, 3).isEmpty(), "master off ⇒ inert");
    }

    @Test
    void mkIGrantsEveryEnabledBuffAtTierOne() {
        BuffConfig def = BuffConfig.defaults();
        ResolvedBuffs r = BuffResolver.resolve(def, 1);
        long enabled = def.buffs().values().stream().filter(BuffSetting::enabled).count();
        assertEquals(enabled, r.tiers().size(), "every enabled buff is active");
        for (int t : r.tiers().values()) assertEquals(1, t, "Mk I ⇒ tier 1 across the board");
    }

    @Test
    void daemonLevelIsTheTierAndClampsToTheCeiling() {
        BuffConfig def = BuffConfig.defaults();
        for (int t : BuffResolver.resolve(def, 2).tiers().values()) assertEquals(2, t);
        for (int t : BuffResolver.resolve(def, 3).tiers().values()) assertEquals(3, t);
        ResolvedBuffs over = BuffResolver.resolve(def, 99);   // a hypothetical Mk-too-high
        assertEquals(3, over.daemonLevel(), "level clamps to the +3 ceiling");
        for (int t : over.tiers().values()) assertEquals(3, t, "no buff exceeds +3 however high the Daemon");
    }

    @Test
    void drainIsTierScaledAndSummed() {
        BuffConfig def = BuffConfig.defaults();
        double one = BuffResolver.resolve(def, 1).dataPerSec();
        double three = BuffResolver.resolve(def, 3).dataPerSec();
        assertTrue(one > 0, "running buffs costs Data");
        assertEquals(one * 3.0, three, 1e-9, "3× the tier ⇒ 3× the drain (all default caps ≥3)");
    }

    @Test
    void perBuffCapLimitsJustThatBuff() {                       // the shop-economy Fortune lever
        BuffConfig cfg = new BuffConfig(true, Map.of(
                BuffId.FORTUNE.id, new BuffSetting(true, 1, 0.5),
                BuffId.HASTE.id,   new BuffSetting(true, 3, 0.25)));
        ResolvedBuffs r = BuffResolver.resolve(cfg, 3);        // Mk III
        assertEquals(1, r.tier(BuffId.FORTUNE), "Fortune capped to +1 even on a Mk III Daemon");
        assertEquals(3, r.tier(BuffId.HASTE),   "an uncapped buff still rides to +3");
        assertEquals(0, r.tier(BuffId.LOOTING), "a buff absent from the config is inactive");
    }

    @Test
    void disabledOrZeroCappedBuffsAreOmitted() {
        BuffConfig cfg = new BuffConfig(true, Map.of(
                BuffId.FORTUNE.id,    new BuffSetting(false, 3, 0.5),   // explicitly off
                BuffId.LOOTING.id,    new BuffSetting(true, 0, 0.5),    // capped to nothing
                BuffId.UNBREAKING.id, new BuffSetting(true, 3, 0.5)));  // the only live one
        ResolvedBuffs r = BuffResolver.resolve(cfg, 3);
        assertEquals(0, r.tier(BuffId.FORTUNE));
        assertEquals(0, r.tier(BuffId.LOOTING));
        assertEquals(3, r.tier(BuffId.UNBREAKING));
        assertEquals(1, r.tiers().size(), "only the one live buff survives");
    }

    @Test
    void applicableFilterChargesOnlyForDeliverableBuffs() {
        // The adapter delivers buffs as their hooks land. With the filter, the player is billed ONLY for the
        // buffs the mod can currently apply — never for one it hasn't wired up.
        BuffConfig def = BuffConfig.defaults();
        Set<BuffId> delivered = Set.of(BuffId.HASTE, BuffId.SATURATION, BuffId.MAGNET);
        ResolvedBuffs all = BuffResolver.resolve(def, 3);
        ResolvedBuffs some = BuffResolver.resolve(def, 3, delivered);

        assertTrue(some.tier(BuffId.HASTE) > 0, "a delivered buff is granted");
        assertEquals(0, some.tier(BuffId.FORTUNE), "an undelivered buff is excluded by the filter");
        assertEquals(0, some.tier(BuffId.AUTO_SMELT), "...as is one in a partially-delivered category");
        assertTrue(some.dataPerSec() < all.dataPerSec(), "and so it costs strictly less to run");
        assertTrue(delivered.containsAll(some.tiers().keySet()), "only delivered buffs survive the filter");
    }

    @Test
    void aFreeBuffStillAppliesButDrainsNothing() {
        BuffConfig cfg = new BuffConfig(true, Map.of(
                BuffId.SATURATION.id, new BuffSetting(true, 3, 0.0)));
        ResolvedBuffs r = BuffResolver.resolve(cfg, 2);
        assertEquals(2, r.tier(BuffId.SATURATION), "a 0-cost buff still applies");
        assertEquals(0.0, r.dataPerSec(), 1e-9, "and drains no Data");
    }
}
