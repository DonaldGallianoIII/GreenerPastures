package com.greenerpastures.display;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Specimen Statue's client-sync payload - <b>cosmetics only, by construction</b> (Display Suite -
 * Deuce, 2026-07-14). At insert time the server distills the disk's Pokémon down to exactly what a
 * renderer needs; the full {@code SPECIMEN} compound (IVs, OT, moves - player data) stays server-side in
 * the stored disk stack and is never broadcast.
 *
 * <p>Pure + map-packed so the whitelist is headless-testable: {@link #toMap()} emits exactly
 * {@link #KEYS} and nothing else - a private field can't leak because the record can't hold one. The
 * BlockEntity writes the map's entries verbatim as string NBT for client sync.
 */
public record RenderSpec(String species, String form, List<String> aspects, String gender, float scaleHint) {

    /** The complete client-visible surface. Anything not on this list does not leave the server. */
    public static final List<String> KEYS = List.of("species", "form", "aspects", "gender", "scaleHint");

    public static final RenderSpec EMPTY = new RenderSpec("", "", List.of(), "", 1.0f);

    public RenderSpec {
        species = species == null ? "" : species.toLowerCase(Locale.ROOT);
        form = form == null ? "" : form;
        aspects = aspects == null ? List.of() : List.copyOf(aspects);
        gender = gender == null ? "" : gender;
        scaleHint = (Float.isFinite(scaleHint) && scaleHint > 0f) ? scaleHint : 1.0f;
    }

    public boolean isEmpty() {
        return species.isEmpty();
    }

    /** The whole point of the museum. */
    public boolean shiny() {
        return aspects.contains("shiny");
    }

    /** String-string map, keys exactly {@link #KEYS} - what the BlockEntity syncs, what the tests audit. */
    public Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("species", species);
        map.put("form", form);
        map.put("aspects", String.join(",", aspects));
        map.put("gender", gender);
        map.put("scaleHint", Float.toString(scaleHint));
        return map;
    }

    /** Inverse of {@link #toMap()}; missing or garbage entries fall back to safe defaults, never throw. */
    public static RenderSpec fromMap(Map<String, String> map) {
        if (map == null) return EMPTY;
        List<String> aspects = new ArrayList<>();
        String joined = map.getOrDefault("aspects", "");
        for (String a : joined.split(",")) {
            if (!a.isBlank()) aspects.add(a.trim());
        }
        float scaleHint = 1.0f;
        try {
            scaleHint = Float.parseFloat(map.getOrDefault("scaleHint", "1.0"));
        } catch (NumberFormatException ignored) {
            // hand-edited NBT: keep the default rather than refuse to render
        }
        return new RenderSpec(
                map.getOrDefault("species", ""),
                map.getOrDefault("form", ""),
                aspects,
                map.getOrDefault("gender", ""),
                scaleHint);
    }
}
