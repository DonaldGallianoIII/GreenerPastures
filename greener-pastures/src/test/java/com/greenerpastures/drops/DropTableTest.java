package com.greenerpastures.drops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the pure drop-table roll (the basis of passive, ground-free harvesting). */
class DropTableTest {

    @Test
    void dropsAtIsStrictlyBelowChance() {
        DropEntry e = new DropEntry("minecraft:gold_nugget", 0.5, 1, 1);
        assertTrue(e.dropsAt(0.49));
        assertFalse(e.dropsAt(0.50), "roll == chance does not drop");
        assertTrue(new DropEntry("x", 1.0, 1, 1).dropsAt(0.999), "chance 1.0 always (rolls are < 1)");
        assertFalse(new DropEntry("x", 0.0, 1, 1).dropsAt(0.0), "chance 0.0 never");
    }

    @Test
    void quantityRangeIsInclusive() {
        DropEntry e = new DropEntry("x", 1.0, 2, 5);
        assertEquals(2, e.quantityAt(0.0));
        assertEquals(4, e.quantityAt(0.5), "2 + floor(0.5 × 4)");
        assertEquals(5, e.quantityAt(0.999));
        assertEquals(3, new DropEntry("x", 1.0, 3, 3).quantityAt(0.7), "min == max → fixed");
    }

    @Test
    void constructorClampsBadValues() {
        DropEntry e = new DropEntry("x", 1.5, -2, -5);
        assertEquals(1.0, e.chance(), 1e-9);
        assertEquals(0, e.min());
        assertEquals(0, e.max(), "max floored up to min");
    }

    @Test
    void rollFiresGuaranteedEntries() {
        DropTable t = new DropTable(List.of(
                new DropEntry("minecraft:bone", 1.0, 1, 1),
                new DropEntry("minecraft:string", 1.0, 1, 1)));
        Map<String, Integer> r = t.roll(new Random(1));
        assertEquals(1, r.get("minecraft:bone"));
        assertEquals(1, r.get("minecraft:string"));
    }

    @Test
    void rollSkipsZeroChance() {
        assertTrue(new DropTable(List.of(new DropEntry("x", 0.0, 1, 9))).roll(new Random(7)).isEmpty());
    }

    @Test
    void rollStacksTheSameItem() {
        DropTable t = new DropTable(List.of(
                new DropEntry("minecraft:slime_ball", 1.0, 1, 1),
                new DropEntry("minecraft:slime_ball", 1.0, 2, 2)));
        assertEquals(3, t.roll(new Random(42)).get("minecraft:slime_ball"), "1 + 2 merged");
    }

    @Test
    void emptyTableRollsEmpty() {
        assertTrue(DropTable.EMPTY.roll(new Random()).isEmpty());
        assertTrue(DropTable.EMPTY.isEmpty());
    }

    @Test
    void widenedByRaisesOnlyTheCeiling() {
        DropEntry e = new DropEntry("x", 1.0, 2, 3).widenedBy(2);
        assertEquals(2, e.min(), "floor unchanged");
        assertEquals(5, e.max(), "ceiling +2");
        assertEquals(e, e.widenedBy(0), "zero bonus is a no-op (record equality)");
        assertEquals(e, e.widenedBy(-1), "negative bonus is a no-op");
    }

    @Test
    void fixedQuantityEntryRollsARangeUnderDropYield() {
        // The native-drop fix (Deuce, 2026-07-22): a %-only, fixed-quantity-1 entry (quick_claw) must roll
        // [1, 1+yield] when it procs, not a flat 1. DropsBridge.rollQty builds exactly this and rolls it.
        DropEntry quickClaw = new DropEntry("cobblemon:quick_claw", 1.0, 1, 1).widenedBy(2);
        assertEquals(1, quickClaw.min(), "floor stays at the base quantity");
        assertEquals(3, quickClaw.max(), "ceiling +2 → can now roll up to 3");
        assertEquals(1, quickClaw.quantityAt(0.0), "low roll still gives the base 1 - never fewer");
        assertEquals(3, quickClaw.quantityAt(0.999), "high roll gives the widened 3");
        // no Drop Yield → unchanged flat 1
        DropEntry noYield = new DropEntry("cobblemon:quick_claw", 1.0, 1, 1).widenedBy(0);
        assertEquals(1, noYield.quantityAt(0.999), "yield 0 → still a flat 1");
    }

    @Test
    void tableWidenedByWidensEveryEntry() {
        DropTable t = new DropTable(List.of(
                new DropEntry("a", 1.0, 1, 1),
                new DropEntry("b", 1.0, 0, 1))).widenedBy(3);
        assertEquals(4, t.entries().get(0).max());
        assertEquals(4, t.entries().get(1).max());
        assertTrue(t.widenedBy(0) == t, "zero bonus returns the same instance");
    }
}
