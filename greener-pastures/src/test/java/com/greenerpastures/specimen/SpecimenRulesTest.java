package com.greenerpastures.specimen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Mon-compression rails — every dupe/strand gate, in refusal-priority order. */
class SpecimenRulesTest {

    @Test
    void happyPathIsAllowed() {
        assertNull(SpecimenRules.compressRefusal(3, 1, false, true, true, true));
    }

    @Test
    void battleGateFirstAlways() {
        assertEquals("You can't archive a specimen mid-battle.",
                SpecimenRules.compressRefusal(3, 99, true, false, false, false));
    }

    @Test
    void slotGates() {
        assertEquals("No such party slot.", SpecimenRules.compressRefusal(3, -1, false, true, true, true));
        assertEquals("No such party slot.", SpecimenRules.compressRefusal(3, 6, false, true, true, true));
        assertEquals("That party slot is empty.", SpecimenRules.compressRefusal(3, 2, false, false, true, true));
    }

    @Test
    void neverStrandTheTrainer() {
        assertEquals("Your last party member stays with you.",
                SpecimenRules.compressRefusal(1, 0, false, true, true, true));
        assertNull(SpecimenRules.compressRefusal(2, 0, false, true, true, true), "two mons → one may go");
    }

    @Test
    void mediaAndLandingGates() {
        assertEquals("You need a blank Specimen Disk.",
                SpecimenRules.compressRefusal(3, 0, false, true, false, true));
        assertEquals("No inventory room for the written disk.",
                SpecimenRules.compressRefusal(3, 0, false, true, true, false));
    }

    @Test
    void releaseGates() {
        assertNull(SpecimenRules.releaseRefusal(true, true));
        assertEquals("This disk is blank — archive a party mon from the Notebook's Specimens tab.",
                SpecimenRules.releaseRefusal(false, true));
        assertEquals("Party and PC are both full — no room to release.",
                SpecimenRules.releaseRefusal(true, false));
    }
}
