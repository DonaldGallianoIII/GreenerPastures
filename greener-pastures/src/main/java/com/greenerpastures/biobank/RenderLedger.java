package com.greenerpastures.biobank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The BioBank's "Send to Renderer" dry-run - given the batch of eggs about to be rendered (destroyed →
 * Data), produce a per-species preview with safety flags, so the player sees exactly what they'll
 * consume BEFORE committing (the {@code −500 Froakie · −90 Charmander ✦shiny} ledger). Minecraft-free.
 *
 * <p>The shiny / perfect-IV flags are an <b>independent scan</b> of the actual batch (not derived from
 * the render filter) - defense-in-depth against a mis-set filter.
 */
public final class RenderLedger {
    private RenderLedger() {}

    /** One species row of the ledger: rendered as {@code -count species [flags]}. */
    public record Line(String species, int count, boolean hasShiny, boolean hasPerfect) {}

    /** The whole preview. {@code anyFlagged} ⇒ the UI should require a confirm click before sending. */
    public record Preview(List<Line> lines, int total, boolean anyFlagged) {}

    /** Build the preview for the eggs about to be rendered, grouped by species in first-seen order. */
    public static Preview of(List<EggSummary> batch) {
        Map<String, int[]> agg = new LinkedHashMap<>();   // species -> {count, shinySeen, perfectSeen}
        for (EggSummary e : batch) {
            int[] a = agg.computeIfAbsent(e.species(), k -> new int[3]);
            a[0]++;
            if (e.shiny()) a[1] = 1;
            if (e.perfectIvs() >= 1) a[2] = 1;
        }
        List<Line> lines = new ArrayList<>(agg.size());
        int total = 0;
        boolean anyFlagged = false;
        for (Map.Entry<String, int[]> en : agg.entrySet()) {
            int[] a = en.getValue();
            boolean shiny = a[1] == 1, perfect = a[2] == 1;
            lines.add(new Line(en.getKey(), a[0], shiny, perfect));
            total += a[0];
            anyFlagged |= shiny || perfect;
        }
        return new Preview(lines, total, anyFlagged);
    }
}
