package com.greenerpastures.notify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The donation feed's rolling window: newest first, 24h TTL, hard cap - never a RAM leak. */
class DonationFeedTest {

    private static final long T0 = 1_000_000_000_000L;

    @BeforeEach
    void fresh() {
        DonationFeed.clearAll();
    }

    @Test
    void newestFirstWithTierFlag() {
        DonationFeed.push("Deuce222XX", "klink", 100, 1.01, false, T0);
        DonationFeed.push("Phishing4Feebas", "klink", 100, 1.02, true, T0 + 1000);
        List<DonationFeed.Entry> feed = DonationFeed.entries(T0 + 2000);
        assertEquals(2, feed.size());
        assertEquals("Phishing4Feebas", feed.get(0).who());
        assertTrue(feed.get(0).tierUp());
        assertEquals("Deuce222XX", feed.get(1).who());
        assertFalse(feed.get(1).tierUp());
    }

    @Test
    void entriesExpireAfter24h() {
        DonationFeed.push("Deuce222XX", "klink", 100, 1.01, false, T0);
        DonationFeed.push("Tinderbeef", "eevee", 100, 1.01, false, T0 + DonationFeed.TTL_MS - 1);
        assertEquals(2, DonationFeed.entries(T0 + DonationFeed.TTL_MS).size());       // 24h exactly - still live
        List<DonationFeed.Entry> later = DonationFeed.entries(T0 + DonationFeed.TTL_MS + 1);
        assertEquals(1, later.size());                                                // the first one aged out
        assertEquals("Tinderbeef", later.get(0).who());
        assertTrue(DonationFeed.entries(T0 + 2 * DonationFeed.TTL_MS + 2).isEmpty()); // the window fully drains
    }

    @Test
    void hardCapBoundsTheWindow() {
        for (int i = 0; i < DonationFeed.CAP + 50; i++) {
            DonationFeed.push("p" + i, "klink", 100, 1.0, false, T0 + i);
        }
        List<DonationFeed.Entry> feed = DonationFeed.entries(T0 + DonationFeed.CAP + 50);
        assertEquals(DonationFeed.CAP, feed.size());
        assertEquals("p" + (DonationFeed.CAP + 49), feed.get(0).who());   // newest kept, oldest shed
    }

    @Test
    void junkInputIgnored() {
        DonationFeed.push(null, "klink", 100, 1.0, false, T0);
        DonationFeed.push("Deuce222XX", null, 100, 1.0, false, T0);
        assertTrue(DonationFeed.entries(T0).isEmpty());
    }
}
