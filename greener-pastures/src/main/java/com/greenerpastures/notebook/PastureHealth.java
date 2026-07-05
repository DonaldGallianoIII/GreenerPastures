package com.greenerpastures.notebook;

import java.util.ArrayList;
import java.util.List;

/**
 * The pasture <b>health strip</b> (#37) - the "why is my farm quiet?" warnings the console shows on a pasture
 * view and as ⚠ markers on the Pastures tab: not linked · no Kernel · too few parents · no breeding lines
 * wired · egg tray full · BioBank full for a species. <b>Minecraft-free</b>: {@link #evaluate} is pure (booleans/counts in, flags out)
 * so the whole warning matrix is unit-tested headless; the MC layer ({@code NotebookNet}) gathers the inputs.
 *
 * <p>Ordering is severity-ish: link state first (nothing collects while unlinked), then the breeding blockers.
 * {@code monCount = -1} means "unknown" (chunk not loaded) - parent checks are skipped rather than guessed.
 */
public final class PastureHealth {
    private PastureHealth() {}

    /** One warning chip: stable {@code id} (+ species suffix for bank_full), an icon, and the player-facing text. */
    public record Flag(String id, String icon, String text) {}

    public static List<Flag> evaluate(boolean linked, boolean hasKernel, int monCount, boolean hasLines,
                                      boolean queueFull, List<String> fullSpecies) {
        List<Flag> out = new ArrayList<>();
        if (!linked) {
            out.add(new Flag("unlinked", "🔗", "Not linked - drops & eggs are not being collected"));
        }
        if (!hasKernel) {
            out.add(new Flag("no_kernel", "🧬", "No Kernel - multi-pair breeding & drop harvest offline"));
        }
        if (hasKernel && monCount >= 0 && monCount < 2) {
            out.add(new Flag("no_parents", "👥", "Fewer than 2 parents - nothing to breed"));
        }
        // Lines are registry state - known even with the chunk unloaded (monCount -1). Skipped below
        // 2 parents so the more fundamental chip leads; breeding never runs without wired lines.
        if (hasKernel && !hasLines && (monCount >= 2 || monCount == -1)) {
            out.add(new Flag("no_lines", "🧵", "No breeding lines - wire a pair in this pasture's graph"));
        }
        if (queueFull) {
            out.add(new Flag("tray_full", "🥚", "Egg tray full - breeding is paused"));
        }
        if (fullSpecies != null) {
            for (String s : fullSpecies) {
                out.add(new Flag("bank_full:" + s, "🏦", "BioBank full for " + s + " - eggs fall back to the tray"));
            }
        }
        return out;
    }

    /** The compact CSV of flag ids (badge markers for the Pastures tab) - {@code ""} = healthy. */
    public static String idsCsv(List<Flag> flags) {
        StringBuilder sb = new StringBuilder();
        for (Flag f : flags) {
            if (sb.length() > 0) sb.append(',');
            sb.append(f.id());
        }
        return sb.toString();
    }
}
