package com.greenerpastures.ritual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Snack Repel charge + merge math (Overdrive pt.1 rev 2 — can charged by berry count, ultra-cake style). */
class SnackRepelMathTest {

    @Test
    void chargeScalesWithBerryCountAndCapsAtSix() {
        assertEquals(10, SnackRepelMath.chargeMagnitude(1, 10.0), "one chilan = ÷10 — Deuce's canonical example");
        assertEquals(30, SnackRepelMath.chargeMagnitude(3, 10.0));
        assertEquals(60, SnackRepelMath.chargeMagnitude(6, 10.0));
        assertEquals(60, SnackRepelMath.chargeMagnitude(9, 10.0), "6-copy cap — double a pot cook, just like the cake");
    }

    @Test
    void uselessChargesAreRefused() {
        assertEquals(0, SnackRepelMath.chargeMagnitude(0, 10.0));
        assertEquals(0, SnackRepelMath.chargeMagnitude(1, 0.0));
        assertEquals(0, SnackRepelMath.chargeMagnitude(1, 1.2), "÷1 repels nothing — invalid charge");
    }

    @Test
    void cansMergePerTypeCappedAtDoubleTheStrongest() {
        Map<String, Integer> m = SnackRepelMath.mergeCans(List.of(
                Map.entry("dark", 60), Map.entry("dark", 60), Map.entry("dark", 60)));
        assertEquals(Map.of("dark", 120), m, "3 full cans cap at 2× the strongest — double additive, just not more");
        Map<String, Integer> two = SnackRepelMath.mergeCans(List.of(
                Map.entry("dark", 60), Map.entry("normal", 10)));
        assertEquals(2, two.size());
        assertEquals(60, two.get("dark"));
        assertEquals(10, two.get("normal"));
    }

    @Test
    void mergeIgnoresJunkEntries() {
        Map<String, Integer> m = SnackRepelMath.mergeCans(List.of(
                Map.entry("dark", 1), Map.entry("fire", 40)));
        assertEquals(Map.of("fire", 40), m, "÷1 entries are no-ops and dropped");
        assertTrue(SnackRepelMath.mergeCans(null).isEmpty());
        assertTrue(SnackRepelMath.mergeCans(List.of()).isEmpty());
    }
}
