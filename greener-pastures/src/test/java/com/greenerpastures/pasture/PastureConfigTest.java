package com.greenerpastures.pasture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** pastures.json - the operator's catch-up pacing knob (a PACING knob, never a power knob). */
class PastureConfigTest {

    @Test
    void defaultsAreTwelveHoursAndTickMathIsExact() {
        PastureConfig c = PastureConfig.defaults();
        assertEquals(12, c.maxCatchupHours());
        assertEquals(12L * 60 * 60 * 20, c.maxCatchupTicks(), "12h = 864,000 ticks");
    }

    @Test
    void hoursClampToSaneOperatorRange() {
        assertEquals(0, new PastureConfig(-5).maxCatchupHours(), "negative = disabled, not time travel");
        assertEquals(0, new PastureConfig(0).maxCatchupTicks(), "0 = catch-up off entirely");
        assertEquals(168, new PastureConfig(9000).maxCatchupHours(), "a week is the ceiling");
        assertEquals(12, new PastureConfig(Double.NaN).maxCatchupHours(), "garbage json falls to default");
        assertEquals(1800L * 20, new PastureConfig(0.5).maxCatchupTicks(), "fractional hours are allowed");
    }

    @Test
    void loadWritesDefaultsOnFirstRunAndRoundTrips(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("greenerpastures").resolve("pastures.json");
        PastureConfig first = PastureConfig.load(file);
        assertEquals(PastureConfig.defaults(), first);
        assertTrue(Files.exists(file), "first load drops the editable file");

        Files.writeString(file, "{\"maxCatchupHours\": 48}");
        assertEquals(48, PastureConfig.load(file).maxCatchupHours());

        Files.writeString(file, "{not json");
        assertEquals(PastureConfig.defaults(), PastureConfig.load(file), "corrupt file falls back, never crashes");
    }
}
