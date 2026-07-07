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
        assertEquals(offers.get(3), GameShop.wareAt(A, t, 3, true));
        assertNull(GameShop.wareAt(A, t, -1, true));
        assertNull(GameShop.wareAt(A, t, GameShop.SLOTS, true), "forged slot index buys nothing");
    }

    @Test
    void eggFlagKeepsShelvesConsistentWhenCobbreedingIsAbsent() {
        for (long w = 0; w < 30; w++) {
            long t = w * GameShop.WINDOW_MS;
            var noEgg = GameShop.offersFor(A, t, false);
            assertTrue(noEgg.stream().noneMatch(x -> GameShop.MYSTERY_EGG_ID.equals(x.itemId())),
                    "window " + w + " offered the egg without Cobbreeding");
            for (int i = 0; i < GameShop.SLOTS; i++) {
                assertEquals(noEgg.get(i), GameShop.wareAt(A, t, i, false), "derivation must match validation");
            }
        }
    }

    @Test
    void everyPurchaseTurnsTheShelves() {
        long t = 11 * GameShop.WINDOW_MS + 77;
        List<GameShop.Ware> before = GameShop.offersFor(A, t, true, 0);
        List<GameShop.Ware> after = GameShop.offersFor(A, t, true, 1);
        assertNotEquals(before, after, "a buy must refresh the stock (rolls 0 -> 1)");
        assertEquals(after, GameShop.offersFor(A, t, true, 1), "same rolls = same shelves (relog-stable)");
        assertEquals(GameShop.SLOTS, after.size());
    }

    @Test
    void rollsFeedValidationTheSameAsDerivation() {
        long t = 13 * GameShop.WINDOW_MS + 9;
        for (long rolls : new long[]{0, 1, 5, 1234}) {
            List<GameShop.Ware> offers = GameShop.offersFor(A, t, true, rolls);
            for (int i = 0; i < GameShop.SLOTS; i++) {
                assertEquals(offers.get(i), GameShop.wareAt(A, t, i, true, rolls),
                        "rolls " + rolls + " slot " + i + " must validate what was shown");
            }
        }
    }

    @Test
    void zeroRollsMatchesTheLegacyOverloads() {
        long t = 17 * GameShop.WINDOW_MS + 300;
        assertEquals(GameShop.offersFor(A, t), GameShop.offersFor(A, t, true, 0));
        assertEquals(GameShop.wareAt(A, t, 2, true), GameShop.wareAt(A, t, 2, true, 0));
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
