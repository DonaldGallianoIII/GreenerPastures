package com.greenerpastures.display;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exhibit Pen rails - insert refusal order, shared-zoo eject permission, eject slot selection. */
class ExhibitRulesTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DONOR = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void happyInsertIsAllowed() {
        assertNull(ExhibitRules.insertRefusal(true, false, 0, ExhibitRules.DEFAULT_SLOTS));
        assertNull(ExhibitRules.insertRefusal(true, false, 5, 6), "last free slot still accepts");
    }

    @Test
    void blankGateFirstAlways() {
        assertEquals("This disk is blank - archive a party mon from the Notebook's Specimens tab.",
                ExhibitRules.insertRefusal(false, true, 99, 6));
    }

    @Test
    void glitchRefusesConfinement() {
        assertEquals("MissingNo. refuses confinement - it is a trophy, not livestock.",
                ExhibitRules.insertRefusal(true, true, 0, 6));
    }

    @Test
    void fullPenRefuses() {
        assertEquals("This pen is full (6 residents).", ExhibitRules.insertRefusal(true, false, 6, 6));
        assertEquals("This pen is full (2 residents).", ExhibitRules.insertRefusal(true, false, 2, 2),
                "config-shrunk pens report their own cap");
    }

    @Test
    void ejectGates() {
        assertEquals("This pen is empty.", ExhibitRules.ejectRefusal(0, false));
        assertEquals("Only the donor or the pen's owner can take these disks.",
                ExhibitRules.ejectRefusal(3, false));
        assertNull(ExhibitRules.ejectRefusal(3, true));
    }

    @Test
    void takePermissionIsDonorOrOwner() {
        assertTrue(ExhibitRules.canTake(DONOR, DONOR, OWNER), "donor takes own disk");
        assertTrue(ExhibitRules.canTake(OWNER, DONOR, OWNER), "owner takes any disk");
        assertFalse(ExhibitRules.canTake(STRANGER, DONOR, OWNER), "stranger takes nothing");
        assertFalse(ExhibitRules.canTake(null, DONOR, OWNER), "no requester, no take");
    }

    @Test
    void ejectPicksLastInsertedTakeable() {
        List<UUID> slots = Arrays.asList(DONOR, OWNER, DONOR);
        assertEquals(2, ExhibitRules.ejectIndex(slots, DONOR, OWNER), "donor gets their newest");
        assertEquals(2, ExhibitRules.ejectIndex(slots, OWNER, OWNER), "owner gets the newest of all");

        List<UUID> othersOnTop = Arrays.asList(DONOR, OWNER, OWNER);
        assertEquals(0, ExhibitRules.ejectIndex(othersOnTop, DONOR, OWNER),
                "skips over other donors' disks instead of refusing");
    }

    @Test
    void ejectRefusesWhenNothingTakeable() {
        assertEquals(-1, ExhibitRules.ejectIndex(List.of(), DONOR, OWNER), "empty pen");
        assertEquals(-1, ExhibitRules.ejectIndex(List.of(OWNER, OWNER), STRANGER, OWNER), "stranger");
        assertEquals(-1, ExhibitRules.ejectIndex(List.of(OWNER), null, OWNER), "no requester");
    }
}
