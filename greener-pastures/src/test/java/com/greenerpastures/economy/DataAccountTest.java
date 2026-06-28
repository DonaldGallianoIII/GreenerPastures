package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the Data currency account. */
class DataAccountTest {

    @Test
    void creditAddsToBalance() {
        DataAccount a = new DataAccount(100);
        a.credit(50);
        assertEquals(150L, a.balance());
    }

    @Test
    void debitPaysWhenAffordable() {
        DataAccount a = new DataAccount(100);
        assertTrue(a.tryDebit(40));
        assertEquals(60L, a.balance());
    }

    @Test
    void starveLeavesBalanceUntouched() {
        DataAccount a = new DataAccount(30);
        assertFalse(a.tryDebit(50), "can't afford → unpaid");
        assertEquals(30L, a.balance(), "starve never goes negative; the tether just stays inert");
    }

    @Test
    void canAffordChecks() {
        DataAccount a = new DataAccount(50);
        assertTrue(a.canAfford(50));
        assertFalse(a.canAfford(51));
        assertTrue(a.canAfford(0));
    }

    @Test
    void neverNegativeAndIgnoresNonPositiveCredit() {
        DataAccount a = new DataAccount(-10);
        assertEquals(0L, a.balance(), "initial clamps to 0");
        a.credit(-5);
        assertEquals(0L, a.balance());
        assertTrue(a.tryDebit(0), "a zero debit is always 'paid'");
    }

    @Test
    void creditSaturatesInsteadOfOverflowingNegative() {
        DataAccount a = new DataAccount(Long.MAX_VALUE - 5);
        a.credit(100);                                   // would wrap to negative with a plain +=
        assertEquals(Long.MAX_VALUE, a.balance(), "overflow saturates at MAX_VALUE");
        assertTrue(a.balance() > 0, "balance must never go negative");
        assertTrue(a.canAfford(Long.MAX_VALUE), "and the saturated balance still reads as affordable");
    }
}
