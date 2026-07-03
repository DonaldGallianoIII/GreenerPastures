package com.greenerpastures.notebook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the pure pasture-health matrix (#37) — the console's warning strip + tab badges. */
class PastureHealthTest {

    private static List<String> ids(boolean linked, boolean kernel, int mons, boolean full, List<String> bank) {
        return PastureHealth.evaluate(linked, kernel, mons, full, bank).stream().map(PastureHealth.Flag::id).toList();
    }

    @Test
    void healthyPastureRaisesNothing() {
        assertTrue(ids(true, true, 6, false, null).isEmpty());
    }

    @Test
    void unlinkedAlwaysFlags() {
        assertEquals(List.of("unlinked"), ids(false, true, 6, false, null));
    }

    @Test
    void missingKernelFlags() {
        assertEquals(List.of("no_kernel"), ids(true, false, 6, false, null));
    }

    @Test
    void tooFewParentsFlagsOnlyWithAKernel() {
        assertEquals(List.of("no_parents"), ids(true, true, 1, false, null));
        assertEquals(List.of("no_parents"), ids(true, true, 0, false, null));
        // no Kernel → multi-pair breeding is off anyway; don't stack a parents nag on top
        assertEquals(List.of("no_kernel"), ids(true, false, 1, false, null));
    }

    @Test
    void unknownMonCountSkipsParentCheck() {
        assertTrue(ids(true, true, -1, false, null).isEmpty());   // chunk unloaded — never guess
    }

    @Test
    void fullTrayFlags() {
        assertEquals(List.of("tray_full"), ids(true, true, 6, true, null));
    }

    @Test
    void bankFullFlagsPerSpecies() {
        assertEquals(List.of("bank_full:Eevee", "bank_full:Ditto"),
                ids(true, true, 6, false, List.of("Eevee", "Ditto")));
    }

    @Test
    void worstCaseStacksInSeverityOrder() {
        assertEquals(List.of("unlinked", "no_kernel", "tray_full"),
                ids(false, false, 6, true, null));
    }

    @Test
    void idsCsvJoinsAndStaysEmptyWhenHealthy() {
        assertEquals("", PastureHealth.idsCsv(PastureHealth.evaluate(true, true, 6, false, null)));
        assertEquals("unlinked,no_kernel",
                PastureHealth.idsCsv(PastureHealth.evaluate(false, false, 6, false, null)));
    }
}
