package com.greenerpastures.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for Soul-Tether inscription economics (re-inscribable, never profitable). */
class TetherInscriptionTest {

    @Test
    void inscribeBlankChargesTheTierCost() {
        TetherInscription.Result r = TetherInscription.inscribe(Tether.blank(), "shiny", 2, 1000);
        assertTrue(r.ok());
        assertEquals(400L, r.dataDelta(), "tier II = 2² × 100");
        assertEquals("shiny", r.tether().function());
        assertEquals(2, r.tether().tier());
    }

    @Test
    void inscribeFailsWhenUnaffordableAndLeavesItUnchanged() {
        Tether blank = Tether.blank();
        TetherInscription.Result r = TetherInscription.inscribe(blank, "shiny", 3, 100);   // needs 900
        assertFalse(r.ok());
        assertEquals(0L, r.dataDelta());
        assertEquals(blank, r.tether(), "unchanged on failure");
    }

    @Test
    void reInscribeRefundsTheOldTierThenChargesTheNew() {
        Tether t1 = new Tether("shiny", 1);                  // wipeRefund(1) = 50
        TetherInscription.Result r = TetherInscription.inscribe(t1, "speed", 3, 1000);   // cost 900
        assertTrue(r.ok());
        assertEquals(850L, r.dataDelta(), "900 new − 50 refund");
        assertEquals("speed", r.tether().function(), "retargeted");
        assertEquals(3, r.tether().tier());
    }

    @Test
    void wipeRefundsPartAndReturnsBlank() {
        TetherInscription.Result r = TetherInscription.wipe(new Tether("shiny", 2), 0);   // refund 200
        assertTrue(r.ok(), "a wipe is always affordable (it credits)");
        assertEquals(-200L, r.dataDelta(), "negative = credited back");
        assertTrue(r.tether().isBlank());
    }

    @Test
    void flippingIsNeverProfitable() {
        // inscribe blank→II (pay 400), then wipe (get 200 back) = net 200 lost, never a gain.
        long spent = TetherInscription.inscribe(Tether.blank(), "shiny", 2, 1000).dataDelta();
        long back = -TetherInscription.wipe(new Tether("shiny", 2), 0).dataDelta();
        assertTrue(back < spent, "you always lose Data over an inscribe→wipe round trip");
    }
}
