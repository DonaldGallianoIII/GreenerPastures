package com.greenerpastures.client.ui;

import com.greenerpastures.client.ui.DaemonController.Unit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Daemon's pairing model is Minecraft-free (that's why it also drives the desktop studio), so its
 * logic tests need no bootstrap at all - pure + instant.
 */
class DaemonControllerTest {

    private static Unit unit(String name, int bucket) {
        return new Unit(UUID.randomUUID(), name, name, bucket);
    }

    @Test
    void readsPreWiredPairsFromBuckets() {
        Unit a = unit("Ditto", 1), b = unit("Charmander", 1);
        Unit c = unit("Gible", 2), d = unit("Eevee", 2);
        DaemonController ctrl = new DaemonController(List.of(a, b, c, d), 5, "Farm");

        assertEquals(2, ctrl.pairCount(), "two full buckets = two complete pairs");
        Map<UUID, Integer> p = ctrl.pairings();
        assertEquals(Integer.valueOf(1), p.get(a.id()));
        assertEquals(Integer.valueOf(1), p.get(b.id()));
        assertEquals(Integer.valueOf(2), p.get(c.id()));
        assertEquals(Integer.valueOf(2), p.get(d.id()));
    }

    @Test
    void maxPairsClampsToTheEightPairCeiling() {
        assertEquals(8, new DaemonController(List.of(), 99, "Farm").maxPairs(), "tether cap is 8 pairs - never more");
        assertEquals(0, new DaemonController(List.of(), 0, "Farm").maxPairs(), "no Kernel = no breeding threads");
    }

    @Test
    void aLoneUnitInABucketIsNotAPair() {
        DaemonController ctrl = new DaemonController(List.of(unit("Ditto", 1)), 5, "Farm");
        assertEquals(0, ctrl.pairCount());
        assertTrue(ctrl.pairings().isEmpty(), "a half-filled bucket yields no pairing");
    }
}
