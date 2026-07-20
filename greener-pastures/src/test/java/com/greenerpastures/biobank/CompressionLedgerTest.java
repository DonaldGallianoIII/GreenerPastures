package com.greenerpastures.biobank;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The Compression press math: 100 eggs = one press = a permanent, stacking +5% drop-proc multiplier. */
class CompressionLedgerTest {

    @Test
    void freshLedgerIsNeutral() {
        CompressionLedger l = new CompressionLedger();
        assertTrue(l.isEmpty());
        assertEquals(0, l.eggsOf("klink"));
        assertEquals(0, l.pressesOf("klink"));
        assertEquals(1.0, l.multiplierOf("klink"));
    }

    @Test
    void onePressIsFivePercent() {
        CompressionLedger l = new CompressionLedger();
        l.record("klink", 100);
        assertEquals(1, l.pressesOf("klink"));
        assertEquals(1.05, l.multiplierOf("klink"), 1e-9);
        assertEquals(1.0, l.multiplierOf("persian"));   // per-species, never global spillover
    }

    @Test
    void pressesStackAdditivelyAndForever() {
        CompressionLedger l = new CompressionLedger();
        for (int i = 0; i < 20; i++) l.record("klink", 100);
        assertEquals(20, l.pressesOf("klink"));
        assertEquals(2.0, l.multiplierOf("klink"), 1e-9);   // 20 presses = ×2.0 - the treadmill has no cap
    }

    @Test
    void partialBatchesEarnNothingUntilComplete() {
        CompressionLedger l = new CompressionLedger();
        l.record("klink", 99);
        assertEquals(0, l.pressesOf("klink"));
        assertEquals(1.0, l.multiplierOf("klink"));
        l.record("klink", 1);
        assertEquals(1, l.pressesOf("klink"));
    }

    @Test
    void normalizeCollapsesEverySpelling() {
        assertEquals("mrmime", CompressionLedger.normalize("Mr. Mime"));
        assertEquals("mrmime", CompressionLedger.normalize("mr-mime"));
        assertEquals("mrmime", CompressionLedger.normalize("mrmime"));
        assertEquals("klink", CompressionLedger.normalize("  Klink "));
        assertEquals("", CompressionLedger.normalize(null));
        CompressionLedger l = new CompressionLedger();
        l.record("Mr. Mime", 100);
        assertEquals(1.05, l.multiplierOf("mrmime"), 1e-9);   // bank key and display name meet on one entry
    }

    @Test
    void serverLedgerUsesCommunalConstants() {
        CompressionLedger sv = CompressionLedger.server();
        sv.record("klink", 800);
        assertEquals(0, sv.pressesOf("klink"));               // 800 < 1000 - no tier yet
        assertEquals(1.0, sv.multiplierOf("klink"));
        assertEquals(200, sv.toNextPress("klink"));
        sv.record("klink", 200);
        assertEquals(1, sv.pressesOf("klink"));
        assertEquals(1.01, sv.multiplierOf("klink"), 1e-9);   // 1000 eggs = +1% for everyone
        assertEquals(1000, sv.toNextPress("klink"));          // fresh tier - a full batch to the next
        sv.record("klink", 9000);
        assertEquals(1.10, sv.multiplierOf("klink"), 1e-9);   // stacks forever, same as personal
    }

    @Test
    void serverSnapshotRoundTripsWithServerConstants() {
        CompressionLedger sv = CompressionLedger.server();
        sv.record("klink", 2000);
        CompressionLedger back = CompressionLedger.serverFromSnapshot(sv.snapshot());
        assertEquals(2, back.pressesOf("klink"));
        assertEquals(1.02, back.multiplierOf("klink"), 1e-9);
        // the PERSONAL loader on the same data reads it at personal constants - the two never mix
        assertEquals(1.0 + 0.05 * 20, CompressionLedger.fromSnapshot(sv.snapshot()).multiplierOf("klink"), 1e-9);
    }

    @Test
    void moreMultiplierComposition() {
        CompressionLedger mine = new CompressionLedger();
        mine.record("klink", 400);                            // ×1.20 personal
        CompressionLedger sv = CompressionLedger.server();
        sv.record("klink", 5000);                             // ×1.05 server
        assertEquals(1.20 * 1.05, mine.multiplierOf("klink") * sv.multiplierOf("klink"), 1e-9);   // the harvest multiplies, never adds
    }

    @Test
    void ignoresJunkInput() {
        CompressionLedger l = new CompressionLedger();
        l.record(null, 100);
        l.record("   ", 100);
        l.record("klink", 0);
        l.record("klink", -50);
        assertTrue(l.isEmpty());
    }

    @Test
    void snapshotRoundTrips() {
        CompressionLedger l = new CompressionLedger();
        l.record("klink", 300);
        l.record("persian", 100);
        Map<String, Long> snap = l.snapshot();
        CompressionLedger back = CompressionLedger.fromSnapshot(snap);
        assertEquals(3, back.pressesOf("klink"));
        assertEquals(1, back.pressesOf("persian"));
        assertEquals(1.15, back.multiplierOf("klink"), 1e-9);
        snap.put("klink", 999_999L);                       // defensive copy - mutating it changes nothing
        assertEquals(300, l.eggsOf("klink"));
    }

    @Test
    void fromSnapshotDropsJunkEntries() {
        CompressionLedger back = CompressionLedger.fromSnapshot(Map.of("klink", -5L, "persian", 200L));
        assertEquals(0, back.eggsOf("klink"));
        assertEquals(2, back.pressesOf("persian"));
    }
}
