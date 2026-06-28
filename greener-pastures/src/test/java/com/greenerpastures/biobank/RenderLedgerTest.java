package com.greenerpastures.biobank;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the BioBank "Send to Renderer" preview ledger — pure logic. */
class RenderLedgerTest {

    private static EggSummary egg(String species, boolean shiny, int perfect) {
        return new EggSummary(species, shiny, 90, perfect);
    }

    @Test
    void groupsBySpeciesAndCounts() {
        RenderLedger.Preview p = RenderLedger.of(List.of(
                egg("froakie", false, 0), egg("froakie", false, 0), egg("froakie", false, 0),
                egg("charmander", false, 0)));
        assertEquals(2, p.lines().size());
        assertEquals(4, p.total());
        RenderLedger.Line froakie = p.lines().get(0);
        assertEquals("froakie", froakie.species());
        assertEquals(3, froakie.count());
    }

    @Test
    void flagsSpeciesContainingAShiny() {
        RenderLedger.Preview p = RenderLedger.of(List.of(
                egg("charmander", false, 0), egg("charmander", true, 0)));   // one shiny in the batch
        RenderLedger.Line line = p.lines().get(0);
        assertTrue(line.hasShiny(), "a shiny anywhere in the species flags the line");
        assertFalse(line.hasPerfect());
        assertTrue(p.anyFlagged(), "preview overall is flagged");
    }

    @Test
    void flagsSpeciesContainingAPerfectIv() {
        RenderLedger.Preview p = RenderLedger.of(List.of(egg("bulbasaur", false, 1)));
        assertTrue(p.lines().get(0).hasPerfect());
        assertTrue(p.anyFlagged());
    }

    @Test
    void aCleanBatchIsNotFlagged() {
        RenderLedger.Preview p = RenderLedger.of(List.of(
                egg("froakie", false, 0), egg("rattata", false, 0)));
        assertFalse(p.anyFlagged(), "no shiny / no perfect IV = safe to send without a confirm");
    }

    @Test
    void preservesFirstSeenSpeciesOrder() {
        RenderLedger.Preview p = RenderLedger.of(List.of(
                egg("zubat", false, 0), egg("abra", false, 0), egg("zubat", false, 0)));
        assertEquals("zubat", p.lines().get(0).species());
        assertEquals("abra", p.lines().get(1).species());
    }

    @Test
    void emptyBatchIsEmptyPreview() {
        RenderLedger.Preview p = RenderLedger.of(List.of());
        assertTrue(p.lines().isEmpty());
        assertEquals(0, p.total());
        assertFalse(p.anyFlagged());
    }
}
