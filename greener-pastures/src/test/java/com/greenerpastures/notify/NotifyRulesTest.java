package com.greenerpastures.notify;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the pure notification decision core (v1: the shiny-egg-laid ping). */
class NotifyRulesTest {

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void shinyEggLaidFiresWithCoords() {
        var n = NotifyRules.evaluate(
                row("type", "egg_laid", "shiny", Boolean.TRUE, "x", 10, "y", 64, "z", -20), NotifyConfig.defaults());
        assertTrue(n.isPresent());
        assertTrue(n.get().message().contains("Shiny egg laid"));
        assertTrue(n.get().message().contains("10, 64, -20"), "shows the pasture location");
        assertTrue(n.get().sound());
    }

    @Test
    void procShinyGetsTheBonusNote() {
        var n = NotifyRules.evaluate(
                row("type", "egg_laid", "shiny", Boolean.TRUE, "proc_shiny", Boolean.TRUE, "x", 0, "y", 0, "z", 0),
                NotifyConfig.defaults());
        assertTrue(n.isPresent());
        assertTrue(n.get().message().contains("bonus proc"), "a Daemon-proc'd shiny is called out");
    }

    @Test
    void nonShinyOrOtherEventsDoNotFire() {
        assertTrue(NotifyRules.evaluate(row("type", "egg_laid", "shiny", Boolean.FALSE), NotifyConfig.defaults()).isEmpty());
        assertTrue(NotifyRules.evaluate(row("type", "egg_laid"), NotifyConfig.defaults()).isEmpty(), "no shiny field");
        assertTrue(NotifyRules.evaluate(row("type", "egg_rendered", "shiny", Boolean.TRUE), NotifyConfig.defaults()).isEmpty());
        assertTrue(NotifyRules.evaluate(row("type", "pasture_toggle"), NotifyConfig.defaults()).isEmpty());
    }

    @Test
    void masterOffOrShinyTriggerOffSuppresses() {
        var shiny = row("type", "egg_laid", "shiny", Boolean.TRUE);
        assertTrue(NotifyRules.evaluate(shiny, new NotifyConfig(false, true, true, "chat", "all")).isEmpty(), "master off");
        assertTrue(NotifyRules.evaluate(shiny, new NotifyConfig(true, false, true, "chat", "all")).isEmpty(), "shiny trigger off");
        assertTrue(NotifyRules.evaluate(shiny, null).isEmpty(), "null config is safe");
    }

    @Test
    void soundFlagAndCoordsTrackTheInputs() {
        assertFalse(NotifyRules.evaluate(row("type", "egg_laid", "shiny", Boolean.TRUE),
                new NotifyConfig(true, true, false, "chat", "all")).get().sound(), "sound off → no chime");
        assertFalse(NotifyRules.evaluate(row("type", "egg_laid", "shiny", Boolean.TRUE), NotifyConfig.defaults())
                .get().message().contains(" at "), "no x/y/z → no location clause");
    }

    @Test
    void stringTrueShinyAlsoFires() {
        // an event whose flag arrives as the String "true" (e.g. reparsed) must still trip the rule
        assertTrue(NotifyRules.evaluate(row("type", "egg_laid", "shiny", "true"), NotifyConfig.defaults()).isPresent());
    }
}
