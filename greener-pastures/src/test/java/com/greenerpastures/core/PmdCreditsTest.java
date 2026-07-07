package com.greenerpastures.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** The About card's artist roll - parsed from the SAME file the jar ships. */
class PmdCreditsTest {

    @Test
    void dedupesSortsAndTrims() {
        String text = """
                Header line with a colon: not an artist
                Second header
                  voltorb: JFain
                  froakie: Cloudy, Emmuffin,  JFain
                  axew: G〜
                """;
        assertEquals("Cloudy, Emmuffin, G〜, JFain", PmdCredits.artistRoll(text));
    }

    @Test
    void emptyAndNullAreSafe() {
        assertEquals("", PmdCredits.artistRoll(null));
        assertEquals("", PmdCredits.artistRoll(""));
        assertEquals("", PmdCredits.artistRoll("no indented lines here"));
    }

    @Test
    void parsesTheShippedCreditsFile() throws IOException {
        try (InputStream in = PmdCreditsTest.class.getResourceAsStream("/assets/greenerpastures/pmd_credits.txt")) {
            assertNotNull(in, "pmd_credits.txt must ship in the jar");
            String roll = PmdCredits.artistRoll(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(roll.contains("JFain"), "the angry Voltorb's artist is thanked: " + roll);
            assertTrue(roll.contains("Emmuffin"), roll);
            assertFalse(roll.toUpperCase().contains("CHUNSOFT"), "game rips are forbidden; the roll must stay fan-made");
        }
    }
}
