package com.greenerpastures.core;

import java.util.TreeSet;

/**
 * Parses the bundled PMD Sprite Collab credits file ({@code assets/greenerpastures/pmd_credits.txt})
 * into the artist roll the About card thanks by name. The file is generator-owned (scratchpad/pmd);
 * its shape is two header lines followed by indented {@code "  species: artist, artist"} rows -
 * this reader keys off the indent so header colons never leak into the roll.
 */
public final class PmdCredits {
    private PmdCredits() {}

    /** Distinct artist names from the credits text, sorted, comma-joined. Empty string if none parse. */
    public static String artistRoll(String creditsText) {
        if (creditsText == null) return "";
        TreeSet<String> names = new TreeSet<>();
        for (String line : creditsText.split("\n")) {
            int colon = line.indexOf(':');
            if (!line.startsWith("  ") || colon < 0) continue;
            for (String artist : line.substring(colon + 1).split(",")) {
                String t = artist.trim();
                if (!t.isEmpty()) names.add(t);
            }
        }
        return String.join(", ", names);
    }
}
