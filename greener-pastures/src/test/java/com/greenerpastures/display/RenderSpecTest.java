package com.greenerpastures.display;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The statue's client payload - the no-leak whitelist. A player's mon data (IVs, OT, moves) must never
 * reach clients; these tests pin the sync surface to exactly {@link RenderSpec#KEYS}.
 */
class RenderSpecTest {

    private static final RenderSpec ABSOL = new RenderSpec(
            "absol", "", List.of("shiny"), "MALE", 1.2f);

    @Test
    void syncSurfaceIsExactlyTheWhitelist() {
        Map<String, String> map = ABSOL.toMap();
        assertEquals(new LinkedHashSet<>(RenderSpec.KEYS), map.keySet(),
                "every key ships, no key beyond the whitelist can ship");
        for (String key : RenderSpec.KEYS) {
            assertFalse(key.equalsIgnoreCase("ivs") || key.equalsIgnoreCase("ot")
                    || key.equalsIgnoreCase("moves"), "player-private fields are structurally absent");
        }
    }

    @Test
    void roundTripsThroughTheMap() {
        assertEquals(ABSOL, RenderSpec.fromMap(ABSOL.toMap()));

        RenderSpec formed = new RenderSpec("vulpix", "alolan", List.of("alolan", "shiny"), "FEMALE", 0.8f);
        assertEquals(formed, RenderSpec.fromMap(formed.toMap()), "form + multi-aspect survive");
    }

    @Test
    void shinyRidesTheAspects() {
        assertTrue(ABSOL.shiny(), "the whole point of the museum");
        assertFalse(new RenderSpec("absol", "", List.of(), "MALE", 1f).shiny());
        assertTrue(RenderSpec.fromMap(ABSOL.toMap()).shiny(), "shiny survives the sync round-trip");
    }

    @Test
    void hostileOrMissingDataNeverThrows() {
        assertEquals(RenderSpec.EMPTY, RenderSpec.fromMap(null));
        assertTrue(RenderSpec.fromMap(Map.of()).isEmpty());

        Map<String, String> garbage = new HashMap<>();
        garbage.put("species", "Eevee");
        garbage.put("scaleHint", "not-a-number");
        garbage.put("aspects", ",, ,shiny ,");
        RenderSpec spec = RenderSpec.fromMap(garbage);
        assertEquals("eevee", spec.species(), "species normalizes lowercase");
        assertEquals(1.0f, spec.scaleHint(), "garbage float falls back, never refuses to render");
        assertEquals(List.of("shiny"), spec.aspects(), "blank aspect entries dropped");
    }

    @Test
    void constructorSanitizesNulls() {
        RenderSpec spec = new RenderSpec(null, null, null, null, Float.NaN);
        assertTrue(spec.isEmpty());
        assertEquals(List.of(), spec.aspects());
        assertEquals(1.0f, spec.scaleHint());
    }
}
