package com.greenerpastures.pasture.breeding;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the locked-boolean operator claim (who pays the tether cost). */
class PastureClaimTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void clickingAFreeBoxClaimsIt() {
        PastureClaim.Result r = PastureClaim.toggle(null, A);
        assertEquals(PastureClaim.Outcome.CLAIMED, r.outcome());
        assertEquals(A, r.owner(), "locks ON to the clicker");
        assertTrue(r.changed());
    }

    @Test
    void clickingYourOwnBoxReleasesIt() {
        PastureClaim.Result r = PastureClaim.toggle(A, A);
        assertEquals(PastureClaim.Outcome.RELEASED, r.outcome());
        assertNull(r.owner(), "unlocks — free for the next person");
        assertTrue(r.changed());
    }

    @Test
    void anotherPlayerCannotToggleYourLock() {
        PastureClaim.Result r = PastureClaim.toggle(A, B);
        assertEquals(PastureClaim.Outcome.LOCKED_BY_OTHER, r.outcome());
        assertEquals(A, r.owner(), "owner unchanged — only A can release");
        assertFalse(r.changed());
    }

    @Test
    void theFullShareSequence() {
        // free → A claims → B blocked → A releases → B claims (the group-pasture handoff)
        UUID owner = null;
        owner = PastureClaim.toggle(owner, A).owner();   assertEquals(A, owner);
        assertFalse(PastureClaim.toggle(owner, B).changed(), "B can't take A's lock");
        owner = PastureClaim.toggle(owner, A).owner();   assertNull(owner, "A releases");
        owner = PastureClaim.toggle(owner, B).owner();   assertEquals(B, owner, "now B can claim");
    }

    @Test
    void nullClickerIsRejectedDefensively() {
        assertFalse(PastureClaim.toggle(A, null).changed());
    }
}
