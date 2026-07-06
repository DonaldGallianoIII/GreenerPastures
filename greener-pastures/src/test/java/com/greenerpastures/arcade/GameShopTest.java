package com.greenerpastures.arcade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The rotating shop - window math, per-player determinism, distinct shelves, purchase validation. */
class GameShopTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void windowsTurnEveryFifteenMinutes() {
        assertEquals(0, GameShop.windowIndex(0));
        assertEquals(0, GameShop.windowIndex(GameShop.WINDOW_MS - 1));
        assertEquals(1, GameShop.windowIndex(GameShop.WINDOW_MS));
        assertEquals(GameShop.WINDOW_MS, GameShop.windowEndsAt(1));
        assertEquals(2 * GameShop.WINDOW_MS, GameShop.windowEndsAt(GameShop.WINDOW_MS + 5));
    }

    @Test
    void shelvesAreStableWithinAWindowAndRotateAcrossWindows() {
        long t = 42 * GameShop.WINDOW_MS + 1234;
        List<GameShop.Ware> a1 = GameShop.offersFor(A, t);
        List<GameShop.Ware> a2 = GameShop.offersFor(A, t + GameShop.WINDOW_MS / 2);
        assertEquals(a1, a2, "same window = same shelves (relog/restart safe)");
        List<GameShop.Ware> next = GameShop.offersFor(A, t + GameShop.WINDOW_MS);
        assertNotEquals(a1, next, "the window turned - new stock");
    }

    @Test
    void eachPlayerGetsTheirOwnRotation() {
        long t = 7 * GameShop.WINDOW_MS;
        assertNotEquals(GameShop.offersFor(A, t), GameShop.offersFor(B, t),
                "per-player shelves (mobile-style 'your shop')");
    }

    @Test
    void shelvesAreAlwaysSixDistinctWares() {
        for (long w = 0; w < 40; w++) {
            List<GameShop.Ware> offers = GameShop.offersFor(A, w * GameShop.WINDOW_MS);
            assertEquals(GameShop.SLOTS, offers.size());
            assertEquals(GameShop.SLOTS, offers.stream().map(GameShop.Ware::itemId).distinct().count(),
                    "window " + w + " duplicated a ware");
        }
    }

    @Test
    void purchaseValidationReDerivesTheShelf() {
        long t = 9 * GameShop.WINDOW_MS + 500;
        List<GameShop.Ware> offers = GameShop.offersFor(A, t);
        assertEquals(offers.get(3), GameShop.wareAt(A, t, 3));
        assertNull(GameShop.wareAt(A, t, -1));
        assertNull(GameShop.wareAt(A, t, GameShop.SLOTS), "forged slot index buys nothing");
    }

    @Test
    void catalogPricesArePositiveAndIdsNamespaced() {
        for (GameShop.Ware w : GameShop.CATALOG) {
            assertTrue(w.price() > 0, w.itemId());
            assertTrue(w.count() > 0, w.itemId());
            assertTrue(w.itemId().contains(":"), w.itemId());
        }
        assertTrue(GameShop.CATALOG.size() > GameShop.SLOTS, "rotation needs more catalog than shelves");
    }
}
